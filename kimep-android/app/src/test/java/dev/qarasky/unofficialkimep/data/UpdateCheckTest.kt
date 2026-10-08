package dev.qarasky.unofficialkimep.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun check(body: String, version: String = "1.0.0", status: HttpStatusCode = HttpStatusCode.OK): UpdateCheck.Result = runBlocking {
        val client = HttpClient(MockEngine {
            assertEquals("https://api.github.com/repos/qarasky/kimep-mobile-clients/releases", it.url.toString())
            assertEquals("UnofficialKimepApp (Android)", it.headers[HttpHeaders.UserAgent])
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            UpdateCheck.checkForUpdates(version, client)
        } finally {
            client.close()
        }
    }

    @Test
    fun sameVersionShowsUpToDate() {
        assertEquals(UpdateCheck.Result.UpToDate, check("""[{"tag_name":"v1.0.0"}]"""))
    }

    @Test
    fun newVersionHasReleaseLink() {
        val result = check("""[{"tag_name":"v1.0.1","html_url":"https://github.com/qarasky/kimep-mobile-clients/releases/tag/v1.0.1"}]""")
        assertEquals(
            UpdateCheck.Result.Available(UpdateCheck.AppUpdate("v1.0.1", "https://github.com/qarasky/kimep-mobile-clients/releases/tag/v1.0.1")),
            result,
        )
    }

    @Test
    fun stableBuildSkipsDraftsAndPrereleases() {
        assertEquals(UpdateCheck.Result.UpToDate, check("""[
            {"tag_name":"v2.0.0","draft":true},
            {"tag_name":"v1.1.0-beta","prerelease":true},
            {"tag_name":"v1.0.0"}
        ]"""))
    }

    @Test
    fun prereleaseBuildCanSeePrereleases() {
        val result = check("""[{"tag_name":"v1.1.0-beta","prerelease":true}]""", "1.0.0-beta")
        assertEquals(UpdateCheck.Result.Available(UpdateCheck.AppUpdate("v1.1.0-beta", UpdateCheck.RELEASES_PAGE)), result)
    }

    @Test
    fun choosesHighestVersionNotFirstPublishedRelease() {
        val result = check("""[{"tag_name":"v1.0.1"},{"tag_name":"v1.2.0"},{"tag_name":"v1.1.0"}]""")
        assertEquals(UpdateCheck.Result.Available(UpdateCheck.AppUpdate("v1.2.0", UpdateCheck.RELEASES_PAGE)), result)
    }

    @Test
    fun errorsDoNotPretendTheAppIsUpToDate() {
        assertEquals(UpdateCheck.Result.Failed, check("{}", status = HttpStatusCode.Forbidden))
        assertEquals(UpdateCheck.Result.Failed, check("not json"))
    }

    @Test
    fun networkFailureHasExplicitResult() = runBlocking {
        val client = HttpClient(MockEngine { throw IOException("Offline") })
        try {
            assertEquals(UpdateCheck.Result.Failed, UpdateCheck.checkForUpdates("1.0.0", client))
        } finally {
            client.close()
        }
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed() {
        runBlocking {
            val client = HttpClient(MockEngine { throw CancellationException("Cancelled") })
            try {
                UpdateCheck.checkForUpdates("1.0.0", client)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun versionComparisonHandlesStableUpgradeAndNumericOrdering() {
        assertTrue(UpdateCheck.compareVersions("v1.0.0", "0.3-beta") > 0)
        assertTrue(UpdateCheck.compareVersions("1.0.0", "1.0.0-beta") > 0)
        assertTrue(UpdateCheck.compareVersions("1.10.0", "1.9.0") > 0)
        assertEquals(0, UpdateCheck.compareVersions("v1.0.0", "1.0.0"))
    }
}
