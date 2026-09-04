package com.example.data.repository

import com.example.data.db.LectureDao
import com.example.data.db.SubjectDao
import com.example.data.model.DiagramItem
import com.example.data.model.FormulaItem
import com.example.data.model.GeneratedLectureResult
import com.example.data.model.LectureNote
import com.example.data.model.SubjectSlot
import com.example.data.model.TimestampMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class LectureRepository(
    private val subjectDao: SubjectDao,
    private val lectureDao: LectureDao
) {
    val allSlots: Flow<List<SubjectSlot>> = subjectDao.getAllSlots()

    fun getLecturesForSlot(slotId: Long): Flow<List<LectureNote>> {
        return lectureDao.getLecturesForSlot(slotId)
    }

    suspend fun createSlot(name: String, description: String = "", colorHex: String = "#1A237E"): Long {
        return withContext(Dispatchers.IO) {
            subjectDao.insertSlot(
                SubjectSlot(
                    name = name.trim(),
                    description = description.trim(),
                    colorHex = colorHex
                )
            )
        }
    }

    suspend fun renameSlot(slotId: Long, newName: String) {
        withContext(Dispatchers.IO) {
            val existing = subjectDao.getSlotById(slotId) ?: return@withContext
            subjectDao.updateSlot(existing.copy(name = newName.trim()))
        }
    }

    suspend fun deleteSlot(slotId: Long) {
        withContext(Dispatchers.IO) {
            lectureDao.deleteLecturesBySlotId(slotId)
            subjectDao.deleteSlotById(slotId)
        }
    }

    suspend fun addLectureNote(
        slotId: Long,
        result: GeneratedLectureResult,
        sourceType: String,
        sourceUri: String,
        durationFormatted: String = "42:15",
        capturedFrameUri: String? = null
    ): Long {
        return withContext(Dispatchers.IO) {
            val note = LectureNote(
                slotId = slotId,
                title = result.title,
                sourceType = sourceType,
                sourceUri = sourceUri,
                summary = result.summary,
                keyConceptsJson = result.toKeyConceptsJson(),
                formulasJson = result.toFormulasJson(),
                diagramsJson = result.toDiagramsJson(),
                timestampsJson = result.toTimestampsJson(),
                videoDurationFormatted = durationFormatted,
                capturedFrameUri = capturedFrameUri
            )
            lectureDao.insertLecture(note)
        }
    }

    suspend fun deleteLecture(lectureId: Long) {
        withContext(Dispatchers.IO) {
            lectureDao.deleteLectureById(lectureId)
        }
    }

    suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            lectureDao.deleteAllLectures()
            subjectDao.deleteAllSlots()
        }
    }
}
