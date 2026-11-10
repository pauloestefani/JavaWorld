#!/usr/bin/env bash
set -u
T=/data/local/tmp
adb shell getprop ro.build.version.release
adb push launcher-x86_64 $T/jwlauncher >/dev/null && adb shell chmod 755 $T/jwlauncher
adb push frpc $T/frpc >/dev/null && adb shell chmod 755 $T/frpc
echo "### frpc version"; adb shell $T/frpc -v

prep() { # $1 = java major
  local JV=$1 tag=download_jre$1
  rm -rf jre$JV && mkdir -p jre$JV
  curl -sL "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/$tag/jre$JV-android-x86_64.tar.xz" | tar -xJ -C jre$JV
  echo "--- jre$JV top:"; ls jre$JV; ls jre$JV/lib | head -5; find jre$JV -name jvm.cfg; find jre$JV -name libjli.so; find jre$JV -name libjvm.so
  find jre$JV -type l -delete
  adb shell rm -rf $T/jre$JV
  adb push jre$JV $T/ >/dev/null
}
envfor() { # prints env assignment for java major $1
  local JV=$1 H=$T/jre$1
  local JVM JLI
  JVM=$(cd jre$JV && find . -name libjvm.so | head -1 | sed 's#^\./##')
  JLI=$(cd jre$JV && find . -name libjli.so | head -1 | sed 's#^\./##')
  local JVMDIR LIBDIR
  JVMDIR=$(dirname "$JVM"); LIBDIR=$(dirname "$JVMDIR")
  echo "JAVA_HOME=$H JW_LIBJLI=$H/$JLI JW_LIBJVM=$H/$JVM LD_LIBRARY_PATH=$H/$JVMDIR:$H/$LIBDIR/jli:$H/$LIBDIR:/system/lib64 HOME=$T/mc TMPDIR=$T/mc/tmp"
}

adb shell mkdir -p $T/mc/tmp
for JV in 8 17 21 25; do
  echo "### JRE $JV"
  prep $JV
  E=$(envfor $JV)
  echo "env: $E"
  adb shell "cd $T/mc && $E $T/jwlauncher -version" 2>&1 | tail -5
done

runsrv() { # $1 = mc version, $2 = java major
  local V=$1 JV=$2
  echo "### SERVER $V on java $JV"
  local SURL
  SURL=$(curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | jq -r --arg v "$V" '.versions[] | select(.id==$v) | .url' | xargs curl -s | jq -r .downloads.server.url)
  curl -sL -o server.jar "$SURL"
  adb shell "rm -rf $T/srv$V; mkdir -p $T/srv$V/tmp; echo eula=true > $T/srv$V/eula.txt"
  adb push server.jar $T/srv$V/server.jar >/dev/null
  local E; E=$(envfor $JV)
  local start=$(date +%s)
  adb shell "cd $T/srv$V && (sleep 170; echo stop) | $E timeout 230 $T/jwlauncher -Xms256M -Xmx1536M -XX:-UsePerfData -XX:ActiveProcessorCount=4 -Djava.io.tmpdir=$T/srv$V/tmp -Duser.home=$T/srv$V -Djava.awt.headless=true -jar server.jar nogui" 2>&1 | tail -25
  echo "elapsed $(( $(date +%s) - start ))s"
  adb shell ls $T/srv$V
}
runsrv 1.21.1 21
runsrv 26.3 25
