set -u
ls $ANDROID_HOME/ndk
NDKL=${ANDROID_NDK_LATEST_HOME}
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "ndk;25.2.9519653" "ndk;23.2.8568313" > /dev/null 2>&1
ls $ANDROID_HOME/ndk
NM=$NDKL/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm
RE=$NDKL/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf
echo "### libc++_shared exports of emutls per NDK"
for n in $ANDROID_HOME/ndk/*; do
  f=$n/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so
  echo "$(basename $n): $($NM -D --defined-only $f 2>/dev/null | grep -c emutls) emutls syms; $($NM -D --defined-only $f | wc -l) total"
  $NM -D --defined-only $f | grep -i emutls
done
echo "### builtins emutls"
for n in $ANDROID_HOME/ndk/*; do
  for a in $(find $n/toolchains/llvm/prebuilt/linux-x86_64/lib* -name 'libclang_rt.builtins-aarch64-android.a' 2>/dev/null); do
    echo "$a"; $NM $a 2>/dev/null | grep -i "emutls_get_address"
  done
done
SYS=$NDKL/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/33
$NM -D --defined-only $SYS/*.so 2>/dev/null | awk '{print $NF}' | sort -u > sys.txt
$NM -D --defined-only $NDKL/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so | awk '{print $NF}' | sort -u > cxx.txt
wc -l sys.txt cxx.txt
for v in 8 17 21 25; do
  mkdir -p jre$v && curl -fsSL https://github.com/pauloestefani/JavaWorld/releases/download/runtimes/jre$v-android-arm64.tar.xz | tar -xJ -C jre$v
  echo "### JRE $v"
  find jre$v -name '*.so' | while read f; do $NM -D --defined-only "$f" 2>/dev/null | awk '{print $NF}'; done | sort -u > def$v.txt
  echo "NEEDED (all libs):"; find jre$v -name '*.so' -exec $RE -d {} \; 2>/dev/null | grep NEEDED | awk '{print $NF}' | sort | uniq -c
  echo "Unresolved:"
  find jre$v -name '*.so' | while read f; do
    $NM -D --undefined-only "$f" 2>/dev/null | awk '{print $NF}' | sed 's/@.*//' | sort -u | while read s; do
      grep -qxF "$s" def$v.txt || grep -qxF "$s" sys.txt || grep -qxF "$s" cxx.txt || echo "  $f: $s"
    done
  done | head -60
  echo "ident note libjvm:"; $RE -n $(find jre$v -name libjvm.so | head -1) | grep -A3 -i android | head -8
done
echo "### Amethyst APK"
url=$(gh api repos/AngelAuraMC/Amethyst-Android/releases/latest --jq '.assets[] | select(.name|endswith(".apk")) | .browser_download_url' | head -3)
echo "$url"
u=$(echo "$url" | grep -i -E 'arm64|universal|release' | head -1); [ -z "$u" ] && u=$(echo "$url" | head -1)
curl -fsSL -o am.apk "$u" && mkdir am && cd am && unzip -q ../am.apk 'lib/arm64-v8a/*' && ls lib/arm64-v8a | head -50
f=lib/arm64-v8a/libc++_shared.so; [ -f $f ] && { echo "amethyst libc++ emutls:"; $NM -D --defined-only $f | grep -i emutls; }
for f in lib/arm64-v8a/*.so; do $NM -D --defined-only $f 2>/dev/null | grep -q __emutls_get_address && echo "exports emutls: $f"; done
