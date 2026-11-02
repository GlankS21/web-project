"""Runs the change and seeded-vulnerability scenarios of the experiment (scenarios.json).

The scenarios are applied to a copy of ecommerce/backend and analysis/taint, never to the repository
itself. The copy is a git repository with one commit, `base`; each scenario resets it to `base`,
applies its patch (paths backend/... and taint/...), builds the analysed services, extracts the
channel model and runs incremental.py against the warm cache of earlier runs. With --cold the same
commit is also analysed with an empty cache, and the two chain sets are compared (RQ2).

usage: run_experiments.py [--only S01,V03] [--cold] [--repeat N] [--refresh]
env:   VKR_EXP (default %LOCALAPPDATA%/vkr-exp), JAVA_HOME, MICANS_JAR
output: <VKR_EXP>/out/<scenario>/{patch.diff,build.log,channels.json,report.json,report-cold.json},
        <VKR_EXP>/out/results.csv (one row per run, appended)
"""
import argparse
import csv
import json
import os
import re
import shutil
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
EXP = os.environ.get('VKR_EXP', os.path.join(os.environ.get('LOCALAPPDATA', os.path.expanduser('~')), 'vkr-exp'))
REPO = os.path.join(EXP, 'repo')
OUT = os.path.join(EXP, 'out')
JAVA_HOME = os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-24')
SERVICES = ['cart-service', 'order-service', 'inventory-service']
EXTRACTOR = os.path.join(ROOT, 'analysis', 'channel-extractor')
EXTRACTOR_JAR = os.path.join(EXTRACTOR, 'target', 'channel-extractor-0.1.0-SNAPSHOT.jar')
TEXT = ('.java', '.xml', '.yml', '.yaml', '.properties', '.sql', '.json', '.md', '.sh', '.py', '.txt', 'mvnw')
FIELDS = ['scenario', 'kind', 'run', 'rerun', 'expectedRerun', 'rerunAsExpected', 'buildSeconds', 'seconds',
          'serviceSeconds', 'chains', 'crossing', 'new', 'removed', 'expectation', 'coldSeconds', 'equivalent']


def git(*args, **kw):
    return subprocess.run(['git', '-C', REPO, *args], check=True, capture_output=True, text=True, **kw).stdout


def refresh_copy():
    """Copies the analysed sources (no build output) and commits them as `base`."""
    def writable(func, path, _):  # git object files are read-only on Windows
        os.chmod(path, 0o700)
        func(path)
    if os.path.isdir(REPO):
        shutil.rmtree(REPO, onerror=writable)
    skip = shutil.ignore_patterns('target', 'node_modules', '.idea', 'work', 'classes', '__pycache__')
    shutil.copytree(os.path.join(ROOT, 'ecommerce', 'backend'), os.path.join(REPO, 'backend'), ignore=skip)
    shutil.copytree(os.path.join(ROOT, 'analysis', 'taint'), os.path.join(REPO, 'taint'), ignore=skip)
    # LF everywhere: the working tree's line endings depend on core.autocrlf, the patches must not
    for d, _, files in os.walk(REPO):
        for fn in files:
            if fn.endswith(TEXT):
                p = os.path.join(d, fn)
                data = open(p, 'rb').read()
                if b'\r\n' in data:
                    open(p, 'wb').write(data.replace(b'\r\n', b'\n'))
    subprocess.run(['git', 'init', '-q', REPO], check=True)
    for k, v in (('core.autocrlf', 'false'), ('user.name', 'exp'), ('user.email', 'exp@local')):
        git('config', k, v)
    git('add', '-A')
    git('commit', '-qm', 'base')
    git('tag', '-f', 'base')


def checkout(scenario):
    git('reset', '-q', '--hard', 'base')
    git('clean', '-qfdx')  # build output too: a class deleted by the previous patch must not survive
    if scenario.get('patch'):
        patch = os.path.join(HERE, 'patches', scenario['patch'])
        subprocess.run(['git', '-C', REPO, 'apply', '--whitespace=nowarn', patch], check=True)


def build(log):
    t0 = time.time()
    mvnw = ['sh', 'mvnw', '-B', '-ntp', '-Dmaven.test.skip=true', '-pl', ','.join(SERVICES), '-am', 'package']
    with open(log, 'w', encoding='utf-8') as f:
        subprocess.run(mvnw, cwd=os.path.join(REPO, 'backend'), check=True, stdout=f, stderr=subprocess.STDOUT,
                       env={**os.environ, 'JAVA_HOME': JAVA_HOME})
    return time.time() - t0


def extract_model(out):
    if not os.path.exists(EXTRACTOR_JAR):
        subprocess.run(['sh', 'mvnw', '-B', '-ntp', '-q', '-DskipTests', 'package'], cwd=EXTRACTOR, check=True,
                       env={**os.environ, 'JAVA_HOME': JAVA_HOME})
    subprocess.run([os.path.join(JAVA_HOME, 'bin', 'java'), '-jar', EXTRACTOR_JAR, os.path.join(REPO, 'backend'),
                    '--out', out], check=True, capture_output=True)


