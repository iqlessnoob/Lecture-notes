package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SubjectSlot
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subject_slots ORDER BY createdAt ASC")
    fun getAllSlots(): Flow<List<SubjectSlot>>

    @Query("SELECT * FROM subject_slots WHERE id = :slotId LIMIT 1")
    suspend fun getSlotById(slotId: Long): SubjectSlot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: SubjectSlot): Long

    @Update
    suspend fun updateSlot(slot: SubjectSlot)

    @Delete
    suspend fun deleteSlot(slot: SubjectSlot)

    @Query("DELETE FROM subject_slots WHERE id = :slotId")
    suspend fun deleteSlotById(slotId: Long)

    @Query("DELETE FROM subject_slots")
    suspend fun deleteAllSlots()
}
