package com.shadowzen.tvstore.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 状态变更都带有前置状态条件，避免下载线程覆盖用户刚做的暂停或删除。 */
@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAt")
    fun observeAll(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE appId = :appId")
    fun observe(appId: String): Flow<DownloadTask?>

    @Query("SELECT * FROM download_tasks WHERE appId = :appId")
    suspend fun get(appId: String): DownloadTask?

    @Upsert
    suspend fun upsert(task: DownloadTask)

    @Query("DELETE FROM download_tasks WHERE appId = :appId")
    suspend fun delete(appId: String): Int

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' ORDER BY createdAt LIMIT 1")
    suspend fun nextQueued(): DownloadTask?

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING', 'VERIFYING')")
    suspend fun countPending(): Int

    @Query(
        "UPDATE download_tasks SET status = 'QUEUED', bytesPerSecond = 0, updatedAt = :now " +
            "WHERE status IN ('DOWNLOADING', 'VERIFYING')",
    )
    suspend fun requeueInterrupted(now: Long): Int

    @Query(
        "UPDATE download_tasks SET status = :to, errorMessage = :errorMessage, bytesPerSecond = 0, " +
            "updatedAt = :now WHERE appId = :appId AND status IN (:from)",
    )
    suspend fun transition(
        appId: String,
        from: List<DownloadStatus>,
        to: DownloadStatus,
        errorMessage: String?,
        now: Long,
    ): Int

    @Query(
        "UPDATE download_tasks SET totalBytes = :totalBytes, validator = :validator, " +
            "downloadedBytes = :downloadedBytes, updatedAt = :now WHERE appId = :appId AND status = 'DOWNLOADING'",
    )
    suspend fun updateResponse(appId: String, totalBytes: Long?, validator: String?, downloadedBytes: Long, now: Long): Int

    @Query(
        "UPDATE download_tasks SET downloadedBytes = :downloadedBytes, bytesPerSecond = :bytesPerSecond, " +
            "errorMessage = NULL, updatedAt = :now WHERE appId = :appId AND status = 'DOWNLOADING'",
    )
    suspend fun updateProgress(appId: String, downloadedBytes: Long, bytesPerSecond: Long, now: Long): Int
}
