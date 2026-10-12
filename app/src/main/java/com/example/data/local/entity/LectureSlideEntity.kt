package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SlideStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

@Entity(
    tableName = "lecture_slides",
    indices = [
        Index(value = ["lectureId", "slideNumber"], unique = true),
        Index(value = ["lectureId"])
    ]
)
data class LectureSlideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lectureId: Long,
    val slideNumber: Int,
    val imagePath: String,
    val title: String = "",
    val extractedText: String = "",
    val structuredJson: String = "", // Stored JSON representing SlideStructuredData
    val status: SlideStatus = SlideStatus.PENDING,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
