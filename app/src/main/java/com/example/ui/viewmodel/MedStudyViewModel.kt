package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.AnkiPackageExporter
import com.example.data.local.ApiKeyManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DeckEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.LectureEntity
import com.example.data.local.entity.LectureSlideEntity
import com.example.data.remote.AcademicAnalysisResult
import com.example.data.remote.GeminiService
import com.example.data.repository.MedStudyRepository
import com.example.data.sample.MedicalLectureSamples
import com.example.data.sample.MedicalSampleLecture
import com.example.util.PdfTextExtractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed class Screen {
    data object Home : Screen()
    data class LectureDetail(val lectureId: Long) : Screen()
    data class AcademicAnalysis(val lectureId: Long) : Screen()
    data class CardReview(val deckId: Long? = null, val lectureId: Long? = null) : Screen()
    data class Practice(val deckId: Long? = null, val lectureId: Long? = null) : Screen()
    data class Export(val deckId: Long? = null) : Screen()
}

data class UiOperationStatus(
    val isLoading: Boolean = false,
    val message: String = "",
    val progressText: String = "",
    val canCancel: Boolean = false,
    val failedCount: Int = 0,
    val failedLectureId: Long? = null,
    val error: String? = null,
    val success: String? = null
)

class MedStudyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = MedStudyRepository(
        lectureDao = db.lectureDao(),
        deckDao = db.deckDao(),
        flashcardDao = db.flashcardDao(),
        slideDao = db.slideDao(),
        geminiService = GeminiService()
    )

    val lectures: StateFlow<List<LectureEntity>> = repository.allLectures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val decks: StateFlow<List<DeckEntity>> = repository.allDecks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCards: StateFlow<List<FlashcardEntity>> = repository.allCards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _opStatus = MutableStateFlow(UiOperationStatus())
    val opStatus: StateFlow<UiOperationStatus> = _opStatus.asStateFlow()

    // Dialog states
    private val _showApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = _showApiKeyDialog.asStateFlow()

    private val _showPrivacyDialog = MutableStateFlow(false)
    val showPrivacyDialog: StateFlow<Boolean> = _showPrivacyDialog.asStateFlow()

    private var pendingPdfUri: Uri? = null

    // Active editing card dialog
    private val _editingCard = MutableStateFlow<FlashcardEntity?>(null)
    val editingCard: StateFlow<FlashcardEntity?> = _editingCard.asStateFlow()

    // Exported file ready for share/save
    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile: StateFlow<File?> = _exportedFile.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSampleDataLoaded()
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun clearStatus() {
        _opStatus.value = UiOperationStatus()
    }

    fun setEditingCard(card: FlashcardEntity?) {
        _editingCard.value = card
    }

    fun clearExportedFile() {
        _exportedFile.value = null
    }

    fun openApiKeyDialog() {
        _showApiKeyDialog.value = true
    }

    fun closeApiKeyDialog() {
        _showApiKeyDialog.value = false
    }

    fun isPrivacyNoticeAccepted(): Boolean = ApiKeyManager.isPrivacyNoticeAccepted(getApplication())

    fun acceptPrivacyNotice() {
        ApiKeyManager.setPrivacyNoticeAccepted(getApplication(), true)
        _showPrivacyDialog.value = false
        pendingPdfUri?.let { uri ->
            pendingPdfUri = null
            processPdfImport(uri)
        }
    }

    fun dismissPrivacyNotice() {
        _showPrivacyDialog.value = false
        pendingPdfUri = null
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _opStatus.value = UiOperationStatus(message = "Operation cancelled by user.")
    }

    fun loadSampleLecture(sample: MedicalSampleLecture) {
        viewModelScope.launch {
            _opStatus.value = UiOperationStatus(isLoading = true, message = "Importing ${sample.title}...")
            try {
                val lectureId = repository.createLecture(
                    title = sample.title,
                    subject = sample.subject,
                    rawContent = sample.content,
                    pageCount = 4
                )
                val deckId = repository.createDeck(
                    name = "${sample.subject} - High Yield",
                    subject = sample.subject,
                    lectureId = lectureId
                )
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    success = "Lecture '${sample.title}' loaded into workspace."
                )
                _currentScreen.value = Screen.LectureDetail(lectureId)
            } catch (e: Exception) {
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    error = "Failed to load sample: ${e.localizedMessage}"
                )
            }
        }
    }

    fun getApiKey(): String = ApiKeyManager.getApiKey(getApplication())

    fun setApiKey(key: String) {
        ApiKeyManager.setApiKey(getApplication(), key)
        if (key.isNotBlank()) {
            _opStatus.value = UiOperationStatus(success = "Gemini API key saved securely.")
        }
    }

    fun clearApiKey() {
        ApiKeyManager.clearApiKey(getApplication())
        _opStatus.value = UiOperationStatus(success = "Gemini API key cleared.")
    }

    fun hasApiKey(): Boolean = ApiKeyManager.hasApiKey(getApplication())

    fun importPdf(uri: Uri) {
        if (!isPrivacyNoticeAccepted()) {
            pendingPdfUri = uri
            _showPrivacyDialog.value = true
            return
        }
        processPdfImport(uri)
    }

    private fun processPdfImport(uri: Uri) {
        viewModelScope.launch {
            _opStatus.value = UiOperationStatus(
                isLoading = true,
                message = "Rendering PDF pages (max width 1600px, JPEG quality 80)..."
            )
            val result = PdfTextExtractor.extractFromUri(getApplication(), uri)
            result.onSuccess { extracted ->
                try {
                    val subject = detectSubject(extracted.title, "")
                    val slidePaths = extracted.pages.map { it.imagePath }
                    val lectureId = repository.createLecture(
                        title = extracted.title,
                        subject = subject,
                        rawContent = "Medical Lecture Slides: ${extracted.title} (${extracted.pageCount} pages)",
                        pageCount = extracted.pageCount,
                        slideImages = slidePaths
                    )
                    _opStatus.value = UiOperationStatus(
                        isLoading = false,
                        success = "Rendered ${extracted.pageCount} slide images from '${extracted.title}' ready for multimodal analysis."
                    )
                    _currentScreen.value = Screen.LectureDetail(lectureId)
                } catch (e: Exception) {
                    _opStatus.value = UiOperationStatus(
                        isLoading = false,
                        error = "Failed to save lecture: ${e.localizedMessage}"
                    )
                }
            }.onFailure { err ->
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    error = "Could not parse PDF: ${err.localizedMessage}"
                )
            }
        }
    }

    fun addManualLecture(title: String, subject: String, content: String) {
        if (title.isBlank() || content.isBlank()) {
            _opStatus.value = UiOperationStatus(error = "Title and lecture text are required.")
            return
        }
        viewModelScope.launch {
            _opStatus.value = UiOperationStatus(isLoading = true, message = "Saving lecture...")
            try {
                val lectureId = repository.createLecture(
                    title = title.trim(),
                    subject = subject.trim().ifBlank { "General Medicine" },
                    rawContent = content.trim(),
                    pageCount = (content.length / 1500).coerceAtLeast(1)
                )
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    success = "Lecture saved successfully."
                )
                _currentScreen.value = Screen.LectureDetail(lectureId)
            } catch (e: Exception) {
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    error = "Save failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun runAcademicAnalysis(lectureId: Long) {
        val key = getApiKey()
        if (key.isBlank()) {
            _opStatus.value = UiOperationStatus(
                error = "Gemini API key is not configured. Please enter your key in Settings."
            )
            _showApiKeyDialog.value = true
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _opStatus.value = UiOperationStatus(
                isLoading = true,
                canCancel = true,
                message = "Analyzing lecture slides via multimodal vision...",
                progressText = "Preparing batches of 6 slides..."
            )

            val result = repository.analyzeLecture(lectureId, key) { cur, tot, msg ->
                _opStatus.value = _opStatus.value.copy(
                    progressText = msg,
                    message = msg
                )
            }

            val failed = repository.getFailedSlides(lectureId)

            if (result.isSuccess && failed.isEmpty()) {
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    success = "Academic analysis complete!"
                )
                _currentScreen.value = Screen.AcademicAnalysis(lectureId)
            } else if (result.isSuccess && failed.isNotEmpty()) {
                val failedNumbers = failed.map { it.slideNumber }.joinToString(", ")
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    message = "Analysis completed with ${failed.size} failed slide(s): $failedNumbers",
                    failedCount = failed.size,
                    failedLectureId = lectureId
                )
                _currentScreen.value = Screen.AcademicAnalysis(lectureId)
            } else {
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    error = result.exceptionOrNull()?.localizedMessage ?: "Analysis failed",
                    failedCount = failed.size,
                    failedLectureId = lectureId
                )
            }
        }
    }

    fun retryFailedSlides(lectureId: Long) {
        val key = getApiKey()
        if (key.isBlank()) {
            _opStatus.value = UiOperationStatus(
                error = "Gemini API key is not configured. Please enter your key in Settings."
            )
            _showApiKeyDialog.value = true
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _opStatus.value = UiOperationStatus(
                isLoading = true,
                canCancel = true,
                message = "Retrying failed slides with backoff...",
                progressText = "Retrying..."
            )

            val result = repository.retryFailedSlides(lectureId, key) { cur, tot, msg ->
                _opStatus.value = _opStatus.value.copy(
                    progressText = msg,
                    message = msg
                )
            }

            val failed = repository.getFailedSlides(lectureId)

            if (result.isSuccess && failed.isEmpty()) {
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    success = "All slides re-analyzed successfully!"
                )
                _currentScreen.value = Screen.AcademicAnalysis(lectureId)
            } else {
                val failedNumbers = failed.map { it.slideNumber }.joinToString(", ")
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    error = if (failed.isNotEmpty()) "${failed.size} slide(s) still failed ($failedNumbers)." else "Retry completed.",
                    failedCount = failed.size,
                    failedLectureId = lectureId
                )
            }
        }
    }

    fun generateAnkiCards(lectureId: Long, deckId: Long? = null) {
        val key = getApiKey()
        if (key.isBlank()) {
            _opStatus.value = UiOperationStatus(
                error = "Gemini API key is not configured. Please enter your key in Settings."
            )
            _showApiKeyDialog.value = true
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _opStatus.value = UiOperationStatus(
                isLoading = true,
                canCancel = true,
                message = "Generating USMLE cards, active recall & visual images...",
                progressText = "Processing sections..."
            )
            val mediaDir = File(getApplication<Application>().filesDir, "anki_media")
            val result = repository.generateCardsForLecture(lectureId, deckId, key, mediaDir) { cur, tot, msg ->
                _opStatus.value = _opStatus.value.copy(
                    progressText = msg,
                    message = msg
                )
            }

            result.onSuccess { generatedList ->
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    success = "Created ${generatedList.size} high-yield medical flashcards!"
                )
                _currentScreen.value = Screen.CardReview(lectureId = lectureId)
            }.onFailure { err ->
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    error = err.localizedMessage ?: "Card generation failed"
                )
            }
        }
    }

    fun saveEditedCard(card: FlashcardEntity) {
        viewModelScope.launch {
            try {
                if (card.id == 0L) {
                    repository.insertCustomCard(card)
                } else {
                    repository.updateCard(card)
                }
                _editingCard.value = null
                _opStatus.value = UiOperationStatus(success = "Card updated.")
            } catch (e: Exception) {
                _opStatus.value = UiOperationStatus(error = "Failed to save card: ${e.localizedMessage}")
            }
        }
    }

    fun deleteCard(card: FlashcardEntity) {
        viewModelScope.launch {
            try {
                repository.deleteCard(card)
                _opStatus.value = UiOperationStatus(success = "Card deleted.")
            } catch (e: Exception) {
                _opStatus.value = UiOperationStatus(error = "Delete failed: ${e.localizedMessage}")
            }
        }
    }

    fun deleteLecture(lectureId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteLecture(lectureId)
                _currentScreen.value = Screen.Home
                _opStatus.value = UiOperationStatus(success = "Lecture removed.")
            } catch (e: Exception) {
                _opStatus.value = UiOperationStatus(error = "Delete failed: ${e.localizedMessage}")
            }
        }
    }

    fun deleteDeck(deckId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteDeck(deckId)
                _currentScreen.value = Screen.Home
                _opStatus.value = UiOperationStatus(success = "Deck removed.")
            } catch (e: Exception) {
                _opStatus.value = UiOperationStatus(error = "Delete failed: ${e.localizedMessage}")
            }
        }
    }

    fun toggleCardInclusion(cardId: Long, current: Boolean) {
        viewModelScope.launch {
            repository.setCardIncluded(cardId, !current)
        }
    }

    fun toggleAllCardsInDeck(deckId: Long, includeAll: Boolean) {
        viewModelScope.launch {
            repository.setAllCardsIncluded(deckId, includeAll)
        }
    }

    fun exportDeckAsApkg(deckId: Long, deckName: String) {
        viewModelScope.launch {
            _opStatus.value = UiOperationStatus(isLoading = true, message = "Building compliant .apkg Anki package...")
            try {
                val cards = repository.getIncludedCardsForDeck(deckId)
                if (cards.isEmpty()) {
                    _opStatus.value = UiOperationStatus(error = "No included cards to export in this deck.")
                    return@launch
                }
                val file = AnkiPackageExporter.exportApkg(getApplication(), deckName, cards)
                _exportedFile.value = file
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    success = "Generated ${file.name} with ${cards.size} cards!"
                )
            } catch (e: Exception) {
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    error = "Export failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun exportDeckAsTsv(deckId: Long, deckName: String) {
        viewModelScope.launch {
            _opStatus.value = UiOperationStatus(isLoading = true, message = "Generating Anki-compatible TSV file...")
            try {
                val cards = repository.getIncludedCardsForDeck(deckId)
                if (cards.isEmpty()) {
                    _opStatus.value = UiOperationStatus(error = "No included cards to export in this deck.")
                    return@launch
                }
                val file = AnkiPackageExporter.exportTsv(getApplication(), deckName, cards)
                _exportedFile.value = file
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    success = "Generated ${file.name} with ${cards.size} cards!"
                )
            } catch (e: Exception) {
                _opStatus.value = UiOperationStatus(
                    isLoading = false,
                    error = "Export failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun getLectureById(lectureId: Long): LectureEntity? {
        return lectures.value.firstOrNull { it.id == lectureId }
    }

    fun getDeckById(deckId: Long): DeckEntity? {
        return decks.value.firstOrNull { it.id == deckId }
    }

    fun parseLectureAnalysis(lecture: LectureEntity): AcademicAnalysisResult? {
        return repository.parseStoredAnalysis(lecture.academicAnalysisJson)
    }

    fun getLectureSlidesFlow(lectureId: Long) = repository.getLectureSlidesFlow(lectureId)

    suspend fun getFailedSlideCount(lectureId: Long): Int = repository.getFailedSlides(lectureId).size

    private fun detectSubject(title: String, content: String): String {
        val lower = "$title $content".lowercase()
        return when {
            lower.contains("heart") || lower.contains("cardio") || lower.contains("artery") || lower.contains("infarct") -> "Cardiology"
            lower.contains("drug") || lower.contains("agonist") || lower.contains("receptor") || lower.contains("pharma") -> "Pharmacology"
            lower.contains("brain") || lower.contains("stroke") || lower.contains("neuro") || lower.contains("cortex") -> "Neurology"
            lower.contains("immune") || lower.contains("allergy") || lower.contains("antibody") || lower.contains("hypersensitiv") -> "Immunology"
            lower.contains("kidney") || lower.contains("renal") || lower.contains("nephr") -> "Nephrology"
            lower.contains("lung") || lower.contains("pulmon") || lower.contains("asthma") -> "Pulmonology"
            lower.contains("tumor") || lower.contains("cancer") || lower.contains("onco") -> "Oncology"
            lower.contains("bacteri") || lower.contains("virus") || lower.contains("microbio") -> "Microbiology"
            else -> "General Medicine"
        }
    }
}
