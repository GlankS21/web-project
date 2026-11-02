#!/bin/sh
# usage: run_taint.sh <service> <workdir> <taint.yml> <outdir> <log> [cs]
# env: MICANS_JAR (Micans build jar), JAVA_HOME (JDK running the analysis), VKR_DEBUG (DebugDump filter),
#      VKR_WORK (work dir with the JRE model, default work/)
T=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-"C:/Program Files/Java/jdk-24"}
MICANS_JAR=${MICANS_JAR:-"C:/Users/asgat/OneDrive/Máy tính/WorkSpace/ITMO - Thsi/vkr-artifacts/miscans/src-extract/micans-source/build/micans.jar"}
W=$(cygpath -w "$2")
MJ=$(cygpath -w "$MICANS_JAR")
LC=$(cygpath -w "$T/launcher/classes")
rm -rf "$4"
# Tai-e resolves the JRE model (java-benchmarks/JREs/jre1.8/rt.jar) relative to the working directory
cd "${VKR_WORK:-$T/work}/jre-home" || exit 2
start=$(date +%s)
"$JAVA_HOME/bin/java" -Xmx24g -Xss512m ${VKR_DEBUG:+-Dvkr.debug=$VKR_DEBUG} -cp "$LC;$MJ" vkr.MicansTaintMain "$1" \
  "$W\app,$W\common" "$W\lib,@$W\libjars.txt" "$(cygpath -w "$3")" "$(cygpath -w "$4")" ${6:-ci} > "$5" 2>&1
echo "exit=$? seconds=$(( $(date +%s) - start ))" | tee -a "$5"
