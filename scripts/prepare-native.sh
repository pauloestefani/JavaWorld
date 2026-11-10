#!/usr/bin/env bash
# Prepara os binários nativos que vão dentro do APK:
#  - libjwlauncher.so : launcher do Java (compilado aqui com o NDK)
#  - libfrpc.so       : cliente do túnel frp (baixado do GitHub oficial do frp)
#  - libc++_shared.so : biblioteca C++ que o Java 25 precisa
set -euo pipefail

FRP_VERSION="${FRP_VERSION:-0.72.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
  NDK="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)"
fi
[ -d "$NDK" ] || { echo "NDK não encontrado"; exit 1; }
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
echo "Usando NDK: $NDK"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

build_abi() {
  local abi=$1 triple=$2 frparch=$3
  mkdir -p "$OUT/$abi"
  "$TC/bin/${triple}26-clang" -O2 -fPIE -pie -Wall -o "$OUT/$abi/libjwlauncher.so" "$ROOT/app/src/main/cpp/launcher.c" -ldl
  "$TC/bin/llvm-strip" "$OUT/$abi/libjwlauncher.so"
  cp "$TC/sysroot/usr/lib/$triple/libc++_shared.so" "$OUT/$abi/libc++_shared.so"
  curl -fsSL "https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/frp_${FRP_VERSION}_linux_${frparch}.tar.gz" | tar -xz -C "$work"
  cp "$work/frp_${FRP_VERSION}_linux_${frparch}/frpc" "$OUT/$abi/libfrpc.so"
  chmod 755 "$OUT/$abi/"*.so
  ls -la "$OUT/$abi"
}

build_abi arm64-v8a aarch64-linux-android arm64
build_abi x86_64 x86_64-linux-android amd64
