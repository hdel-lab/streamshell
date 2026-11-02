package com.streamshell.core.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListParserTest {
    @Test fun parsesDomainExceptionHostsAndThirdParty() {
        val r = FilterListParser.parse(
            """
            [Adblock Plus 2.0]
            ! comment
            # other comment
            ||ads.example.com^
            ||track.example.net^${'$'}third-party
            @@||ok.example.com^
            0.0.0.0 malware.test
            127.0.0.1 localhost
            """.trimIndent()
        )
        assertEquals(4, r.rules.size)
        assertTrue(r.rules.any { it.domain == "ads.example.com" && !it.exception && !it.thirdPartyOnly })
        assertTrue(r.rules.any { it.domain == "track.example.net" && it.thirdPartyOnly })
        assertTrue(r.rules.any { it.domain == "ok.example.com" && it.exception })
        assertTrue(r.rules.any { it.domain == "malware.test" })
    }

    @Test fun unsupportedSyntaxIsSkippedAndCounted() {
        val r = FilterListParser.parse("example.com##.banner\n||x.com/path^\n/regex/\n||y.com^\$script\n||ok.com^")
        assertEquals(listOf("ok.com"), r.rules.map { it.domain })
        assertEquals(4, r.skipped)
    }

    @Test fun ipAddressesAreRejected() {
        val r = FilterListParser.parse("||1.2.3.4^\n0.0.0.0 5.6.7.8")
        assertTrue(r.rules.isEmpty())
        assertEquals(2, r.skipped)
    }

    @Test fun malformedInputNeverThrows() {
        val garbage = "||^\n||..^\n@@\n||a b.com^\n\u0001\u0002\n" + "x".repeat(10_000) + "\n||" + "a".repeat(300) + ".com^"
        val r = FilterListParser.parse(garbage)
        assertTrue(r.rules.isEmpty())
    }

    @Test fun ruleLimitTruncates() {
        val text = (1..50).joinToString("\n") { "||d$it.example.com^" }
        val r = FilterListParser.parse(text, maxRules = 10)
        assertEquals(10, r.rules.size)
        assertTrue(r.truncated)
    }
}

class FilterListValidatorTest {
    @Test fun rejectsEmptyBinaryOversizedAndRulelessLists() {
        assertTrue(FilterListValidator.validate(ByteArray(0)) is FilterListValidator.Result.Invalid)
        assertTrue(FilterListValidator.validate(byteArrayOf(1, 0, 2)) is FilterListValidator.Result.Invalid)
        assertTrue(FilterListValidator.validate(ByteArray(FilterListValidator.MAX_BYTES + 1) { 'a'.code.toByte() }) is FilterListValidator.Result.Invalid)
        assertTrue(FilterListValidator.validate("! only comments\n##cosmetic".toByteArray()) is FilterListValidator.Result.Invalid)
    }

    @Test fun acceptsAValidList() {
        assertTrue(FilterListValidator.validate("||a.example.com^".toByteArray()) is FilterListValidator.Result.Valid)
    }
}

class DomainFilterEngineTest {
    private fun engine(vararg rules: DomainRule) = DomainFilterEngine(rules.toList())

    @Test fun blocksDomainAndSubdomains() {
        val e = engine(DomainRule("ads.example.com", false, false, "easyprivacy"))
        assertEquals(FilterDecision.Block("||ads.example.com^", "easyprivacy"), e.evaluate("ads.example.com", null))
        assertTrue(e.evaluate("x.y.ads.example.com", null) is FilterDecision.Block)
        assertEquals(FilterDecision.Allow(AllowReason.NO_MATCH), e.evaluate("example.com", null))
        assertEquals(FilterDecision.Allow(AllowReason.NO_MATCH), e.evaluate("notads.example.com", null))
    }

    @Test fun exceptionBeatsBlock() {
        val e = engine(DomainRule("example.com", false, false), DomainRule("ok.example.com", true, false))
        assertEquals(FilterDecision.Allow(AllowReason.EXCEPTION), e.evaluate("ok.example.com", null))
        assertTrue(e.evaluate("other.example.com", null) is FilterDecision.Block)
    }

