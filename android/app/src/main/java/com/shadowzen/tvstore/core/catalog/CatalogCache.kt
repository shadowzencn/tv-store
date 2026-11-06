package com.shadowzen.tvstore.core.catalog

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 最近一次成功获取的目录原文及其 ETag。 */
data class CachedCatalog(val etag: String?, val body: String)

/**
 * 目录缓存：单个文件，第一行是 ETag（没有时为空行），其余是响应原文。
 * 原文和 ETag 放在同一个文件里并以“写临时文件再替换”的方式保存，二者不会互相错位，也不会留下写了一半的文件。
 */
class CatalogCache(private val file: File) {

    fun read(): CachedCatalog? {
        if (!file.exists()) return null
        val text = file.readText(Charsets.UTF_8)
        val separator = text.indexOf('\n')
        if (separator < 0) throw IOException("目录缓存文件格式错误")
        val etag = text.substring(0, separator).takeIf { it.isNotEmpty() }
        return CachedCatalog(etag, text.substring(separator + 1))
    }

    fun write(cached: CachedCatalog) {
        val etag = cached.etag.orEmpty()
        require('\n' !in etag && '\r' !in etag) { "ETag 不能包含换行" }
        val directory = file.parentFile
        if (directory != null && !directory.isDirectory && !directory.mkdirs()) {
            throw IOException("无法创建目录缓存文件夹：$directory")
        }
        val temp = File(file.path + ".tmp")
        temp.writeText(etag + "\n" + cached.body, Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun clear() {
        if (file.exists() && !file.delete()) throw IOException("无法删除目录缓存文件")
    }
}