def analyse(work, model, report, baseline=None):
    args = [sys.executable, '-I', os.path.join(REPO, 'taint', 'scripts', 'incremental.py'),
            '--model', model, '--out', report]
    if baseline:
        args += ['--baseline', baseline]
    env = {**os.environ, 'VKR_WORK': work, 'ECOMMERCE': os.path.join(REPO, 'backend'), 'JAVA_HOME': JAVA_HOME,
           'PYTHONUTF8': '1'}
    subprocess.run(args, check=True, env=env)
    return json.load(open(report, encoding='utf-8'))


def cold_work():
    """Empty cache and workdirs; the JRE model (jre-home) is kept, it does not depend on the commit."""
    work = os.path.join(EXP, 'work-cold')
    if os.path.isdir(work):
        for name in os.listdir(work):
            if name != 'jre-home':
                p = os.path.join(work, name)
                shutil.rmtree(p) if os.path.isdir(p) else os.remove(p)
    else:
        os.makedirs(work)
    if not os.path.isdir(os.path.join(work, 'jre-home')) and os.path.isdir(os.path.join(EXP, 'work', 'jre-home')):
        shutil.copytree(os.path.join(EXP, 'work', 'jre-home'), os.path.join(work, 'jre-home'))
    return work


def expectation(scenario, report):
    """vuln: a new chain matches every pattern; negative: no new chain matches them all; change: none."""
    patterns = scenario.get('chain')
    if not patterns:
        return ''
    hits = [c for c in report.get('new', []) if all(re.search(p, c) for p in patterns)]
    if scenario['kind'] == 'vuln':
        return 'found' if hits else 'MISSED'
    return 'FALSE POSITIVE' if hits else 'not reported'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', help='comma-separated scenario ids (the base run S00 is always available)')
    ap.add_argument('--cold', action='store_true', help='also analyse each commit with an empty cache')
    ap.add_argument('--repeat', type=int, default=1)
    ap.add_argument('--refresh', action='store_true', help='recreate the copy from the current working tree')
    a = ap.parse_args()
    scenarios = json.load(open(os.path.join(HERE, 'scenarios.json'), encoding='utf-8'))
    if a.refresh or not os.path.isdir(os.path.join(REPO, '.git')):
        refresh_copy()
    os.makedirs(OUT, exist_ok=True)
    base_report = os.path.join(OUT, 'S00', 'report.json')
    chosen = [s for s in scenarios if not a.only or s['id'] in a.only.split(',')]
    if not os.path.exists(base_report) and chosen[0]['id'] != 'S00':
        chosen.insert(0, scenarios[0])  # every scenario is compared with the base commit
    results = os.path.join(OUT, 'results.csv')
    new_file = not os.path.exists(results)
    with open(results, 'a', newline='', encoding='utf-8') as f:
        rows = csv.DictWriter(f, FIELDS)
        if new_file:
            rows.writeheader()
        for s in chosen:
            for run in range(1, a.repeat + 1):
                d = os.path.join(OUT, s['id'])
                os.makedirs(d, exist_ok=True)
                print(f'== {s["id"]} run {run}: {s["description"]}', flush=True)
                checkout(s)
                with open(os.path.join(d, 'patch.diff'), 'w', encoding='utf-8') as p:
                    p.write(git('diff', 'base'))
                build_s = build(os.path.join(d, 'build.log'))
                model = os.path.join(d, 'channels.json')
                extract_model(model)
                is_base = s['id'] == 'S00'
                r = analyse(os.path.join(EXP, 'work'), model, os.path.join(d, 'report.json'),
                            None if is_base else base_report)
                rerun = [svc for svc, x in r['services'].items() if not x['runCached']]
                row = {'scenario': s['id'], 'kind': s['kind'], 'run': run, 'rerun': ' '.join(rerun),
                       'expectedRerun': ' '.join(s.get('rerun', [])),
                       'rerunAsExpected': 'rerun' not in s or sorted(rerun) == sorted(s['rerun']),
                       'buildSeconds': round(build_s), 'seconds': r['seconds'],
                       'serviceSeconds': ' '.join(f'{x["seconds"]:.0f}' for x in r['services'].values()),
                       'chains': len(r['chains']), 'crossing': sum('-->' in c for c in r['chains']),
                       'new': len(r.get('new', [])), 'removed': len(r.get('removed', [])),
                       'expectation': expectation(s, r)}
                if a.cold:
                    c = analyse(cold_work(), model, os.path.join(d, 'report-cold.json'))
                    row['coldSeconds'] = c['seconds']
                    row['equivalent'] = c['chains'] == r['chains']
                rows.writerow(row)
                f.flush()
                print(' '.join(f'{k}={v}' for k, v in row.items()), flush=True)


if __name__ == '__main__':
    main()
