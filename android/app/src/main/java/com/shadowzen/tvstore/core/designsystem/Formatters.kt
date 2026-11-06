package com.shadowzen.tvstore.core.designsystem

import java.util.Locale

fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) {
        String.format(Locale.ROOT, "%.1f MB", mb)
    } else {
        String.format(Locale.ROOT, "%d KB", bytes / 1024)
    }
}

fun formatSpeed(bytesPerSecond: Long): String = "${formatFileSize(bytesPerSecond)}/s"

fun androidVersionName(sdk: Int): String = when (sdk) {
    21 -> "Android 5.0"
    22 -> "Android 5.1"
    23 -> "Android 6.0"
    24 -> "Android 7.0"
    25 -> "Android 7.1"
    26 -> "Android 8.0"
    27 -> "Android 8.1"
    in 28..31 -> "Android ${sdk - 19}"
    32 -> "Android 12L"
    in 33..36 -> "Android ${sdk - 20}"
    else -> "API $sdk"
}
