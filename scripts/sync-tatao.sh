#!/usr/bin/env bash
# Copia a tela do Tátão (sala-lan.html do repositório T-t-o) pra dentro do app,
# ligando a ponte do Android (shim.js) antes de tudo.
# Uso: scripts/sync-tatao.sh /caminho/para/sala-lan.html
set -euo pipefail
SRC="${1:?informe o caminho do sala-lan.html do Tátão}"
OUT="$(dirname "$0")/../app/src/main/assets/tatao/index.html"
python3 - "$SRC" "$OUT" <<'PY'
import sys
h = open(sys.argv[1], encoding="utf-8").read()
tag = '<meta charset="utf-8">'
assert h.count(tag) == 1, "não achei o <meta charset> do Tátão"
h = h.replace(tag, tag + '\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n<script src="shim.js"></script>' if 'name="viewport"' not in h else tag + '\n<script src="shim.js"></script>')
open(sys.argv[2], "w", encoding="utf-8").write(h)
print("ok:", sys.argv[2], len(h) // 1024, "KB")
PY
