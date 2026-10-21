package com.ankandebnath.allphotos.logic

/** Reads web addresses out of address-bar text or the text in a screenshot. */
object Links {
    private val tlds = setOf(
        "com", "org", "net", "io", "co", "ai", "app", "dev", "me", "tv", "gg", "info", "edu", "gov", "bd", "in",
        "uk", "us", "ca", "au", "de", "fr", "jp", "xyz", "site", "store", "shop", "blog", "news", "agency",
        "tech", "studio", "page", "link", "ly", "to", "fm", "so", "ru", "cn", "br", "es", "it", "nl", "pk", "sg",
    )
    private val hostRe = Regex("""(?i)\b((?:https?://)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+([a-z]{2,12}))(/[^\s"'<>]*)?""")

    /** Tidies an address bar's text: drops "https://", "www." and trailing slashes. */
    fun clean(raw: String?): String? {
        var s = raw?.trim() ?: return null
        if (s.isEmpty() || s.contains(' ') && !s.contains('/')) return null
        s = s.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        s = s.trimEnd('/')
        if (!s.contains('.')) return null
        return s.take(300)
    }

    /** The site part of an address: "en.wikipedia.org/wiki/x" → "en.wikipedia.org". */
    fun domain(url: String?): String? {
        val c = clean(url) ?: return null
        return c.substringBefore('/').substringBefore('?').substringBefore(':').lowercase().removePrefix("m.")
            .takeIf { it.contains('.') }
    }

    /** A link that opens the address in a browser. */
    fun openable(url: String?): String? {
        val c = clean(url) ?: return null
        return "https://$c"
    }

    /**
     * The most likely web address in a screenshot's text. Address bars sit near the top, so the
     * first convincing address wins.
     */
    fun findInText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        for (line in text.lineSequence().take(12)) {
            for (m in hostRe.findAll(line)) {
                val tld = m.groupValues[2].lowercase()
                val host = m.groupValues[1]
                if (tld !in tlds) continue
                if (host.contains('@')) continue
                val full = host + m.groupValues[3]
                return clean(full)
            }
        }
        return null
    }
}
