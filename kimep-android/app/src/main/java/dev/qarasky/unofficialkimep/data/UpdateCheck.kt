package dev.qarasky.unofficialkimep.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Checks GitHub releases for a newer app version.
 *
 * Uses the releases *list* (not `/releases/latest`) because all releases so
 * far are prereleases, which `/latest` skips (it 404s when no stable release
 * exists). Users on a prerelease build are offered prereleases; users on a
 * stable build are only offered stable releases. Failures are swallowed —
 * the check must never block app start.
 */
object UpdateCheck {
    const val OWNER = "qarasky"
    const val REPO = "kimep-mobile-clients"

    /** Fallback when the API is unreachable: the "latest" redirect page. */
    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"

    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tag: String = "",
        @SerialName("html_url") val url: String = "",
        @SerialName("draft") val draft: Boolean = false,
        @SerialName("prerelease") val prerelease: Boolean = false,
    )

    data class AppUpdate(val tag: String, val url: String)

    suspend fun latestNewerThan(currentVersion: String): AppUpdate? {
        val client = HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
        try {
            val response = client.get(API_URL) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "UnofficialKimepApp (Android)")
            }
            if (!response.status.isSuccess()) return null
            val releases: List<GithubRelease> = response.body()
            val currentIsPre = isPrerelease(currentVersion)
            return releases
                .filter { !it.draft && it.tag.isNotBlank() }
                .filter { currentIsPre || !it.prerelease }
                .firstOrNull { compareVersions(it.tag, currentVersion) > 0 }
                ?.let { AppUpdate(it.tag, it.url.ifBlank { RELEASES_PAGE }) }
        } catch (_: Exception) {
            return null
        } finally {
            client.close()
        }
    }

    private fun isPrerelease(version: String): Boolean =
        normalize(version).contains('-')

    private fun normalize(version: String): String =
        version.trim().removePrefix("v").removePrefix("V")

    /**
     * Compares "0.2-beta" style versions. Returns negative/zero/positive.
     * Numeric core first; a stable version (no qualifier) beats any
     * prerelease of the same core; different qualifiers compare lexically.
     */
    fun compareVersions(a: String, b: String): Int {
        val (coreA, qualA) = split(normalize(a))
        val (coreB, qualB) = split(normalize(b))
        val len = maxOf(coreA.size, coreB.size)
        for (i in 0 until len) {
            val diff = (coreA.getOrElse(i) { 0 }).compareTo(coreB.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        if (qualA == qualB) return 0
        if (qualA.isEmpty()) return 1
        if (qualB.isEmpty()) return -1
        return qualA.compareTo(qualB)
    }

    private fun split(version: String): Pair<List<Int>, String> {
        val dash = version.indexOf('-')
        val core = if (dash < 0) version else version.substring(0, dash)
        val qual = if (dash < 0) "" else version.substring(dash + 1)
        return core.split('.').map { it.toIntOrNull() ?: 0 } to qual
    }
}
