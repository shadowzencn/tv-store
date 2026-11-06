package com.shadowzen.tvstore.core.download

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.database.StoreDatabase
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 下载队列在 Android 11（TCL Q10L 的系统版本）环境下的端到端行为：Room 持久化 + 真实 HTTP 下载。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DownloadQueueRunnerTest {
    @get:Rule val temp = TemporaryFolder()

    private val content = testContent(400_000)
    private val dispatcher = RangeFileServer(content)
    private val server = MockWebServer()
    private val clockValue = AtomicLong(1_000)
    private val scheduled = AtomicInteger(0)
    private val completed = CopyOnWriteArrayList<String>()

    private lateinit var db: StoreDatabase
    private lateinit var files: DownloadFiles
    private lateinit var manager: DownloadManager
    private lateinit var runner: DownloadQueueRunner

    @Before
    fun setUp() {
        server.dispatcher = dispatcher
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StoreDatabase::class.java).build()
        files = DownloadFiles(temp.newFolder("downloads"))
        val clock = { clockValue.incrementAndGet() }
        manager = DownloadManager(db.downloadTaskDao(), files, { scheduled.incrementAndGet() }, clock)
        runner = DownloadQueueRunner(
            dao = db.downloadTaskDao(),
            engine = DownloadEngine(OkHttpClient(), availableBytes = { Long.MAX_VALUE }, progressIntervalNanos = 20_000_000L),
            files = files,
            retryDelaysMillis = listOf(0, 0, 0),
            clock = clock,
            onCompleted = { completed += it.appId },
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun request(appId: String = "app", data: ByteArray = content, versionCode: Long = 1) = DownloadRequest(
        appId = appId,
        appName = "测试应用",
        packageName = "com.example.$appId",
        versionName = "1.0",
        versionCode = versionCode,
        url = server.url("/$appId-$versionCode.apk").toString(),
        sha256 = sha256Hex(data),
        sizeBytes = data.size.toLong(),
    )

    private suspend fun task(appId: String = "app"): DownloadTask? = db.downloadTaskDao().get(appId)

    private suspend fun waitUntil(condition: suspend () -> Boolean) = withTimeout(15_000) {
        while (!condition()) delay(10)
    }

    @Test
    fun downloadsQueuedTaskAndMarksCompleted() = runBlocking {
        manager.enqueue(request())
        assertEquals(1, scheduled.get())
        assertEquals(DownloadStatus.QUEUED, task()!!.status)

        runner.run()

        val done = task()!!
        assertEquals(DownloadStatus.COMPLETED, done.status)
        assertEquals(content.size.toLong(), done.downloadedBytes)
        assertEquals(content.size.toLong(), done.totalBytes)
        assertEquals("\"v1\"", done.validator)
        assertArrayEquals(content, files.apkFile(done).readBytes())
        assertEquals(listOf("app"), completed)
    }

    @Test
    fun pauseKeepsPartialDataAndResumeContinuesWithRange() = runBlocking {
        dispatcher.throttle = 16_384L to 40L
        manager.enqueue(request())
        val running = async(Dispatchers.Default) { runner.run() }
        waitUntil { (task()?.downloadedBytes ?: 0) > 0 }

        assertTrue(manager.pause("app"))
        withTimeout(15_000) { running.await() }

        val paused = task()!!
        assertEquals(DownloadStatus.PAUSED, paused.status)
        val partial = files.partFile(paused).length()
        assertTrue("partial=$partial", partial in 1 until content.size)
        assertTrue(completed.isEmpty())

        dispatcher.throttle = null
        assertTrue(manager.resume("app"))
        runner.run()

        val done = task()!!
        assertEquals(DownloadStatus.COMPLETED, done.status)
        assertArrayEquals(content, files.apkFile(done).readBytes())
        val resumeRequest = dispatcher.requests.last()
        assertEquals("bytes=$partial-", resumeRequest.headers["Range"])
        assertEquals("\"v1\"", resumeRequest.headers["If-Range"])
    }

    @Test
    fun recoversTaskInterruptedByProcessDeath() = runBlocking {
        manager.enqueue(request())
        // 模拟进程在下载途中被杀：状态停在下载中，已写入部分数据
        val queued = task()!!
        db.downloadTaskDao().upsert(
            queued.copy(status = DownloadStatus.DOWNLOADING, downloadedBytes = 150_000, totalBytes = content.size.toLong(), validator = "\"v1\""),
        )
        files.partFile(queued).writeBytes(content.copyOf(150_000))

        runner.run()

        val done = task()!!
        assertEquals(DownloadStatus.COMPLETED, done.status)
        assertArrayEquals(content, files.apkFile(done).readBytes())
        assertEquals(1, dispatcher.requests.size)
        assertEquals("bytes=150000-", dispatcher.requests.single().headers["Range"])
    }

    @Test
    fun resumePendingWorkSchedulesOnlyWhenTasksArePending() = runBlocking {
        manager.resumePendingWork()
        assertEquals(0, scheduled.get())

        manager.enqueue(request())
        manager.resumePendingWork()
        assertEquals(2, scheduled.get())
    }

    @Test
    fun retriesAutomaticallyAfterConnectionDrop() = runBlocking {
        dispatcher.disconnectNext.set(1)
        manager.enqueue(request())

        runner.run()

        val done = task()!!
        assertEquals(DownloadStatus.COMPLETED, done.status)
        assertArrayEquals(content, files.apkFile(done).readBytes())
        assertEquals(2, dispatcher.requests.size)
        assertTrue(dispatcher.requests.last().headers["Range"]!!.startsWith("bytes="))
    }

    @Test
    fun failsAfterRetriesAreExhaustedAndManualRetrySucceeds() = runBlocking {
        dispatcher.statusOverride = 503
        manager.enqueue(request())

        runner.run()

        val failed = task()!!
        assertEquals(DownloadStatus.FAILED, failed.status)
        assertTrue(failed.errorMessage!!.contains("503"))
        assertEquals(4, dispatcher.requests.size)

        dispatcher.statusOverride = null
        assertTrue(manager.resume("app"))
        runner.run()
        assertEquals(DownloadStatus.COMPLETED, task()!!.status)
    }

    @Test
    fun checksumMismatchFailsWithoutAutomaticRetry() = runBlocking {
        manager.enqueue(request().copy(sha256 = "a".repeat(64)))

        runner.run()

        val failed = task()!!
        assertEquals(DownloadStatus.FAILED, failed.status)
        assertTrue(failed.errorMessage!!.contains("SHA-256"))
        assertEquals(1, dispatcher.requests.size)
        assertFalse(files.partFile(failed).exists())
        assertFalse(files.apkFile(failed).exists())
    }

    @Test
    fun deletingRunningTaskStopsDownloadAndRemovesFiles() = runBlocking {
        dispatcher.throttle = 16_384L to 40L
        manager.enqueue(request())
        val queued = task()!!
        val running = async(Dispatchers.Default) { runner.run() }
        waitUntil { (task()?.downloadedBytes ?: 0) > 0 }

        manager.delete("app")
        withTimeout(15_000) { running.await() }

        assertNull(task())
        assertFalse(files.partFile(queued).exists())
        assertFalse(files.apkFile(queued).exists())
        assertTrue(completed.isEmpty())
    }

    @Test
    fun processesQueueInOrder() = runBlocking {
        val second = testContent(120_000, seed = 11)
        // 两个任务用不同内容：按请求路径返回对应文件
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse {
                dispatcher.content = if (request.url.encodedPath.startsWith("/second")) second else content
                return dispatcher.dispatch(request)
            }
        }
        manager.enqueue(request("first"))
        manager.enqueue(request("second", second))

        runner.run()

        assertEquals(listOf("first", "second"), completed)
        assertEquals(DownloadStatus.COMPLETED, task("first")!!.status)
        assertArrayEquals(second, files.apkFile(task("second")!!).readBytes())
    }

    @Test
    fun unexpectedErrorFailsOnlyThatTask() = runBlocking {
        val second = testContent(50_000, seed = 5)
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse {
                dispatcher.content = if (request.url.encodedPath.startsWith("/second")) second else content
                return dispatcher.dispatch(request)
            }
        }
        val errors = CopyOnWriteArrayList<Throwable>()
        val failingFirst = DownloadQueueRunner(
            dao = db.downloadTaskDao(),
            engine = DownloadEngine(
                OkHttpClient(),
                availableBytes = { dir ->
                    if (errors.isEmpty()) throw NullPointerException("模拟系统接口异常") else dir.usableSpace
                },
            ),
            files = files,
            retryDelaysMillis = listOf(0, 0, 0),
            clock = { clockValue.incrementAndGet() },
            onError = { _, e -> errors += e },
        )
        manager.enqueue(request("first"))
        manager.enqueue(request("second", second))

        failingFirst.run()

        assertEquals(1, errors.size)
        assertEquals(DownloadStatus.FAILED, task("first")!!.status)
        assertTrue(task("first")!!.errorMessage!!.contains("内部错误"))
        assertEquals(DownloadStatus.COMPLETED, task("second")!!.status)
    }

    @Test
    fun enqueueResumesSameVersionAndReplacesOldVersion() = runBlocking {
        manager.enqueue(request())
        manager.pause("app")
        val v1 = task()!!
        files.partFile(v1).writeBytes(content.copyOf(1_000))

        manager.enqueue(request())
        assertEquals(DownloadStatus.QUEUED, task()!!.status)
        assertTrue(files.partFile(v1).exists())

        manager.enqueue(request(versionCode = 2))
        val v2 = task()!!
        assertEquals(2L, v2.versionCode)
        assertEquals(0L, v2.downloadedBytes)
        assertFalse(files.partFile(v1).exists())
    }

    @Test
    fun markInstalledDeletesApkButKeepsRecord() = runBlocking {
        manager.enqueue(request())
        runner.run()
        val done = task()!!
        assertTrue(files.apkFile(done).exists())

        manager.markInstalled("app")

        assertEquals(DownloadStatus.INSTALLED, task()!!.status)
        assertFalse(files.apkFile(done).exists())
    }

    @Test
    fun missingApkMarksTaskFailedAndRetryDownloadsAgain() = runBlocking {
        manager.enqueue(request())
        runner.run()
        files.apkFile(task()!!).delete()

        manager.markFileMissing("app")
        assertEquals(DownloadStatus.FAILED, task()!!.status)
        manager.resume("app")
        runner.run()

        assertEquals(DownloadStatus.COMPLETED, task()!!.status)
        assertArrayEquals(content, files.apkFile(task()!!).readBytes())
    }
}
