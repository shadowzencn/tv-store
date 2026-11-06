package com.shadowzen.tvstore.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** 一次下载需要的全部信息。[validator] 是上次响应的 ETag 或 Last-Modified，用于 If-Range 确认文件未变。 */
data class DownloadSpec(
    val url: String,
    val expectedSha256: String,
    val expectedSize: Long?,
    val partFile: File,
    val targetFile: File,
    val validator: String?,
)

interface DownloadListener {
    /** 收到可用的响应头后调用。[offset] 是本次续传的起点，0 表示从头下载。 */
    suspend fun onResponse(totalBytes: Long?, validator: String?, offset: Long)

    suspend fun onProgress(downloadedBytes: Long, totalBytes: Long?, bytesPerSecond: Long)

    suspend fun onVerifying()
}

enum class DownloadError(val retryable: Boolean) {
    NETWORK(true),
    SERVER(true),
    HTTP(false),
    SIZE_MISMATCH(false),
    CHECKSUM_MISMATCH(false),
    INSUFFICIENT_STORAGE(false),
    STORAGE(false),
    INTERNAL(false),
}

sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data class Failure(val error: DownloadError, val message: String) : DownloadResult
}

/**
 * 单个文件的下载器：HTTP Range 断点续传、If-Range 校验、进度与速度统计、SHA-256 校验。
 * 未完成的数据保存在 [DownloadSpec.partFile]，校验通过后才改名为 [DownloadSpec.targetFile]。
 * 取消协程即停止下载并保留已下载部分；除取消外，所有错误都以 [DownloadResult.Failure] 返回。
 */
