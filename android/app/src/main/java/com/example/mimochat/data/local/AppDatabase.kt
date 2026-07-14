package com.example.mimochat.data.local

import android.content.Context
import androidx.room.*
import com.example.mimochat.data.ConversationEntity
import com.example.mimochat.data.MessageEntity
import com.example.mimochat.data.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, MemoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mimo_chat.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE enabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabled(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE memories SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