    @Test fun thirdPartyOnlyIsRespected() {
        val e = engine(DomainRule("cdn.site.com", false, true))
        assertEquals(FilterDecision.Allow(AllowReason.FIRST_PARTY), e.evaluate("cdn.site.com", "www.site.com"))
        assertTrue(e.evaluate("cdn.site.com", "other.org") is FilterDecision.Block)
    }

    @Test fun hostNormalisationAndTldGuard() {
        val e = engine(DomainRule("example.com", false, false))
        assertTrue(e.evaluate("ADS.Example.COM.", null) is FilterDecision.Block)
        assertTrue(DomainFilterEngine.suffixes("a.b.com").none { it == "com" })
    }

    @Test fun duplicateRulesAreCollapsed() {
        val e = engine(DomainRule("a.com", false, true, "one"), DomainRule("a.com", false, false, "two"), DomainRule("a.com", false, false, "three"))
        assertEquals(1, e.ruleCount)
        assertEquals(FilterDecision.Block("||a.com^", "two"), e.evaluate("a.com", "a.com"))
    }

    @Test fun lookupStaysFastWithLargeLists() {
        val rules = (0 until 200_000).map { DomainRule("host$it.example$it.com", false, false) }
        val e = DomainFilterEngine(rules)
        val start = System.nanoTime()
        var blocked = 0
        repeat(20_000) { if (e.evaluate("sub.host${it * 7}.example${it * 7}.com", null) is FilterDecision.Block) blocked++ }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(20_000, blocked)
        assertTrue("20k lookups took ${ms}ms", ms < 2_000)
    }
}

class LayeredFilterTest {
    private val lists = DomainFilterEngine(
        listOf(DomainRule("ads.example.com", false, false, "easyprivacy"), DomainRule("fine.ads.example.com", true, false))
    )

    @Test fun userAllowBeatsEverything() {
        val f = LayeredFilter(setOf("example.com"), setOf("example.com"), lists)
        assertEquals(FilterDecision.Allow(AllowReason.USER_ALLOW), f.evaluate("ads.example.com", null))
    }

    @Test fun listExceptionBeatsUserBlock() {
        val f = LayeredFilter(emptySet(), setOf("ads.example.com"), lists)
        assertEquals(FilterDecision.Allow(AllowReason.EXCEPTION), f.evaluate("fine.ads.example.com", null))
    }

    @Test fun userBlockIsAttributedToCustom() {
        val f = LayeredFilter(emptySet(), setOf("evil.test"), lists)
        assertEquals(FilterDecision.Block("||evil.test^", USER_SOURCE), f.evaluate("a.evil.test", null))
    }

    @Test fun listBlockStillWorksAndAllowRestoresIt() {
        assertTrue(LayeredFilter(emptySet(), emptySet(), lists).evaluate("ads.example.com", null) is FilterDecision.Block)
        assertTrue(LayeredFilter(setOf("ads.example.com"), emptySet(), lists).evaluate("ads.example.com", null) is FilterDecision.Allow)
    }
}

class DomainInputTest {
    @Test fun normalisesCommonInputs() {
        assertEquals("example.com", DomainInput.normalize(" Example.com "))
        assertEquals("example.com", DomainInput.normalize("https://example.com/path?q=1"))
        assertEquals("example.com", DomainInput.normalize("||example.com^"))
    }

    @Test fun rejectsInvalid() {
        for (bad in listOf("", "localhost", "1.2.3.4", "a b.com", "-x.com", "x..com", "exa*mple.com", "a".repeat(260) + ".com")) {
            assertNull(bad, DomainInput.normalize(bad))
        }
    }
}

class FilterConfigSerializerTest {
    @Test fun roundTrips() {
        val c = FilterConfig.defaults().let {
            it.copy(
                statuses = mapOf("easyprivacy" to ListStatus(10, 20, 5, 3, "boom", "\"etag\"", "Mon")),
                trustedHosts = it.trustedHosts + "my.host.org"
            )
        }
        assertEquals(c, FilterConfigSerializer.decode(FilterConfigSerializer.encode(c)))
    }

    @Test fun corruptOrMissingFallsBackToDefaults() {
        assertEquals(FilterConfig.defaults(), FilterConfigSerializer.decode(null))
        assertEquals(FilterConfig.defaults(), FilterConfigSerializer.decode("{not json"))
    }
}
