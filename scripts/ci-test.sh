#!/usr/bin/env bash
# Teste de ponta a ponta no emulador: instala o APK, cria servidores, liga,
# testa o túnel de verdade (frps na máquina do CI) e tira prints das telas.
set -u
PKG=io.github.pauloestefani.javaworld
OUT=ci-out
mkdir -p "$OUT"
RESULT="$OUT/result.txt"
: > "$RESULT"
pass() { echo "PASS $*" | tee -a "$RESULT"; }
fail() { echo "FAIL $*" | tee -a "$RESULT"; }

adb install -r JavaWorld.apk || { fail install; exit 0; }
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell dumpsys deviceidle whitelist +$PKG || true
adb logcat -c
adb logcat -v time > "$OUT/logcat.txt" 2>&1 &
LOGCAT_PID=$!

shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }
bc() { adb shell am broadcast -a $PKG.TEST -n $PKG/.TestHooks "$@" >/dev/null; }
open_route() { adb shell am start -n $PKG/.ui.MainActivity --es route "'$1'" >/dev/null; }
wait_log() { # $1 = padrão, $2 = segundos
  local end=$(( $(date +%s) + $2 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    grep -q "$1" "$OUT/logcat.txt" && return 0
    sleep 3
  done
  return 1
}
last_id() { grep -o 'created id=[a-z0-9]*' "$OUT/logcat.txt" | tail -1 | cut -d= -f2; }

adb shell am start -n $PKG/.ui.MainActivity >/dev/null
sleep 8
shot 01-inicio

# Túnel: servidor frps na máquina do CI (usando o script de instalação de verdade)
sudo TOKEN=citoken BIND_PORT=7000 MC_PORTS=25566 bash tunel/instalar-frps.sh > "$OUT/frps-install.txt" 2>&1 \
  && pass "script do frps" || fail "script do frps"
bc --es cmd tunnel --es host 10.0.2.2 --ei port 7000 --es token citoken --ei remote 25566

# ---------- Vanilla ----------
bc --es cmd create --es loader vanilla --es mc 1.21.1 --es name "'Mundo Vanilla'" --ei ram 1024
sleep 4
VID=$(last_id)
echo "vanilla id=$VID"
open_route "server/$VID"
sleep 3
shot 02-painel-desligado
bc --es cmd start --es id "$VID"
sleep 20
shot 03-preparando
if wait_log "status=RUNNING" 600; then pass "vanilla ligou"; else fail "vanilla não ligou"; fi
sleep 5
shot 04-painel-online
python3 scripts/mcping.py 127.0.0.1 25566 > "$OUT/ping-tunel.txt" 2>&1 && pass "ping pelo túnel" || fail "ping pelo túnel"
adb forward tcp:25570 tcp:25565
python3 scripts/mcping.py 127.0.0.1 25570 > "$OUT/ping-direto.txt" 2>&1 && pass "ping direto" || fail "ping direto"
cat "$OUT"/ping-*.txt
bc --es cmd send --es text "'say Ola do JavaWorld'"
open_route "server/$VID?tab=1"
sleep 4
shot 05-console
open_route "device"
sleep 5
shot 06-celular
open_route "tunnel"
sleep 3
shot 07-tunel
open_route "servers"
sleep 3
shot 08-lista
bc --es cmd stop
if wait_log "status=STOPPED" 120; then pass "vanilla desligou"; else fail "vanilla não desligou"; fi

# ---------- Fabric + mod do Modrinth ----------
bc --es cmd create --es loader fabric --es mc 1.21.1 --es name "'Mundo Fabric'" --ei ram 1024
sleep 6
FID=$(last_id)
echo "fabric id=$FID"
bc --es cmd mod --es id "$FID" --es project lithium
if wait_log "mod installed" 90; then pass "mod instalado"; else fail "mod não instalou"; fi
open_route "server/$FID?tab=2"
sleep 4
shot 09-mods
open_route "server/$FID"
sleep 2
# conta quantas vezes já ficou RUNNING para esperar a próxima
before=$(grep -c "status=RUNNING" "$OUT/logcat.txt")
bc --es cmd start --es id "$FID"
end=$(( $(date +%s) + 600 ))
ok=0
while [ "$(date +%s)" -lt "$end" ]; do
  now=$(grep -c "status=RUNNING" "$OUT/logcat.txt")
  if [ "$now" -gt "$before" ]; then ok=1; break; fi
  if grep -q "status=CRASHED" "$OUT/logcat.txt"; then break; fi
  sleep 5
done
[ $ok = 1 ] && pass "fabric ligou" || fail "fabric não ligou"
sleep 3
shot 10-fabric-online
grep -i "lithium" "$OUT/logcat.txt" | head -3 || true
bc --es cmd stop
wait_log "status=STOPPED" 120 || true

kill $LOGCAT_PID 2>/dev/null || true
adb logcat -d -s JavaWorld JavaWorldTest JavaWorldTunnel AndroidRuntime > "$OUT/logcat-app.txt" 2>&1 || true
echo "==== RESULTADO ===="
cat "$RESULT"
