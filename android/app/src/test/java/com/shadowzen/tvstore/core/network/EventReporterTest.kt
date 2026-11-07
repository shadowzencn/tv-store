package com.shadowzen.tvstore.core.network

import android.app.Application
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class EventReporterTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun reportsDownloadAndInstallEventsWithUserAgent() = runBlocking {
        server.enqueue(MockResponse.Builder().code(201).body("""{"id":1}""").build())
        server.enqueue(MockResponse.Builder().code(201).body("""{"id":2}""").build())

        val httpClient = StoreHttpClient.create()
        val reporter = EventReporter(httpClient, server.url("/"))
        val task = DownloadTask(
            appId = "smarttube",
            appName = "SmartTube",
            packageName = "org.smarttube.stable",
            versionName = "32.68",
            versionCode = 2458,
            url = "https://example.com/st.apk",
            sha256 = "a".repeat(64),
            expectedBytes = 1000,
            totalBytes = 1000,
            downloadedBytes = 1000,
            bytesPerSecond = 0,
            status = DownloadStatus.COMPLETED,
            errorMessage = null,
            validator = null,
            createdAt = 1,
            updatedAt = 1,
        )

        assertTrue(reporter.reportDownload(task))
        val req1 = server.takeRequest()
        assertEquals("/api/v1/events", req1.url.encodedPath)
        assertTrue(req1.headers["User-Agent"].orEmpty().startsWith("TVStore/"))
        val json1 = JSONObject(req1.body?.utf8().orEmpty())
        assertEquals("download", json1.getString("type"))
        assertEquals("smarttube", json1.getString("appId"))
        assertEquals("org.smarttube.stable", json1.getString("packageName"))
        assertEquals(2458L, json1.getLong("versionCode"))

        assertTrue(reporter.reportInstall(task))
        val req2 = server.takeRequest()
        val json2 = JSONObject(req2.body?.utf8().orEmpty())
        assertEquals("install", json2.getString("type"))
    }
}
