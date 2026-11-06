package com.shadowzen.tvstore.core.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * @property catalog 当前显示的目录；还没有任何可用数据时为 null。
 * @property errorMessage 最近一次刷新失败的原因；刷新成功后清除。
 */
data class CatalogState(
    val catalog: Catalog? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 应用目录的唯一来源：先显示本地缓存，再向服务器刷新。
 * 刷新失败时保留已有目录，只记录错误；只有从未成功获取过目录时界面才显示错误状态。
 */
class CatalogRepository(
    private val api: CatalogApi,
    private val cache: CatalogCache,
    private val parse: (String) -> Catalog,
    private val onError: (String, Throwable) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow(CatalogState(isLoading = true))
    val state: StateFlow<CatalogState> = mutableState.asStateFlow()

    private val refreshLock = Mutex()
    private var cacheLoaded = false
    private var currentEtag: String? = null

    /** 刷新目录。已有刷新在进行时直接返回，避免重复按“重试”发出多个请求。 */
    suspend fun refresh() {
        if (!refreshLock.tryLock()) return
        try {
            withContext(ioDispatcher) {
                if (!cacheLoaded) {
                    loadCache()
                    cacheLoaded = true
                }
                mutableState.update { it.copy(isLoading = true) }
                fetchFromServer()
            }
        } finally {
            refreshLock.unlock()
        }
    }

    private fun loadCache() {
        val cached = try {
            cache.read()
        } catch (e: IOException) {
            onError("读取目录缓存失败", e)
            null
        } ?: return
        try {
            val catalog = parse(cached.body)
            currentEtag = cached.etag
            mutableState.update { it.copy(catalog = catalog) }
        } catch (e: CatalogFormatException) {
            onError("目录缓存无法解析，已丢弃", e)
            try {
                cache.clear()
            } catch (io: IOException) {
                onError("删除损坏的目录缓存失败", io)
            }
        }
    }

    private suspend fun fetchFromServer() {
        val hasCatalog = mutableState.value.catalog != null
        val failure: String = try {
            when (val response = api.fetch(etag = currentEtag.takeIf { hasCatalog })) {
                CatalogResponse.NotModified -> {
                    if (hasCatalog) {
                        mutableState.update { it.copy(isLoading = false, errorMessage = null) }
                        return
                    }
                    onError("服务器返回 304，但本地没有目录缓存", IllegalStateException("unexpected 304"))
                    "服务器返回的数据异常，请稍后重试"
                }
                is CatalogResponse.Updated -> {
                    val catalog = parse(response.body)
                    saveCache(CachedCatalog(response.etag?.takeIf(::isStorableEtag), response.body))
                    currentEtag = response.etag
                    mutableState.value = CatalogState(catalog = catalog, isLoading = false, errorMessage = null)
                    return
                }
            }
        } catch (e: IOException) {
            onError("获取应用目录失败（网络）", e)
            "无法连接服务器，请检查网络后重试"
        } catch (e: CatalogHttpException) {
            onError("获取应用目录失败（HTTP）", e)
            val reason = e.serverMessage ?: "服务器暂时无法提供应用目录"
            "$reason（HTTP ${e.statusCode}）"
        } catch (e: CatalogFormatException) {
            onError("应用目录格式错误", e)
            "应用目录数据格式有误，请稍后重试"
        }
        mutableState.update { it.copy(isLoading = false, errorMessage = failure) }
    }

    /** 缓存写入失败不影响本次显示，下次启动会重新从服务器获取。 */
    private fun saveCache(cached: CachedCatalog) {
        try {
            cache.write(cached)
        } catch (e: IOException) {
            onError("写入目录缓存失败", e)
        }
    }

    private fun isStorableEtag(etag: String): Boolean = '\n' !in etag && '\r' !in etag
}
