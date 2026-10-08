package dev.qarasky.unofficialkimep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.qarasky.unofficialkimep.data.ApiDate
import dev.qarasky.unofficialkimep.data.Grading
import dev.qarasky.unofficialkimep.data.analytics.Analytics
import dev.qarasky.unofficialkimep.data.analytics.AnalyticsEvents
import dev.qarasky.unofficialkimep.data.model.AssessmentScore
import dev.qarasky.unofficialkimep.data.model.ClassMeeting
import dev.qarasky.unofficialkimep.data.model.FinalGrade
import dev.qarasky.unofficialkimep.data.model.GpaCredits

/** What the course bottom sheet shows. One entry point per tappable card. */
sealed interface CourseDetail {
    data class Current(val score: AssessmentScore) : CourseDetail
    data class Completed(val grade: FinalGrade) : CourseDetail
    data class Scheduled(val meeting: ClassMeeting) : CourseDetail
}

/**
 * Expandable bottom sheet (swipe up for full page) with per-course details
 * and a what-if GPA calculator.
 *
 * Score weights are not exposed by the API, so the predictor assumes equal
 * weights (overall = mean of Score1/2/3). Retake modelling treats the new
 * grade as additional credits — replacement policies are not modelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    detail: CourseDetail?,
    gpa: GpaCredits,
    analytics: Analytics?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (detail == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(detail) {
        analytics?.track(AnalyticsEvents.COURSE_DETAIL, mapOf("kind" to kindOf(detail)))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (detail) {
                is CourseDetail.Current -> CurrentContent(detail.score, gpa)
                is CourseDetail.Completed -> CompletedContent(detail.grade, gpa)
                is CourseDetail.Scheduled -> ScheduledContent(detail.meeting, gpa)
            }
        }
    }
}

private fun kindOf(detail: CourseDetail): String = when (detail) {
    is CourseDetail.Current -> "current"
    is CourseDetail.Completed -> "completed"
    is CourseDetail.Scheduled -> "scheduled"
}

@Composable
private fun SheetTitle(title: String, subtitle: String?) {
    Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (!subtitle.isNullOrBlank()) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrentContent(score: AssessmentScore, gpa: GpaCredits) {
    SheetTitle(score.title ?: "Course", score.code)
    score.finalAssessment?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = "Final assessment: $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    val labels = listOf("Midterm 1 · 30%", "Midterm 2 · 30%", "Final · 40%")
    val apiScores = remember(score) {
        listOf(score.score1, score.score2, score.score3)
    }
    val remainingIndices = remember(score) {
        apiScores.mapIndexedNotNull { i, s -> if (s == null) i else null }
    }

    val gradedText = apiScores.mapIndexedNotNull { i, s ->
        s?.let { "${labels[i].substringBefore(" ·")}: ${"%.0f".format(it)}" }
    }
    if (gradedText.isNotEmpty()) {
        Text(
            text = "Graded so far: " + gradedText.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    var guess1 by rememberSaveable(score) { mutableFloatStateOf(75f) }
    var guess2 by rememberSaveable(score) { mutableFloatStateOf(75f) }
    var guess3 by rememberSaveable(score) { mutableFloatStateOf(75f) }
    fun guessFor(i: Int): Float = when (i) {
        0 -> guess1
        1 -> guess2
        else -> guess3
    }
    fun setGuess(i: Int, v: Float) = when (i) {
        0 -> guess1 = v
        1 -> guess2 = v
        else -> guess3 = v
    }

    if (remainingIndices.isNotEmpty()) {
        Text(
            text = "Try remaining scores",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        remainingIndices.forEach { i ->
            ScoreSlider(
                label = "Assessment ${i + 1} (${labels[i].substringAfter("· ")})",
                value = guessFor(i),
                onValue = { setGuess(i, it) },
            )
        }
    }

    val effective = List(3) { i -> apiScores[i] ?: guessFor(i).toDouble() }
    val overall = Grading.weightedAverage(effective[0], effective[1], effective[2])
    val projected = Grading.gradeForScore(overall)

    ResultRow(
        overall = overall,
        letter = projected.letter,
        point = projected.point,
    )

    var target by rememberSaveable(score) { mutableStateOf(projected.letter) }
    GradePicker(selected = target, onSelect = { target = it })

    val targetMin = Grading.minScoreForLetter(target)
    val required = Grading.requiredOnRemaining(apiScores, targetMin)
    Text(
        text = when {
            remainingIndices.isEmpty() && overall >= targetMin ->
                "Target $target met with current scores."
            remainingIndices.isEmpty() ->
                "Target $target not met — all assessments are graded."
            required == null ->
                "Target $target is out of reach (would need over 100 on remaining)."
            else ->
                "Need ${"%.1f".format(required)} avg on remaining to reach $target (needs $targetMin overall)."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    var credits by rememberSaveable(score) { mutableIntStateOf(3) }
    CreditsStepper(credits = credits, onChange = { credits = it })
    GpaImpact(gpa = gpa, coursePoint = projected.point, courseCredits = credits)
    AssumptionNote("Weights: Midterm 1 · 30%, Midterm 2 · 30%, Final · 40%.")
}

@Composable
private fun CompletedContent(grade: FinalGrade, gpa: GpaCredits) {
    SheetTitle(grade.title, semesterLabel(grade.semester))
    Row(verticalAlignment = Alignment.CenterVertically) {
        GradeBadge(grade.grade)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "%.2f pts".format(grade.point),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        text = "Retake simulator",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    var newGrade by rememberSaveable(grade) { mutableStateOf(grade.grade) }
    var credits by rememberSaveable(grade) { mutableIntStateOf(3) }
    GradePicker(selected = newGrade, onSelect = { newGrade = it })
    CreditsStepper(credits = credits, onChange = { credits = it })
    GpaImpact(gpa = gpa, coursePoint = Grading.pointForLetter(newGrade), courseCredits = credits)
    AssumptionNote("Modelled as additional credits; replacement policy not applied.")
}

@Composable
private fun ScheduledContent(meeting: ClassMeeting, gpa: GpaCredits) {
    SheetTitle(meeting.title, meeting.courseId)
    meeting.hall?.takeIf { it.isNotBlank() }?.let {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
    }
    meeting.instructor?.takeIf { it.isNotBlank() }?.let {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
    }
    val time = listOfNotNull(
        ApiDate.formatTime(meeting.timeFrom),
        ApiDate.formatTime(meeting.timeTo),
    ).joinToString(" – ").takeIf { it.isNotBlank() }
    Text(
        text = listOfNotNull(meeting.weekDay.takeIf { it.isNotBlank() }, time).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    meeting.section?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = "Section $it · ${semesterLabel(meeting.semester)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        text = "What-if calculator",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    var hypo by rememberSaveable(meeting) { mutableStateOf("A") }
    var credits by rememberSaveable(meeting) { mutableIntStateOf(3) }
    GradePicker(selected = hypo, onSelect = { hypo = it })
    CreditsStepper(credits = credits, onChange = { credits = it })
    GpaImpact(gpa = gpa, coursePoint = Grading.pointForLetter(hypo), courseCredits = credits)
}

@Composable
private fun ScoreSlider(label: String, value: Float, onValue: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "%.0f".format(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 0f..100f,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradePicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Grading.letters.forEach { letter ->
            FilterChip(
                selected = letter.equals(selected, ignoreCase = true),
                onClick = { onSelect(letter) },
                label = { Text(letter) },
            )
        }
    }
}

@Composable
private fun CreditsStepper(credits: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Course credits",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange((credits - 1).coerceAtLeast(1)) }) { Text("−") }
        Text(
            text = "$credits",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = { onChange((credits + 1).coerceAtMost(12)) }) { Text("+") }
    }
}

@Composable
private fun GpaImpact(gpa: GpaCredits, coursePoint: Double, courseCredits: Int) {
    var baseGpaText by rememberSaveable(gpa) { mutableStateOf(if (gpa.gpa > 0) "%.2f".format(gpa.gpa) else "") }
    var baseCreditsText by rememberSaveable(gpa) {
        mutableStateOf(if (gpa.creditsTaken > 0) "${gpa.creditsTaken}" else "")
    }
    val baseGpa = baseGpaText.toDoubleOrNull() ?: 0.0
    val baseCredits = baseCreditsText.toIntOrNull() ?: 0
    val projected = Grading.projectedGpa(baseGpa, baseCredits, coursePoint, courseCredits)
    val delta = projected - baseGpa

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = baseGpaText,
                    onValueChange = { baseGpaText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Current GPA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = baseCreditsText,
                    onValueChange = { baseCreditsText = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Credits") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Projected GPA",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.2f".format(projected),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "%+.2f with %.2f pts × %d cr".format(delta, coursePoint, courseCredits),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ResultRow(overall: Double, letter: String, point: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = "Projected average", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "%.1f".format(overall),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        GradeBadge(letter)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "%.2f".format(point),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GradeBadge(grade: String) {
    val (background, foreground) = gradeColors(grade)
    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.small) {
        Text(
            text = grade.ifBlank { "—" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AssumptionNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
