package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CardType {
    BASIC,
    CLOZE,
    IMAGE_OCCLUSION
}

enum class YieldLevel {
    VERY_HIGH,
    HIGH,
    MEDIUM
}

@Entity(
    tableName = "flashcards",
    indices = [
        Index(value = ["deckId"]),
        Index(value = ["lectureId"])
    ]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val lectureId: Long? = null,
    val cardType: CardType = CardType.BASIC,
    val front: String, // Front prompt or USMLE Vignette
    val back: String,  // Core Answer / active recall target
    val clozeText: String = "", // e.g. "The enzyme inhibited by allopurinol is {{c1::xanthine oxidase}}."
    val explanation: String = "", // Detailed pathophysiological rationale
    val clinicalCorrelation: String = "", // Board pearl / USMLE relevance
    val yieldLevel: YieldLevel = YieldLevel.HIGH,
    val tags: String = "", // Comma-separated: "Cardiology,Pathology,USMLE_Step1"
    val imagePath: String = "", // Local image file path or diagram uri if image occlusion
    val occlusionMasksJson: String = "", // JSON array of occlusion rectangles for diagram study
    val isIncluded: Boolean = true, // User checkbox to export or exclude
    val isDuplicate: Boolean = false,
    val duplicateSignature: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
