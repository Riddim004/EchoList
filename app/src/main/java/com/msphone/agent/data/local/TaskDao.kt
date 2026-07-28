package com.msphone.agent.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,        // WORK / LIFE
    val remindTimeMillis: Long?, // null = 无提醒
    val timeExpression: String?,
    val note: String?,
    val rawInput: String,
    val status: String,          // PENDING / DONE / EXPIRED
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface TaskDao {

    @Query(
        "SELECT * FROM tasks ORDER BY " +
            "CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, " +
            "CASE WHEN remindTimeMillis IS NULL THEN 1 ELSE 0 END, " +
            "remindTimeMillis ASC, createdAt DESC"
    )
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND remindTimeMillis IS NOT NULL")
    suspend fun getPendingWithReminder(): List<TaskEntity>

    @Query(
        "SELECT * FROM tasks WHERE " +
            "(:category IS NULL OR category = :category) AND " +
            "(:fromMillis IS NULL OR remindTimeMillis >= :fromMillis) AND " +
            "(:toMillis IS NULL OR remindTimeMillis <= :toMillis) " +
            "ORDER BY remindTimeMillis ASC"
    )
    suspend fun query(category: String?, fromMillis: Long?, toMillis: Long?): List<TaskEntity>

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET remindTimeMillis = :remindAtMillis, updatedAt = :now WHERE id = :id")
    suspend fun updateRemindTime(id: Long, remindAtMillis: Long?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
