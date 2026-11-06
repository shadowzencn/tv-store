package com.shadowzen.tvstore.core.download

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.shadowzen.tvstore.TvStoreApplication
import com.shadowzen.tvstore.core.database.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** 通过真实的应用容器和 WorkManager 调度一次下载，验证调度、Worker 和清单配置连在一起能工作。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DownloadWorkerTest {
    private val content = testContent(200_000)
    private val server = MockWebServer()
    private lateinit var app: TvStoreApplication

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        server.dispatcher = RangeFileServer(content)
        server.start()
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun scheduledWorkDownloadsTaskOnceNetworkIsAvailable() = runBlocking {
        val manager = app.container.downloadManager
        manager.enqueue(
            DownloadRequest(
                appId = "worker-app",
                appName = "测试应用",
                packageName = "com.example.worker",
                versionName = "1.0",
                versionCode = 1,
                url = server.url("/worker.apk").toString(),
                sha256 = sha256Hex(content),
                sizeBytes = content.size.toLong(),
            ),
        )

        val workManager = WorkManager.getInstance(app)
        val work = workManager.getWorkInfosForUniqueWork("download-queue").get().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        WorkManagerTestInitHelper.getTestDriver(app)!!.setAllConstraintsMet(work.id)

        val finished = withTimeout(15_000) {
            var info = workManager.getWorkInfoById(work.id).get()!!
            while (!info.state.isFinished) {
                delay(20)
                info = workManager.getWorkInfoById(work.id).get()!!
            }
            info
        }
        val task = manager.get("worker-app")!!
        assertEquals("task=$task", WorkInfo.State.SUCCEEDED, finished.state)
        assertEquals(DownloadStatus.COMPLETED, task.status)
        assertArrayEquals(content, app.container.downloadFiles.apkFile(task).readBytes())
    }
}
