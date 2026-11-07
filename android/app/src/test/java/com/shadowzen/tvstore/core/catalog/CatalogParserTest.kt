package com.shadowzen.tvstore.core.catalog

import android.app.Application
import com.shadowzen.tvstore.core.catalog.CatalogFixtures.app
import com.shadowzen.tvstore.core.catalog.CatalogFixtures.catalog
import com.shadowzen.tvstore.core.catalog.CatalogFixtures.version
import com.shadowzen.tvstore.core.model.ABI_ARMEABI_V7A
import com.shadowzen.tvstore.core.model.CompatibilityStatus
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json 只在 Android 运行时（Robolectric）中有实现，所以用 Robolectric 运行。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class CatalogParserTest {
    private val baseUrl = "https://store.example.com/".toHttpUrl()

    @Test
    fun mapsFieldsAndResolvesRelativeDownloadUrl() {
        val parsed = CatalogParser.parse(catalog(app("local-player", featured = true)), baseUrl)

        assertEquals(listOf("video", "network"), parsed.categories.map { it.id })
        val app = parsed.apps.single()
        assertEquals("local-player", app.id)
        assertEquals("应用 local-player", app.name)
        assertEquals("2.4.1", app.versionName)
        assertEquals(19_500_000L, app.sizeBytes)
        assertEquals(21, app.minSdk)
        assertEquals(listOf("armeabi-v7a", "arm64-v8a"), app.abis)
        assertEquals(CompatibilityStatus.TESTED, app.compatibility)
        assertNull(app.knownIssues)
        assertTrue(app.featured)
        assertEquals(1_772_352_000_000L, app.publishedAtMillis)
        val download = app.download!!
        assertEquals("com.example.player", download.packageName)
        assertEquals(241L, download.versionCode)
        assertEquals("https://store.example.com/files/apks/${"a".repeat(64)}.apk", download.url)
        assertEquals("a".repeat(64), download.sha256)
        assertEquals("影音娱乐", parsed.categoryName("video"))
        assertEquals("未分类", parsed.categoryName("missing"))
    }

    @Test
    fun keepsAbsoluteDownloadUrlAndToleratesOptionalFields() {
        val body = catalog(
            app("a", version = version(downloadUrl = "https://cdn.example.com/x.apk", publishedAt = null, compatibility = null)),
            app("b", version = version(compatibility = "spec_match", publishedAt = "2026-03-01T16:00:00.000000+08:00")),
            app("c", version = version(abis = emptyList())),
        )
        val apps = CatalogParser.parse(body, baseUrl).apps

        assertEquals("https://cdn.example.com/x.apk", apps[0].download!!.url)
        assertNull(apps[0].publishedAtMillis)
        assertEquals(CompatibilityStatus.UNVERIFIED, apps[0].compatibility)
        assertFalse(apps[0].featured)
        assertEquals(CompatibilityStatus.PARAMS_MATCH, apps[1].compatibility)
        assertEquals(1_772_352_000_000L, apps[1].publishedAtMillis)
        assertTrue(apps[2].abis.isEmpty())
        assertTrue(apps[2].supportsArmeabiV7a)
    }

    @Test
    fun parsesIconUrlAndFallsBackToNullWhenMissingOrInvalid() {
        val body = catalog(
            app("relative", iconUrl = "/files/icons/relative.png"),
            app("absolute", iconUrl = "https://tv.shadowzen.xyz/files/icons/absolute.png"),
            app("invalid", iconUrl = "not a url"),
            app("missing"),
        )

        val apps = CatalogParser.parse(body, baseUrl).apps

        assertEquals("https://store.example.com/files/icons/relative.png", apps[0].iconUrl)
        assertEquals("https://tv.shadowzen.xyz/files/icons/absolute.png", apps[1].iconUrl)
        // 图标是装饰性字段，无效地址只降级为占位图标，不丢弃整个应用
        assertNull(apps[2].iconUrl)
        assertNull(apps[3].iconUrl)
    }

    @Test
    fun skipsInvalidAndDuplicateAppsButKeepsTheRest() {
        val skipped = mutableListOf<String>()
        val body = catalog(
            app("good"),
            app("no-version", version = null),
            app("bad-sha", version = version(sha256 = "1234")),
            app("good"),
        )

        val apps = CatalogParser.parse(body, baseUrl) { skipped += it }.apps

        assertEquals(listOf("good"), apps.map { it.id })
        assertEquals(3, skipped.size)
    }


    /** 服务端真实输出（PHP 端到端流程生成）：电视端必须能解析，字段值与 aapt2 读取结果一致。 */
    @Test
    fun parsesRealServerOutput() {
        val body = javaClass.getResource("/fixtures/server-catalog.json")!!.readText()

        val parsed = CatalogParser.parse(body, "https://store.example.com/".toHttpUrl())

        assertEquals(listOf("tools"), parsed.categories.map { it.id })
        val app = parsed.apps.single()
        assertEquals("tv-store", app.id)
        assertEquals("tools", app.categoryId)
        assertTrue(app.featured)
        assertEquals("0.1.0", app.versionName)
        assertEquals(28, app.minSdk)
        assertTrue(ABI_ARMEABI_V7A in app.abis)
        assertEquals(2_371_781L, app.sizeBytes)
        assertEquals(CompatibilityStatus.PARAMS_MATCH, app.compatibility)
        assertTrue(app.publishedAtMillis != null)
        val download = app.download!!
        assertEquals("com.shadowzen.tvstore", download.packageName)
        assertEquals(1L, download.versionCode)
        assertEquals("32b1dcfb868262eb44c24c578f56919f9febc62bea754bc53ea54d2fb7e16b10", download.sha256)
        assertEquals(
            "https://store.example.com/files/apks/32b1dcfb868262eb44c24c578f56919f9febc62bea754bc53ea54d2fb7e16b10.apk",
            download.url,
        )
    }

    @Test
    fun rejectsMalformedTopLevelStructure() {
        assertThrows(CatalogFormatException::class.java) { CatalogParser.parse("<html>502</html>", baseUrl) }
        assertThrows(CatalogFormatException::class.java) { CatalogParser.parse("""{"categories":[]}""", baseUrl) }
        assertThrows(CatalogFormatException::class.java) { CatalogParser.parse("[]", baseUrl) }
    }
}
