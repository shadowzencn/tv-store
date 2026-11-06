package com.shadowzen.tvstore.core.download

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.Buffer
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 模拟正确实现 Range / If-Range 的文件服务器，可注入断线、限速和错误状态码。 */
class RangeFileServer(
    @Volatile var content: ByteArray,
    @Volatile var etag: String = "\"v1\"",
) : Dispatcher() {
    @Volatile var supportsRange = true
    @Volatile var statusOverride: Int? = null

    /** 每个周期发送的字节数和周期毫秒数；null 表示不限速。 */
    @Volatile var throttle: Pair<Long, Long>? = null

    /** 接下来这么多个响应会在传输正文途中断开连接。 */
    val disconnectNext = AtomicInteger(0)
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        statusOverride?.let { return MockResponse.Builder().code(it).build() }

        val data = content
        val builder = MockResponse.Builder().addHeader("ETag", etag)
        val range = request.headers["Range"]
        val ifRange = request.headers["If-Range"]
        val useRange = supportsRange && range != null && (ifRange == null || ifRange == etag)
        if (useRange) {
            val start = range!!.removePrefix("bytes=").removeSuffix("-").toLong()
            if (start >= data.size) {
                return builder.code(416).addHeader("Content-Range", "bytes */${data.size}").build()
            }
            builder.code(206)
                .addHeader("Content-Range", "bytes $start-${data.size - 1}/${data.size}")
                .body(Buffer().write(data, start.toInt(), data.size - start.toInt()))
        } else {
            builder.code(200).body(Buffer().write(data))
        }
        if (disconnectNext.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
            builder.onResponseBody(SocketEffect.ShutdownConnection)
        }
        throttle?.let { (bytes, millis) -> builder.throttleBody(bytes, millis, TimeUnit.MILLISECONDS) }
        return builder.build()
    }
}

fun testContent(size: Int, seed: Int = 7): ByteArray = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
