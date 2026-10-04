package com.kavach

import com.kavach.data.BlocklistRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies the blocklist line parser across hosts, plain, and ABP formats. */
class BlocklistParserTest {

    private fun p(line: String) = BlocklistRepository.parseLine(line)

    @Test fun hostsFormat() {
        assertEquals("doubleclick.net", p("0.0.0.0 doubleclick.net"))
        assertEquals("ads.example.com", p("127.0.0.1 ads.example.com"))
        assertEquals("trackers.example.org", p("0.0.0.0 trackers.example.org  # comment"))
    }

    @Test fun plainFormat() {
        assertEquals("adservice.google.com", p("adservice.google.com"))
        assertEquals("cdn.adnetwork.io", p("cdn.adnetwork.io"))
    }

    @Test fun abpFormat() {
        assertEquals("doubleclick.net", p("||doubleclick.net^"))
        assertEquals("ads.example.com", p("||ads.example.com^\$third-party"))
        assertEquals("tracker.example.net", p("||tracker.example.net^\$important,domain=x.com"))
    }

    @Test fun rejectsJunk() {
        assertNull(p("# comment"))
        assertNull(p("! comment"))
        assertNull(p("[Adblock Plus 2.0]"))
        assertNull(p(""))
        assertNull(p("localhost"))
        assertNull(p("0.0.0.0 localhost"))
        assertNull(p("0.0.0.0 0.0.0.0"))
        assertNull(p("127.0.0.1"))
        assertNull(p("/banner/*/img^"))
        assertNull(p("||*ads*^"))
    }

}
