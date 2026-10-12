package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CardType
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.YieldLevel

@Composable
fun CardEditorDialog(
    initialCard: FlashcardEntity?,
    defaultDeckId: Long,
    defaultLectureId: Long?,
    onDismiss: () -> Unit,
    onSave: (FlashcardEntity) -> Unit
) {
    var cardType by remember { mutableStateOf(initialCard?.cardType ?: CardType.BASIC) }
    var yieldLevel by remember { mutableStateOf(initialCard?.yieldLevel ?: YieldLevel.HIGH) }
    var front by remember { mutableStateOf(initialCard?.front ?: "") }
    var back by remember { mutableStateOf(initialCard?.back ?: "") }
    var clozeText by remember { mutableStateOf(initialCard?.clozeText ?: "") }
    var explanation by remember { mutableStateOf(initialCard?.explanation ?: "") }
    var clinicalCorrelation by remember { mutableStateOf(initialCard?.clinicalCorrelation ?: "") }
    var tags by remember { mutableStateOf(initialCard?.tags ?: "Cardiology,USMLE_Step1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialCard != null) "Edit Flashcard" else "New Medical Flashcard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Card Type Selector
                Text("Card Type", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = cardType == CardType.BASIC,
                        onClick = { cardType = CardType.BASIC },
                        label = { Text("Basic") }
                    )
                    FilterChip(
                        selected = cardType == CardType.CLOZE,
                        onClick = { cardType = CardType.CLOZE },
                        label = { Text("Cloze Deletion") }
                    )
                    FilterChip(
                        selected = cardType == CardType.IMAGE_OCCLUSION,
                        onClick = { cardType = CardType.IMAGE_OCCLUSION },
                        label = { Text("Occlusion") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Yield Level Selector
                Text("Yield Level", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = yieldLevel == YieldLevel.VERY_HIGH,
                        onClick = { yieldLevel = YieldLevel.VERY_HIGH },
                        label = { Text("Very High") }
                    )
                    FilterChip(
                        selected = yieldLevel == YieldLevel.HIGH,
                        onClick = { yieldLevel = YieldLevel.HIGH },
                        label = { Text("High") }
                    )
                    FilterChip(
                        selected = yieldLevel == YieldLevel.MEDIUM,
                        onClick = { yieldLevel = YieldLevel.MEDIUM },
                        label = { Text("Medium") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Image display if card has attached visual
                val cardImg = initialCard?.imagePath?.ifBlank {
                    Regex("""<img\s+[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(front)?.groupValues?.getOrNull(1) ?: ""
                } ?: Regex("""<img\s+[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(front)?.groupValues?.getOrNull(1) ?: ""

                if (cardImg.isNotBlank()) {
                    Text("Associated Visual / Slide Image", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    CardImagePreview(imagePath = cardImg)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Front (Question / Clinical Vignette)
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text(if (cardType == CardType.CLOZE) "Card Title / Prompt" else "Front (Active Recall Vignette)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_front_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Cloze Text (if Cloze)
                if (cardType == CardType.CLOZE) {
                    OutlinedTextField(
                        value = clozeText,
                        onValueChange = { clozeText = it },
                        label = { Text("Cloze Text with {{c1::...}}") },
                        placeholder = { Text("e.g. In MI, {{c1::Troponin}} peaks at 24h while {{c2::CK-MB}} tests re-infarction.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("card_cloze_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                clozeText += " {{c1::target}} "
                            }
                        ) {
                            Text("+ {{c1::...}}", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                clozeText += " {{c2::target}} "
                            }
                        ) {
                            Text("+ {{c2::...}}", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Back (Answer)
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text("Back (Answer / Key Concept)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_back_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Pathophysiological Explanation
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Pathophysiological Explanation") },
                    placeholder = { Text("Detailed rationale explaining the underlying mechanism...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("card_explanation_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Clinical Correlation / Board Pearl
                OutlinedTextField(
                    value = clinicalCorrelation,
                    onValueChange = { clinicalCorrelation = it },
                    label = { Text("Clinical Pearl / USMLE Relevance") },
                    placeholder = { Text("e.g. Associated with Beck's triad; emergency pericardiocentesis required") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("Cardiology,Pathology,USMLE_Step1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val entity = (initialCard ?: FlashcardEntity(
                        deckId = defaultDeckId,
                        lectureId = defaultLectureId,
                        front = front,
                        back = back
                    )).copy(
                        cardType = cardType,
                        yieldLevel = yieldLevel,
                        front = front.trim(),
                        back = back.trim(),
                        clozeText = if (cardType == CardType.CLOZE) clozeText.trim() else "",
                        explanation = explanation.trim(),
                        clinicalCorrelation = clinicalCorrelation.trim(),
                        tags = tags.trim()
                    )
                    onSave(entity)
                },
                modifier = Modifier.testTag("save_card_button"),
                enabled = (cardType == CardType.CLOZE && clozeText.isNotBlank()) || (front.isNotBlank() && back.isNotBlank())
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Card")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
