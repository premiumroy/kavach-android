package com.kavach.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY domain ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1")
    suspend fun enabled(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity): Long

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM block_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<LogEntity>>

    @Query("SELECT COUNT(*) FROM block_logs WHERE blocked = 1 AND timestamp > :since")
    fun observeBlockedCount(since: Long): Flow<Int>

    @Insert
    suspend fun insert(log: LogEntity)

    @Query("DELETE FROM block_logs")
    suspend fun clear()
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY name ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE enabled = 1")
    suspend fun enabled(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: SourceEntity)

    @Query("UPDATE sources SET lastUpdated = :ts, domainCount = :count WHERE id = :id")
    suspend fun markUpdated(id: String, ts: Long, count: Int)
}
