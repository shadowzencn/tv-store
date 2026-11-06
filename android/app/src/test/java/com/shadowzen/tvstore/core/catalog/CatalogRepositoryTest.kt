package com.shadowzen.tvstore.core.catalog

import android.app.Application
import com.shadowzen.tvstore.core.catalog.CatalogFixtures.app
import com.shadowzen.tvstore.core.catalog.CatalogFixtures.catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class CatalogRepositoryTest {
    @get:Rule val temp = TemporaryFolder()

    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()
    private lateinit var cacheFile: File
    private lateinit var cache: CatalogCache
    private val errors = mutableListOf<String>()

    @Before
    fun setUp() {
        server.start()
        cacheFile = File(temp.root, "catalog/catalog.cache")
        cache = CatalogCache(cacheFile)
    }

    @After
    fun tearDown() = server.close()

    private fun repository(baseUrl: HttpUrl = server.url("/store/")) = CatalogRepository(
        api = CatalogApi(client, baseUrl),
        cache = cache,
        parse = { CatalogParser.parse(it, baseUrl) },
        onError = { message, _ -> errors += message },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun ok(body: String, etag: String? = "\"v1\"") = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .apply { if (etag != null) addHeader("ETag", etag) }
        .body(body)
        .build()

    @Test
    fun firstLoadFetchesCatalogAndCachesBodyWithEtag() = runBlocking {
        val body = catalog(app("a"))
        server.enqueue(ok(body))
        val repository = repository()

        repository.refresh()

        val state = repository.state.value
        assertEquals(listOf("a"), state.catalog!!.apps.map { it.id })
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
        val request = server.takeRequest()
        assertEquals("/store/api/v1/catalog", request.url.encodedPath)
        assertNull(request.headers["If-None-Match"])
        assertEquals(CachedCatalog("\"v1\"", body), cache.read())
        // 下载地址按服务器根路径解析
        assertEquals(server.url("/files/apks/${"a".repeat(64)}.apk").toString(), state.catalog.apps[0].download!!.url)
    }

    @Test
    fun notModifiedKeepsCachedCatalogAndSendsIfNoneMatch() = runBlocking {
        cache.write(CachedCatalog("\"v1\"", catalog(app("cached"))))
        server.enqueue(MockResponse.Builder().code(304).build())
        val repository = repository()

        repository.refresh()

        assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
        val state = repository.state.value
        assertEquals(listOf("cached"), state.catalog!!.apps.map { it.id })
        assertNull(state.errorMessage)
    }

    @Test
    fun newCatalogReplacesCacheAndUsesNewEtagNextTime() = runBlocking {
        cache.write(CachedCatalog("\"v1\"", catalog(app("old"))))
        val newBody = catalog(app("new"))
        server.enqueue(ok(newBody, etag = "\"v2\""))
        server.enqueue(MockResponse.Builder().code(304).build())
        val repository = repository()

        repository.refresh()
        assertEquals(listOf("new"), repository.state.value.catalog!!.apps.map { it.id })
        assertEquals(CachedCatalog("\"v2\"", newBody), cache.read())

        repository.refresh()
        server.takeRequest()
        assertEquals("\"v2\"", server.takeRequest().headers["If-None-Match"])
        assertEquals(listOf("new"), repository.state.value.catalog!!.apps.map { it.id })
    }

    @Test
    fun networkFailureWithCacheKeepsShowingCacheAndReportsError() = runBlocking {
        cache.write(CachedCatalog("\"v1\"", catalog(app("cached"))))
        val unreachable = server.url("/")
        server.close()
        val repository = repository(unreachable)

        repository.refresh()

        val state = repository.state.value
        assertEquals(listOf("cached"), state.catalog!!.apps.map { it.id })
        assertEquals("无法连接服务器，请检查网络后重试", state.errorMessage)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun networkFailureWithoutCacheLeavesNoCatalogThenRetrySucceeds() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("""{"error":{"code":"server_error","message":"服务器内部错误"}}""").build())
        server.enqueue(ok(catalog(app("a"))))
        val repository = repository()

        repository.refresh()
        var state = repository.state.value
        assertNull(state.catalog)
        assertEquals("服务器内部错误（HTTP 500）", state.errorMessage)

        repository.refresh()
        state = repository.state.value
        assertEquals(listOf("a"), state.catalog!!.apps.map { it.id })
        assertNull(state.errorMessage)
    }

    @Test
    fun nonJsonErrorBodyIsReportedByStatusCode() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).body("<html>Too Many</html>").build())
        val repository = repository()

        repository.refresh()

        assertEquals("服务器暂时无法提供应用目录（HTTP 429）", repository.state.value.errorMessage)
    }

    @Test
    fun malformedResponseDoesNotOverwriteCache() = runBlocking {
        val cachedBody = catalog(app("cached"))
        cache.write(CachedCatalog("\"v1\"", cachedBody))
        server.enqueue(ok("{not json", etag = "\"v2\""))
        val repository = repository()

        repository.refresh()

        val state = repository.state.value
        assertEquals(listOf("cached"), state.catalog!!.apps.map { it.id })
        assertEquals("应用目录数据格式有误，请稍后重试", state.errorMessage)
        assertEquals(CachedCatalog("\"v1\"", cachedBody), cache.read())
    }

    @Test
    fun corruptedCacheIsDiscardedAndFetchedWithoutIfNoneMatch() = runBlocking {
        cache.write(CachedCatalog("\"v1\"", "{broken"))
        server.enqueue(ok(catalog(app("a")), etag = "\"v2\""))
        val repository = repository()

        repository.refresh()

        assertNull(server.takeRequest().headers["If-None-Match"])
        assertEquals(listOf("a"), repository.state.value.catalog!!.apps.map { it.id })
        assertTrue(errors.any { it.contains("缓存") })
        assertEquals("\"v2\"", cache.read()!!.etag)
    }

    @Test
    fun emptyCatalogIsAValidResult() = runBlocking {
        server.enqueue(ok("""{"categories":[],"apps":[]}""", etag = null))
        val repository = repository()

        repository.refresh()

        val state = repository.state.value
        assertNotNull(state.catalog)
        assertTrue(state.catalog!!.apps.isEmpty())
        assertNull(state.errorMessage)
        assertNull(cache.read()!!.etag)
    }
}
