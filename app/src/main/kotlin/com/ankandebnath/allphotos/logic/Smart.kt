package com.ankandebnath.allphotos.logic

/** Something useful found in a piece of text: a phone number, a link or an email address. */
data class SmartAction(val kind: Kind, val value: String) {
    enum class Kind { PHONE, LINK, EMAIL }
}

/** Finds phone numbers, links and emails in text read from a photo. */
object Smart {
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    // Bangladeshi and international numbers: +8801XXXXXXXXX, 01XXX-XXXXXX, (02) 1234 5678 and the like.
    private val phone = Regex("""(?<![\w.])(\+?\d[\d\s().-]{6,17}\d)(?![\w.])""")

    fun find(text: String): List<SmartAction> {
        val out = ArrayList<SmartAction>()
        val emails = email.findAll(text).map { it.value }.toList()
        emails.forEach { out += SmartAction(SmartAction.Kind.EMAIL, it) }
        val withoutEmails = emails.fold(text) { acc, e -> acc.replace(e, " ") }
        Links.findInText(withoutEmails)?.let { out += SmartAction(SmartAction.Kind.LINK, it) }
        for (m in phone.findAll(withoutEmails)) {
            val raw = m.groupValues[1]
            val digits = raw.count { it.isDigit() }
            // Dates, prices and long reference numbers are not phone numbers.
            if (digits !in 7..15) continue
            if (Regex("""^\d{1,2}[./-]\d{1,2}[./-]\d{2,4}$""").matches(raw.trim())) continue
            if (raw.count { it == '.' } > 1) continue
            out += SmartAction(SmartAction.Kind.PHONE, raw.trim().replace(Regex("""\s+"""), " "))
        }
        return out.distinctBy { it.kind to it.value }
    }

    /** The number as the dialler wants it: digits and a leading plus only. */
    fun dialable(phone: String): String = buildString {
        phone.forEachIndexed { i, c -> if (c.isDigit() || (c == '+' && i == 0)) append(c) }
    }
}
