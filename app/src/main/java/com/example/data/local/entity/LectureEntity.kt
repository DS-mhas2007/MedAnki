package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lectures")
data class LectureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val subject: String, // e.g. Cardiology, Pharmacology, Neurology, Pathology
    val rawContent: String,
    val pageCount: Int = 1,
    val summary: String = "",
    val academicAnalysisJson: String = "", // Stored JSON with concepts, mechanisms, correlations, exam questions
    val slideImagesJson: String = "", // JSON array of rendered slide image file paths
    val createdAt: Long = System.currentTimeMillis(),
    val cardCount: Int = 0
)
