"""Writes the shared field-level channel config for common-lib event records:
 - every component of an event record is a field source (receiver side: one taint label per field),
 - every constructor argument is a sink (sender side: which field of the payload is tainted).
usage: mk_event_fields.py <common classes dir> <out sources.yml> <out sinks.yml>
"""
import os
import re
import subprocess
import sys

JAVAP = os.path.join(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-24'), 'bin', 'javap')
common, out_sources, out_sinks = sys.argv[1:4]
pkg = os.path.join(common, 'com', 'shop', 'common', 'event')
sources, sinks = [], []
for f in sorted(os.listdir(pkg)):
    if not f.endswith('.class') or '$' in f:
        continue
    cls = 'com.shop.common.event.' + f[:-6]
    out = subprocess.run([JAVAP, '-p', '-cp', common, cls], capture_output=True, text=True).stdout
    if 'extends java.lang.Record' not in out:
        continue
    fields = re.findall(r'private final (\S+) (\w+);', out)
    for ftype, name in fields:
        if not ftype.startswith('java.util.List'):  # list elements are covered by their own record
            sources.append(f'  - {{ kind: field, field: "<{cls}: {ftype} {name}>" }}')
    erased = ','.join(re.sub(r'<.*>', '', t) for t, _ in fields)
    for i, (ftype, name) in enumerate(fields):
        sinks.append(f'  - {{ method: "<{cls}: void <init>({erased})>", index: {i} }}  # {name}')
open(out_sources, 'w', encoding='utf-8').write(
    '  # event record components: receiver-side taint label per field\n' + '\n'.join(sources) + '\n')
open(out_sinks, 'w', encoding='utf-8').write(
    '  # event constructors: sender-side, which payload field is tainted\n' + '\n'.join(sinks) + '\n')
print(len(sources), 'field sources,', len(sinks), 'constructor sinks')
