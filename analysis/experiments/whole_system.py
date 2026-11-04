"""Whole-system baseline: Micans analyses the four services together in one run, as in its paper.

The per-service taint configs prepared by prepare.sh are merged. Kept: the HTTP sources (endpoint
parameters and request-DTO fields), the local sinks (SQL, logging), transfers and sanitizers. Dropped:
the channel ends the per-service analysis needs to stitch services afterwards (event record
components and entity fields as sources; event constructors, Feign clients, RabbitTemplate and the
outbox as sinks):
in one run Micans connects Feign calls and RabbitMQ messages itself. The vkr plugins are the same.

The run and, with --per-service, each service alone are logged with -Xlog:gc, whose largest heap
before a collection is reported as the peak heap.

usage: whole_system.py <workdir prepared for the four services> [--xmx 24g] [--per-service] [--cs ci]
output: <workdir>/whole/{taint.yml,run.log,gc.log}, <workdir>/<service>/gc-run.log, a table on stdout
"""
import argparse
import os
import re
import subprocess
import time

HERE = os.path.dirname(os.path.abspath(__file__))
T = os.path.join(os.path.dirname(HERE), 'taint')
JAVA_HOME = os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-24')
MICANS_JAR = os.environ.get('MICANS_JAR', 'C:/Users/asgat/OneDrive/Máy tính/WorkSpace/ITMO - Thsi/'
                            'vkr-artifacts/miscans/src-extract/micans-source/build/micans.jar')
SERVICES = ['cart-service', 'order-service', 'inventory-service', 'payment-service']
SECTIONS = ('sources', 'sinks', 'transfers', 'sanitizers')
CHANNEL_SOURCE = re.compile(r'kind: field, field: "<com\.shop\.(common\.event|\w+\.entity)\.')
CHANNEL_SINK = re.compile(r'<(com\.shop\.(common\.event\.\w+: void <init>|\w+\.client\.|[\w.]+: void enqueue\()'
                          r'|org\.springframework\.amqp\.)')


def merged_config(work, out):
    entries = {s: [] for s in SECTIONS}
    for svc in SERVICES:
        section = None
        for line in open(os.path.join(work, svc, 'taint-db.yml'), encoding='utf-8'):
            head = re.match(r'^(\w+):', line)
            if head:
                section = head.group(1) if head.group(1) in SECTIONS else None
            elif section and line.lstrip().startswith('- '):
                entry = line.split('  #')[0].strip()
                if section == 'sources' and CHANNEL_SOURCE.search(entry):
                    continue
                if section == 'sinks' and CHANNEL_SINK.search(entry):
                    continue
                if entry not in entries[section]:
                    entries[section].append(entry)
    with open(out, 'w', encoding='utf-8') as f:
        f.write('# merged by whole_system.py: HTTP sources, local sinks\ncall-site-mode: true\n')
        for s in SECTIONS:
            f.write(f'\n{s}:\n' + ''.join(f'  {e}\n' for e in entries[s]))
    return {s: len(v) for s, v in entries.items()}


def run(work, names, config, out, xmx, cs):
    """one Micans run of the given services; returns (exit code, seconds, peak heap MB, flows)"""
    os.makedirs(out, exist_ok=True)
    w = lambda *p: os.path.join(work, *p).replace('/', '\\')
    apps = '|'.join(f'{w(s, "app")},{w(s, "common")}' for s in names)
    libs = '|'.join(f'{w(s, "lib")},@{w(s, "libjars.txt")}' for s in names)
    gc = os.path.join(out, 'gc.log')
    cmd = [os.path.join(JAVA_HOME, 'bin', 'java'), f'-Xmx{xmx}', '-Xss512m', f'-Xlog:gc:file={gc}',
           '-cp', f'{os.path.join(T, "launcher", "classes")};{MICANS_JAR}', 'vkr.MicansTaintMain',
           ','.join(names), apps, libs, config, os.path.join(out, 'tai-e'), cs]
    t0 = time.time()
    with open(os.path.join(out, 'run.log'), 'w', encoding='utf-8') as log:
        # Tai-e resolves the JRE model relative to the working directory
        code = subprocess.run(cmd, cwd=os.path.join(work, 'jre-home'), stdout=log, stderr=subprocess.STDOUT).returncode
    seconds = time.time() - t0
    peak = max((int(m) for m in re.findall(r'(\d+)M->\d+M', open(gc, encoding='utf-8').read())), default=0)
    with open(os.path.join(out, 'run.log'), encoding='utf-8', errors='replace') as f:
        flows = sum(1 for l in f if l.startswith('TaintFlow{'))
    return code, seconds, peak, flows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('work')
    ap.add_argument('--xmx', default='24g')
    ap.add_argument('--cs', default='ci')
    ap.add_argument('--per-service', action='store_true')
    a = ap.parse_args()
    rows = []
    if a.per_service:
        for svc in SERVICES:
            rows.append((svc, *run(a.work, [svc], os.path.join(a.work, svc, 'taint-db.yml'),
                                   os.path.join(a.work, svc, 'gc-run'), a.xmx, a.cs)))
    else:
        config = os.path.join(a.work, 'whole', 'taint.yml')
        os.makedirs(os.path.dirname(config), exist_ok=True)
        print('merged config:', merged_config(a.work, config))
        rows.append(('whole system', *run(a.work, SERVICES, config, os.path.join(a.work, 'whole'), a.xmx, a.cs)))
    for name, code, seconds, peak, flows in rows:
        print(f'{name:18} exit={code} seconds={seconds:.0f} peakHeapMB={peak} taintFlows={flows}')


if __name__ == '__main__':
    main()
