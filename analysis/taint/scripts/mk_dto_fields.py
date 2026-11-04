"""Field sources for the request DTOs of a service: every non-list `private final` field of the
classes in its request-DTO package, nested ones included (e.g. CreateOrderRequest.items[*].productId).
Inserted into the service's base config before `sinks:`.
usage: mk_dto_fields.py <app classes> <dto package> <config.yml> <out.yml>"""
import os
import re
import subprocess
import sys

JAVAP = os.path.join(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-24'), 'bin', 'javap')

app, pkg, src, out = sys.argv[1:5]
lines = ['  # request DTOs are only built by deserialising the HTTP body, so every scalar field of a',
         '  # nested DTO (e.g. CreateOrderRequest.items[*].productId) is attacker-controlled']
pkg_dir = os.path.join(app, *pkg.split('.'))
for f in sorted(os.listdir(pkg_dir)):
    if not f.endswith('.class'):
        continue
    cls = f'{pkg}.{f[:-6]}'
    text = subprocess.run([JAVAP, '-p', '-cp', app, cls], capture_output=True, text=True).stdout
    for typ, name in re.findall(r'^\s*private final (\S+) ([a-zA-Z0-9]+);', text, re.M):
        if 'java.util.List' not in typ:
            lines.append(f'  - {{ kind: field, field: "<{cls}: {typ} {name}>" }}')
s = open(src, encoding='utf-8').read()
assert '\nsinks:\n' in s and 'kind: field' not in s
s = s.replace('\nsinks:\n', '\n' + '\n'.join(lines) + '\n\nsinks:\n', 1)
open(out, 'w', encoding='utf-8').write(s)
print(len(lines) - 2, 'request-DTO field sources')
