package dev.qarasky.unofficialkimep.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import dev.qarasky.unofficialkimep.data.model.AssessmentScore
import dev.qarasky.unofficialkimep.data.model.ClassMeeting
import dev.qarasky.unofficialkimep.data.model.FinalExam
import dev.qarasky.unofficialkimep.data.model.FinalGrade
import dev.qarasky.unofficialkimep.data.model.GpaCredits
import dev.qarasky.unofficialkimep.data.model.IdRequest
import dev.qarasky.unofficialkimep.data.model.LoginRequest
import dev.qarasky.unofficialkimep.data.model.LoginResponse
import dev.qarasky.unofficialkimep.data.model.PersonalInfo
import kotlinx.serialization.json.Json

/**
 * Thin client for the KIMEP Mobile API.
 *
 * Every request is a JSON POST with the session GUID in the body as "id"; there is no
 * Authorization header. See docs/KIMEP_Mobile_API.md.
 */
class KimepApi {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    internal val http: HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        defaultRequest {
            url(BASE_URL)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    internal suspend inline fun <reified Req, reified Res> post(path: String, body: Req): Res {
        val response = http.post(path) { setBody(body) }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, "HTTP ${response.status.value}")
        }
        return response.body()
    }

    suspend fun login(studentId: String, password: String): LoginResponse =
        post("auth/login", LoginRequest(studentId, password))

    suspend fun personalInfo(id: String): PersonalInfo =
        post("personal/info", IdRequest(id))

    suspend fun schedule(id: String): List<ClassMeeting> =
        post("schedule/personal", IdRequest(id))

    suspend fun gpa(id: String): GpaCredits =
        post("schedule/GPACRS", IdRequest(id))

    suspend fun finalGrades(id: String): List<FinalGrade> =
        post("schedule/final_grades", IdRequest(id))

    suspend fun assessmentScores(id: String): List<AssessmentScore> =
        post("schedule/AssessmentScores", IdRequest(id))

    suspend fun finalExams(id: String): List<FinalExam> =
        post("schedule/_finalexams", IdRequest(id))

    companion object {
        const val BASE_URL = "https://www.kimep.kz/ext/mobile/"

        fun avatarUrl(id: String): String =
            "${BASE_URL}avatar/thumb/$id?width=400&height=400"
    }
}
