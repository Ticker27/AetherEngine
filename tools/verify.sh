#!/bin/sh
# AetherEngine — local verify gate (รันก่อน push ทุกครั้ง)
# ต้องมี: android.jar + flutter stubs ที่ $AETHER_SDK (ค่าเริ่มต้น /var/minis/workspace/aether-tools/sdk)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AE="$ROOT"
SDK="${AETHER_SDK:-/var/minis/workspace/aether-tools/sdk}"
cd "$AE"
echo "== 1) Java compile (android.jar + flutter stubs) =="
rm -rf /tmp/javac-out && mkdir -p /tmp/javac-out
javac -encoding UTF-8 -source 17 -target 17 -nowarn \
  -cp "$SDK/android.jar:/tmp/stub-out" -d /tmp/javac-out \
  $(find android/app/src/main/java -name '*.java')
echo "   OK"
echo "== 2) C++ syntax =="
JDK=/usr/lib/jvm/java-17-openjdk
g++ -fsyntax-only -std=c++17 -I "$SDK/cpp-stubs" -I $JDK/include -I $JDK/include/linux \
  android/app/src/main/cpp/aether.cpp
echo "   OK"
echo "== 3) XML/YAML valid =="
python3 - <<'PY'
import xml.etree.ElementTree as ET, yaml, glob
for p in glob.glob('android/app/src/main/res/**/*.xml', recursive=True) + ['android/app/src/main/AndroidManifest.xml']:
    ET.parse(p)
yaml.safe_load(open('.github/workflows/build.yml'))
print('   OK')
PY
echo "== 4) manifest classes exist =="
python3 - <<'PY'
import re, os
man=open('android/app/src/main/AndroidManifest.xml').read()
miss=0
names = set()
for m in re.finditer(r'<(activity|service|provider|receiver|application)\b[^>]*android:name="([^"]+)"', man, re.S):
    names.add(m.group(2))
for n in sorted(names):
    if not n.startswith('com.'):
        continue
    cls=n.split('$')[0]
    if not os.path.exists('android/app/src/main/java/'+cls.replace('.','/')+'.java'):
        print('   MISS', n); miss=1
print('   OK' if not miss else '   FAIL'); exit(miss)
PY
echo "== 5) router codes ⊇ dart codes =="
python3 - <<'PY'
import re
rt=set(re.findall(r'case "(\w)":', open('android/app/src/main/java/com/aether/CommandRouter.java').read()))
dt=set(re.findall(r"dispatch\('(\w)'", open('lib/core/engine_channel.dart').read()))
assert dt <= rt, (dt - rt)
print('   OK', sorted(rt))
PY
echo "== 6) no hidden-class imports =="
if rg -n 'import android\.(app\.ActivityThread|os\.Process);' android/app/src/main/java; then echo '   FAIL'; exit 1; else echo '   OK'; fi
echo "ALL LOCAL GATES PASSED"
