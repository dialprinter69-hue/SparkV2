package com.example.sparkv2.data

enum class CriterionId {
    MIN_PAY,
    MAX_DISTANCE,
    DOLLARS_PER_MILE,
    DOLLARS_PER_HOUR,
    MIN_BASE_PAY,
    MAX_TIP_RATIO,
    MAX_DEADHEAD,
    MAX_ITEMS,
    MAX_DROPOFFS,
    STORE_LOCATION,
    ORDER_TYPES,
    OFFER_TAGS,
}

data class CriterionResult(
    val id: CriterionId,
    val passed: Boolean,
    val actual: String,
    val required: String,
    val note: String? = null,
) {
    companion object {
        const val NOTE_UNKNOWN = "unknown"
    }
}

data class OfferEvaluation(
    val passes: Boolean,
    val criteria: List<CriterionResult>,
    val detectedTypes: List<FlagLabel>,
    val detectedTags: List<FlagLabel>,
    val dollarsPerMile: Float?,
    val dollarsPerHour: Float? = null,
    val tip: Double? = null,
    val basePay: Double? = null,
) {
    val failingCriteria: List<CriterionResult> get() = criteria.filter { !it.passed }
}

/** Maps an active order flag to a string resource for display. */
data class FlagLabel(
    val nameRes: Int,
    val allowed: Boolean,
)

object OfferEvaluator {

    fun evaluate(order: ParsedOrder, settings: SparkSettings): OfferEvaluation {
        val criteria = mutableListOf<CriterionResult>()

        criteria += minPayCheck(order, settings)
        criteria += distanceCheck(order, settings)
        criteria += dollarsPerMileCheck(order, settings)
        // Spark-rich criteria are only evaluated when the driver turned them on, so the "why"
        // list stays focused and an unused filter can never reject an offer.
        if (settings.minDollarsPerHour > 0f) criteria += dollarsPerHourCheck(order, settings)
        if (settings.minBasePay > 0f) criteria += basePayCheck(order, settings)
        if (settings.maxTipRatio < 1f) criteria += tipRatioCheck(order, settings)
        if (settings.maxDeadhead > 0f) criteria += deadheadCheck(order, settings)
        if (settings.itemLimitEnabled) criteria += itemsCheck(order, settings)
        criteria += dropoffsCheck(order, settings)
        criteria += storeCheck(order, settings)
        criteria += orderTypeCheck(order, settings)
        criteria += offerTagCheck(order, settings)

        val dollarsPerMile = order.distance?.takeIf { it > 0f }?.let {
            (order.price / it).toFloat()
        }

        val (types, tags) = detectFlagLabels(order.flags, settings)

        return OfferEvaluation(
            passes = criteria.all { it.passed },
            criteria = criteria,
            detectedTypes = types,
            detectedTags = tags,
            dollarsPerMile = dollarsPerMile,
            dollarsPerHour = order.dollarsPerHour?.toFloat(),
            tip = order.tip,
            basePay = order.effectiveBasePay,
        )
    }

