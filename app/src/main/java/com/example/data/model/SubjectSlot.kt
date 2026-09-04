package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subject_slots")
data class SubjectSlot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val colorHex: String = "#3F51B5",
    val createdAt: Long = System.currentTimeMillis()
)
