package dev.qarasky.unofficialkimep.screenshots

import dev.qarasky.unofficialkimep.data.KimepApi
import dev.qarasky.unofficialkimep.data.model.AssessmentScore
import dev.qarasky.unofficialkimep.data.model.ClassMeeting
import dev.qarasky.unofficialkimep.data.model.FinalGrade
import dev.qarasky.unofficialkimep.data.model.GpaCredits
import dev.qarasky.unofficialkimep.data.model.PersonalInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Shared demo/test fixture: fictional student and grades, with supplied F2026 catalog metadata. */
object DemoStudent {
    val profile = PersonalInfo(firstName = "Alex", lastName = "Student", programId = "Bachelor of Business Administration")
    private val gpa = GpaCredits(gpa = 3.42, creditsEarned = 98, creditsTaken = 101)

    data class Course(
        val code: String,
        val title: String,
        val section: String,
        val hall: String,
        val instructor: String,
        val days: List<String>,
        val hour: Int,
        val minute: Int,
        val score1: Double,
        val score2: Double? = null,
        val credits: Int = 3,
    )

    // Six distinct three-credit enrollments, not twelve courses: each meets twice a week.
    val courses = listOf(
        Course("GEN/OPM2402", "Business Statistical Analysis", "3", "#421/Dostyk bld.", "Arailym Aukenova, MSc", listOf("Monday", "Wednesday"), 13, 0, 73.0),
        Course("ACC2201", "Management Accounting I", "3", "#116/NEW bld.", "Nurlan Orazalin, Ph.D., DBA, CMA", listOf("Monday", "Wednesday"), 16, 0, 20.0),
        Course("FIN3210", "Corporate Finance", "1", "#112/NEW bld.", "Halil Kiymaz, PhD", listOf("Tuesday", "Thursday"), 10, 0, 82.0, 78.0),
        Course("MGT3212", "Organizational Behavior", "1", "#508/Valikhanov bld.", "Stephane Bignoux, Ph. D.", listOf("Tuesday", "Thursday"), 16, 0, 88.0),
        Course("IFS2203", "Management Information Systems", "4", "#322/Valikhanov bld.", "Oleg Vlassov, MBA", listOf("Tuesday", "Thursday"), 8, 30, 65.0),
        Course("MKT3130", "Principles of Marketing", "1", "#UMAI", "Jonathan Ross Gilbert, Ph.D.", listOf("Tuesday", "Thursday"), 11, 30, 79.0),
    )
    val scores = courses.map {
        AssessmentScore(semester = "F2026", code = it.code, title = it.title, score1 = it.score1, score2 = it.score2)
    }
    private val transcript = listOf(
        FinalGrade(1, "S2026", "Financial Accounting I", "A", 4.0),
        FinalGrade(2, "S2026", "Financial Management", "A-", 3.67),
        FinalGrade(3, "S2026", "Introduction to Operations Management", "B+", 3.33),
        FinalGrade(4, "F2025", "Principles of Management", "A", 4.0),
        FinalGrade(5, "F2025", "International Business", "B", 3.0),
    )
    val meetings = courses.flatMapIndexed { courseIndex, course ->
        course.days.mapIndexed { dayIndex, day ->
            meeting(courseIndex * 10 + dayIndex + 1, day, course)
        }
    }

    private fun time(hour: Int, minute: Int): String =
        "/Date(${LocalDateTime.of(1899, 12, 30, hour, minute).toInstant(ZoneOffset.ofHours(6)).toEpochMilli()})/"

    private fun meeting(id: Int, day: String, course: Course): ClassMeeting {
        val end = LocalTime.of(course.hour, course.minute).plusMinutes(75)
        return ClassMeeting(
            id = id, weekDay = day, semester = "F2026", title = course.title, courseId = course.code,
            hall = course.hall, instructor = course.instructor, section = course.section,
            timeFrom = time(course.hour, course.minute), timeTo = time(end.hour, end.minute),
        )
    }

    fun api(): KimepApi {
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath.substringAfterLast('/')) {
                "GPACRS" -> Json.encodeToString(gpa)
                "AssessmentScores" -> Json.encodeToString(scores)
                "final_grades" -> Json.encodeToString(transcript)
                "personal" -> Json.encodeToString(meetings)
                "_finalexams" -> "[]"
                "info" -> Json.encodeToString(profile)
                else -> error("Unexpected demo API request: ${request.url}")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return KimepApi(HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest {
                url(KimepApi.BASE_URL)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }
        })
    }
}
