"""Lays out the classes of a service workdir for Micans.

common-lib is extracted to <workdir>/common (analysed as application code). The other library jars
stay as jars and are listed in <workdir>/libjars.txt: writing their ~50k classes out as files took
~7 min per service on Windows, reading them from the jars takes seconds. A library jar that has
classes newer than Micans' ASM 9.4 can read is extracted to <workdir>/lib instead.
usage: prep_micans.py <workdir>"""
import sys, os, zipfile, struct

# \\?\ prefix lifts the Windows 260-char path limit
W = '\\\\?\\' + os.path.abspath(sys.argv[1])
libdir = os.path.join(W, 'bootjar', 'BOOT-INF', 'lib')
common = os.path.join(W, 'common')
lib = os.path.join(W, 'lib')
os.makedirs(common, exist_ok=True)
os.makedirs(lib, exist_ok=True)


def classes(z):
    return [n for n in z.namelist()
            if n.endswith('.class') and not n.startswith('META-INF/') and not n.endswith('module-info.class')]


def too_new(data):
    return len(data) >= 8 and struct.unpack('>H', data[6:8])[0] > 64


jars = []
for jar in sorted(os.listdir(libdir)):
    path = os.path.join(libdir, jar)
    with zipfile.ZipFile(path) as z:
        names = classes(z)
        if not jar.startswith('common-lib') and not any(too_new(z.open(n).read(8)) for n in names):
            jars.append(path[4:])  # without \\?\: the list is read by Java
            continue
        dest = common if jar.startswith('common-lib') else lib
        for n in names:
            out = os.path.join(dest, *n.split('/'))
            if os.path.exists(out):
                continue
            os.makedirs(os.path.dirname(out), exist_ok=True)
            with open(out, 'wb') as f:
                f.write(z.read(n))
with open(os.path.join(W, 'libjars.txt'), 'w', encoding='utf-8') as f:
    f.write(''.join(j + '\n' for j in jars))

# Micans bundles ASM 9.4 (max class version 64 = Java 20); downgrade newer headers
stats = {'jars': len(jars)}
for d in ('app', 'common', 'lib'):
    patched = total = 0
    for root, _, files in os.walk(os.path.join(W, d)):
        for fn in files:
            if not fn.endswith('.class'):
                continue
            p = os.path.join(root, fn)
            total += 1
            with open(p, 'r+b') as f:
                h = f.read(8)
                if too_new(h):
                    f.seek(6)
                    f.write(struct.pack('>H', 61))
                    patched += 1
    stats[d] = (total, patched)
print(stats)
