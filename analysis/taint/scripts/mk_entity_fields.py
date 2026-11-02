"""Field sources for the persistent state of a service: every reference-typed field of the classes
in its entity package (entities and embeddables). A load of such a field reads what an earlier
request may have stored, so its taint label 'Entity.field' is the receiving end of a database
channel (the sending end is a StoreFlow reported by vkr.plugin.SpringDataModel).
usage: mk_entity_fields.py <app classes> <entity package> <out.yml>"""
import os
import re
import subprocess
import sys

JAVAP = os.path.join(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-24'), 'bin', 'javap')
SKIP_TYPES = re.compile(r'^(java\.util\.(List|Set|Map|Collection)|byte\[\])')

app, pkg, out = sys.argv[1:4]
lines = ['  # entity fields: what a request reads back from the database']
pkg_dir = os.path.join(app, *pkg.split('.'))
for f in sorted(os.listdir(pkg_dir)):
    if not f.endswith('.class') or '$' in f:
        continue
    cls = f'{pkg}.{f[:-6]}'
    text = subprocess.run([JAVAP, '-p', '-cp', app, cls], capture_output=True, text=True).stdout
    if re.search(r'\bfinal class \S+ extends java\.lang\.Enum|\benum\b', text.split('{')[0]):
        continue
    for typ, name in re.findall(r'^\s+(?:private|protected) (?!static)(?:final )?([\w.$\[\]]+) (\w+);', text, re.M):
        if '.' not in typ or SKIP_TYPES.match(typ) or typ.startswith(pkg):
            continue  # primitives carry no taint object; collections and nested entities are walked into
        lines.append(f'  - {{ kind: field, field: "<{cls}: {typ} {name}>" }}')
open(out, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
print(len(lines) - 1, 'entity field sources')
