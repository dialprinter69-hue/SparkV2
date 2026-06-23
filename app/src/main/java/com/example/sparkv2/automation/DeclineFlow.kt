package com.example.sparkv2.automation

class DeclineFlow {
    // Read from the accessibility (main) thread and mutated from the scan thread, so guard
    // visibility with @Volatile.
    @Volatile private var awaitingConfirmation = false
    @Volatile private var startedAtMs = 0L
    @Volatile private var confirmAttempts = 0

    fun startDecline() {
        awaitingConfirmation = true
        startedAtMs = System.currentTimeMillis()
        confirmAttempts = 0
    }

    fun reset() {
        awaitingConfirmation = false
        startedAtMs = 0L
        confirmAttempts = 0
    }

    /** Increments and returns the number of confirm-tap attempts for the current decline. */
    fun recordConfirmAttempt(): Int = ++confirmAttempts

    fun isAwaitingConfirmation(): Boolean {
        if (!awaitingConfirmation) return false
        if (System.currentTimeMillis() - startedAtMs > CONFIRM_TIMEOUT_MS) {
            reset()
            return false
        }
        return true
    }

    companion object {
        private const val CONFIRM_TIMEOUT_MS = 20_000L
    }
}
