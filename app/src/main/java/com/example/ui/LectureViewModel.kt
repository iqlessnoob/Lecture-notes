package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.GeneratedLectureResult
import com.example.data.model.LectureNote
import com.example.data.model.SubjectSlot
import com.example.data.repository.LectureRepository
import com.example.service.GeminiLectureService
import com.example.service.PdfExportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LectureViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = LectureRepository(db.subjectDao(), db.lectureDao())
    private val geminiService = GeminiLectureService(application)
    private val pdfExportService = PdfExportService(application)
    private val prefs = application.getSharedPreferences("lecturescribe_prefs", Context.MODE_PRIVATE)

    val allSlots: StateFlow<List<SubjectSlot>> = repository.allSlots
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedSlotId = MutableStateFlow<Long?>(null)
    val selectedSlotId: StateFlow<Long?> = _selectedSlotId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lecturesInSelectedSlot: StateFlow<List<LectureNote>> = _selectedSlotId
        .flatMapLatest { slotId ->
            if (slotId != null) {
                repository.getLecturesForSlot(slotId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedLectureId = MutableStateFlow<Long?>(null)
    val selectedLectureId: StateFlow<Long?> = _selectedLectureId.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingStatusText = MutableStateFlow("Analyzing lecture recording…")
    val processingStatusText: StateFlow<String> = _processingStatusText.asStateFlow()

    private val _uiEventMessage = MutableSharedFlow<String>()
    val uiEventMessage: SharedFlow<String> = _uiEventMessage.asSharedFlow()

    private val _customApiKey = MutableStateFlow(
        prefs.getString("gemini_api_key", "") ?: ""
    )
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allSlots.collect { slots ->
                val sampleSlots = slots.filter {
                    it.name == "Organic Chemistry" || it.name == "Electromagnetism & Waves"
                }
                if (sampleSlots.isNotEmpty()) {
                    sampleSlots.forEach { repository.deleteSlot(it.id) }
                } else if (_selectedSlotId.value == null && slots.isNotEmpty()) {
                    _selectedSlotId.value = slots.first().id
                } else if (slots.isEmpty()) {
                    _selectedSlotId.value = null
                    _selectedLectureId.value = null
                }
            }
        }
    }

    fun selectSlot(slotId: Long) {
        _selectedSlotId.value = slotId
        _selectedLectureId.value = null
    }

    fun selectLecture(lectureId: Long?) {
        _selectedLectureId.value = lectureId
    }

    fun setCustomApiKey(key: String) {
        val trimmed = key.trim()
        _customApiKey.value = trimmed
        prefs.edit().putString("gemini_api_key", trimmed).apply()
    }

    fun createSlot(name: String, description: String = "", colorHex: String = "#1A237E") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createSlot(name, description, colorHex)
            _selectedSlotId.value = newId
            _uiEventMessage.emit("Subject '$name' created")
        }
    }

    fun renameSlot(slotId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameSlot(slotId, newName)
            _uiEventMessage.emit("Subject renamed to '$newName'")
        }
    }

    fun deleteSlot(slotId: Long) {
        viewModelScope.launch {
            val currentSlots = allSlots.value
            repository.deleteSlot(slotId)
            val remaining = currentSlots.filter { it.id != slotId }
            _selectedSlotId.value = remaining.firstOrNull()?.id
            _selectedLectureId.value = null
            _uiEventMessage.emit("Subject and its lecture notes deleted")
        }
    }

    fun deleteLecture(lectureId: Long) {
        viewModelScope.launch {
            repository.deleteLecture(lectureId)
            if (_selectedLectureId.value == lectureId) {
                _selectedLectureId.value = null
            }
            _uiEventMessage.emit("Lecture deleted")
        }
    }

    fun processYouTubeLecture(slotId: Long, url: String) {
        if (url.isBlank()) return
        val currentSlot = allSlots.value.find { it.id == slotId } ?: return

        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatusText.value = "Fetching lecture details & stream metadata…"

            val result = geminiService.generateNotesForYouTube(
                youtubeUrl = url,
                subjectSlotName = currentSlot.name,
                customApiKey = _customApiKey.value
            )

            if (result.isSuccess) {
                _processingStatusText.value = "Formatting LaTeX derivations & Mermaid diagrams…"
                delay(400)
                val generated = result.getOrThrow()
                val noteId = repository.addLectureNote(
                    slotId = slotId,
                    result = generated,
                    sourceType = "YOUTUBE",
                    sourceUri = url,
                    durationFormatted = "Full Video"
                )
                _selectedLectureId.value = noteId
                _uiEventMessage.emit("Notes generated for '${generated.title}'!")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to generate notes."
                _uiEventMessage.emit(err)
            }
            _isProcessing.value = false
        }
    }

    fun processUploadedVideo(slotId: Long, uri: Uri, fileName: String) {
        val currentSlot = allSlots.value.find { it.id == slotId } ?: return

        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatusText.value = "Sampling visual keyframes from lecture recording…"

            val result = geminiService.generateNotesForUploadedVideo(
                videoUri = uri,
                subjectSlotName = currentSlot.name,
                customApiKey = _customApiKey.value
            )

            if (result.isSuccess) {
                _processingStatusText.value = "Verifying LaTeX formulas & diagram structures…"
                delay(400)
                val generated = result.getOrThrow()
                val noteId = repository.addLectureNote(
                    slotId = slotId,
                    result = generated,
                    sourceType = "UPLOAD",
                    sourceUri = uri.toString(),
                    durationFormatted = "Recording"
                )
                _selectedLectureId.value = noteId
                _uiEventMessage.emit("Notes generated for '${generated.title}'!")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to analyze video."
                _uiEventMessage.emit(err)
            }
            _isProcessing.value = false
        }
    }

    fun exportPdf(
        context: Context,
        slot: SubjectSlot,
        lecture: LectureNote,
        onComplete: (Result<PdfExportService.ExportResult>) -> Unit
    ) {
        viewModelScope.launch {
            val result = pdfExportService.exportLectureToPdf(slot, lecture)
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }
}
