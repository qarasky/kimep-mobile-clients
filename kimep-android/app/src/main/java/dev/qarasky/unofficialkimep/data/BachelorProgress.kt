package dev.qarasky.unofficialkimep.data

/** Credit-based bachelor's standing, not the student's actual year of enrollment. */
object BachelorProgress {
    const val GRADUATION_CREDITS = 146

    fun standingYear(creditsEarned: Int): Int = when {
        creditsEarned <= 30 -> 1
        creditsEarned <= 68 -> 2
        creditsEarned <= 106 -> 3
        else -> 4
    }

    fun graduationProgress(creditsEarned: Int): Float =
        (creditsEarned.toFloat() / GRADUATION_CREDITS).coerceIn(0f, 1f)

    fun creditsRemaining(creditsEarned: Int): Int =
        (GRADUATION_CREDITS - creditsEarned.coerceAtLeast(0)).coerceAtLeast(0)
}
