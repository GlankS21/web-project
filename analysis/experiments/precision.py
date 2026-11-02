"""Labels the reported chains with the rules of labels.json and computes the precision.

Two units are counted: chains as the analysis prints them, and findings, which merge the chains of
one entry point and one sink statement (the same flow is printed once per granularity of the source,
<1>, <1/reason>, <CancelOrderRequest.reason>, and once per path through the channels). A finding is
a TP when one of its chains is.

usage: precision.py [--out DIR] [--report S00/report.json --new V01/report.json ...]
default: S00 (all chains) and the new chains of every vuln and negative scenario in scenarios.json
output: <DIR>/labels.csv (one row per distinct chain), the precision table on stdout
"""
import argparse
import csv
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
EXP = os.environ.get('VKR_EXP', os.path.join(os.environ.get('LOCALAPPDATA', os.path.expanduser('~')), 'vkr-exp'))
OUT = os.path.join(EXP, 'out')


def chain_key(chain):  # same as incremental.py: line numbers do not make a chain different
    return re.sub(r'(#[\w$]+):\d+', r'\1', chain)


def finding_key(chain):
    """entry point without its field label + sink statement"""
    root = re.sub(r' <[^>]*>$', '', chain.split('  ')[0])
    return root, re.search(r'CWE-\d+ @ \S+', chain).group(0)


def label(chain, rules):
    for r in rules:
        if all(re.search(p, chain) for p in r['all']):
            return r
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    rules = json.load(open(os.path.join(HERE, 'labels.json'), encoding='utf-8'))['rules']
    scenarios = json.load(open(os.path.join(HERE, 'scenarios.json'), encoding='utf-8'))
    groups = {'S00': json.load(open(os.path.join(a.out, 'S00', 'report.json'), encoding='utf-8'))['chains']}
    for s in scenarios:
        if s['kind'] in ('vuln', 'negative'):
            groups[s['id']] = json.load(open(os.path.join(a.out, s['id'], 'report.json'), encoding='utf-8'))['new']

    labelled, missing = {}, []
    for sid, chains in groups.items():
        for c in chains:
            r = label(c, rules)
            if r is None:
                missing.append(c)
                continue
            row = labelled.setdefault(chain_key(c), {'chain': c, 'label': r['label'], 'reason': r['reason'],
                                                     'rule': r['id'], 'scenarios': []})
            row['scenarios'].append(sid)
    if missing:
        print(f'{len(missing)} chains match no rule:', *sorted(set(missing)), sep='\n', file=sys.stderr)
        sys.exit(1)

    with open(os.path.join(a.out, 'labels.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, ['label', 'reason', 'rule', 'scenarios', 'chain'])
        w.writeheader()
        for row in sorted(labelled.values(), key=lambda r: r['chain']):
            w.writerow({**row, 'scenarios': ' '.join(row['scenarios'])})

    def precision(rows):
        tp = sum(r['label'] == 'TP' for r in rows)
        findings = {}
        for r in rows:
            k = finding_key(r['chain'])
            findings[k] = findings.get(k, False) or r['label'] == 'TP'
        ftp = sum(findings.values())
        return (f'{tp}/{len(rows)} = {tp / len(rows):.2f}' if rows else '-',
                f'{ftp}/{len(findings)} = {ftp / len(findings):.2f}' if findings else '-')

    print(f'{"set":<10} {"chains TP/all":<20} {"findings TP/all":<20} FP reasons')
    for sid, chains in groups.items():
        rows = [labelled[chain_key(c)] for c in chains]
        reasons = {}
        for r in rows:
            if r['label'] == 'FP':
                reasons[r['reason']] = reasons.get(r['reason'], 0) + 1
        p, fp = precision(rows)
        print(f'{sid:<10} {p:<20} {fp:<20} {" ".join(f"{k}={v}" for k, v in sorted(reasons.items()))}')
    p, fp = precision(list(labelled.values()))
    print(f'{"all":<10} {p:<20} {fp:<20} (distinct chains of all sets)')


if __name__ == '__main__':
    main()
