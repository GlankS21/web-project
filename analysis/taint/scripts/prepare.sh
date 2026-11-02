#!/bin/sh
# Builds work/ for the given services: the JRE model, the extracted application and library
# classes, and the generated taint configs (coarse -> field-level -> database channel).
# usage: prepare.sh <service>...        e.g. prepare.sh cart-service order-service inventory-service
# env: JAVA_HOME (JDK 24 whose modules form the JRE model), ECOMMERCE (backend dir with built jars)
set -e
T=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-"C:/Program Files/Java/jdk-24"}
ECOMMERCE=${ECOMMERCE:-"$T/../../ecommerce/backend"}
export JAVA_HOME
K="$T/work"
mkdir -p "$K"

# JRE model: JDK classes downgraded to class version 61 for Micans' ASM 9.4, packed where Tai-e
# looks for a Java 8 rt.jar; record bootstraps resolve through an analysis-only ObjectMethods stub
RT="$K/jre-home/java-benchmarks/JREs/jre1.8/rt.jar"
if [ ! -f "$RT" ]; then
    MODS="java.base java.logging java.sql java.naming java.desktop java.management java.xml java.instrument java.compiler jdk.unsupported java.transaction.xa java.net.http java.scripting java.security.jgss java.rmi java.datatransfer java.prefs"
    INC=$(for m in $MODS; do printf "regex:/%s/.*," "$m"; done)
    rm -rf "$K/jdkx" "$K/stubs"
    "$JAVA_HOME/bin/jimage" extract --include "${INC%,}" --dir "$(cygpath -w "$K/jdkx")" "$(cygpath -w "$JAVA_HOME/lib/modules")"
    (cd "$T/jdk-stubs" && "$JAVA_HOME/bin/javac" --release 8 -d "$K/stubs" java/lang/invoke/*.java java/lang/runtime/*.java)
    python -I "$T/scripts/pack_jre.py" "$K/jdkx" "$RT"
    python -I - "$RT" "$K/stubs/java/lang/runtime/ObjectMethods.class" <<'EOF'
import sys, zipfile, os
rt, stub = sys.argv[1], sys.argv[2]
tmp = rt + '.tmp'
name = 'java/lang/runtime/ObjectMethods.class'
with zipfile.ZipFile(rt) as zin, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, open(stub, 'rb').read() if item.filename == name else zin.read(item.filename))
os.replace(tmp, rt)
print('replaced', name)
EOF
fi

for svc in "$@"; do
    x=${svc%-service}
    W="$K/$svc"
    if [ ! -d "$W/app" ]; then
        rm -rf "$W"
        mkdir -p "$W/bootjar"
        cp -r "$ECOMMERCE/$svc/target/classes" "$W/app"
        (cd "$W/bootjar" && unzip -q "$ECOMMERCE/$svc/target/$svc.jar" 'BOOT-INF/lib/*')
        python -I "$T/scripts/prep_micans.py" "$W"
    fi
    # event records are shared through common-lib: same field config for every service
    [ -f "$K/event-sources.yml" ] || (cd "$K" && python -I "$T/scripts/mk_event_fields.py" "$W/common" event-sources.yml event-sinks.yml)
    base="$T/config/taint-$x.yml"
    if [ -d "$W/app/com/shop/$x/dto/request" ] && grep -q 'mk_dto_fields' "$base"; then
        python -I "$T/scripts/mk_dto_fields.py" "$W/app" "com.shop.$x.dto.request" "$base" "$W/taint-coarse.yml"
    else
        cp "$base" "$W/taint-coarse.yml"
    fi
    (cd "$K" && python -I "$T/scripts/mk_field_config.py" "$W/taint-coarse.yml" "$W/taint-field.yml")
    python -I "$T/scripts/mk_entity_fields.py" "$W/app" "com.shop.$x.entity" "$W/entity-sources.yml"
    python -I "$T/scripts/mk_db_config.py" "$W/taint-field.yml" "$W/entity-sources.yml" "$W/taint-db.yml"
    echo "$svc: $W/taint-db.yml"
done
