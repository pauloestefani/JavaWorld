#!/usr/bin/env bash
# JavaWorld — instala o servidor do túnel (frps) numa máquina Linux na nuvem.
#
# Uso (como root):
#   curl -fsSL https://raw.githubusercontent.com/pauloestefani/JavaWorld/main/tunel/instalar-frps.sh | sudo TOKEN='seu-token' bash
#
# Variáveis:
#   TOKEN      senha do túnel (a mesma que está no app). Se vazio, uma é gerada.
#   BIND_PORT  porta que o celular usa para conectar (padrão 7000)
#   MC_PORTS   portas públicas liberadas para o Minecraft: "25565", "25565-25570" ou "25565,25570"
#   FRP_VERSION versão do frp (padrão 0.72.0)
set -euo pipefail

FRP_VERSION="${FRP_VERSION:-0.72.0}"
TOKEN="${TOKEN:-}"
BIND_PORT="${BIND_PORT:-7000}"
MC_PORTS="${MC_PORTS:-25565}"

if [ "$(id -u)" -ne 0 ]; then
  echo "Rode como root (use sudo)." >&2
  exit 1
fi

if [ -z "$TOKEN" ]; then
  TOKEN="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24 || true)"
  GENERATED=1
fi

case "$(uname -m)" in
  aarch64|arm64) ARCH=arm64 ;;
  x86_64|amd64) ARCH=amd64 ;;
  *) echo "Arquitetura não suportada: $(uname -m)" >&2; exit 1 ;;
esac

echo "==> Baixando o frp ${FRP_VERSION} (${ARCH})"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fsSL "https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/frp_${FRP_VERSION}_linux_${ARCH}.tar.gz" \
  | tar -xz -C "$tmp"
install -m 0755 "$tmp/frp_${FRP_VERSION}_linux_${ARCH}/frps" /usr/local/bin/frps

# Monta a lista de portas permitidas
allow=""
fw_ports=()
IFS=',' read -ra parts <<< "$MC_PORTS"
for p in "${parts[@]}"; do
  p="$(echo "$p" | tr -d ' ')"
  [ -z "$p" ] && continue
  if [[ "$p" == *-* ]]; then
    a="${p%-*}"; b="${p#*-}"
    allow+="{ start = $a, end = $b }, "
    fw_ports+=("$a:$b")
  else
    allow+="{ single = $p }, "
    fw_ports+=("$p")
  fi
done
allow="${allow%, }"

echo "==> Escrevendo /etc/frp/frps.toml"
mkdir -p /etc/frp
cat > /etc/frp/frps.toml <<EOF
# Gerado pelo JavaWorld
bindPort = ${BIND_PORT}
auth.method = "token"
auth.token = "${TOKEN}"
allowPorts = [ ${allow} ]
transport.tcpMux = true
log.to = "console"
log.level = "info"
EOF
chmod 600 /etc/frp/frps.toml

echo "==> Criando o serviço frps"
cat > /etc/systemd/system/frps.service <<'EOF'
[Unit]
Description=JavaWorld - servidor do túnel (frps)
After=network-online.target
Wants=network-online.target

[Service]
ExecStart=/usr/local/bin/frps -c /etc/frp/frps.toml
Restart=always
RestartSec=3
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now frps
systemctl restart frps

echo "==> Liberando as portas no firewall da máquina"
open_port() { # $1 = porta ou faixa a:b
  if command -v iptables >/dev/null 2>&1; then
    iptables -C INPUT -p tcp --dport "$1" -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p tcp --dport "$1" -j ACCEPT
  fi
  if command -v ip6tables >/dev/null 2>&1; then
    ip6tables -C INPUT -p tcp --dport "$1" -j ACCEPT 2>/dev/null || ip6tables -I INPUT 1 -p tcp --dport "$1" -j ACCEPT || true
  fi
  if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
    firewall-cmd --permanent --add-port="${1/:/-}/tcp" >/dev/null || true
  fi
  if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
    ufw allow "$1/tcp" >/dev/null || true
  fi
}
open_port "$BIND_PORT"
for p in "${fw_ports[@]}"; do open_port "$p"; done
command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --reload >/dev/null 2>&1 || true
if command -v netfilter-persistent >/dev/null 2>&1; then
  netfilter-persistent save >/dev/null 2>&1 || true
elif [ -d /etc/iptables ]; then
  iptables-save > /etc/iptables/rules.v4 || true
  ip6tables-save > /etc/iptables/rules.v6 2>/dev/null || true
fi

IP="$(curl -fsS --max-time 5 https://ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')"
sleep 1
echo
echo "================ JavaWorld ================"
if systemctl is-active --quiet frps; then
  echo " Túnel instalado e rodando!"
else
  echo " ATENÇÃO: o frps não está rodando. Veja: journalctl -u frps"
fi
echo " IP público:      ${IP}"
echo " Porta do túnel:  ${BIND_PORT}"
echo " Portas do jogo:  ${MC_PORTS}"
if [ "${GENERATED:-0}" = "1" ]; then
  echo " Token (copie para o app): ${TOKEN}"
fi
echo
echo " Lembre de liberar as portas TCP ${BIND_PORT} e ${MC_PORTS} na"
echo " Security List da rede (VCN) no painel da Oracle."
echo "==========================================="
