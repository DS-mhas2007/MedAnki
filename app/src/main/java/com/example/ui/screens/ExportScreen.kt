package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.local.entity.CardType
import com.example.data.local.entity.DeckEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.ui.theme.BasicPurple
import com.example.ui.theme.ClozeTeal
import com.example.ui.theme.OcclusionIndigo
import com.example.ui.theme.SuccessGreen
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    decks: List<DeckEntity>,
    allCards: List<FlashcardEntity>,
    initialDeckId: Long?,
    exportedFile: File?,
    onBack: () -> Unit,
    onExportApkg: (deckId: Long, deckName: String) -> Unit,
    onExportTsv: (deckId: Long, deckName: String) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedDeckId by remember {
        mutableLongStateOf(
            initialDeckId ?: decks.firstOrNull()?.id ?: 0L
        )
    }

    // 0 = .apkg (Anki Package), 1 = .txt (TSV)
    var selectedFormatIndex by remember { mutableIntStateOf(0) }

    val activeDeck = decks.firstOrNull { it.id == selectedDeckId } ?: decks.firstOrNull()
    val deckCards = allCards.filter { it.deckId == activeDeck?.id }
    val includedCards = deckCards.filter { it.isIncluded }

    val basicCount = includedCards.count { it.cardType == CardType.BASIC }
    val clozeCount = includedCards.count { it.cardType == CardType.CLOZE }
    val occlusionCount = includedCards.count { it.cardType == CardType.IMAGE_OCCLUSION }

    // Save File via SAF CreateDocument
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (selectedFormatIndex == 0) "application/octet-stream" else "text/plain"
        )
    ) { destinationUri: Uri? ->
        if (destinationUri != null && exportedFile != null && exportedFile.exists()) {
            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                    FileInputStream(exportedFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                Toast.makeText(context, "Saved ${exportedFile.name} successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Anki Deck") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Deck Selection
            Text("Select Deck to Export", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            if (decks.isEmpty()) {
                Text("No decks created yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    decks.forEach { deck ->
                        FilterChip(
                            selected = deck.id == activeDeck?.id,
                            onClick = { selectedDeckId = deck.id },
                            label = { Text(deck.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Export Format Selection
            Text("Export Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedCard(
                    onClick = { selectedFormatIndex = 0 },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selectedFormatIndex == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(".apkg", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("Recommended", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Anki Package (SQLite + Cloze models). Imports directly into AnkiDroid or Desktop Anki with 1 tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedCard(
                    onClick = { selectedFormatIndex = 1 },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selectedFormatIndex == 1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(".txt (TSV)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Standard tab-separated values with Anki headers for manual import into Anki collections.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Deck Summary Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Deck Content Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${activeDeck?.name ?: "No deck selected"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ready to export: ${includedCards.size} cards (of ${deckCards.size} total in deck)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TypeCountBadge(count = basicCount, label = "Basic", color = BasicPurple)
                        TypeCountBadge(count = clozeCount, label = "Cloze", color = ClozeTeal)
                        TypeCountBadge(count = occlusionCount, label = "Occlusion", color = OcclusionIndigo)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Generate Button
            Button(
                onClick = {
                    if (activeDeck != null) {
                        if (selectedFormatIndex == 0) {
                            onExportApkg(activeDeck.id, activeDeck.name)
                        } else {
                            onExportTsv(activeDeck.id, activeDeck.name)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("generate_export_button"),
                enabled = activeDeck != null && includedCards.isNotEmpty()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (selectedFormatIndex == 0) "Generate .apkg Package (${includedCards.size} Cards)"
                    else "Generate Anki .txt File (${includedCards.size} Cards)"
                )
            }

            // Exported Result Card
            if (exportedFile != null && exportedFile.exists()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = SuccessGreen.copy(alpha = 0.12f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "File Ready for Anki!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = exportedFile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Size: ${exportedFile.length() / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    shareExportedFile(context, exportedFile, selectedFormatIndex == 0)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_file_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share with Anki")
                            }

                            FilledTonalButton(
                                onClick = {
                                    saveDocumentLauncher.launch(exportedFile.name)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save to Storage")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Guidance Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How to Import into Anki",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. AnkiDroid (Android): Tap 'Share with Anki' above and select AnkiDroid. It will directly unpack and import your deck into AnkiDroid without any manual configuration!\n\n" +
                                "2. Anki Desktop (Mac/PC): Share via email, Google Drive, or Save to storage. Then double-click the .apkg file or click File > Import in Anki.\n\n" +
                                "3. The deck preserves USMLE tags, active recall prompts, and Cloze {{c1::...}} styling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TypeCountBadge(count: Int, label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

private fun shareExportedFile(context: Context, file: File, isApkg: Boolean) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val mimeType = if (isApkg) "application/octet-stream" else "text/plain"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with AnkiDroid or Share"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open share sheet: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
