package dev.qarasky.unofficialkimep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingTest {
    @Test
    fun lowMidtermMakesHigherGradesImpossible() {
        val scores = listOf(20.0, null, null)
        assertEquals(76.0, Grading.maximumOverall(scores), 1e-9)
        assertNull(Grading.requiredOnRemaining(scores, 85.0))
        assertEquals(95.7142857, Grading.requiredOnRemaining(scores, 73.0)!!, 1e-6)
    }

    @Test
    fun linkedScoresRaiseFinalWhenMidtermDrops() {
        val scores = listOf(60.0, null, null)
        val plan = Grading.linkedScores(scores, 73.0, 1, 60.0)!!
        assertEquals(60.0, plan[0], 1e-9)
        assertEquals(60.0, plan[1], 1e-9)
        assertEquals(92.5, plan[2], 1e-9)
        assertEquals(73.0, Grading.weightedAverage(plan[0], plan[1], plan[2]), 1e-9)
    }

    @Test
    fun infeasibleSliderValuesStopAtFeasibleLimit() {
        val scores = listOf(60.0, null, null)
        val plan = Grading.linkedScores(scores, 73.0, 1, 0.0)!!
        assertEquals(50.0, plan[1], 1e-9)
        assertEquals(100.0, plan[2], 1e-9)
    }

    @Test
    fun anyRemainingAssessmentCanDriveThePlan() {
        for (scores in listOf(listOf(null, null, null), listOf(null, 60.0, null), listOf(null, null, 80.0))) {
            for (index in scores.indices.filter { scores[it] == null }) {
                for (value in listOf(0.0, 50.0, 100.0)) {
                    val plan = Grading.linkedScores(scores, 73.0, index, value)!!
                    assertTrue(plan.all { it in 0.0..100.0 })
                    assertEquals(73.0, Grading.weightedAverage(plan[0], plan[1], plan[2]), 1e-9)
                }
            }
        }
    }

    @Test
    fun oneRemainingScoreIsFixedToTheGoal() {
        val plan = Grading.linkedScores(listOf(60.0, 60.0, null), 73.0, 2, 20.0)!!
        assertEquals(92.5, plan[2], 1e-9)
    }

    @Test
    fun completedAndAlreadyMetGoalsAreHandled() {
        assertNull(Grading.requiredOnRemaining(listOf(60.0, 70.0, 80.0), 73.0))
        assertEquals(0.0, Grading.requiredOnRemaining(listOf(100.0, 100.0, null), 53.0)!!, 1e-9)
        val plan = Grading.linkedScores(listOf(100.0, 100.0, null), 53.0, 2, 80.0)!!
        assertEquals(0.0, plan[2], 1e-9)
    }

    @Test
    fun shortcutDetectsImpossibleScoreWithoutChangingTheGoal() {
        assertNull(Grading.requiredOnRemaining(listOf(20.0, 50.0, null), 73.0))
        assertNull(Grading.linkedScores(listOf(20.0, null, null), 85.0, 1, 100.0))
        assertNull(Grading.linkedScores(listOf(60.0, null, null), 73.0, 0, 100.0))
    }

    @Test
    fun exactMaximumIsReachable() {
        val scores = listOf(20.0, null, null)
        assertEquals(100.0, Grading.requiredOnRemaining(scores, 76.0)!!, 1e-9)
        assertEquals(listOf(20.0, 100.0, 100.0), Grading.linkedScores(scores, 76.0, 1, 0.0))
    }
}
