package com.streamshell.core.privacy

/**
 * Documented rule format (a strict subset of Adblock-style syntax plus hosts files):
 *
 *   ! comment            (also '#' and '[Adblock ...]' header lines)
 *   ||example.com^       block example.com and all subdomains
 *   ||example.com^$third-party   same, but only when requested by another site
 *   @@||example.com^     exception: never block this domain
 *   0.0.0.0 example.com  hosts-file line (127.0.0.1 also accepted)
 *
 * Every other line (cosmetic filters, regex rules, path rules, unknown options, scripts) is
 * counted as skipped. Rules are data only: nothing from a list is ever executed.
 */
data class DomainRule(val domain: String, val exception: Boolean, val thirdPartyOnly: Boolean, val listId: String? = null)

data class ParseResult(val rules: List<DomainRule>, val skipped: Int, val truncated: Boolean)

object FilterListParser {
    const val DEFAULT_MAX_RULES = 250_000

    private val domainRegex = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
    private val hostsLine = Regex("^(?:0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+([^\\s#]+)\\s*(?:#.*)?$")
    private val ignoredHosts = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0")

    fun parse(text: String, maxRules: Int = DEFAULT_MAX_RULES): ParseResult {
        val rules = ArrayList<DomainRule>()
        var skipped = 0
        var truncated = false
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("#") || line.startsWith("[")) continue
            val rule = parseLine(line)
            if (rule == null) {
                skipped++
            } else {
                if (rules.size >= maxRules) {
                    truncated = true
                    break
                }
                rules += rule
            }
        }
        return ParseResult(rules, skipped, truncated)
    }

    private fun parseLine(line: String): DomainRule? {
        hostsLine.matchEntire(line)?.let { m ->
            val host = m.groupValues[1].lowercase()
            return if (host in ignoredHosts || !domainRegex.matches(host) || host.all { it.isDigit() || it == '.' }) {
                null
            } else {
                DomainRule(host, exception = false, thirdPartyOnly = false)
            }
        }
        var body = line
        var exception = false
        if (body.startsWith("@@")) {
            exception = true
            body = body.substring(2)
        }
        if (!body.startsWith("||")) return null
        body = body.substring(2)
        var options = ""
        val dollar = body.indexOf('$')
        if (dollar >= 0) {
            options = body.substring(dollar + 1)
            body = body.substring(0, dollar)
        }
        if (!body.endsWith("^")) return null
        val domain = body.removeSuffix("^").lowercase()
        if (!domainRegex.matches(domain) || domain.length > 253 || domain.all { it.isDigit() || it == '.' }) return null

        var thirdParty = false
        if (options.isNotEmpty()) {
            for (opt in options.split(',')) {
                when (opt.trim()) {
                    "third-party", "3p" -> thirdParty = true
                    else -> return null // unknown modifier: do not guess its meaning
                }
            }
        }
        return DomainRule(domain, exception, thirdParty)
    }
}

/** Validation applied to anything that came from outside (downloaded or cached on disk). */
object FilterListValidator {
    const val MAX_BYTES = 6 * 1024 * 1024

    sealed interface Result {
        data class Valid(val parsed: ParseResult) : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(bytes: ByteArray): Result {
        if (bytes.size > MAX_BYTES) return Result.Invalid("List exceeds ${MAX_BYTES / (1024 * 1024)} MB")
        if (bytes.isEmpty()) return Result.Invalid("List is empty")
        // Reject binary content: NUL bytes never occur in text filter lists.
        if (bytes.any { it == 0.toByte() }) return Result.Invalid("List contains binary data")
        val text = try {
            bytes.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            return Result.Invalid("List is not valid text")
        }
        val parsed = FilterListParser.parse(text)
        if (parsed.rules.isEmpty()) return Result.Invalid("List contains no supported rules")
        return Result.Valid(parsed)
    }
}
