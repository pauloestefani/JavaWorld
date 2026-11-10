#!/usr/bin/env python3
"""Pergunta o status de um servidor de Minecraft Java (Server List Ping)."""
import json
import socket
import struct
import sys


def varint(n):
    out = b""
    n &= 0xFFFFFFFF
    while True:
        b = n & 0x7F
        n >>= 7
        out += struct.pack("B", b | (0x80 if n else 0))
        if not n:
            return out


def read_varint(sock):
    n = 0
    for i in range(5):
        b = sock.recv(1)
        if not b:
            raise IOError("conexão fechada")
        b = b[0]
        n |= (b & 0x7F) << (7 * i)
        if not b & 0x80:
            return n
    raise IOError("varint grande demais")


def recv_exact(sock, size):
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise IOError("conexão fechada")
        data += chunk
    return data


def ping(host, port, timeout=10):
    with socket.create_connection((host, port), timeout=timeout) as s:
        h = host.encode()
        handshake = b"\x00" + varint(767) + varint(len(h)) + h + struct.pack(">H", port) + varint(1)
        s.sendall(varint(len(handshake)) + handshake)
        s.sendall(varint(1) + b"\x00")
        read_varint(s)  # tamanho do pacote
        read_varint(s)  # id do pacote
        size = read_varint(s)
        return json.loads(recv_exact(s, size).decode("utf-8"))


if __name__ == "__main__":
    host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 25565
    try:
        info = ping(host, port)
    except Exception as e:  # noqa: BLE001
        print(f"FALHOU {host}:{port}: {e}")
        sys.exit(1)
    desc = info.get("description")
    if isinstance(desc, dict):
        desc = desc.get("text", desc)
    print(f"OK {host}:{port} versão={info.get('version', {}).get('name')} motd={desc!r} "
          f"jogadores={info.get('players', {}).get('online')}/{info.get('players', {}).get('max')}")
