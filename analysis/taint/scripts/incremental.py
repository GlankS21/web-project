"""Incremental inter-service taint analysis (ADR-001, option B).

Each service is re-analysed only when something its Micans run reads has changed. Two cache levels:
  run key     = hash(service classes, common-lib classes, library jars, base config, config
                generators, launcher, Micans jar, JDK, context sensitivity)  -> taint/store flows
  summary key = hash(run key, channel model, summarize.py)                   -> summary.json
Composition is cheap and always recomputed from the summaries; its chains to a sink are compared
with a baseline report, and new chains fail the run with --gate.

usage: incremental.py [--services a,b,c] [--model channels.json] [--baseline report.json]
                      [--out report.json] [--gate]
env:   ECOMMERCE (backend dir with built jars), MICANS_JAR, JAVA_HOME, VKR_WORK (default work/)
"""
import argparse
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

T = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORK = os.environ.get('VKR_WORK', os.path.join(T, 'work'))
CACHE = os.path.join(WORK, 'cache')
ECOMMERCE = os.environ.get('ECOMMERCE', os.path.join(T, '..', '..', 'ecommerce', 'backend'))
JAVA_HOME = os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-24')
MICANS_JAR = os.environ.get('MICANS_JAR', 'C:/Users/asgat/OneDrive/Máy tính/WorkSpace/ITMO - Thsi/'
                            'vkr-artifacts/miscans/src-extract/micans-source/build/micans.jar')
CS = '1-call'
SERVICES = ['cart-service', 'order-service', 'inventory-service', 'payment-service']
CONFIG_SCRIPTS = ['prepare.sh', 'prep_micans.py', 'pack_jre.py', 'mk_dto_fields.py', 'mk_event_fields.py',
                  'mk_field_config.py', 'mk_entity_fields.py', 'mk_db_config.py', 'run_taint.sh']
FLOW_PREFIXES = ('TaintFlow{', 'StoreFlow{')


class Hasher:
    """sha256 over named parts, so that moving bytes between parts changes the key."""

    def __init__(self):
        self.h = hashlib.sha256()

    def part(self, name, data):
        self.h.update(f'{name}\0{len(data)}\0'.encode())
        self.h.update(data)

    def file(self, name, path):
        with open(path, 'rb') as f:
            self.part(name, f.read())

    def tree(self, name, root, suffix=''):
        for dirpath, _, files in sorted(os.walk(root)):
            for fn in sorted(files):
                if fn.endswith(suffix):
                    p = os.path.join(dirpath, fn)
                    self.file(f'{name}/{os.path.relpath(p, root)}', p)

    def hexdigest(self):
        return self.h.hexdigest()


def jar_classes(h, name, data):
    """A rebuilt jar differs in its entry timestamps only: hash the class entries' names and CRCs."""
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for e in sorted(z.infolist(), key=lambda e: e.filename):
            if e.filename.endswith('.class'):
                h.part(f'{name}!{e.filename}', e.CRC.to_bytes(4, 'big'))


def run_key(svc):
    x = svc[:-len('-service')]
    h = Hasher()
    h.tree('app', os.path.join(ECOMMERCE, svc, 'target', 'classes'), '.class')
    with zipfile.ZipFile(os.path.join(ECOMMERCE, svc, 'target', f'{svc}.jar')) as boot:
        for e in sorted(boot.infolist(), key=lambda e: e.filename):
            if not (e.filename.startswith('BOOT-INF/lib/') and e.filename.endswith('.jar')):
                continue
            if os.path.basename(e.filename).startswith('common-lib'):
                jar_classes(h, 'common', boot.read(e))  # built with the services: content only
            else:
                h.part(e.filename, e.CRC.to_bytes(4, 'big'))  # released artifact: the jar itself
    h.file('config', os.path.join(T, 'config', f'taint-{x}.yml'))
    for s in CONFIG_SCRIPTS:
        h.file(s, os.path.join(T, 'scripts', s))
    h.tree('launcher', os.path.join(T, 'launcher', 'src'), '.java')
    h.tree('jdk-stubs', os.path.join(T, 'jdk-stubs'), '.java')
    h.file('micans', MICANS_JAR)
    h.file('jdk', os.path.join(JAVA_HOME, 'release'))
    h.part('cs', CS.encode())
    return h.hexdigest()


def chain_key(chain):
    """A chain without line numbers (Class#method:42, publisher and listener ids): code moved by an
    unrelated edit is the same finding, not a new one."""
    return re.sub(r'(#[\w$]+):\d+', r'\1', chain)


def summary_key(rkey, model):
    h = Hasher()
    h.part('run', rkey.encode())
    h.file('model', model)
    h.file('summarize', os.path.join(T, 'scripts', 'summarize.py'))
    return h.hexdigest()


def sh(*args, **kw):
    subprocess.run(['sh', *args], check=True, **kw)


