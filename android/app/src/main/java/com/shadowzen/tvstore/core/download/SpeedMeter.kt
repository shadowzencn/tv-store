package com.shadowzen.tvstore.core.download

/** 用最近一段时间内的字节增量计算下载速度，避免瞬时波动。 */
class SpeedMeter(private val windowNanos: Long = 3_000_000_000L) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    /** 记录 [nanos] 时刻的累计字节数并返回每秒字节数。 */
    fun record(nanos: Long, totalBytes: Long): Long {
        samples.addLast(nanos to totalBytes)
        while (samples.size > 2 && nanos - samples[1].first >= windowNanos) samples.removeFirst()
        val (startNanos, startBytes) = samples.first()
        val elapsed = nanos - startNanos
        if (elapsed <= 0) return 0
        return ((totalBytes - startBytes).coerceAtLeast(0) * 1_000_000_000.0 / elapsed).toLong()
    }
}
