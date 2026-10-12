package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.SlideStatus
import com.example.ui.screens.AcademicAnalysisScreen
import com.example.ui.screens.ApiKeySettingsDialog
import com.example.ui.screens.CardEditorDialog
import com.example.ui.screens.CardReviewScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LectureDetailScreen
import com.example.ui.screens.PdfUploadDialog
import com.example.ui.screens.PracticeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MedStudyViewModel
import com.example.ui.viewmodel.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MedStudyApp()
            }
        }
    }
}

@Composable
fun MedStudyApp(viewModel: MedStudyViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val lectures by viewModel.lectures.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val allCards by viewModel.allCards.collectAsStateWithLifecycle()
    val opStatus by viewModel.opStatus.collectAsStateWithLifecycle()
    val editingCard by viewModel.editingCard.collectAsStateWithLifecycle()
    val exportedFile by viewModel.exportedFile.collectAsStateWithLifecycle()
    val showApiKeyDialog by viewModel.showApiKeyDialog.collectAsStateWithLifecycle()
    val showPrivacyDialog by viewModel.showPrivacyDialog.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddLectureDialog by remember { mutableStateOf(false) }

    LaunchedEffect(opStatus.error) {
        opStatus.error?.let { err ->
            val hasKey = viewModel.hasApiKey()
            val actionLabel = if (!hasKey) "Settings" else null
            val result = snackbarHostState.showSnackbar(
                message = err,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && !hasKey) {
                viewModel.openApiKeyDialog()
            }
            viewModel.clearStatus()
        }
    }

    LaunchedEffect(opStatus.success) {
        opStatus.success?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { _ ->
        when (val screen = currentScreen) {
            is Screen.Home -> {
                HomeScreen(
                    lectures = lectures,
                    decks = decks,
                    totalCardsCount = allCards.size,
                    onNavigate = { viewModel.navigateTo(it) },
                    onOpenAddLecture = { showAddLectureDialog = true },
                    onOpenSettings = { viewModel.openApiKeyDialog() },
                    onDeleteLecture = { viewModel.deleteLecture(it) },
                    onDeleteDeck = { viewModel.deleteDeck(it) }
                )
            }

            is Screen.LectureDetail -> {
                val lecture = viewModel.getLectureById(screen.lectureId)
                if (lecture != null) {
                    val slides by viewModel.getLectureSlidesFlow(lecture.id).collectAsStateWithLifecycle(emptyList())
                    val failedSlideNumbers = slides.filter { it.status == SlideStatus.FAILED }.map { it.slideNumber }

                    LectureDetailScreen(
                        lecture = lecture,
                        failedSlideNumbers = failedSlideNumbers,
                        onBack = { viewModel.navigateTo(Screen.Home) },
                        onRunAnalysis = { viewModel.runAcademicAnalysis(lecture.id) },
                        onRetryFailedSlides = { viewModel.retryFailedSlides(lecture.id) },
                        onGenerateCards = { viewModel.generateAnkiCards(lecture.id) },
                        onViewAnalysis = { viewModel.navigateTo(Screen.AcademicAnalysis(lecture.id)) },
                        onViewCards = { viewModel.navigateTo(Screen.CardReview(lectureId = lecture.id)) },
                        onDelete = { viewModel.deleteLecture(lecture.id) }
                    )
                } else {
                    viewModel.navigateTo(Screen.Home)
                }
            }

            is Screen.AcademicAnalysis -> {
                val lecture = viewModel.getLectureById(screen.lectureId)
                if (lecture != null) {
                    val analysis = viewModel.parseLectureAnalysis(lecture)
                    AcademicAnalysisScreen(
                        lecture = lecture,
                        analysisResult = analysis,
                        onBack = { viewModel.navigateTo(Screen.LectureDetail(lecture.id)) },
                        onGenerateCards = { viewModel.generateAnkiCards(lecture.id) }
                    )
                } else {
                    viewModel.navigateTo(Screen.Home)
                }
            }

            is Screen.CardReview -> {
                val matchingCards = when {
                    screen.deckId != null -> allCards.filter { it.deckId == screen.deckId }
                    screen.lectureId != null -> allCards.filter { it.lectureId == screen.lectureId }
                    else -> allCards
                }
                val deck = screen.deckId?.let { viewModel.getDeckById(it) }
                val lecture = screen.lectureId?.let { viewModel.getLectureById(it) }
                val title = deck?.name ?: lecture?.title ?: "All Flashcards"

                CardReviewScreen(
                    title = title,
                    cards = matchingCards,
                    deckId = screen.deckId,
                    onBack = {
                        if (screen.lectureId != null) {
                            viewModel.navigateTo(Screen.LectureDetail(screen.lectureId))
                        } else {
                            viewModel.navigateTo(Screen.Home)
                        }
                    },
                    onEditCard = { viewModel.setEditingCard(it) },
                    onAddNewCard = {
                        val defaultDeckId = screen.deckId ?: decks.firstOrNull()?.id ?: 1L
                        viewModel.setEditingCard(
                            FlashcardEntity(
                                deckId = defaultDeckId,
                                lectureId = screen.lectureId,
                                front = "",
                                back = ""
                            )
                        )
                    },
                    onDeleteCard = { viewModel.deleteCard(it) },
                    onToggleCardInclusion = { cardId, current ->
                        viewModel.toggleCardInclusion(cardId, current)
                    },
                    onToggleAllInclusion = { includeAll ->
                        screen.deckId?.let { viewModel.toggleAllCardsInDeck(it, includeAll) }
                    },
                    onStartPractice = {
                        viewModel.navigateTo(Screen.Practice(deckId = screen.deckId, lectureId = screen.lectureId))
                    },
                    onExportDeck = {
                        viewModel.navigateTo(Screen.Export(deckId = screen.deckId))
                    }
                )
            }

            is Screen.Practice -> {
                val cardsToStudy = when {
                    screen.deckId != null -> allCards.filter { it.deckId == screen.deckId && it.isIncluded }
                    screen.lectureId != null -> allCards.filter { it.lectureId == screen.lectureId && it.isIncluded }
                    else -> allCards.filter { it.isIncluded }
                }
                val deck = screen.deckId?.let { viewModel.getDeckById(it) }
                val title = deck?.name ?: "Practice Session"

                PracticeScreen(
                    title = title,
                    cards = cardsToStudy,
                    onBack = {
                        if (screen.deckId != null) {
                            viewModel.navigateTo(Screen.CardReview(deckId = screen.deckId))
                        } else if (screen.lectureId != null) {
                            viewModel.navigateTo(Screen.LectureDetail(screen.lectureId))
                        } else {
                            viewModel.navigateTo(Screen.Home)
                        }
                    },
                    onExport = {
                        viewModel.navigateTo(Screen.Export(deckId = screen.deckId))
                    }
                )
            }

            is Screen.Export -> {
                ExportScreen(
                    decks = decks,
                    allCards = allCards,
                    initialDeckId = screen.deckId,
                    exportedFile = exportedFile,
                    onBack = { viewModel.navigateTo(Screen.Home) },
                    onExportApkg = { deckId, deckName ->
                        viewModel.exportDeckAsApkg(deckId, deckName)
                    },
                    onExportTsv = { deckId, deckName ->
                        viewModel.exportDeckAsTsv(deckId, deckName)
                    }
                )
            }
        }

        // Add Lecture Dialog
        if (showAddLectureDialog) {
            PdfUploadDialog(
                onDismiss = { showAddLectureDialog = false },
                onPdfSelected = { uri -> viewModel.importPdf(uri) },
                onManualSubmit = { title, subject, content ->
                    viewModel.addManualLecture(title, subject, content)
                },
                onSampleSelected = { sample ->
                    viewModel.loadSampleLecture(sample)
                }
            )
        }

        // Privacy Notice Dialog (One-time before first upload)
        if (showPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPrivacyNotice() },
                icon = {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Cloud AI Processing Notice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "To extract high-resolution histology micrographs, anatomical diagrams, and complex pathways, your lecture slide images and transcripts are transmitted to Google's Gemini Multimodal API.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "• Transmitted data is used solely to generate your flashcards.\n• Generated decks and structured data remain stored in your private on-device Room database.\n• This application is not fully offline and requires your personal Gemini API key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.acceptPrivacyNotice() },
                        modifier = Modifier.testTag("accept_privacy_button")
                    ) {
                        Text("I Understand & Agree")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissPrivacyNotice() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Settings / API Key Dialog
        if (showApiKeyDialog) {
            ApiKeySettingsDialog(
                currentKey = viewModel.getApiKey(),
                onDismiss = { viewModel.closeApiKeyDialog() },
                onSaveKey = { newKey ->
                    viewModel.setApiKey(newKey)
                }
            )
        }

        // Edit Card Dialog
        editingCard?.let { card ->
            val defaultDeckId = card.deckId.takeIf { it > 0 } ?: (decks.firstOrNull()?.id ?: 1L)
            CardEditorDialog(
                initialCard = if (card.id != 0L) card else null,
                defaultDeckId = defaultDeckId,
                defaultLectureId = card.lectureId,
                onDismiss = { viewModel.setEditingCard(null) },
                onSave = { updated ->
                    viewModel.saveEditedCard(updated)
                }
            )
        }

        // Loading Overlay Dialog with progress ("Analyzing slides 9-16 of 52") and cancel button
        if (opStatus.isLoading) {
            Dialog(onDismissRequest = {
                if (opStatus.canCancel) viewModel.cancelActiveOperation()
            }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = opStatus.message.ifBlank { "Processing..." },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        if (opStatus.progressText.isNotBlank() && opStatus.progressText != opStatus.message) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = opStatus.progressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (opStatus.canCancel) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelActiveOperation() },
                                modifier = Modifier.testTag("cancel_operation_button")
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}
