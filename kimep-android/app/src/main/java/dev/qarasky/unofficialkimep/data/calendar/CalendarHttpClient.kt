package dev.qarasky.unofficialkimep.data.calendar

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/** The HTTP client for KIMEP's calendar page and PDFs. */
fun calendarHttpClient() = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }
}
