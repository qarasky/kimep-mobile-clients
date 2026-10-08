package dev.qarasky.unofficialkimep.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BachelorProgressTest {
    @Test
    fun standingUsesEarnedCreditBoundaries() {
        val cases = mapOf(0 to 1, 30 to 1, 31 to 2, 68 to 2, 69 to 3, 106 to 3, 107 to 4, 146 to 4, 160 to 4)
        cases.forEach { (credits, year) ->
            assertEquals("Standing for $credits credits", year, BachelorProgress.standingYear(credits))
        }
    }

    @Test
    fun graduationUses146CreditsNotCreditsTaken() {
        assertEquals(98f / 146f, BachelorProgress.graduationProgress(98), 1e-6f)
        assertEquals(48, BachelorProgress.creditsRemaining(98))
        assertEquals(3, BachelorProgress.standingYear(98))
    }

    @Test
    fun graduationProgressIsClampedAtTarget() {
        for (credits in listOf(146, 160)) {
            assertEquals(1f, BachelorProgress.graduationProgress(credits), 0f)
            assertEquals(0, BachelorProgress.creditsRemaining(credits))
        }
        assertEquals(0f, BachelorProgress.graduationProgress(0), 0f)
        assertEquals(146, BachelorProgress.creditsRemaining(0))
        assertEquals(0f, BachelorProgress.graduationProgress(-1), 0f)
        assertEquals(146, BachelorProgress.creditsRemaining(-1))
    }
}
