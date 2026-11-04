import os, struct, sys, zipfile

# Packs extracted JDK module classes into one rt.jar whose class headers are
# downgraded to Java 17 (61), so Micans' ASM 9.4 / Soot can load them as the JRE model.
# \\?\ prefix lifts the Windows 260-char path limit
src = '\\\\?\\' + os.path.abspath(sys.argv[1])
out_jar = sys.argv[2]
os.makedirs(os.path.dirname(out_jar), exist_ok=True)
seen = set()
patched = 0
with zipfile.ZipFile(out_jar, 'w', zipfile.ZIP_DEFLATED) as z:
    for module in sorted(os.listdir(src)):
        root_dir = os.path.join(src, module)
        for root, _, files in os.walk(root_dir):
            for fn in files:
                if not fn.endswith('.class') or fn == 'module-info.class':
                    continue
                rel = os.path.relpath(os.path.join(root, fn), root_dir).replace(os.sep, '/')
                if rel in seen:
                    continue
                seen.add(rel)
                data = bytearray(open(os.path.join(root, fn), 'rb').read())
                if struct.unpack('>H', data[6:8])[0] > 64:
                    data[6:8] = struct.pack('>H', 61)
                    patched += 1
                z.writestr(rel, bytes(data))
print(len(seen), 'classes,', patched, 'patched')
