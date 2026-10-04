package com.kavach.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class BlocklistStats(
    val loaded: Boolean = false,
    val domainCount: Int = 0,
    val ruleCount: Int = 0,
    val sources: Int = 0,
    val lastUpdate: Long = 0,
)

data class UpdateResult(
    val totalDomains: Int,
    val sourcesOk: Int,
    val errors: List<String>,
)

/**
 * Loads blocklists into fast in-memory lookup sets and answers `isBlocked`.
 *
 * Priority (highest first): user ALLOW rule > user BLOCK rule > blocklists.
 * Matching walks parent domains, so a rule for `example.com` also matches
 * `ads.example.com`.
 */
class BlocklistRepository(private val context: Context) {

    private val blocked = HashSet<String>(1 shl 21)
    private val allow = HashSet<String>()
    private val explicitBlock = HashSet<String>()
    private val _stats = MutableStateFlow(BlocklistStats())
    val stats: StateFlow<BlocklistStats> = _stats

    private val db get() = KavachDatabase.get(context)

    suspend fun hasCachedLists(): Boolean = withContext(Dispatchers.IO) {
        db.sources().enabled().any { sourceFile(it.id).exists() && sourceFile(it.id).length() > 0 }
    }

    /** (Re)load everything: bundled list + downloaded sources + user rules. */
    suspend fun reload() = withContext(Dispatchers.IO) {
        val nextBlocked = HashSet<String>(1 shl 21)
        val nextAllow = HashSet<String>()
        val nextExplicit = HashSet<String>()
        var sourceCount = 0

        // Bundled built-in list (72k+ domains) so filtering works with no downloads.
        runCatching {
            context.assets.open("blocklist_builtin.txt").bufferedReader().forEachLine { line ->
                parseLine(line)?.let { nextBlocked.add(it) }
            }
        }
        runCatching {
            context.assets.open("blocklist_starter.txt").bufferedReader().forEachLine { line ->
                parseLine(line)?.let { nextBlocked.add(it) }
            }
        }

        for (src in db.sources().enabled()) {
            val f = sourceFile(src.id)
            if (f.exists() && f.length() > 0) {
                sourceCount++
                f.bufferedReader().forEachLine { line -> parseLine(line)?.let { nextBlocked.add(it) } }
            }
        }

        for (rule in db.rules().enabled()) {
            val d = rule.domain.trim().lowercase()
            if (d.isEmpty()) continue
            when (rule.type) {
                RuleType.ALLOW -> nextAllow.add(d)
                RuleType.BLOCK -> nextExplicit.add(d)
            }
        }

        synchronized(this) {
            blocked.clear(); blocked.addAll(nextBlocked)
            allow.clear(); allow.addAll(nextAllow)
            explicitBlock.clear(); explicitBlock.addAll(nextExplicit)
        }
        _stats.value = _stats.value.copy(
            loaded = true,
            domainCount = blocked.size,
            ruleCount = allow.size + explicitBlock.size,
            sources = sourceCount,
        )
    }

    /** True if [domain] should be blocked. */
    fun isBlocked(domain: String): Boolean {
        val d = domain.trim().trimEnd('.').lowercase()
        if (d.isEmpty()) return false
        val suffixes = suffixes(d)
        synchronized(this) {
            for (s in suffixes) if (s in allow) return false
            for (s in suffixes) if (s in explicitBlock) return true
            for (s in suffixes) if (s in blocked) return true
        }
        return false
    }

    private fun suffixes(domain: String): List<String> {
        val parts = domain.split('.')
        if (parts.size <= 1) return listOf(domain)
        val out = ArrayList<String>(parts.size)
        for (i in 0 until parts.size - 1) out.add(parts.subList(i, parts.size).joinToString("."))
        return out
    }

    private fun sourceFile(id: String): File = File(context.filesDir, "blocklist_$id.txt")

    /** Download and cache one source. Throws on failure. Returns domains parsed. */
    suspend fun downloadSource(src: SourceEntity): Int = withContext(Dispatchers.IO) {
        val conn = (URL(src.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("User-Agent", "Kavach/0.2 (+adblocker)")
            instanceFollowRedirects = true
        }
        try {
            conn.inputStream.use { input ->
                val tmp = File(context.filesDir, "blocklist_${src.id}.tmp")
                var n = 0
                tmp.bufferedWriter().use { out ->
                    input.bufferedReader().forEachLine { line ->
                        val d = parseLine(line)
                        if (d != null) { out.write(d); out.write("\n"); n++ }
                    }
                }
                if (n == 0) throw IllegalStateException("no domains parsed (unrecognised format)")
                val dest = sourceFile(src.id)
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) throw IllegalStateException("could not save file")
                n
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Update all enabled sources, then reload. Reports per-source errors. */
    suspend fun updateAll(): UpdateResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var total = 0
        var ok = 0
        for (src in db.sources().enabled()) {
            try {
                val n = downloadSource(src)
                total += n
                ok++
                db.sources().markUpdated(src.id, System.currentTimeMillis(), n)
            } catch (e: Exception) {
                errors.add("${src.id}: ${e.javaClass.simpleName} - ${e.message ?: "failed"}")
            }
        }
        reload()
        _stats.value = _stats.value.copy(lastUpdate = System.currentTimeMillis())
        UpdateResult(totalDomains = total, sourcesOk = ok, errors = errors)
    }

    companion object {
        /**
         * Parse one line from a blocklist into a domain, or null.
         * Supports hosts ("0.0.0.0 domain"), plain ("domain"), and
         * ABP/uBO ("||domain^", "||domain^$third-party").
         */
        fun parseLine(raw: String): String? {
            var line = raw.trim()
            if (line.isEmpty()) return null
            val first = line[0]
            if (first == '#' || first == '!' || first == '[' || first == '/' || first == '@') return null

            if (line.startsWith("||")) {
                var d = line.substring(2)
                val cut = d.indexOfFirst { it == '^' || it == '$' || it == '/' || it == '|' || it == '*' }
                if (cut >= 0) d = d.substring(0, cut)
                return normalize(d)
            }

            val hash = line.indexOf('#')
            if (hash > 0) line = line.substring(0, hash).trim()

            val parts = line.split(Regex("\\s+"))
            val candidate = when {
                parts.size >= 2 && parts[0] in HOSTS_IPS -> parts[1]
                parts.size == 1 -> parts[0]
                else -> return null
            }
            return normalize(candidate)
        }

        private val HOSTS_IPS = setOf("0.0.0.0", "127.0.0.1", "::1", "::")
        private val DOMAIN_RE = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
        private val IPV4_RE = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        private fun normalize(input: String): String? {
            var d = input.trim().trimEnd('.').lowercase()
            if (d.startsWith("*.")) d = d.substring(2)
            if (d.isEmpty() || d == "localhost") return null
            if (d.any { it == '*' || it == '/' || it == '^' || it == '|' || it == ':' }) return null
            if (!d.contains('.')) return null
            if (IPV4_RE.matches(d)) return null      // bare IP, not a domain
            if (!DOMAIN_RE.matches(d)) return null
            return d
        }
    }
}
