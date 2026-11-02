"""Markdown view of an incremental.py report, for the CI job summary.
usage: report_md.py <report.json> [title]"""
import json
import sys

r = json.load(open(sys.argv[1], encoding='utf-8'))
print(f'## {sys.argv[2] if len(sys.argv) > 2 else "Inter-service taint analysis"}\n')
print('| Service | Micans run | Summary | Seconds |\n|---|---|---|---|')
for svc, s in r['services'].items():
    print(f'| {svc} | {"cached" if s["runCached"] else "recomputed"} | '
          f'{"cached" if s["summaryCached"] else "recomputed"} | {s["seconds"]:.0f} |')
print(f'\n{len(r["chains"])} chains to a sink, {r["seconds"]:.0f} s total.')
if 'new' not in r:
    print('\nNo baseline: chains were not compared.')
else:
    print(f'{len(r["new"])} new, {len(r["removed"])} removed against the baseline.')
    for title, key in (('New chains', 'new'), ('Removed chains', 'removed')):
        if r[key]:
            print(f'\n### {title}\n\n```')
            print('\n'.join(r[key]))
            print('```')
