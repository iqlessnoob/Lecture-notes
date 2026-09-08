package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lecture_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = LectureNote::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lectureId")]
)
data class LectureChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lectureId: Long,
    val sender: String, // "USER" or "AI"
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
)
