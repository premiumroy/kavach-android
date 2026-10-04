package com.kavach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun toRuleType(v: String): RuleType = RuleType.valueOf(v)
    @TypeConverter fun fromRuleType(t: RuleType): String = t.name
}

@Database(entities = [RuleEntity::class, LogEntity::class, SourceEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class KavachDatabase : RoomDatabase() {
    abstract fun rules(): RuleDao
    abstract fun logs(): LogDao
    abstract fun sources(): SourceDao

    companion object {
        @Volatile private var INSTANCE: KavachDatabase? = null
        fun get(context: Context): KavachDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, KavachDatabase::class.java, "kavach.db"
                ).build().also { INSTANCE = it }
            }
    }
}
