package com.shadowzen.tvstore.feature.home

import com.shadowzen.tvstore.core.catalog.Catalog
import com.shadowzen.tvstore.core.model.AppShelf
import com.shadowzen.tvstore.core.model.StoreApp

const val RECENT_SHELF_ID = "recent"
const val RECENT_SHELF_LIMIT = 12

/** 首页内容：精选单独用大卡片显示，其余为横向列表。 */
data class HomeContent(
    val featured: List<StoreApp>,
    val shelves: List<AppShelf>,
) {
    val isEmpty: Boolean
        get() = featured.isEmpty() && shelves.isEmpty()
}

/**
 * 首页分组在电视端生成：精选（管理员标记，保持目录顺序）、最近更新（按发布时间倒序）、各分类（按分类顺序）。
 * 没有应用的分组不显示。
 */
fun buildHomeContent(catalog: Catalog): HomeContent {
    val recent = catalog.apps
        .filter { it.publishedAtMillis != null }
        .sortedByDescending { it.publishedAtMillis }
        .take(RECENT_SHELF_LIMIT)
    val shelves = buildList {
        if (recent.isNotEmpty()) add(AppShelf(RECENT_SHELF_ID, "最近更新", recent))
        catalog.categories.forEach { category ->
            val apps = catalog.apps.filter { it.categoryId == category.id }
            // 分类 ID 由管理员定义，加前缀避免与“featured”“recent”等固定分组的 ID 冲突
            if (apps.isNotEmpty()) add(AppShelf("category:${category.id}", category.name, apps))
        }
    }
    return HomeContent(featured = catalog.apps.filter { it.featured }, shelves = shelves)
}
