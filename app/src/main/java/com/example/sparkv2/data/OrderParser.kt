package com.example.sparkv2.data

object OrderParser {
    // Earnings shown with an explicit label are the most trustworthy signal for the payout —
    // far safer than "largest $ on screen", which on Shop & Deliver can be a cart subtotal.
    private val earningsRegex = Regex(
        """(?:estimated|est\.?|you(?:'|’)?ll\s+(?:earn|make)|earnings?|earn|payout|pay|total\s+pay|guaranteed)\s*:?\s*[\$€]?\s*([0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val earningsNoDollarRegex = Regex(
        """(?:estimated|est\.?|you(?:'|’)?ll\s+(?:earn|make)|earnings?|earn|payout|pay|ganar(?:ás)?|ganancia)\s*:?\s*([0-9]+(?:\.[0-9]{1,2})?)(?!\d)""",
        RegexOption.IGNORE_CASE,
    )
    private val trailingEarningsRegex = Regex(
        """([0-9]+(?:\.[0-9]{1,2})?)\s*(?:estimated|est\.?|estimate|earnings?|payout)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val explicitPriceRegex = Regex("""[\$€]\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    private val labeledEarningsRegex = Regex(
        """(?:est\.?|estimated|pay|payout|earn|earnings?)\s*:?\s*\$?\s*([0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val fallbackPriceRegex = Regex("""(?<!\d)([0-9]+(?:\.[0-9]{1,2})?)(?!\d)""")

    private val cartTotalContext = Regex(
        """(?:cart\s+total|subtotal|items?\s+total|order\s+total)\s*:?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // Tip / base-pay breakdown. Spark renders these a few different ways, so we try each in turn.
    private val tipRegexes = listOf(
        Regex("""\$\s*([0-9]+(?:\.[0-9]{1,2})?)\s*(?:in\s+)?tips?""", RegexOption.IGNORE_CASE),
        Regex(
            """(?:includes|incl\.?|est\.?|estimated|customer)?\s*tips?\s*:?\s*\$\s*([0-9]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""propina\s*:?\s*\$?\s*([0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
    )
    private val basePayRegexes = listOf(
        Regex(
            """(?:base(?:\s+pay)?|guaranteed|spark\s+pay)\s*:?\s*\$\s*([0-9]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """pago\s+base\
            |s*:?\s*\$?\s*([0-9]+(?:\.[0-9]{1,2})?)""".trimMargin(),
            RegexOption.IGNORE_CASE,
        ),
    )

    private val distanceRegexMi = Regex(
        """(\d+(?:\.\d+)?)\s*(?:mi|mile|miles)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val distanceRegexKm = Regex(
        """(\d+(?:\.\d+)?)\s*(?:km|kilometer|kilometers)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val labeledDistanceRegex = Regex(
        """(?:trip\s+distance|total\s+distance|distance|dist|trip)\s*:?\s*(\d+(?:\.\d+)?)\s*(?:mi|mile|miles|km)?""",
        RegexOption.IGNORE_CASE,
    )
    private val milesLeadingRegex = Regex(
        """(?:mi|mile|miles)\s*:?\s*(\d+(?:\.\d+)?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val deadheadContextRegex = Regex(
        """(?:to\s+store|to\s+pickup|to\s+the\s+store|deadhead|pickup\s+distance|store\s+distance|\d+(?:\.\d+)?\s*(?:mi|miles?)\s+to\s+(?:the\s+)?(?:store|pickup))\b[^|]{0,8}$""",
        RegexOption.IGNORE_CASE,
    )
    // Best-effort deadhead distance to the pickup; usually absent, so it stays null.
    private val storeDistanceRegex = Regex(
        """(?:to\s+store|to\s+pickup|to\s+the\s+store|pickup|store|deadhead)\s*(?:is|:)?\s*(\d+(?:\.\d+)?)\s*(?:mi|miles?)""",
        RegexOption.IGNORE_CASE,
    )
    private val storeDistanceLeadingRegex = Regex(
        """(\d+(?:\.\d+)?)\s*(?:mi|miles?)\s+to\s+(?:the\s+)?(?:store|pickup)""",
        RegexOption.IGNORE_CASE,
    )

    // Trip duration. Parse "1 hr 20 min" / "45 min" / "1 hour".
    private val hourMinRegex = Regex(
        """(\d+)\s*(?:h|hr|hrs|hour|hours)\b(?:\s*(\d+)\s*(?:m|min|mins|minute|minutes)\b)?""",
        RegexOption.IGNORE_CASE,
    )
    private val minOnlyRegex = Regex(
        """(\d+)\s*(?:min|mins|minute|minutes)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val itemsRegex = Regex(
        """(\d+)\s*(?:items?|art[ií]culos?|productos?|units?|unidades?)\b""",
        RegexOption.IGNORE_CASE,
    )

    // Pickup store, e.g. "Walmart LEOMINSTER #2964" or "Walmart Supercenter Leominster".
    private val storeWithNumberRegex = Regex(
        """walmart\s+([a-z][a-z0-9 .'&\-]*?)\s*#\s*(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val storeNameOnlyRegex = Regex(
        """walmart\s+([a-z][a-z .'&\-]{1,30}?)(?=\s*[|,]|\s{2,}|$)""",
        RegexOption.IGNORE_CASE,
    )

    private val dropoffsRegex = Regex(
        """(\d+)\s*(?:drop(?:\s*|-)?offs?|deliver(?:y|ies)|entregas?|stops?|locations?)""",
        RegexOption.IGNORE_CASE,
    )
    private val stopsOnlyRegex = Regex(
        """(\d+)\s*stops?\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): ParsedOrder? {
        val normalized = normalizeText(text)
        val price = parsePrice(normalized) ?: return null
        val storeDistance = storeDistanceLeadingRegex.find(normalized)?.groupValues?.get(1)?.toFloatOrNull()
            ?: storeDistanceRegex.find(normalized)?.groupValues?.get(1)?.toFloatOrNull()

        // Tip / base breakdown. Whichever is shown, derive the other from the total when possible
        // so $/hr and tip-ratio guards have data to work with.
        val rawTip = firstNumber(normalized, tipRegexes)
        val rawBase = firstNumber(normalized, basePayRegexes)
        val tip = rawTip ?: rawBase?.let { (price - it).takeIf { v -> v in 0.0..price } }
        val basePay = rawBase ?: rawTip?.let { (price - it).takeIf { v -> v in 0.0..price } }

        return ParsedOrder(
            price = price,
            distance = parseTripDistance(normalized),
            dropoffs = parseDropoffs(normalized),
            flags = OrderFlagDetector.detect(normalized),
            text = normalized,
            basePay = basePay,
            tip = tip,
            estimatedMinutes = parseMinutes(normalized),
            itemCount = itemsRegex.find(normalized)?.groupValues?.get(1)?.toIntOrNull(),
            storeDistance = storeDistance,
            storeName = parseStore(normalized),
        )
    }

    internal fun parseFirstCard(text: String): ParsedOrder? {
        val normalized = normalizeText(text)
        val prices = explicitPriceRegex.findAll(normalized).toList()
        if (prices.size >= 2) {
            val slice = normalized.substring(0, prices[1].range.first).trim().trimEnd('|', ' ')
            parse(slice)?.let { return it }
        }
        // Some stacked feeds repeat "Earn" labels without a second $ — split on that boundary.
        val earnSplit = Regex(
            """\b(?:earn|ganar(?:ás)?|you(?:'|')?ll\s+(?:earn|make))\b""",
            RegexOption.IGNORE_CASE,
        )
        val earnMatches = earnSplit.findAll(normalized).toList()
        if (earnMatches.size >= 2) {
            val slice = normalized.substring(0, earnMatches[1].range.first).trim().trimEnd('|', ' ')
            parse(slice)?.let { return it }
        }
        return null
    }

    /**
     * When Spark pipes offer fields together ("$22 | 2 stops | 15 miles | ASAP Shopping"),
     * try each segment and growing prefixes until one parses cleanly.
     */
    internal fun parseBestSegment(text: String): ParsedOrder? {
        val normalized = normalizeText(text)
        if (!normalized.contains('|')) return null

        val segments = normalized.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        for (segment in segments) {
            parse(segment)?.let { return it }
        }
        for (i in 1 until segments.size) {
            parse(segments.take(i).joinToString(" | "))?.let { return it }
        }
        return null
    }

    private fun normalizeText(text: String): String {
        return text
            .replace('\n', ' ')
            .replace('’', '\'')
            .replace(Regex("""[\u00A0\u2009]"""), " ")
            .trim()
    }

    internal fun parsePrice(normalized: String): Double? {
        earningsRegex.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        earningsNoDollarRegex.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        labeledEarningsRegex.findAll(normalized)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .firstOrNull()
            ?.let { return it }

        trailingEarningsRegex.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        explicitPriceRegex.findAll(normalized)
            .mapNotNull { match ->
                val contextStart = (match.range.first - 28).coerceAtLeast(0)
                val context = normalized.substring(contextStart, match.range.first)
                if (isCartTotalContext(context)) return@mapNotNull null
                match.groupValues[1].toDoubleOrNull()
            }
            .maxOrNull()
            ?.let { return it }

        return fallbackPriceRegex.findAll(normalized)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 4.0..200.0 }
            .maxOrNull()
    }

    internal fun parseTripDistance(normalized: String): Float? {
        labeledDistanceRegex.find(normalized)?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }

        milesLeadingRegex.find(normalized)?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }

        val tripMiles = distanceRegexMi.findAll(normalized)
            .mapNotNull { match ->
                val contextStart = (match.range.first - 28).coerceAtLeast(0)
                val context = normalized.substring(contextStart, match.range.first)
                if (deadheadContextRegex.containsMatchIn(context)) return@mapNotNull null
                match.groupValues[1].toFloatOrNull()
            }
            .toList()
        tripMiles.maxOrNull()?.let { return it }

        return distanceRegexKm.find(normalized)?.groupValues?.get(1)?.toFloatOrNull()
            ?.times(0.621371f)
    }

    internal fun parseDropoffs(normalized: String): Int {
        return dropoffsRegex.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            ?: stopsOnlyRegex.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            ?: 1
    }

    internal fun isCartTotalContext(contextBeforePrice: String): Boolean {
        return cartTotalContext.containsMatchIn(contextBeforePrice.trimEnd()) ||
            contextBeforePrice.contains("cart total", ignoreCase = true) ||
            contextBeforePrice.contains("subtotal", ignoreCase = true)
    }

    /**
     * Reads the pickup store off the offer text. Prefers the "<name> #<number>" form so two
     * offers from the same city but different stores stay distinguishable; falls back to the
     * bare "Walmart <name>" when no store number is shown. Returns a tidy Title-Cased label.
     */
    private fun parseStore(text: String): String? {
        storeWithNumberRegex.find(text)?.let { m ->
            val name = m.groupValues[1].trim().trimEnd(',', '|', '-').trim()
            val number = m.groupValues[2]
            if (name.isNotBlank()) return "Walmart ${titleCase(name)} #$number"
        }
        storeNameOnlyRegex.find(text)?.let { m ->
            val name = m.groupValues[1].trim().trimEnd(',', '|', '-').trim()
            if (name.isNotBlank() && !name.equals("supercenter", ignoreCase = true)) {
                return "Walmart ${titleCase(name)}"
            }
        }
        return null
    }

    private fun titleCase(raw: String): String {
        return raw.split(' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun firstNumber(text: String, regexes: List<Regex>): Double? {
        for (regex in regexes) {
            regex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    private fun parseMinutes(text: String): Int? {
        hourMinRegex.find(text)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull()
            val mins = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (hours != null) return hours * 60 + mins
        }
        return minOnlyRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun meetsCriteria(order: ParsedOrder, settings: SparkSettings): Boolean {
        return OfferEvaluator.evaluate(order, settings).passes
    }
}