    private fun dollarsPerHourCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val perHour = order.dollarsPerHour?.toFloat()
        val passed = perHour?.let { it >= settings.minDollarsPerHour } ?: true
        return CriterionResult(
            id = CriterionId.DOLLARS_PER_HOUR,
            passed = passed,
            actual = perHour?.let { formatPerHour(it) } ?: "?",
            required = formatPerHour(settings.minDollarsPerHour),
            note = if (perHour == null) CriterionResult.NOTE_UNKNOWN else null,
        )
    }

    private fun basePayCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val base = order.effectiveBasePay?.toFloat()
        val passed = base?.let { it >= settings.minBasePay } ?: true
        return CriterionResult(
            id = CriterionId.MIN_BASE_PAY,
            passed = passed,
            actual = base?.let { formatMoney(it) } ?: "?",
            required = formatMoney(settings.minBasePay),
            note = if (base == null) CriterionResult.NOTE_UNKNOWN else null,
        )
    }

    private fun tipRatioCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val ratio = order.tipRatio?.toFloat()
        val passed = ratio?.let { it <= settings.maxTipRatio } ?: true
        return CriterionResult(
            id = CriterionId.MAX_TIP_RATIO,
            passed = passed,
            actual = ratio?.let { formatPercent(it) } ?: "?",
            required = formatPercent(settings.maxTipRatio),
            note = if (ratio == null) CriterionResult.NOTE_UNKNOWN else null,
        )
    }

    private fun deadheadCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val deadhead = order.storeDistance
        val passed = deadhead?.let { it <= settings.maxDeadhead } ?: true
        return CriterionResult(
            id = CriterionId.MAX_DEADHEAD,
            passed = passed,
            actual = deadhead?.let { formatMiles(it) } ?: "?",
            required = formatMiles(settings.maxDeadhead),
            note = if (deadhead == null) CriterionResult.NOTE_UNKNOWN else null,
        )
    }

    private fun itemsCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val items = order.itemCount
        val passed = items?.let { it <= settings.maxItems } ?: true
        return CriterionResult(
            id = CriterionId.MAX_ITEMS,
            passed = passed,
            actual = items?.toString() ?: "?",
            required = settings.maxItems.toString(),
            note = if (items == null) CriterionResult.NOTE_UNKNOWN else null,
        )
    }

    private fun minPayCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val passed = order.price >= settings.minPay
        return CriterionResult(
            id = CriterionId.MIN_PAY,
            passed = passed,
            actual = formatMoney(order.price),
            required = formatMoney(settings.minPay.toDouble()),
        )
    }

    private fun distanceCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val distance = order.distance
        val passed = distance?.let { it <= settings.maxDistance } ?: true
        return CriterionResult(
            id = CriterionId.MAX_DISTANCE,
            passed = passed,
            actual = distance?.let { formatMiles(it) } ?: "?",
            required = formatMiles(settings.maxDistance),
            note = if (distance == null) CriterionResult.NOTE_UNKNOWN else null,
        )
    }

    private fun dollarsPerMileCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val distance = order.distance
        val actualPerMile = distance?.takeIf { it > 0f }?.let { (order.price / it).toFloat() }
        // Unknown distance must not reject — same rule as max-distance and $/hr filters.
        val passed = actualPerMile?.let { it >= settings.dollarsPerMile } ?: true
        return CriterionResult(
            id = CriterionId.DOLLARS_PER_MILE,
            passed = passed,
            actual = actualPerMile?.let { formatPerMile(it) } ?: "?",
            required = formatPerMile(settings.dollarsPerMile),
            note = if (actualPerMile == null) CriterionResult.NOTE_UNKNOWN else null,
        )
    }

    private fun dropoffsCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val passed = order.dropoffs <= settings.numDropoffs
        return CriterionResult(
            id = CriterionId.MAX_DROPOFFS,
            passed = passed,
            actual = order.dropoffs.toString(),
            required = settings.numDropoffs.toString(),
        )
    }

    private fun storeCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val passed = StoreMatcher.passes(order.text, settings.storeFilterMode, settings.stores)
        val matched = StoreMatcher.firstMatch(order.text, settings.stores)?.name
        val required = if (settings.storeFilterMode == StoreFilterMode.ONLY_SELECTED) {
            settings.stores.filter { it.enabled && it.name.isNotBlank() }
                .joinToString(", ") { it.name }
                .ifBlank { "any" }
        } else {
            "any"
        }
        return CriterionResult(
            id = CriterionId.STORE_LOCATION,
            passed = passed,
            actual = matched ?: if (passed) "any" else "no match",
            required = required,
        )
    }

    private fun orderTypeCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val typeRules = listOf(
            order.flags.shopDeliverCurbside to settings.shopDeliverCurbside,
            order.flags.shopAndDeliver to settings.shopAndDeliver,
            order.flags.curbside to settings.curbside,
            order.flags.pharmacy to settings.pharmacy,
            order.flags.dotcom to settings.dotcom,
            order.flags.customerReturns to settings.customerReturns,
        )
        val present = typeRules.filter { it.first }
        val passed = present.all { it.second }
        val blocked = present.filter { !it.second }.size
        return CriterionResult(
            id = CriterionId.ORDER_TYPES,
            passed = passed,
            actual = if (present.isEmpty()) "none" else "${present.size}",
            required = if (blocked > 0) "blocked" else "allowed",
        )
    }

    private fun offerTagCheck(order: ParsedOrder, settings: SparkSettings): CriterionResult {
        val tagRules = listOf(
            order.flags.bulkyItem to settings.bulkyItem,
            order.flags.shopperBulk to settings.shopperBulk,
            order.flags.apartment to settings.apartment,
            order.flags.customerVerification to settings.customerVerification,
            order.flags.alcohol to settings.alcohol,
            order.flags.heavy to settings.heavy,
        )
        val present = tagRules.filter { it.first }
        val passed = present.all { it.second }
        val blocked = present.filter { !it.second }.size
        return CriterionResult(
            id = CriterionId.OFFER_TAGS,
            passed = passed,
            actual = if (present.isEmpty()) "none" else "${present.size}",
            required = if (blocked > 0) "blocked" else "allowed",
        )
    }

    private fun detectFlagLabels(flags: OrderFlags, settings: SparkSettings): Pair<List<FlagLabel>, List<FlagLabel>> {
        val types = listOf(
            Triple(flags.shopDeliverCurbside, com.example.sparkv2.R.string.shop_deliver_curbside, settings.shopDeliverCurbside),
            Triple(flags.shopAndDeliver, com.example.sparkv2.R.string.shop_and_deliver, settings.shopAndDeliver),
            Triple(flags.curbside, com.example.sparkv2.R.string.curbside, settings.curbside),
            Triple(flags.pharmacy, com.example.sparkv2.R.string.pharmacy, settings.pharmacy),
            Triple(flags.dotcom, com.example.sparkv2.R.string.dotcom, settings.dotcom),
            Triple(flags.customerReturns, com.example.sparkv2.R.string.customer_returns, settings.customerReturns),
        ).filter { it.first }.map { FlagLabel(it.second, it.third) }

        val tags = listOf(
            Triple(flags.bulkyItem, com.example.sparkv2.R.string.bulky_items, settings.bulkyItem),
            Triple(flags.shopperBulk, com.example.sparkv2.R.string.shopper_bulk, settings.shopperBulk),
            Triple(flags.apartment, com.example.sparkv2.R.string.apartment, settings.apartment),
            Triple(flags.customerVerification, com.example.sparkv2.R.string.customer_verification, settings.customerVerification),
            Triple(flags.alcohol, com.example.sparkv2.R.string.alcohol, settings.alcohol),
            Triple(flags.heavy, com.example.sparkv2.R.string.heavy, settings.heavy),
        ).filter { it.first }.map { FlagLabel(it.second, it.third) }

        return types to tags
    }

    private fun formatMoney(value: Double): String = "$%.2f".format(value)

    private fun formatMoney(value: Float): String = "$%.2f".format(value)

    private fun formatMiles(value: Float): String = "%.1f mi".format(value)

    private fun formatPerMile(value: Float): String = "$%.2f/mi".format(value)

    private fun formatPerHour(value: Float): String = "$%.0f/hr".format(value)

    private fun formatPercent(value: Float): String = "%.0f%%".format(value * 100)
}