class DownloadEngine(
    private val client: OkHttpClient,
    /** 返回目录所在存储可用于写入的字节数。 */
    private val availableBytes: (File) -> Long,
    private val nanoTime: () -> Long = System::nanoTime,
    private val progressIntervalNanos: Long = 500_000_000L,
    private val minFreeBytes: Long = 16L * 1024 * 1024,
) {

    suspend fun download(spec: DownloadSpec, listener: DownloadListener): DownloadResult = withContext(Dispatchers.IO) {
        try {
            downloadInternal(spec, listener)
        } catch (e: IOException) {
            storageFailure(spec.partFile, e)
        }
    }

    private suspend fun downloadInternal(spec: DownloadSpec, listener: DownloadListener): DownloadResult {
        val target = spec.targetFile
        if (target.exists()) {
            // 上次已校验并改名，但任务状态没来得及更新（例如进程被杀）
            listener.onVerifying()
            if (sha256(target).equals(spec.expectedSha256, ignoreCase = true)) return DownloadResult.Success(target)
            deleteOrThrow(target)
        }
        target.absoluteFile.parentFile?.mkdirs()

        var outcome = attempt(spec, listener, allowResume = true)
        if (outcome == Attempt.Restart) {
            deleteOrThrow(spec.partFile)
            outcome = attempt(spec, listener, allowResume = false)
        }
        return when (outcome) {
            Attempt.Restart -> failure(DownloadError.SERVER, "服务器返回的续传范围无效")
            is Attempt.Done -> outcome.result
        }
    }

    private sealed interface Attempt {
        /** 已有数据不能续用，删除后从头下载。 */
        data object Restart : Attempt
        data class Done(val result: DownloadResult) : Attempt
    }

    private suspend fun attempt(spec: DownloadSpec, listener: DownloadListener, allowResume: Boolean): Attempt {
        val part = spec.partFile
        val existing = if (allowResume && part.exists()) part.length() else 0L
        if (!allowResume && part.exists()) deleteOrThrow(part)
        if (spec.expectedSize != null && existing > spec.expectedSize) return Attempt.Restart
        if (spec.expectedSize != null && existing > 0 && existing == spec.expectedSize) {
            return Attempt.Done(finish(spec, listener))
        }

        val request = Request.Builder()
            .url(spec.url)
            // 压缩传输会让字节范围和文件大小失去意义
            .header("Accept-Encoding", "identity")
            .apply {
                if (existing > 0) {
                    header("Range", "bytes=$existing-")
                    spec.validator?.let { header("If-Range", it) }
                }
            }
            .build()
        val call = client.newCall(request)

        return coroutineScope {
            // 阻塞读取不响应协程取消，取消时关闭连接让读取立即结束
            val canceller = launch {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                call.execute().use { response -> handleResponse(spec, listener, response, existing) }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                Attempt.Done(failure(DownloadError.NETWORK, "网络连接失败：${e.message ?: e.javaClass.simpleName}"))
            } finally {
                canceller.cancel()
            }
        }
    }

    private suspend fun handleResponse(
        spec: DownloadSpec,
        listener: DownloadListener,
        response: Response,
        existing: Long,
    ): Attempt {
        val code = response.code
        val offset: Long
        val total: Long?
        when (code) {
            206 -> {
                val range = parseContentRange(response.header("Content-Range"))
                    ?: return Attempt.Done(failure(DownloadError.SERVER, "服务器返回的续传范围无效"))
                if (range.start != existing) {
                    return if (existing > 0) {
                        Attempt.Restart
                    } else {
                        Attempt.Done(failure(DownloadError.SERVER, "服务器返回的续传范围与请求不符"))
                    }
                }
                offset = existing
                total = range.total
            }
            // 服务器不支持 Range，或 If-Range 发现文件已变化，都会返回完整文件
            200 -> {
                offset = 0
                total = response.body.contentLength().takeIf { it >= 0 }
            }
            416 -> {
                val range = parseContentRange(response.header("Content-Range"))
                if (existing > 0 && range?.total == existing) return Attempt.Done(finish(spec, listener))
                return if (existing > 0) {
                    Attempt.Restart
                } else {
                    Attempt.Done(failure(DownloadError.HTTP, "服务器拒绝下载请求（HTTP 416）"))
                }
            }
            408, 429, in 500..599 ->
                return Attempt.Done(failure(DownloadError.SERVER, "服务器暂时不可用（HTTP $code）"))
            else -> return Attempt.Done(failure(DownloadError.HTTP, "服务器拒绝下载请求（HTTP $code）"))
        }

        if (spec.expectedSize != null && total != null && total != spec.expectedSize) {
            deleteOrThrow(spec.partFile)
            return Attempt.Done(
                failure(
                    DownloadError.SIZE_MISMATCH,
                    "安装包大小与商店记录不一致（服务器 $total 字节，记录 ${spec.expectedSize} 字节）",
                ),
            )
        }
        val knownTotal = total ?: spec.expectedSize

        val directory = spec.partFile.absoluteFile.parentFile
        if (knownTotal != null && directory != null && availableBytes(directory) < knownTotal - offset + minFreeBytes) {
            return Attempt.Done(failure(DownloadError.INSUFFICIENT_STORAGE, "存储空间不足，请清理后重试"))
        }

        listener.onResponse(knownTotal, validatorOf(response), offset)
        return writeBody(spec, listener, response, offset, knownTotal)
    }

    private suspend fun writeBody(
        spec: DownloadSpec,
        listener: DownloadListener,
        response: Response,
        offset: Long,
        total: Long?,
    ): Attempt {
        val output = try {
            FileOutputStream(spec.partFile, offset > 0)
        } catch (e: IOException) {
            return Attempt.Done(storageFailure(spec.partFile, e))
        }
        var downloaded = offset
        val meter = SpeedMeter()
        var lastReport = nanoTime()
        meter.record(lastReport, downloaded)

        output.use { out ->
            val source = response.body.byteStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = try {
                    source.read(buffer)
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    listener.onProgress(downloaded, total, 0)
                    return Attempt.Done(failure(DownloadError.NETWORK, "下载中断：${e.message ?: e.javaClass.simpleName}"))
                }
                if (read == -1) break
                try {
                    out.write(buffer, 0, read)
                } catch (e: IOException) {
                    return Attempt.Done(storageFailure(spec.partFile, e))
                }
                downloaded += read
                if (total != null && downloaded > total) {
                    out.close()
                    deleteOrThrow(spec.partFile)
                    return Attempt.Done(failure(DownloadError.SERVER, "服务器返回的数据超过文件大小"))
                }
                val now = nanoTime()
                if (now - lastReport >= progressIntervalNanos) {
                    lastReport = now
                    listener.onProgress(downloaded, total, meter.record(now, downloaded))
                }
            }
            try {
                out.flush()
                out.fd.sync()
            } catch (e: IOException) {
                return Attempt.Done(storageFailure(spec.partFile, e))
            }
        }
        listener.onProgress(downloaded, total, 0)

        if (total != null && downloaded < total) {
            return Attempt.Done(failure(DownloadError.NETWORK, "连接提前结束，已下载 $downloaded / $total 字节"))
        }
        return Attempt.Done(finish(spec, listener))
    }

    private suspend fun finish(spec: DownloadSpec, listener: DownloadListener): DownloadResult {
        listener.onVerifying()
        val actual = try {
            sha256(spec.partFile)
        } catch (e: IOException) {
            return storageFailure(spec.partFile, e)
        }
        if (!actual.equals(spec.expectedSha256, ignoreCase = true)) {
            deleteOrThrow(spec.partFile)
            return failure(DownloadError.CHECKSUM_MISMATCH, "安装包校验失败（SHA-256 不一致），已删除，请重新下载")
        }
        val target = spec.targetFile
        if (target.exists()) deleteOrThrow(target)
        if (!spec.partFile.renameTo(target)) {
            return failure(DownloadError.STORAGE, "无法保存安装包文件")
        }
        return DownloadResult.Success(target)
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun storageFailure(file: File, e: IOException): DownloadResult.Failure {
        val directory = file.absoluteFile.parentFile
        return if (directory != null && availableBytes(directory) < minFreeBytes) {
            failure(DownloadError.INSUFFICIENT_STORAGE, "存储空间不足，请清理后重试")
        } else {
            failure(DownloadError.STORAGE, "写入安装包失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun failure(error: DownloadError, message: String) = DownloadResult.Failure(error, message)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

data class ContentRange(val start: Long, val end: Long?, val total: Long?)

/** 解析 `bytes 100-199/200`、`bytes 100-199/*` 和 `bytes */200`。格式不对返回 null。 */
fun parseContentRange(header: String?): ContentRange? {
    val value = header?.trim() ?: return null
    if (!value.startsWith("bytes ", ignoreCase = true)) return null
    val spec = value.substring(6).trim()
    val slash = spec.indexOf('/')
    if (slash < 0) return null
    val rangePart = spec.substring(0, slash)
    val totalPart = spec.substring(slash + 1)
    val total = if (totalPart == "*") null else totalPart.toLongOrNull() ?: return null
    if (rangePart == "*") return total?.let { ContentRange(start = -1, end = null, total = it) }
    val dash = rangePart.indexOf('-')
    if (dash < 0) return null
    val start = rangePart.substring(0, dash).toLongOrNull() ?: return null
    val end = rangePart.substring(dash + 1).toLongOrNull() ?: return null
    if (start < 0 || end < start || (total != null && end >= total)) return null
    return ContentRange(start, end, total)
}

/** 弱 ETag 不能用于 If-Range，此时退回 Last-Modified。 */
private fun validatorOf(response: Response): String? {
    val etag = response.header("ETag")
    if (etag != null && !etag.startsWith("W/")) return etag
    return response.header("Last-Modified")
}

private fun deleteOrThrow(file: File) {
    if (file.exists() && !file.delete()) throw IOException("无法删除文件：${file.name}")
}
