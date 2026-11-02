import sys, os, zipfile, struct

# \\?\ prefix lifts the Windows 260-char path limit
W = '\\\\?\\' + os.path.abspath(sys.argv[1])
libdir = os.path.join(W, 'bootjar', 'BOOT-INF', 'lib')
common = os.path.join(W, 'common')
lib = os.path.join(W, 'lib')
os.makedirs(common, exist_ok=True)
for jar in sorted(os.listdir(libdir)):
    dest = common if jar.startswith('common-lib') else lib
    with zipfile.ZipFile(os.path.join(libdir, jar)) as z:
        for n in z.namelist():
            if not n.endswith('.class') or n.startswith('META-INF/') or n.endswith('module-info.class'):
                continue
            out = os.path.join(dest, *n.split('/'))
            if os.path.exists(out):
                continue
            os.makedirs(os.path.dirname(out), exist_ok=True)
            with open(out, 'wb') as f:
                f.write(z.read(n))

# Micans bundles ASM 9.4 (max class version 64 = Java 20); downgrade newer headers
stats = {}
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
                if len(h) == 8 and struct.unpack('>H', h[6:8])[0] > 64:
                    f.seek(6)
                    f.write(struct.pack('>H', 61))
                    patched += 1
    stats[d] = (total, patched)
print(stats)
