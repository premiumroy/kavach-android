package com.kavach.data

/**
 * Default blocklist sources. All are widely used, community-maintained lists in
 * hosts or plain-domain format. Users can enable/disable each one.
 *
 * These are domain blocklists for ads, trackers, and malware. Kavach does not
 * target any country or company; lists are chosen for coverage of ad/tracker
 * networks, and users can add their own.
 */
object BlocklistSources {
    val DEFAULTS = listOf(
        SourceEntity(
            id = "stevenblack",
            name = "StevenBlack unified hosts (ads + trackers)",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            enabled = true,
        ),
        SourceEntity(
            id = "oisd",
            name = "OISD basic (ads + trackers)",
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
    )
}
