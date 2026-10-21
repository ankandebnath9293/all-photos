package com.ankandebnath.allphotos.logic

/** Kinds of document a photo can show, like the Documents section in Google Photos. */
enum class DocKind(val label: String) {
    RECEIPT("Receipts"),
    PAYMENT("Payment methods"),
    IDENTITY("Identity"),
    EVENT("Event information"),
    MENU("Recipes and menus"),
    BOOKS("Books and magazines"),
    NOTES("Notes"),
}

/**
 * Sorts photos into document kinds from the text read off them and a few on-device labels. No
 * model is downloaded and nothing leaves the phone: it is keyword scoring over what was read.
 */
object Docs {
    private val card = Regex("""\b(?:\d{4}[ -]){3}\d{4}\b|\b\d{4}[ -]?\*{4}[ -]?\*{4}[ -]?\d{4}\b|\*{4}\s?\d{4}""")
    private val price = Regex("""(?m)(?:৳|tk\.?|bdt|rs\.?|\$|€|£)\s?\d[\d,]*(?:\.\d{1,2})?|\d[\d,]*\.\d{2}\s*$""")

    private val payment = listOf(
        "valid thru", "valid from", "visa", "mastercard", "master card", "debit", "credit card", "card number", "card no",
        "cvv", "cvc", "american express", "amex", "unionpay", "account number", "account no", "a/c no", "routing", "swift",
        "iban", "cardholder", "card holder", "platinum", "classic", "expiry", "exp date",
    )
    private val receipt = listOf(
        "total", "subtotal", "sub total", "grand total", "vat", "tax", "cash", "change", "qty", "quantity", "invoice",
        "bill", "amount", "paid", "receipt", "discount", "transaction id", "trxid", "trx id", "txn", "payment successful",
        "send money", "sent money", "cash out", "cash in", "reference", "order id", "order no", "item", "unit price",
        "service charge", "thank you for", "balance", "payment", "fee", "charge",
    )
    private val identity = listOf(
        "national id", "nid", "identity card", "id card", "passport", "date of birth", "birth", "student id", "id no",
        "id number", "employee id", "driving licen", "blood group", "valid till", "valid upto", "registration no",
        "people's republic", "government", "nationality", "father", "mother", "signature", "roll no", "student",
    )
    private val event = listOf(
        "ticket", "entry pass", "admit one", "admission", "seat", "gate", "venue", "event", "booking", "pnr",
        "boarding pass", "flight", "departure", "arrival", "concert", "show time", "showtime", "screen", "hall",
        "e-ticket", "reservation", "confirmation", "check-in", "check in", "coach", "journey", "passenger",
    )
    private val menu = listOf(
        "menu", "combo", "pcs", "burger", "pizza", "chicken", "fries", "set menu", "recipe", "ingredients", "tbsp",
        "tsp", "cup", "offer", "special", "biryani", "drinks", "coffee", "beverage", "dessert", "platter", "meal",
        "rice", "noodles", "soup", "salad", "sandwich", "starter", "kebab", "tea",
    )
    private val bookLabels = setOf("book", "newspaper", "magazine", "publication", "comics")
    private val noteLabels = setOf("paper", "handwriting", "whiteboard", "blackboard", "notebook", "paper product")

    private fun hits(t: String, words: List<String>): Int = words.count { w ->
        // Short words must stand alone ("tax" yes, "taxi" no).
        if (w.length <= 4) Regex("""(?<![a-z])${Regex.escape(w)}(?![a-z])""").containsMatchIn(t) else t.contains(w)
    }

    fun classify(text: String?, labels: List<String>, isScreenshot: Boolean): DocKind? {
        val raw = text.orEmpty()
        val t = raw.lowercase()
        val lab = labels.map { it.lowercase() }
        val lines = raw.lines().count { it.isNotBlank() }
        if (t.length < 12 && lab.none { it in bookLabels || it in noteLabels || it == "menu" }) return null

        val pay = hits(t, payment) + if (card.containsMatchIn(raw)) 2 else 0
        val id = hits(t, identity)
        val rec = hits(t, receipt) + price.findAll(t).count().coerceAtMost(3)
        val ev = hits(t, event)
        val mn = hits(t, menu) + if ("menu" in lab) 2 else 0

        return when {
            pay >= 3 && pay >= rec -> DocKind.PAYMENT
            id >= 3 && !isScreenshot || id >= 4 -> DocKind.IDENTITY
            rec >= 4 && rec >= ev && rec >= mn -> DocKind.RECEIPT
            ev >= 3 -> DocKind.EVENT
            mn >= 3 -> DocKind.MENU
            rec >= 3 -> DocKind.RECEIPT
            isScreenshot -> null
            lab.any { it in bookLabels } || (t.length > 600 && lines >= 10) -> DocKind.BOOKS
            lab.any { it in noteLabels } || (t.length >= 60 && lines >= 4) -> DocKind.NOTES
            else -> null
        }
    }
}
