package com.shadowzen.tvstore.feature.home

import com.shadowzen.tvstore.core.catalog.Catalog
import com.shadowzen.tvstore.core.model.StoreCategory
import com.shadowzen.tvstore.core.sample.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentTest {
    private val template = SampleCatalog.apps.first()

    private fun app(id: String, category: String, featured: Boolean = false, publishedAt: Long? = null) =
        template.copy(id = id, name = id, categoryId = category, featured = featured, publishedAtMillis = publishedAt)

    private val categories = listOf(StoreCategory("video", "影音娱乐"), StoreCategory("tools", "实用工具"), StoreCategory("game", "游戏"))

    @Test
    fun buildsFeaturedRecentAndCategoryShelvesInOrder() {
        val catalog = Catalog(
            categories,
            listOf(
                app("a", "tools", featured = true, publishedAt = 100),
                app("b", "video", publishedAt = 300),
                app("c", "video", featured = true),
                app("d", "unknown", publishedAt = 200),
            ),
        )

        val content = buildHomeContent(catalog)

        assertEquals(listOf("a", "c"), content.featured.map { it.id })
        assertEquals(listOf(RECENT_SHELF_ID, "category:video", "category:tools"), content.shelves.map { it.id })
        assertEquals(listOf("最近更新", "影音娱乐", "实用工具"), content.shelves.map { it.title })
        assertEquals(listOf("b", "d", "a"), content.shelves[0].apps.map { it.id })
        assertEquals(listOf("b", "c"), content.shelves[1].apps.map { it.id })
        assertEquals(listOf("a"), content.shelves[2].apps.map { it.id })
    }

    @Test
    fun recentShelfIsLimited() {
        val apps = (1..20).map { app("app$it", "video", publishedAt = it.toLong()) }

        val recent = buildHomeContent(Catalog(categories, apps)).shelves.first { it.id == RECENT_SHELF_ID }

        assertEquals(RECENT_SHELF_LIMIT, recent.apps.size)
        assertEquals("app20", recent.apps.first().id)
    }

    @Test
    fun emptyCatalogProducesEmptyContent() {
        assertTrue(buildHomeContent(Catalog(categories, emptyList())).isEmpty)
    }

    @Test
    fun appsWithoutPublishTimeOrFeaturedFlagOnlyAppearInCategories() {
        val content = buildHomeContent(Catalog(categories, listOf(app("x", "game"))))

        assertTrue(content.featured.isEmpty())
        assertEquals(listOf("category:game"), content.shelves.map { it.id })
    }
}
