package io.github.pauloestefani.javaworld.runtime;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Extrator de arquivos .tar simples (formatos ustar, GNU e pax), sem dependências.
 * Suporta pastas, arquivos, links simbólicos e hard links.
 */
public final class TarExtractor {

    public interface Progress {
        void onEntry(String name);
    }

    private TarExtractor() {
    }

    public static void extract(InputStream in, File dest, Progress progress) throws IOException {
        if (!dest.isDirectory() && !dest.mkdirs()) {
            throw new IOException("Não consegui criar " + dest);
        }
        final String destCanon = dest.getCanonicalPath();
        final byte[] header = new byte[512];
        String longName = null;
        String longLink = null;
        String paxPath = null;
        String paxLink = null;
        long paxSize = -1;

        while (true) {
            if (!readBlock(in, header)) {
                break;
            }
            if (isZero(header)) {
                break;
            }
            String name = cstr(header, 0, 100);
            int mode = (int) parseNumber(header, 100, 8);
            long size = parseNumber(header, 124, 12);
            char type = (char) header[156];
            String link = cstr(header, 157, 100);
            boolean posix = header[257] == 'u' && header[258] == 's' && header[259] == 't'
                    && header[260] == 'a' && header[261] == 'r' && header[262] == 0;
            if (posix) {
                String prefix = cstr(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
            }

            if (type == 'L') {
                longName = readString(in, size);
                continue;
            }
            if (type == 'K') {
                longLink = readString(in, size);
                continue;
            }
            if (type == 'x') {
                Map<String, String> pax = parsePax(readString(in, size));
                if (pax.containsKey("path")) paxPath = pax.get("path");
                if (pax.containsKey("linkpath")) paxLink = pax.get("linkpath");
                if (pax.containsKey("size")) paxSize = Long.parseLong(pax.get("size").trim());
                continue;
            }
            if (type == 'g') {
                skipData(in, size);
                continue;
            }

            if (longName != null) name = longName;
            if (paxPath != null) name = paxPath;
            if (longLink != null) link = longLink;
            if (paxLink != null) link = paxLink;
            if (paxSize >= 0) size = paxSize;
            longName = null;
            longLink = null;
            paxPath = null;
            paxLink = null;
            paxSize = -1;

            String clean = cleanName(name);
            if (clean.isEmpty()) {
                skipData(in, hasData(type) ? size : 0);
                continue;
            }
            File out = safeFile(dest, destCanon, clean);
            if (progress != null) {
                progress.onEntry(clean);
            }

            switch (type) {
                case '5':
                    out.mkdirs();
                    break;
                case '2':
                    makeParent(out);
                    deleteIfExists(out);
                    Files.createSymbolicLink(out.toPath(), Paths.get(link));
                    break;
                case '1': {
                    File target = safeFile(dest, destCanon, cleanName(link));
                    makeParent(out);
                    deleteIfExists(out);
                    copyFile(target, out);
                    if (target.canExecute()) out.setExecutable(true, false);
                    break;
                }
                case '0':
                case 0:
                case '7': {
                    makeParent(out);
                    deleteIfExists(out);
                    try (OutputStream os = new FileOutputStream(out)) {
                        copyExactly(in, os, size);
                    }
                    skipPadding(in, size);
                    if ((mode & 0111) != 0) {
                        out.setExecutable(true, false);
                    }
                    out.setReadable(true, false);
                    break;
                }
                default:
                    // Tipos que não usamos (fifo, dispositivos...): só pula os dados.
                    skipData(in, hasData(type) ? size : 0);
                    break;
            }
        }
    }

    private static boolean hasData(char type) {
        return type != '1' && type != '2' && type != '3' && type != '4' && type != '5' && type != '6';
    }

    private static String cleanName(String name) {
        String n = name.replace('\\', '/');
        while (n.startsWith("./")) n = n.substring(2);
        while (n.startsWith("/")) n = n.substring(1);
        while (n.endsWith("/")) n = n.substring(0, n.length() - 1);
        if (n.equals(".")) return "";
        return n;
    }

    private static File safeFile(File dest, String destCanon, String name) throws IOException {
        File f = new File(dest, name);
        String parentCanon = f.getParentFile() == null ? destCanon : f.getParentFile().getCanonicalPath();
        if (!(parentCanon + File.separator).startsWith(destCanon + File.separator) && !parentCanon.equals(destCanon)) {
            throw new IOException("Entrada inválida no arquivo: " + name);
        }
        return f;
    }

    private static void makeParent(File f) throws IOException {
        File p = f.getParentFile();
        if (p != null && !p.isDirectory() && !p.mkdirs()) {
            throw new IOException("Não consegui criar " + p);
        }
    }

    private static void deleteIfExists(File f) throws IOException {
        Files.deleteIfExists(f.toPath());
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream is = new FileInputStream(from); OutputStream os = new FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = is.read(buf)) > 0) os.write(buf, 0, r);
        }
    }

    private static boolean readBlock(InputStream in, byte[] block) throws IOException {
        int off = 0;
        while (off < block.length) {
            int r = in.read(block, off, block.length - off);
            if (r < 0) {
                if (off == 0) return false;
                throw new EOFException("Arquivo .tar cortado");
            }
            off += r;
        }
        return true;
    }

    private static boolean isZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static long parseNumber(byte[] b, int off, int len) {
        if ((b[off] & 0x80) != 0) {
            // Base 256 (GNU) para tamanhos grandes.
            long v = b[off] & 0x7f;
            for (int i = 1; i < len; i++) v = (v << 8) | (b[off + i] & 0xff);
            return v;
        }
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c == 0 || c == ' ') {
                if (v != 0) break;
                continue;
            }
            if (c < '0' || c > '7') break;
            v = (v << 3) + (c - '0');
        }
        return v;
    }

    private static String readString(InputStream in, long size) throws IOException {
        if (size > 1 << 20) throw new IOException("Cabeçalho grande demais");
        byte[] data = new byte[(int) size];
        int off = 0;
        while (off < data.length) {
            int r = in.read(data, off, data.length - off);
            if (r < 0) throw new EOFException();
            off += r;
        }
        skipPadding(in, size);
        int end = data.length;
        while (end > 0 && data[end - 1] == 0) end--;
        return new String(data, 0, end, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parsePax(String text) {
        Map<String, String> map = new HashMap<>();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int pos = 0;
        while (pos < bytes.length) {
            int sp = pos;
            while (sp < bytes.length && bytes[sp] != ' ') sp++;
            if (sp >= bytes.length) break;
            int len;
            try {
                len = Integer.parseInt(new String(bytes, pos, sp - pos, StandardCharsets.UTF_8).trim());
            } catch (NumberFormatException e) {
                break;
            }
            if (len <= 0 || pos + len > bytes.length) break;
            String record = new String(bytes, sp + 1, pos + len - sp - 1, StandardCharsets.UTF_8);
            if (record.endsWith("\n")) record = record.substring(0, record.length() - 1);
            int eq = record.indexOf('=');
            if (eq > 0) map.put(record.substring(0, eq), record.substring(eq + 1));
            pos += len;
        }
        return map;
    }

    private static void copyExactly(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buf = new byte[65536];
        long left = size;
        while (left > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) throw new EOFException("Arquivo .tar cortado");
            out.write(buf, 0, r);
            left -= r;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long pad = (512 - (size % 512)) % 512;
        skipFully(in, pad);
    }

    private static void skipData(InputStream in, long size) throws IOException {
        skipFully(in, size);
        skipPadding(in, size);
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        byte[] buf = new byte[8192];
        long left = n;
        while (left > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) throw new EOFException("Arquivo .tar cortado");
            left -= r;
        }
    }
}
