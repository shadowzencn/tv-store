package com.shadowzen.tvstore

import com.shadowzen.tvstore.core.designsystem.androidVersionName
import com.shadowzen.tvstore.core.designsystem.formatFileSize
import com.shadowzen.tvstore.core.sample.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun androidVersionName_mapsKnownSdkLevels() {
        assertEquals("Android 5.0", androidVersionName(21))
        assertEquals("Android 7.0", androidVersionName(24))
        assertEquals("Android 9", androidVersionName(28))
        assertEquals("Android 11", androidVersionName(30))
        assertEquals("Android 12", androidVersionName(31))
        assertEquals("Android 12L", androidVersionName(32))
        assertEquals("Android 13", androidVersionName(33))
        assertEquals("Android 14", androidVersionName(34))
        assertEquals("Android 16", androidVersionName(36))
        assertEquals("API 99", androidVersionName(99))
    }

    @Test
    fun formatFileSize_usesMbAboveOneMegabyte() {
        assertEquals("18.6 MB", formatFileSize((18.6 * 1024 * 1024).toLong()))
        assertEquals("512 KB", formatFileSize(512 * 1024))
    }
}

class SampleCatalogTest {

    @Test
    fun appIds_areUnique() {
        val ids = SampleCatalog.apps.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyAppBelongsToKnownCategory() {
        val categoryIds = SampleCatalog.categories.map { it.id }.toSet()
        SampleCatalog.apps.forEach { assertTrue(it.id, it.categoryId in categoryIds) }
    }

    @Test
    fun appsWithinEachShelf_areUnique() {
        val shelves = SampleCatalog.shelves.map { it.apps } + listOf(SampleCatalog.featured)
        shelves.forEach { apps -> assertEquals(apps.size, apps.map { it.id }.toSet().size) }
    }

    @Test
    fun containsArm64OnlySampleForIncompatibilityHint() {
        assertTrue(SampleCatalog.apps.any { !it.supportsArmeabiV7a })
    }
}
