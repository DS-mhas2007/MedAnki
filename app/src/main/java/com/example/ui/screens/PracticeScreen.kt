package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CardType
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.YieldLevel
import com.example.ui.theme.BasicPurple
import com.example.ui.theme.ClozeTeal
import com.example.ui.theme.HighYieldGold
import com.example.ui.theme.OcclusionIndigo
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VeryHighYieldRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    title: String,
    cards: List<FlashcardEntity>,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    BackHandler { onBack() }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }
    var sessionFinished by remember { mutableStateOf(false) }

    val currentCard = cards.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Active Recall Study", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (cards.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No cards to practice in this deck.", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
            } else if (sessionFinished) {
                // Session Complete Screen
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Session Complete!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reviewed ${cards.size} medical flashcards.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                currentIndex = 0
                                isFlipped = false
                                sessionFinished = false
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restart")
                        }

                        Button(
                            onClick = onExport,
                            modifier = Modifier.testTag("practice_export_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export to Anki")
                        }
                    }
                }
            } else if (currentCard != null) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / cards.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card ${currentIndex + 1} of ${cards.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val (typeColor, typeLabel) = when (currentCard.cardType) {
                            CardType.CLOZE -> ClozeTeal to "CLOZE"
                            CardType.IMAGE_OCCLUSION -> OcclusionIndigo to "OCCLUSION"
                            CardType.BASIC -> BasicPurple to "BASIC"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(typeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = typeColor, fontWeight = FontWeight.Bold)
                        }

                        val yieldColor = when (currentCard.yieldLevel) {
                            YieldLevel.VERY_HIGH -> VeryHighYieldRed
                            YieldLevel.HIGH -> HighYieldGold
                            YieldLevel.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(yieldColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(currentCard.yieldLevel.name, style = MaterialTheme.typography.labelSmall, color = yieldColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Flashcard Canvas / Box
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isFlipped = !isFlipped }
                        .testTag("practice_flashcard"),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "CLINICAL VIGNETTE / PROMPT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Diagram or slide image if present
                        val cardImg = currentCard.imagePath.ifBlank {
                            Regex("""<img\s+src=["']([^"']+)["']""").find(currentCard.front)?.groupValues?.getOrNull(1) ?: ""
                        }
                        if (cardImg.isNotBlank()) {
                            CardImagePreview(imagePath = cardImg)
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        val cleanFront = currentCard.front.replace(Regex("""<img\s+[^>]+>"""), "").trim()
                        if (currentCard.cardType == CardType.CLOZE && currentCard.clozeText.isNotBlank()) {
                            Text(
                                text = if (isFlipped) {
                                    formatClozeRevealed(currentCard.clozeText)
                                } else {
                                    formatClozeHidden(currentCard.clozeText)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.3
                            )
                        } else {
                            Text(
                                text = cleanFront.ifBlank { currentCard.front },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Answer and Explanation Revealed
                        AnimatedVisibility(
                            visible = isFlipped,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(20.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                        .padding(14.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "TARGET RECALL:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentCard.back,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (currentCard.explanation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Pathophysiological Rationale:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentCard.explanation,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (currentCard.clinicalCorrelation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = HighYieldGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "USMLE Pearl: ${currentCard.clinicalCorrelation}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = HighYieldGold
                                        )
                                    }
                                }
                            }
                        }

                        if (!isFlipped) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Tap card or button below to reveal answer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action: Flip or Anki Grade
                if (!isFlipped) {
                    Button(
                        onClick = { isFlipped = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("show_answer_button")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show Answer")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (currentIndex + 1 < cards.size) {
                                    currentIndex++
                                    isFlipped = false
                                } else {
                                    sessionFinished = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VeryHighYieldRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Again")
                        }

                        Button(
                            onClick = {
                                if (currentIndex + 1 < cards.size) {
                                    currentIndex++
                                    isFlipped = false
                                } else {
                                    sessionFinished = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HighYieldGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hard")
                        }

                        Button(
                            onClick = {
                                if (currentIndex + 1 < cards.size) {
                                    currentIndex++
                                    isFlipped = false
                                } else {
                                    sessionFinished = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Good")
                        }

                        Button(
                            onClick = {
                                if (currentIndex + 1 < cards.size) {
                                    currentIndex++
                                    isFlipped = false
                                } else {
                                    sessionFinished = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Easy")
                        }
                    }
                }
            }
        }
    }
}

private fun formatClozeHidden(text: String): androidx.compose.ui.text.AnnotatedString {
    val regex = Regex("""\{\{c\d+::(.*?)\}\}""")
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        val matches = regex.findAll(text)
        for (match in matches) {
            val range = match.range
            append(text.substring(lastIndex, range.first))
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = ClozeTeal,
                    background = ClozeTeal.copy(alpha = 0.2f)
                )
            ) {
                append(" [...] ")
            }
            lastIndex = range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    return annotated
}

private fun formatClozeRevealed(text: String): androidx.compose.ui.text.AnnotatedString {
    val regex = Regex("""\{\{c\d+::(.*?)\}\}""")
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        val matches = regex.findAll(text)
        for (match in matches) {
            val range = match.range
            append(text.substring(lastIndex, range.first))
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = ClozeTeal,
                    background = ClozeTeal.copy(alpha = 0.2f)
                )
            ) {
                append(" [${match.groupValues[1]}] ")
            }
            lastIndex = range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    return annotated
}
