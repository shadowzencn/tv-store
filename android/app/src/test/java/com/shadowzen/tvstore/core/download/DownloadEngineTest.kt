package com.shadowzen.tvstore.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class DownloadEngineTest {
    @get:Rule val temp = TemporaryFolder()

    private val content = testContent(300_000)
    private val dispatcher = RangeFileServer(content)
    private val server = MockWebServer()
    private var freeSpace = Long.MAX_VALUE
    private val engine = DownloadEngine(OkHttpClient(), availableBytes = { freeSpace }, progressIntervalNanos = 0)
    private lateinit var part: File
    private lateinit var target: File

    @Before
    fun setUp() {
        server.dispatcher = dispatcher
        server.start()
        part = File(temp.root, "a.apk.part")
        target = File(temp.root, "a.apk")
    }

    @After
    fun tearDown() = server.close()

    private fun spec(
        sha: String = sha256Hex(content),
        size: Long? = content.size.toLong(),
        validator: String? = null,
    ) = DownloadSpec(server.url("/a.apk").toString(), sha, size, part, target, validator)

    private fun download(spec: DownloadSpec, listener: RecordingListener = RecordingListener()) =
        runBlocking { engine.download(spec, listener) }

    @Test
    fun downloadsWholeFileAndVerifiesChecksum() {
        val listener = RecordingListener()
        val result = download(spec(), listener)

        assertTrue(result is DownloadResult.Success)
        assertArrayEquals(content, target.readBytes())
        assertFalse(part.exists())
        assertNull(dispatcher.requests.single().headers["Range"])
        assertEquals(Triple(content.size.toLong(), "\"v1\"", 0L), listener.responses.single())
        assertEquals(content.size.toLong(), listener.progress.last())
        assertEquals(1, listener.verifying)
    }

    @Test
    fun resumesFromPartialFileWithRangeAndIfRange() {
        part.writeBytes(content.copyOf(100_000))

        val result = download(spec(validator = "\"v1\""))

        assertTrue(result is DownloadResult.Success)
        assertArrayEquals(content, target.readBytes())
        val request = dispatcher.requests.single()
        assertEquals("bytes=100000-", request.headers["Range"])
        assertEquals("\"v1\"", request.headers["If-Range"])
    }

    @Test
    fun restartsFromZeroWhenFileChangedOnServer() {
        part.writeBytes(testContent(100_000, seed = 99))
        val newContent = testContent(250_000, seed = 3)
        dispatcher.content = newContent
        dispatcher.etag = "\"v2\""

        val result = download(spec(sha = sha256Hex(newContent), size = newContent.size.toLong(), validator = "\"v1\""))

        assertTrue(result is DownloadResult.Success)
        assertArrayEquals(newContent, target.readBytes())
    }

    @Test
    fun restartsFromZeroWhenServerIgnoresRange() {
        dispatcher.supportsRange = false
        part.writeBytes(content.copyOf(100_000))

        val result = download(spec(validator = "\"v1\""))

        assertTrue(result is DownloadResult.Success)
        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun verifiesCompletePartFileWhenServerAnswers416() {
        part.writeBytes(content)

        val result = download(spec(size = null, validator = "\"v1\""))

        assertTrue(result is DownloadResult.Success)
        assertArrayEquals(content, target.readBytes())
        assertEquals("bytes=${content.size}-", dispatcher.requests.single().headers["Range"])
    }

    @Test
    fun skipsNetworkWhenPartFileAlreadyHasExpectedSize() {
        part.writeBytes(content)

        val result = download(spec())

        assertTrue(result is DownloadResult.Success)
        assertTrue(dispatcher.requests.isEmpty())
    }

    @Test
    fun reusesVerifiedTargetFile() {
        target.writeBytes(content)

        val result = download(spec())

        assertTrue(result is DownloadResult.Success)
        assertTrue(dispatcher.requests.isEmpty())
    }

    @Test
    fun checksumMismatchDeletesFileAndFails() {
        val result = download(spec(sha = "0".repeat(64)))

        assertEquals(DownloadError.CHECKSUM_MISMATCH, (result as DownloadResult.Failure).error)
        assertFalse(result.error.retryable)
        assertFalse(part.exists())
        assertFalse(target.exists())
    }

    @Test
    fun sizeMismatchFailsWithoutWriting() {
        val result = download(spec(size = 123L))

        assertEquals(DownloadError.SIZE_MISMATCH, (result as DownloadResult.Failure).error)
        assertFalse(part.exists())
    }

    @Test
    fun insufficientStorageFailsBeforeWriting() {
        freeSpace = content.size.toLong()

        val result = download(spec()) as DownloadResult.Failure

        assertEquals(DownloadError.INSUFFICIENT_STORAGE, result.error)
        assertFalse(result.error.retryable)
        assertFalse(part.exists())
    }

    @Test
    fun serverErrorsAreRetryableAndClientErrorsAreNot() {
        dispatcher.statusOverride = 503
        val serverError = download(spec()) as DownloadResult.Failure
        assertEquals(DownloadError.SERVER, serverError.error)
        assertTrue(serverError.error.retryable)

        dispatcher.statusOverride = 404
        val notFound = download(spec()) as DownloadResult.Failure
        assertEquals(DownloadError.HTTP, notFound.error)
        assertFalse(notFound.error.retryable)
    }

    @Test
    fun disconnectKeepsPartialDataForResume() {
        dispatcher.disconnectNext.set(1)

        val result = download(spec()) as DownloadResult.Failure

        assertEquals(DownloadError.NETWORK, result.error)
        assertTrue(result.error.retryable)
        assertTrue(part.length() in 1 until content.size)
        assertArrayEquals(content.copyOf(part.length().toInt()), part.readBytes())
        val partial = part.length()

        val resumed = download(spec(validator = "\"v1\""))
        assertTrue(resumed is DownloadResult.Success)
        assertArrayEquals(content, target.readBytes())
        assertEquals("bytes=$partial-", dispatcher.requests.last().headers["Range"])
    }

    @Test
    fun cancellationStopsDownloadAndKeepsPartialFile() = runBlocking {
        dispatcher.throttle = 16_384L to 50L
        val job = async(Dispatchers.Default) { engine.download(spec(), RecordingListener()) }
        withTimeout(10_000) {
            while (!part.exists() || part.length() == 0L) delay(20)
        }
        job.cancel()
        withTimeout(10_000) { job.join() }

        assertTrue(job.isCancelled)
        assertTrue(part.length() in 1 until content.size)
        assertFalse(target.exists())
    }

    @Test
    fun parsesContentRange() {
        assertEquals(ContentRange(100, 199, 200), parseContentRange("bytes 100-199/200"))
        assertEquals(ContentRange(0, 9, null), parseContentRange("bytes 0-9/*"))
        assertEquals(200L, parseContentRange("bytes */200")?.total)
        assertNull(parseContentRange("bytes 5-1/200"))
        assertNull(parseContentRange("bytes 0-200/200"))
        assertNull(parseContentRange("items 0-1/2"))
        assertNull(parseContentRange(null))
    }

    @Test
    fun speedMeterUsesRecentWindow() {
        val meter = SpeedMeter(windowNanos = 3_000_000_000L)
        assertEquals(0L, meter.record(0, 0))
        assertEquals(1_000L, meter.record(1_000_000_000L, 1_000))
        assertEquals(1_000L, meter.record(2_000_000_000L, 2_000))
        // 前 3 秒很慢，之后变快：窗口外的旧样本被丢弃
        assertEquals(1_000L, meter.record(3_000_000_000L, 3_000))
        val fast = meter.record(5_000_000_000L, 23_000)
        assertTrue("speed=$fast", fast > 5_000)
    }

    private class RecordingListener : DownloadListener {
        val responses = CopyOnWriteArrayList<Triple<Long?, String?, Long>>()
        val progress = CopyOnWriteArrayList<Long>()
        var verifying = 0

        override suspend fun onResponse(totalBytes: Long?, validator: String?, offset: Long) {
            responses += Triple(totalBytes, validator, offset)
        }

        override suspend fun onProgress(downloadedBytes: Long, totalBytes: Long?, bytesPerSecond: Long) {
            progress += downloadedBytes
        }

        override suspend fun onVerifying() {
            verifying++
        }
    }
}
