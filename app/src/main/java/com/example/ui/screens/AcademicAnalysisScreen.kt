package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.LectureEntity
import com.example.data.remote.AcademicAnalysisResult
import com.example.data.remote.ExamQuestionItem
import com.example.ui.theme.HighYieldGold
import com.example.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicAnalysisScreen(
    lecture: LectureEntity,
    analysisResult: AcademicAnalysisResult?,
    onBack: () -> Unit,
    onGenerateCards: () -> Unit
) {
    BackHandler { onBack() }

    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    val revealedAnswers = remember { mutableStateMapOf<Int, Boolean>() }

    val sections = listOf(
        "All Overview",
        "Main Concepts (${analysisResult?.mainConcepts?.size ?: 0})",
        "Definitions (${analysisResult?.definitions?.size ?: 0})",
        "Mechanisms (${analysisResult?.mechanisms?.size ?: 0})",
        "Classifications (${analysisResult?.classifications?.size ?: 0})",
        "High-Yield Details (${analysisResult?.importantDetails?.size ?: 0})",
        "Clinical Pearls (${analysisResult?.clinicalCorrelations?.size ?: 0})",
        "Exam Questions (${analysisResult?.examQuestions?.size ?: 0})"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Academic Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(lecture.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = onGenerateCards,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("analysis_generate_cards_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Cards")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (analysisResult == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Analysis Available", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Run the academic analysis on this lecture first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Section Filter Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sections.indices.toList()) { index ->
                        FilterChip(
                            selected = selectedSectionIndex == index,
                            onClick = { selectedSectionIndex = index },
                            label = { Text(sections[index], style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Executive Summary
                    if (selectedSectionIndex == 0 && analysisResult.summary.isNotBlank()) {
                        item {
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Executive Academic Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(analysisResult.summary, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // 1. Main Concepts
                    if (selectedSectionIndex == 0 || selectedSectionIndex == 1) {
                        item {
                            SectionHeader(icon = Icons.Default.Lightbulb, title = "Main Concepts & Pathophysiology")
                        }
                        items(analysisResult.mainConcepts) { concept ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(concept.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(concept.explanation, style = MaterialTheme.typography.bodyMedium)
                                    if (concept.highYieldPearl.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(HighYieldGold.copy(alpha = 0.15f))
                                                .padding(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = HighYieldGold, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Board Pearl: ${concept.highYieldPearl}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    // 2. Definitions
                    if (selectedSectionIndex == 0 || selectedSectionIndex == 2) {
                        item {
                            SectionHeader(icon = Icons.AutoMirrored.Filled.MenuBook, title = "Definitions & Core Terminology")
                        }
                        items(analysisResult.definitions) { defItem ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(defItem.term, fontWeight = FontWeight.Bold) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(defItem.definition, style = MaterialTheme.typography.bodyMedium)
                                    if (defItem.context.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Clinical context: ${defItem.context}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    // 3. Mechanisms
                    if (selectedSectionIndex == 0 || selectedSectionIndex == 3) {
                        item {
                            SectionHeader(icon = Icons.Default.Timeline, title = "Mechanisms & Step-by-Step Pathways")
                        }
                        items(analysisResult.mechanisms) { mech ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(mech.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    mech.pathwaySteps.forEachIndexed { stepIdx, step ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${stepIdx + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(step, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (mech.clinicalRelevance.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Clinical relevance: ${mech.clinicalRelevance}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    // 4. Classifications
                    if (selectedSectionIndex == 0 || selectedSectionIndex == 4) {
                        item {
                            SectionHeader(icon = Icons.Default.Bookmark, title = "Classifications & Diagnostic Criteria")
                        }
                        items(analysisResult.classifications) { cl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(cl.category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (cl.subcategories.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            cl.subcategories.forEach { sub ->
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(sub, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                    }
                                    if (cl.criteria.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Criteria: ${cl.criteria}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    // 5. Important Details & Pitfalls
                    if (selectedSectionIndex == 0 || selectedSectionIndex == 5) {
                        item {
                            SectionHeader(icon = Icons.Default.Warning, title = "High-Yield Traps & Pitfalls")
                        }
                        items(analysisResult.importantDetails) { detail ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(detail.topic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(detail.point, style = MaterialTheme.typography.bodyMedium)
                                    if (detail.pitfalls.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "⚠️ Watch Out: ${detail.pitfalls}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    // 6. Clinical Correlations
                    if (selectedSectionIndex == 0 || selectedSectionIndex == 6) {
                        item {
                            SectionHeader(icon = Icons.Default.LocalHospital, title = "Clinical Correlations & Classic Signs")
                        }
                        items(analysisResult.clinicalCorrelations) { corr ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(corr.condition, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Signs / Triad: ${corr.classicTriadOrSigns}", style = MaterialTheme.typography.bodyMedium)
                                    if (corr.usmlePearl.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("USMLE Pearl: ${corr.usmlePearl}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = HighYieldGold)
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    // 7. Exam Questions
                    if (selectedSectionIndex == 0 || selectedSectionIndex == 7) {
                        item {
                            SectionHeader(icon = Icons.Default.Quiz, title = "USMLE-Style Board Vignettes")
                        }
                        items(analysisResult.examQuestions.indices.toList()) { qIdx ->
                            val q = analysisResult.examQuestions[qIdx]
                            val isRevealed = revealedAnswers[qIdx] == true

                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("Clinical Vignette #${qIdx + 1}") }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(q.vignette, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(q.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    q.options.forEach { opt ->
                                        Text(
                                            text = opt,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = { revealedAnswers[qIdx] = !isRevealed }
                                    ) {
                                        Text(if (isRevealed) "Hide Answer" else "Reveal Answer & Explanation")
                                    }

                                    if (isRevealed) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SuccessGreen.copy(alpha = 0.12f))
                                                .padding(12.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Correct: ${q.correctOption}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SuccessGreen
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = q.explanation,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
