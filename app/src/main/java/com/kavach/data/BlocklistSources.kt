package com.kavach.data

/**
 * Default blocklist sources.
 *
 * Formats differ: hosts files ("0.0.0.0 domain"), plain domain lists, and
 * ABP/uBO style ("||domain^"). The parser handles all three.
 * Users can enable/disable each source and add their own.
 *
 * These are general ad / tracker / malware lists. Kavach does not target any
 * country or company; lists are chosen for coverage, and the user is in control.
 */
object BlocklistSources {
    val DEFAULTS = listOf(
        SourceEntity(
            id = "hagezi_pro",
            name = "HaGeZi Pro (ads, trackers, telemetry)",
            url = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt",
            enabled = true,
        ),
        SourceEntity(
            id = "stevenblack",
            name = "StevenBlack unified hosts (ads + trackers)",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            enabled = true,
        ),
        SourceEntity(
            id = "oisd",
            name = "OISD big (ads, trackers, malware)",
            url = "https://big.oisd.nl/",
            enabled = true,
        ),
        SourceEntity(
            id = "adaway",
            name = "AdAway hosts",
            url = "https://adaway.org/hosts.txt",
            enabled = true,
        ),
        SourceEntity(
            id = "peterlowe",
            name = "Peter Lowe's ad/tracker servers",
            url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            enabled = true,
        ),
        SourceEntity(
            id = "1hosts_lite",
            name = "1Hosts Lite",
            url = "https://raw.githubusercontent.com/badmojr/1Hosts/master/Lite/hosts.txt",
            enabled = false,
        ),
        SourceEntity(
            id = "hagezi_tif",
            name = "HaGeZi Threat Intelligence (malware, phishing)",
            url = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/tif.mini.txt",
            enabled = false,
        ),
    )
}
