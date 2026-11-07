package com.shadowzen.tvstore.core.network

import android.app.Application
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class FeedbackApiTest {
    private val server = MockWebServer()
    private lateinit var api: FeedbackApi

    @Before
    fun setUp() {
        server.start()
        api = FeedbackApi(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun submitsFeedbackAndParsesCreatedId() = runBlocking {
        server.enqueue(MockResponse.Builder().code(201).body("""{"id":42}""").build())

        val id = api.submit(
            FeedbackSubmission(
                type = FeedbackType.LAUNCH_FAILED,
                appId = "star",
                description = "打开后闪退",
                deviceModel = "TCL Q10L",
                androidVersion = "Android 11 (API 30)",
                clientVersion = "0.1.0 (1)",
            ),
        )
        assertEquals(42L, id)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/feedback", recorded.url.encodedPath)
        val sent = JSONObject(recorded.body?.utf8().orEmpty())
        assertEquals("launch_failed", sent.getString("type"))
        assertEquals("star", sent.getString("appId"))
        assertEquals("打开后闪退", sent.getString("description"))
        assertEquals("TCL Q10L", sent.getString("deviceModel"))
    }

    @Test
    fun omitsOptionalAppIdAndDescriptionWhenBlank() = runBlocking {
        server.enqueue(MockResponse.Builder().code(201).body("""{"id":7}""").build())

        api.submit(
            FeedbackSubmission(
                type = FeedbackType.OTHER,
                appId = null,
                description = "   ",
                deviceModel = "TCL Q10L",
                androidVersion = "Android 11",
                clientVersion = "0.1.0",
            ),
        )

        val sent = JSONObject(server.takeRequest().body?.utf8().orEmpty())
        assertEquals("other", sent.getString("type"))
        assertFalse(sent.has("appId"))
        assertFalse(sent.has("description"))
    }

    @Test
    fun surfacesRateLimitAndValidationErrors() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .body("""{"error":{"code":"too_many_requests","message":"提交过于频繁，请稍后再试。"}}""")
                .build(),
        )

        try {
            api.submit(
                FeedbackSubmission(
                    type = FeedbackType.INSTALL_FAILED,
                    appId = null,
                    description = null,
                    deviceModel = "TCL Q10L",
                    androidVersion = "11",
                    clientVersion = "0.1.0",
                ),
            )
            fail("Expected FeedbackHttpException")
        } catch (e: FeedbackHttpException) {
            assertEquals(429, e.statusCode)
            assertEquals("too_many_requests", e.errorCode)
            assertEquals("提交过于频繁，请稍后再试。", e.message)
        }
    }
}
