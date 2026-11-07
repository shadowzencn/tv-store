package com.shadowzen.tvstore.core.network

import android.app.Application
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ClientVersionApiTest {
    private val server = MockWebServer()
    private lateinit var api: ClientVersionApi

    @Before
    fun setUp() {
        server.start()
        api = ClientVersionApi(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun parsesPublishedClientVersionAndResolvesRelativeDownloadUrl() = runBlocking {
        val sha = "b".repeat(64)
        val signer = "c".repeat(64)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {
                      "packageName": "com.shadowzen.tvstore",
                      "versionName": "0.2.0",
                      "versionCode": 2,
                      "minSdk": 28,
                      "abis": ["armeabi-v7a", "arm64-v8a"],
                      "sizeBytes": 2600000,
                      "sha256": "$sha",
                      "signerSha256": "$signer",
                      "downloadUrl": "/files/apks/$sha.apk",
                      "compatibility": "verified",
                      "releaseNotes": "新增分类、搜索、收藏与反馈"
                    }
                    """.trimIndent(),
                )
                .build(),
        )

        val result = api.fetchLatest()
        assertTrue(result is ClientVersionResult.Published)
        val release = (result as ClientVersionResult.Published).release
        assertEquals("com.shadowzen.tvstore", release.packageName)
        assertEquals("0.2.0", release.versionName)
        assertEquals(2L, release.versionCode)
        assertEquals(28, release.minSdk)
        assertEquals(listOf("armeabi-v7a", "arm64-v8a"), release.abis)
        assertEquals(2600000L, release.sizeBytes)
        assertEquals(sha, release.sha256)
        assertEquals(signer, release.signerSha256)
        assertEquals(server.url("/files/apks/$sha.apk").toString(), release.downloadUrl)
        assertEquals("新增分类、搜索、收藏与反馈", release.releaseNotes)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/client/version", recorded.url.encodedPath)
    }

    @Test
    fun returnsNotPublishedOn404() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"error":{"code":"not_found","message":"商店客户端暂无已发布版本。"}}""")
                .build(),
        )

        val result = api.fetchLatest()
        assertEquals(ClientVersionResult.NotPublished, result)
    }

    @Test
    fun throwsExceptionWithServerMessageOn500() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(500)
                .body("""{"error":{"code":"server_error","message":"服务器内部错误"}}""")
                .build(),
        )

        try {
            api.fetchLatest()
            fail("Expected ClientVersionHttpException")
        } catch (e: ClientVersionHttpException) {
            assertEquals(500, e.statusCode)
            assertEquals("server_error", e.errorCode)
            assertEquals("服务器内部错误", e.serverMessage)
        }
    }
}