def compile_launcher():
    src = os.path.join(T, 'launcher', 'src')
    files = [os.path.join(d, f) for d, _, fs in os.walk(os.path.join(src, 'vkr')) for f in fs if f.endswith('.java')]
    subprocess.run([os.path.join(JAVA_HOME, 'bin', 'javac'), '--release', '17', '-nowarn', '-cp', MICANS_JAR,
                    '-d', os.path.join(T, 'launcher', 'classes'), *files], check=True)


def prepare(svc, rkey):
    """Fresh workdir (classes and generated configs) for the commit whose run key is rkey."""
    w = os.path.join(WORK, svc)
    marker = os.path.join(w, '.runkey')
    if os.path.exists(marker) and open(marker).read() == rkey:
        return w
    shutil.rmtree(w, ignore_errors=True)
    for f in ('event-sources.yml', 'event-sinks.yml'):  # derived from common-lib, which may have changed
        if os.path.exists(os.path.join(WORK, f)):
            os.remove(os.path.join(WORK, f))
    sh(os.path.join(T, 'scripts', 'prepare.sh'), svc)
    with open(marker, 'w') as f:
        f.write(rkey)
    return w


def analyse(svc, rkey, flows_path):
    """Micans run on a fresh workdir; keeps only the flow lines of the log."""
    w = prepare(svc, rkey)
    log = os.path.join(w, 'run.log')
    sh(os.path.join(T, 'scripts', 'run_taint.sh'), svc, w, os.path.join(w, 'taint-db.yml'),
       os.path.join(w, 'out'), log, CS)
    with open(log, encoding='utf-8', errors='replace') as f:
        lines = f.read().splitlines()
    if not lines or not lines[-1].startswith('exit=0'):
        sys.exit(f'{svc}: analysis failed, see {log}')
    flows = [l for l in lines if l.startswith(FLOW_PREFIXES)]
    with open(flows_path + '.tmp', 'w', encoding='utf-8') as f:
        f.write('\n'.join(flows) + '\n')
    os.replace(flows_path + '.tmp', flows_path)


def summarize(svc, rkey, flows_path, model, out):
    w = prepare(svc, rkey)  # flows may come from the cache while the workdir holds another commit
    subprocess.run([sys.executable, '-I', os.path.join(T, 'scripts', 'summarize.py'), 'summary', svc, flows_path,
                    model, out + '.tmp', os.path.join(w, 'app'), os.path.join(w, 'common')],
                   check=True, stdout=subprocess.DEVNULL)
    os.replace(out + '.tmp', out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--services', default=','.join(SERVICES))
    ap.add_argument('--model', default=os.path.join(T, '..', 'channel-extractor', 'target', 'ecommerce-channels.json'))
    ap.add_argument('--baseline')
    ap.add_argument('--out', default=os.path.join(WORK, 'report.json'))
    ap.add_argument('--gate', action='store_true')
    a = ap.parse_args()
    start = time.time()
    compile_launcher()
    report = {'services': {}}
    summaries = []
    for svc in a.services.split(','):
        t0 = time.time()
        rkey = run_key(svc)
        d = os.path.join(CACHE, svc, rkey[:16])
        os.makedirs(d, exist_ok=True)
        flows = os.path.join(d, 'flows.txt')
        hit_run = os.path.exists(flows)
        if not hit_run:
            analyse(svc, rkey, flows)
        skey = summary_key(rkey, a.model)
        summary = os.path.join(d, f'summary-{skey[:16]}.json')
        hit_summary = os.path.exists(summary)
        if not hit_summary:
            summarize(svc, rkey, flows, a.model, summary)
        summaries.append(summary)
        report['services'][svc] = {'runKey': rkey[:16], 'runCached': hit_run, 'summaryCached': hit_summary,
                                   'seconds': round(time.time() - t0, 1)}
        print(f'{svc}: run {"hit" if hit_run else "miss"}, summary {"hit" if hit_summary else "miss"}, '
              f'{time.time() - t0:.0f} s', flush=True)
    composed = subprocess.run([sys.executable, '-I', os.path.join(T, 'scripts', 'summarize.py'), 'compose',
                               a.model, *summaries], check=True, capture_output=True, text=True).stdout
    chains = sorted({l for l in composed.splitlines() if 'CWE-' in l})
    report['chains'] = chains
    report['seconds'] = round(time.time() - start, 1)
    new = removed = []
    if a.baseline:
        old = json.load(open(a.baseline, encoding='utf-8'))['chains']
        old_keys, cur_keys = {chain_key(c) for c in old}, {chain_key(c) for c in chains}
        new = [c for c in chains if chain_key(c) not in old_keys]
        removed = [c for c in old if chain_key(c) not in cur_keys]
        report['new'], report['removed'] = new, removed
    with open(a.out, 'w', encoding='utf-8') as f:
        json.dump(report, f, indent=1)
    crossing = sum('-->' in c for c in chains)
    print(f'{len(chains)} chains to a sink ({crossing} across a channel)'
          + (f', {len(new)} new, {len(removed)} removed' if a.baseline else '')
          + f'; {report["seconds"]:.0f} s total -> {a.out}')
    for c in new:
        print('NEW', c)
    if a.gate and new:
        sys.exit(1)


if __name__ == '__main__':
    main()
