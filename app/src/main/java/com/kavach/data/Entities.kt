package com.kavach.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RuleType { ALLOW, BLOCK }

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val type: RuleType,
    val enabled: Boolean = true,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "block_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val appPackage: String = "",
    val blocked: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

/** A downloadable blocklist source. */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastUpdated: Long = 0,
    val domainCount: Int = 0,
)
