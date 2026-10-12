package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CardType
import com.example.data.local.entity.DeckEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.YieldLevel
import com.example.ui.theme.BasicPurple
import com.example.ui.theme.ClozeTeal
import com.example.ui.theme.HighYieldGold
import com.example.ui.theme.OcclusionIndigo
import com.example.ui.theme.VeryHighYieldRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardReviewScreen(
    title: String,
    cards: List<FlashcardEntity>,
    deckId: Long?,
    onBack: () -> Unit,
    onEditCard: (FlashcardEntity) -> Unit,
    onAddNewCard: () -> Unit,
    onDeleteCard: (FlashcardEntity) -> Unit,
    onToggleCardInclusion: (cardId: Long, current: Boolean) -> Unit,
    onToggleAllInclusion: (Boolean) -> Unit,
    onStartPractice: () -> Unit,
    onExportDeck: () -> Unit
) {
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<CardType?>(null) }
    var selectedYieldFilter by remember { mutableStateOf<YieldLevel?>(null) }
    var showOnlyIncluded by remember { mutableStateOf(false) }

    val filteredCards = cards.filter { card ->
        val matchesSearch = searchQuery.isBlank() ||
                card.front.contains(searchQuery, ignoreCase = true) ||
                card.back.contains(searchQuery, ignoreCase = true) ||
                card.tags.contains(searchQuery, ignoreCase = true) ||
                card.explanation.contains(searchQuery, ignoreCase = true)

        val matchesType = selectedTypeFilter == null || card.cardType == selectedTypeFilter
        val matchesYield = selectedYieldFilter == null || card.yieldLevel == selectedYieldFilter
        val matchesIncluded = !showOnlyIncluded || card.isIncluded

        matchesSearch && matchesType && matchesYield && matchesIncluded
    }

    val includedCount = cards.count { it.isIncluded }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${cards.size} Total • $includedCount Selected for Anki",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddNewCard, modifier = Modifier.testTag("add_card_action")) {
                        Icon(Icons.Default.Add, contentDescription = "Add card")
                    }
                }
            )
        },
        bottomBar = {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onStartPractice,
                        modifier = Modifier.weight(1f),
                        enabled = cards.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Practice (${cards.size})")
                    }

                    Button(
                        onClick = onExportDeck,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_anki_button"),
                        enabled = includedCount > 0
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Anki ($includedCount)")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search flashcards, tags, explanations...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true
            )

            // Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTypeFilter == null,
                        onClick = { selectedTypeFilter = null },
                        label = { Text("All Types") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == CardType.BASIC,
                        onClick = {
                            selectedTypeFilter = if (selectedTypeFilter == CardType.BASIC) null else CardType.BASIC
                        },
                        label = { Text("Basic") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == CardType.CLOZE,
                        onClick = {
                            selectedTypeFilter = if (selectedTypeFilter == CardType.CLOZE) null else CardType.CLOZE
                        },
                        label = { Text("Cloze") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == CardType.IMAGE_OCCLUSION,
                        onClick = {
                            selectedTypeFilter = if (selectedTypeFilter == CardType.IMAGE_OCCLUSION) null else CardType.IMAGE_OCCLUSION
                        },
                        label = { Text("Occlusion") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedYieldFilter == YieldLevel.VERY_HIGH,
                        onClick = {
                            selectedYieldFilter = if (selectedYieldFilter == YieldLevel.VERY_HIGH) null else YieldLevel.VERY_HIGH
                        },
                        label = { Text("Very High Yield") }
                    )
                }
                item {
                    FilterChip(
                        selected = showOnlyIncluded,
                        onClick = { showOnlyIncluded = !showOnlyIncluded },
                        label = { Text("Selected Only") }
                    )
                }
            }

            // Bulk actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredCards.size} cards shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onToggleAllInclusion(true) }) {
                        Text("Select All", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { onToggleAllInclusion(false) }) {
                        Text("Deselect All", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Cards List
            if (filteredCards.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Style,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Matching Cards", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Try clearing search or filter chips.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(filteredCards, key = { it.id }) { card ->
                        CardItemView(
                            card = card,
                            onToggleInclusion = { onToggleCardInclusion(card.id, card.isIncluded) },
                            onEdit = { onEditCard(card) },
                            onDelete = { onDeleteCard(card) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CardItemView(
    card: FlashcardEntity,
    onToggleInclusion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_item_${card.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (card.isIncluded) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (card.isIncluded) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Type badge, Yield badge, Inclusion Checkbox, Edit / Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Badge
                    val (typeColor, typeLabel) = when (card.cardType) {
                        CardType.CLOZE -> ClozeTeal to "CLOZE"
                        CardType.IMAGE_OCCLUSION -> OcclusionIndigo to "OCCLUSION"
                        CardType.BASIC -> BasicPurple to "BASIC"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Yield Badge
                    val yieldColor = when (card.yieldLevel) {
                        YieldLevel.VERY_HIGH -> VeryHighYieldRed
                        YieldLevel.HIGH -> HighYieldGold
                        YieldLevel.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(yieldColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = card.yieldLevel.name.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = yieldColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = card.isIncluded,
                        onCheckedChange = { onToggleInclusion() }
                    )
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit card", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete card",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Card Image if present
            val cardImg = card.imagePath.ifBlank {
                Regex("""<img\s+src=["']([^"']+)["']""").find(card.front)?.groupValues?.getOrNull(1) ?: ""
            }
            if (cardImg.isNotBlank()) {
                CardImagePreview(imagePath = cardImg)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Card Body
            val cleanFrontText = card.front.replace(Regex("""<img\s+[^>]+>"""), "").trim()
            if (card.cardType == CardType.CLOZE && card.clozeText.isNotBlank()) {
                Text(
                    text = formatClozeText(card.clozeText),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = cleanFrontText.ifBlank { card.front },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Back / Target Answer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "Target Answer:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = card.back,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Pathophysiological Explanation
            if (card.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Explanation: ${card.explanation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Clinical Pearl
            if (card.clinicalCorrelation.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = HighYieldGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "USMLE Pearl: ${card.clinicalCorrelation}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = HighYieldGold
                    )
                }
            }

            // Tags
            if (card.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    card.tags.split(",").filter { it.isNotBlank() }.take(4).forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatClozeText(text: String): androidx.compose.ui.text.AnnotatedString {
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
                    background = ClozeTeal.copy(alpha = 0.15f)
                )
            ) {
                append("[${match.groupValues[1]}]")
            }
            lastIndex = range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    return annotated
}

@Composable
fun CardImagePreview(imagePath: String, modifier: Modifier = Modifier) {
    if (imagePath.isBlank()) return
    val context = LocalContext.current
    val file = remember(imagePath) {
        val f = File(imagePath)
        if (f.exists()) f
        else {
            val inMedia = File(context.filesDir, "anki_media/$imagePath")
            if (inMedia.exists()) inMedia
            else {
                val inSlides = File(context.filesDir, "slides/$imagePath")
                if (inSlides.exists()) inSlides else null
            }
        }
    }
    if (file != null && file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = "Medical Diagram",
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

