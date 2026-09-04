package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.LectureNote
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureDao {
    @Query("SELECT * FROM lecture_notes WHERE slotId = :slotId ORDER BY createdAt DESC")
    fun getLecturesForSlot(slotId: Long): Flow<List<LectureNote>>

    @Query("SELECT * FROM lecture_notes WHERE id = :lectureId LIMIT 1")
    suspend fun getLectureById(lectureId: Long): LectureNote?

    @Query("SELECT COUNT(*) FROM lecture_notes WHERE slotId = :slotId")
    fun getLectureCountForSlot(slotId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: LectureNote): Long

    @Update
    suspend fun updateLecture(lecture: LectureNote)

    @Delete
    suspend fun deleteLecture(lecture: LectureNote)

    @Query("DELETE FROM lecture_notes WHERE id = :lectureId")
    suspend fun deleteLectureById(lectureId: Long)

    @Query("DELETE FROM lecture_notes WHERE slotId = :slotId")
    suspend fun deleteLecturesBySlotId(slotId: Long)

    @Query("DELETE FROM lecture_notes")
    suspend fun deleteAllLectures()
}
