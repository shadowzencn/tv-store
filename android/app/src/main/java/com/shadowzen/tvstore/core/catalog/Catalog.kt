package com.shadowzen.tvstore.core.catalog

import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.core.model.StoreCategory

/** 服务端返回的完整应用目录。分类和应用保持服务端给出的顺序（即管理员设置的排序）。 */
data class Catalog(
    val categories: List<StoreCategory>,
    val apps: List<StoreApp>,
) {
    private val appsById: Map<String, StoreApp> = apps.associateBy { it.id }
    private val categoryNames: Map<String, String> = categories.associate { it.id to it.name }

    fun findApp(id: String): StoreApp? = appsById[id]

    fun categoryName(id: String): String = categoryNames[id] ?: "未分类"
}
