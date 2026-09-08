package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.LectureChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM lecture_chat_messages WHERE lectureId = :lectureId ORDER BY timestamp ASC")
    fun getMessagesForLecture(lectureId: Long): Flow<List<LectureChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: LectureChatMessage): Long

    @Query("DELETE FROM lecture_chat_messages WHERE lectureId = :lectureId")
    suspend fun deleteMessagesForLecture(lectureId: Long)
}
