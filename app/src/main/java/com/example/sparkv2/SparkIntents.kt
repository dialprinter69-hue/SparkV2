package com.example.sparkv2

import com.example.sparkv2.automation.TextIntent

/** Semantic intents for Spark Driver UI — matched fuzzily via [com.example.sparkv2.automation.TextMatcher]. */
object SparkIntents {

    val ACCEPT = TextIntent(
        roots = listOf("accept", "aceptar", "acepta", "acept"),
        excludes = listOf("acceptance", "accept rate", "acceptable", "accepted offers"),
    )

    /**
     * Post-acceptance "advance the trip" controls. The bot must NEVER tap these — accepting an
     * offer is fine, but starting the trip/shopping is left to the driver so they can still
     * cancel if they change their mind.
     */
    val START_TRIP = TextIntent(
        // NOTE: "navigate"/"navegar" are deliberately NOT roots — the map's standard
        // "Navigate up" back-button content-description matched them and made the whole offer
        // feed look like an already-accepted trip, freezing the bot. The "start navigation"
        // phrase below still catches the real trip control.
        roots = listOf("start", "begin", "iniciar", "comenzar", "empezar", "arrive"),
        phrases = listOf(
            "start trip",
            "start shopping",
            "start order",
            "start delivery",
            "start route",
            "begin trip",
            "begin shopping",
            "start now",
            "i've arrived",
            "i have arrived",
            "go to store",
            "start navigation",
            "iniciar viaje",
            "comenzar viaje",
            "empezar viaje",
            "iniciar entrega",
            "comenzar compra",
        ),
        excludes = listOf("navigate up", "navigate back", "up navigation"),
        minRootLength = 5,
    )

    val REJECT = TextIntent(
        // "pass", "skip", "dismiss" removed — too generic, can accidentally match text on or
        // near the Accept button (e.g. "Skip other offers", contentDescriptions, etc.)
        // Spanish conjugations are listed as whole words: the generic root rule rejects letter
        // suffixes (so "accept" ≠ "acceptance"), which would also stop "rechaz" matching
        // "rechazar". Exact-word roots sidestep that.
        roots = listOf(
            "reject", "decline", "rechaz", "rechazar", "rechazo", "rechaza", "refus", "deny",
        ),
        phrases = listOf(
            "not now",
            "no thanks",
            "ahora no",
            "no gracias",
            "maybe later",
            "pass on",
            "skip offer",
            "skip this offer",
        ),
        excludes = listOf("accept"),
        minRootLength = 4,
    )

    val REJECT_CONFIRM = TextIntent(
        roots = listOf("reject", "decline", "rechaz", "confirm", "yes"),
        phrases = listOf(
            "reject offer",
            "reject offers",
            "reject order",
            "decline offer",
            "decline offers",
            "decline order",
            "yes reject",
            "yes decline",
            "confirm reject",
            "confirm decline",
            "rechazar oferta",
            "rechazar orden",
            "rechazar pedido",
            "si rechazar",
            "sure",
            "want to reject",
            "want to decline",
        ),
        excludes = listOf("go back", "cancel", "keep", "stay", "never mind"),
        minRootLength = 4,
    )

    val CANCEL = TextIntent(
        roots = listOf("cancel", "back", "keep", "stay", "volver", "manten", "close"),
        phrases = listOf("go back", "never mind", "not now"),
        minRootLength = 4,
    )

    val OFFER_CARD = TextIntent(
        roots = listOf("offer", "trip", "deliver", "round", "robin", "estimated", "est", "earning"),
        phrases = listOf(
            "just for you",
            "round robin",
            "view offer",
            "view offers",
            "trip detail",
            "trip details",
            "solo para ti",
            "new offer",
            "new offers",
            "new trip",
            "incoming offer",
            "delivery offer",
        ),
        minRootLength = 3,
    )

    val CONFIRM_DIALOG = TextIntent(
        phrases = listOf(
            "are you sure",
            "sure you want",
            "decline this",
            "decline offer",
            "decline offers",
            "decline order",
            "reject this",
            "reject offer",
            "reject offers",
            "reject order",
            "rejecting",
            "declining",
            "seguro que",
            "rechazar esta",
            "rechazar oferta",
            "rechazar orden",
            "rechazar pedido",
            "want to reject",
            "want to decline",
        ),
        roots = listOf("confirm"),
        minRootLength = 4,
    )
}
