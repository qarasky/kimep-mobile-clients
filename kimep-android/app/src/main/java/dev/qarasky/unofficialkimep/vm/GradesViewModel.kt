package dev.qarasky.unofficialkimep.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.qarasky.unofficialkimep.data.KimepRepository
import dev.qarasky.unofficialkimep.data.friendlyMessage
import dev.qarasky.unofficialkimep.data.model.AssessmentScore
import dev.qarasky.unofficialkimep.data.model.FinalGrade
import dev.qarasky.unofficialkimep.data.model.GpaCredits
import kotlinx.coroutines.launch

data class GradesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val gpa: GpaCredits = GpaCredits(),
    val assessment: List<AssessmentScore> = emptyList(),
    val finalGrades: List<FinalGrade> = emptyList(),
)

class GradesViewModel(
    private val repository: KimepRepository,
    private val sessionId: String,
) : ViewModel() {

    var uiState by mutableStateOf(GradesUiState())
        private set

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            uiState = uiState.copy(
                loading = !refresh && uiState.finalGrades.isEmpty(),
                refreshing = refresh,
                error = null,
            )

            val gpaResult = repository.gpa(sessionId)
            val assessmentResult = repository.assessmentScores(sessionId)
            val gradesResult = repository.finalGrades(sessionId)

            val critical = gpaResult.exceptionOrNull() ?: gradesResult.exceptionOrNull()

            uiState = GradesUiState(
                loading = false,
                refreshing = false,
                error = critical?.friendlyMessage(),
                gpa = gpaResult.getOrDefault(GpaCredits()),
                assessment = assessmentResult.getOrDefault(emptyList()),
                finalGrades = gradesResult.getOrDefault(emptyList()),
            )
        }
    }

    companion object {
        fun factory(repository: KimepRepository, sessionId: String) = viewModelFactory {
            initializer { GradesViewModel(repository, sessionId) }
        }
    }
}
