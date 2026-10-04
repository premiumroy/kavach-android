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
    val allowCount: Int = 0,
    val sources: Int = 0,
)

/**
 * Loads blocklists into fast in-memory lookup sets and answers `isBlocked`.
 *
 * Priority (highest first): user ALLOW rule > user BLOCK rule > blocklists.
 * Matching walks parent domains, so a rule for `example.com` also matches
 * `ads.example.com` (standard ad-blocker behaviour).
 */
class BlocklistRepository(private val context: Context) {

    private val blocked = HashSet<String>(1 shl 20)
    private val allow = HashSet<String>()
    private val explicitBlock = HashSet<String>()
    private val _stats = MutableStateFlow(BlocklistStats())
    val stats: StateFlow<BlocklistStats> = _stats

    private val db get() = KavachDatabase.get(context)

    /** (Re)load everything: bundled list + downloaded sources + user rules. */
    suspend fun reload() = withContext(Dispatchers.IO) {
        val nextBlocked = HashSet<String>(1 shl 20)
        val nextAllow = HashSet<String>()
        val nextExplicit = HashSet<String>()
        var sourceCount = 0

        // 1. Bundled starter list (always available offline).
        runCatching {
            context.assets.open("blocklist_starter.txt").bufferedReader().forEachLine { line ->
                parseLine(line)?.let { nextBlocked.add(it) }
            }
        }

        // 2. Downloaded sources cached in filesDir.
        val sources = db.sources().enabled()
        for (src in sources) {
            val f = sourceFile(src.id)
            if (f.exists()) {
                sourceCount++
                f.bufferedReader().forEachLine { line -> parseLine(line)?.let { nextBlocked.add(it) } }
            }
        }

        // 3. User rules.
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
        _stats.value = BlocklistStats(
            loaded = true,
            domainCount = blocked.size,
            allowCount = allow.size + explicitBlock.size,
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

    /** Download and cache one source. Returns the number of domains parsed. */
    suspend fun downloadSource(src: SourceEntity): Int = withContext(Dispatchers.IO) {
        val conn = (URL(src.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Kavach/0.1 (+adblocker)")
            instanceFollowRedirects = true
        }
        val count: Int
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
                tmp.renameTo(sourceFile(src.id))
                count = n
            }
        } finally {
            conn.disconnect()
        }
        db.sources().markUpdated(src.id, System.currentTimeMillis(), count)
        count
    }

    /** Update all enabled sources, then reload. */
    suspend fun updateAll(): Int = withContext(Dispatchers.IO) {
        var total = 0
        for (src in db.sources().enabled()) {
            total += runCatching { downloadSource(src) }.getOrDefault(0)
        }
        reload()
        total
    }

    companion object {
        /** Parse a hosts/plain line into a domain, or null. */
        fun parseLine(raw: String): String? {
            var line = raw.trim()
            if (line.isEmpty()) return null
            if (line.startsWith("#") || line.startsWith("!") || line.startsWith("[")) return null
            val hash = line.indexOf('#')
            if (hash >= 0) line = line.substring(0, hash).trim()
            val parts = line.split(Regex("\\s+"))
            val candidate = when {
                parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::1") -> parts[1]
                parts.size == 1 -> parts[0]
                else -> return null
            }
            val d = candidate.trim().trimEnd('.').lowercase()
            if (d.isEmpty() || d == "localhost" || !d.contains('.')) return null
            if (!d.matches(Regex("^[a-z0-9.*_-]+\\.[a-z0-9.*_-]+$"))) return null
            return d
        }
    }
}
