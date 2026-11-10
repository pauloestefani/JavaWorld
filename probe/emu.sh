#!/usr/bin/env bash
set -u
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
for JV in 21 17; do
  url=$(grep -iE "^  [^ ]*jre-?${JV}[^ ]*(x86_64|amd64)[^ ]* " assets.txt | head -1 | awk '{print $3}')
  [ -z "$url" ] && url=$(grep -iE "^  [^ ]*(jre|jdk)[^ ]*${JV}[^ ]*(x86_64|amd64)[^ ]* " assets.txt | head -1 | awk '{print $3}')
  echo "### JRE $JV url=$url"
  [ -z "$url" ] && continue
  rm -rf jre$JV && mkdir -p jre$JV && cd jre$JV
  curl -sL -o pkg "$url"
  file pkg
  case "$(file -b pkg)" in
    *Zip*) unzip -q pkg; rm pkg ;;
    *XZ*) tar -xJf pkg; rm pkg ;;
    *gzip*) tar -xzf pkg; rm pkg ;;
  esac
  for inner in $(find . -name '*.tar.xz'); do tar -xJf "$inner" && rm "$inner"; done
  echo "--- tree"; find . -maxdepth 3 | head -60
  JH=$(dirname "$(dirname "$(find . -path '*/bin/java' | head -1)")")
  echo "JAVA_HOME candidate: $JH"
  echo "--- libjvm"; find . -name 'libjvm.so'
  cd ..
  [ -z "$JH" ] || [ "$JH" = "." -a ! -f "jre$JV/bin/java" ] && { echo "no bin/java"; continue; }
  adb shell rm -rf /data/local/tmp/jre$JV
  adb push "jre$JV/$JH" /data/local/tmp/jre$JV >/dev/null
  LIBJVM=$(adb shell "find /data/local/tmp/jre$JV -name libjvm.so | head -1" | tr -d '\r')
  LIBDIR=$(dirname "$LIBJVM")
  adb shell "chmod -R 755 /data/local/tmp/jre$JV/bin; cd /data/local/tmp/jre$JV && LD_LIBRARY_PATH=/data/local/tmp/jre$JV/lib:$LIBDIR ./bin/java -version" 2>&1
done

echo "### Run vanilla server with JRE 21"
SURL=$(curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | jq -r '.versions[] | select(.id=="1.21.1") | .url' | xargs curl -s | jq -r .downloads.server.url)
curl -sL -o server.jar "$SURL"
adb shell mkdir -p /data/local/tmp/mc
adb push server.jar /data/local/tmp/mc/server.jar >/dev/null
adb shell "echo eula=true > /data/local/tmp/mc/eula.txt"
LIBJVM=$(adb shell "find /data/local/tmp/jre21 -name libjvm.so | head -1" | tr -d '\r')
LIBDIR=$(dirname "$LIBJVM")
adb shell "mkdir -p /data/local/tmp/mc/tmp; cd /data/local/tmp/mc && HOME=/data/local/tmp/mc TMPDIR=/data/local/tmp/mc/tmp LD_LIBRARY_PATH=/data/local/tmp/jre21/lib:$LIBDIR timeout 240 /data/local/tmp/jre21/bin/java -Xmx1G -XX:-UsePerfData -Djava.io.tmpdir=/data/local/tmp/mc/tmp -jar server.jar nogui" 2>&1 | tail -60
adb shell ls -la /data/local/tmp/mc
