package io.github.pauloestefani.javaworld.ui

import java.util.Locale

object Fmt {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale("pt", "BR"), "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale("pt", "BR"), "%.0f MB", mb)
        return String.format(Locale("pt", "BR"), "%.1f GB", mb / 1024.0)
    }

    fun ram(mb: Int): String =
        if (mb < 1024) "$mb MB" else String.format(Locale("pt", "BR"), "%.1f GB", mb / 1024.0).replace(",0 ", " ")

    fun pct(f: Float?): String = if (f == null) "—" else "${(f * 100).toInt()}%"

    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h > 0 -> "${h}h ${m}min"
            m > 0 -> "${m}min"
            else -> "${s}s"
        }
    }

    fun ghz(khz: Long?): String = if (khz == null) "—" else String.format(Locale("pt", "BR"), "%.2f GHz", khz / 1_000_000.0)

    fun count(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale("pt", "BR"), "%.1f mi", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale("pt", "BR"), "%.0f mil", n / 1_000.0)
        else -> n.toString()
    }
}
