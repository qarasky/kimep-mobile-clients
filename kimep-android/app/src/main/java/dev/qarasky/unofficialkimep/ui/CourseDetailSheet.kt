package dev.qarasky.unofficialkimep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.qarasky.unofficialkimep.data.ApiDate
import dev.qarasky.unofficialkimep.data.Grading
import dev.qarasky.unofficialkimep.data.CalculatorSettings
import dev.qarasky.unofficialkimep.data.SettingsStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
 * and grade planning for current/completed courses. Scheduled courses show metadata only.
 *
 * Score weights are not exposed by the API, so the predictor assumes
 * Midterm 1/2 at 30% each and Final at 40%. Retake modelling treats the new
 * grade as additional credits — replacement policies are not modelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    detail: CourseDetail?,
    gpa: GpaCredits,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (detail == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                is CourseDetail.Scheduled -> ScheduledContent(detail.meeting)
            }
        }
    }
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

    val maximum = Grading.maximumOverall(apiScores)
    var target by rememberSaveable(score) {
        mutableStateOf(Grading.gradeForScore(if (remainingIndices.isEmpty()) maximum else minOf(73.0, maximum)).letter)
    }
    val targetMin = Grading.minScoreForLetter(target)
    val required = Grading.requiredOnRemaining(apiScores, targetMin)
    val feasible = targetMin <= maximum + 1e-9
    Text("What grade do you want?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    GradePicker(
        selected = target,
        onSelect = { target = it },
        isEnabled = { Grading.minScoreForLetter(it) <= maximum + 1e-9 },
    )
    GoalAnswer(target, required, maximum, remainingIndices.isEmpty(), feasible)

    // Goal changes reset the plan; a slider edit keeps the weighted goal fixed.
    var plan by remember(score, target) {
        mutableStateOf(apiScores.map { it ?: (required ?: 100.0) })
    }
    if (remainingIndices.isNotEmpty() && feasible) {
        Text("Plan your remaining scores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (remainingIndices.size > 1) "Linked to $target: lower one score and the others rise. Scores stop at the feasible limits."
            else "Only ${labels[remainingIndices.first()].substringBefore(" ·")} remains; this is the minimum score you need.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        remainingIndices.forEach { i ->
            ScoreSlider(
                label = labels[i],
                value = plan[i].toFloat(),
                enabled = remainingIndices.size > 1 && (required ?: 0.0) > 0.0,
                onValue = { value ->
                    Grading.linkedScores(apiScores, targetMin, i, value.toDouble())?.let { plan = it }
                },
            )
        }
    }

    var credits by rememberSaveable(score) { mutableIntStateOf(3) }
    GpaImpact(
        gpa = gpa,
        coursePoint = if (remainingIndices.isEmpty()) Grading.gradeForScore(maximum).point else Grading.pointForLetter(target),
        courseCredits = credits,
        onCreditsChange = { credits = it },
    )
    AssumptionNote("Assumed weights: Midterm 1 · 30%, Midterm 2 · 30%, Final · 40%. Goals are minimum overall scores.")
}

@Composable
private fun GoalAnswer(target: String, required: Double?, maximum: Double, complete: Boolean, feasible: Boolean) {
    val tough = (required ?: 0.0) >= 85.0
    val background = when {
        !feasible -> MaterialTheme.colorScheme.errorContainer
        tough -> Color(0xFFFFE3A3)
        else -> Color(0xFFCEEFDC)
    }
    val foreground = when {
        !feasible -> MaterialTheme.colorScheme.onErrorContainer
        tough -> Color(0xFF5B3B00)
        else -> Color(0xFF123C29)
    }
    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("To reach $target", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !feasible -> "Out of reach"
                    complete -> "Goal met"
                    else -> "${"%.1f".format(required ?: 0.0)}%"
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            if (!complete && feasible) Text("weighted average needed on remaining assessments")
            Surface(color = foreground.copy(alpha = 0.12f), contentColor = foreground, shape = MaterialTheme.shapes.small) {
                Text(
                    when {
                        !feasible -> "Impossible"
                        complete -> "Achieved"
                        tough -> "Tough · 85%+ needed"
                        else -> "Comfortable · under 85% needed"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Text(
                if (complete) "All assessments graded · overall ${"%.1f".format(maximum)}%"
                else "Maximum possible: ${"%.1f".format(maximum)}% · ${Grading.gradeForScore(maximum).letter}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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
private fun ScheduledContent(meeting: ClassMeeting) {
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
    val sectionAndSemester = listOfNotNull(
        meeting.section?.takeIf { it.isNotBlank() }?.let { "Section $it" },
        meeting.semester.takeIf { it.isNotBlank() }?.let { semesterLabel(it) },
    ).joinToString(" · ")
    if (sectionAndSemester.isNotBlank()) {
        Text(
            text = sectionAndSemester,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoreSlider(label: String, value: Float, enabled: Boolean = true, onValue: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "%.1f".format(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 0f..100f,
            enabled = enabled,
        )
    }
}

@Composable
private fun GradePicker(selected: String, onSelect: (String) -> Unit, isEnabled: (String) -> Boolean = { true }) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Grading.letters.forEach { letter ->
            FilterChip(
                selected = letter.equals(selected, ignoreCase = true),
                onClick = { onSelect(letter) },
                enabled = isEnabled(letter),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                label = { Text(letter, fontWeight = if (letter == selected) FontWeight.Bold else FontWeight.Normal) },
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
private fun GpaImpact(
    gpa: GpaCredits,
    coursePoint: Double,
    courseCredits: Int,
    onCreditsChange: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { SettingsStore(context) }
    val settings by store.calculatorSettings.collectAsStateWithLifecycle(initialValue = CalculatorSettings())
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val baseGpa = settings.gpa ?: gpa.gpa
    val baseCredits = settings.creditsTaken ?: gpa.creditsTaken
    var baseGpaText by rememberSaveable(baseGpa) { mutableStateOf(baseGpa.toString()) }
    var baseCreditsText by rememberSaveable(baseCredits) { mutableStateOf(baseCredits.toString()) }
    val projected = Grading.projectedGpa(baseGpa, baseCredits, coursePoint, courseCredits)
    val delta = projected - baseGpa

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "GPA ${"%.2f".format(baseGpa)} → ${"%.2f".format(projected)} (%+.2f) · %d cr · %s".format(
                        delta, courseCredits, if (expanded) "Hide" else "Edit",
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (expanded) {
                Text("Saved calculator settings · goal-based projection", style = MaterialTheme.typography.bodySmall)
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
                        label = { Text("Credits taken") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                onCreditsChange?.let { CreditsStepper(credits = courseCredits, onChange = it) }
                val editedGpa = baseGpaText.toDoubleOrNull()
                val editedCredits = baseCreditsText.toIntOrNull()
                Row {
                    TextButton(
                        enabled = editedGpa != null && editedGpa in 0.0..4.33 && editedCredits != null && editedCredits >= 0,
                        onClick = {
                            if (editedGpa != null && editedCredits != null) {
                                scope.launch {
                                    store.setCalculatorSettings(editedGpa, editedCredits)
                                    expanded = false
                                }
                            }
                        },
                    ) { Text("Save settings") }
                    TextButton(onClick = {
                        scope.launch {
                            store.resetCalculatorSettings()
                            baseGpaText = gpa.gpa.toString()
                            baseCreditsText = gpa.creditsTaken.toString()
                        }
                    }) { Text("Use portal values") }
                }
                Text(
                    text = "GPA must be 0–4.33; credits are previously taken credits. Projection adds this course as new credits.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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
