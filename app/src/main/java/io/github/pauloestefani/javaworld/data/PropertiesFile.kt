package io.github.pauloestefani.javaworld.data

import java.io.File

/**
 * Lê e edita o server.properties mantendo a ordem e os comentários originais.
 */
class PropertiesFile(private val file: File) {
    private val lines = mutableListOf<String>()

    init {
        if (file.exists()) lines += file.readLines(Charsets.ISO_8859_1)
    }

    val exists: Boolean get() = file.exists()

    fun get(key: String): String? {
        for (line in lines) {
            val kv = split(line) ?: continue
            if (kv.first == key) return unescape(kv.second)
        }
        return null
    }

    fun get(key: String, default: String): String = get(key) ?: default

    fun set(key: String, value: String) {
        val newLine = "$key=${escape(value)}"
        val idx = lines.indexOfFirst { split(it)?.first == key }
        if (idx >= 0) lines[idx] = newLine else lines += newLine
    }

    fun save() {
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.ISO_8859_1)
    }

    private fun split(line: String): Pair<String, String>? {
        val t = line.trimStart()
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) return null
        val i = t.indexOfFirst { it == '=' || it == ':' }
        if (i < 0) return t.trim() to ""
        return t.substring(0, i).trim() to t.substring(i + 1).trimStart()
    }

    private fun escape(v: String): String = buildString {
        for (c in v) {
            when {
                c == '\\' -> append("\\\\")
                c.code < 0x20 || c.code > 0x7e -> append(String.format("\\u%04x", c.code))
                else -> append(c)
            }
        }
    }

    private fun unescape(v: String): String {
        if (!v.contains('\\')) return v
        val sb = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                val n = v[i + 1]
                when (n) {
                    'u' -> if (i + 5 < v.length) {
                        val code = v.substring(i + 2, i + 6).toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar()); i += 6; continue
                        }
                    }
                    'n' -> { sb.append('\n'); i += 2; continue }
                    't' -> { sb.append('\t'); i += 2; continue }
                    else -> { sb.append(n); i += 2; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
