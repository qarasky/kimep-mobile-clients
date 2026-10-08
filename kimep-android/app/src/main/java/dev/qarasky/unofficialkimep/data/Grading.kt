package dev.qarasky.unofficialkimep.data

/**
 * KIMEP undergraduate grading scale (0-100 score -> letter -> 4.33 points).
 *
 * Score bands were provided by the user; boundaries are inclusive on the lower
 * end (e.g. 85.0 counts as A, 84.9 as A-). Anything below 50 is F.
 *
 * Assessment weights (user-provided): Midterm 1 30%, Midterm 2 30%,
 * Final 40%. Overall = s1*0.3 + s2*0.3 + s3*0.4.
 */
data class GradeStep(
    val letter: String,
    val point: Double,
    val minScore: Double,
)

object Grading {
    val scale: List<GradeStep> = listOf(
        GradeStep("A+", 4.33, 90.0),
        GradeStep("A", 4.00, 85.0),
        GradeStep("A-", 3.67, 80.0),
        GradeStep("B+", 3.33, 77.0),
        GradeStep("B", 3.00, 73.0),
        GradeStep("B-", 2.67, 70.0),
        GradeStep("C+", 2.33, 67.0),
        GradeStep("C", 2.00, 63.0),
        GradeStep("C-", 1.67, 60.0),
        GradeStep("D+", 1.33, 57.0),
        GradeStep("D", 1.00, 53.0),
        GradeStep("D-", 0.67, 50.0),
        GradeStep("F", 0.00, 0.0),
    )

    /** Midterm 1, Midterm 2, Final weights. Must sum to 1. */
    val assessmentWeights: List<Double> = listOf(0.3, 0.3, 0.4)

    /** Weighted overall for three complete scores. */
    fun weightedAverage(s1: Double, s2: Double, s3: Double): Double =
        s1 * assessmentWeights[0] + s2 * assessmentWeights[1] + s3 * assessmentWeights[2]

    /** Letter grades usable as calculator targets / hypothetical results. */
    val letters: List<String> = scale.map { it.letter }

    fun gradeForScore(score: Double): GradeStep =
        scale.firstOrNull { score >= it.minScore } ?: scale.last()

    fun pointForLetter(letter: String): Double =
        scale.firstOrNull { it.letter.equals(letter.trim(), ignoreCase = true) }?.point ?: 0.0

    fun minScoreForLetter(letter: String): Double =
        scale.firstOrNull { it.letter.equals(letter.trim(), ignoreCase = true) }?.minScore ?: 0.0

    /**
     * Cumulative GPA after adding one course result.
     * Note: treats the course as additional credits; retake-replacement
     * policies are not modelled (flagged in the UI).
     */
    fun projectedGpa(
        currentGpa: Double,
        creditsTaken: Int,
        coursePoint: Double,
        courseCredits: Int,
    ): Double {
        val total = creditsTaken + courseCredits
        if (total <= 0 || courseCredits <= 0) return currentGpa
        return (currentGpa * creditsTaken + coursePoint * courseCredits) / total
    }

    /**
     * Required uniform average on the remaining (ungraded, null slots)
     * assessments to reach an overall [targetAvg], using [assessmentWeights].
     * [scores] is size 3 (Score1..3), null = ungraded. Returns null when
     * already impossible (> 100 needed) or nothing remaining.
     */
    fun requiredOnRemaining(scores: List<Double?>, targetAvg: Double): Double? {
        require(scores.size == 3) { "scores must be size 3" }
        var remainingWeight = 0.0
        var knownPoints = 0.0
        scores.forEachIndexed { i, s ->
            if (s == null) remainingWeight += assessmentWeights[i]
            else knownPoints += s * assessmentWeights[i]
        }
        if (remainingWeight <= 0.0) return null
        val needed = (targetAvg - knownPoints) / remainingWeight
        return if (needed > 100.0) null else needed.coerceAtLeast(0.0)
    }
}
