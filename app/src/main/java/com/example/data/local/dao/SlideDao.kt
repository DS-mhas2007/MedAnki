package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LectureSlideEntity
import com.example.data.local.entity.SlideStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SlideDao {
    @Query("SELECT * FROM lecture_slides WHERE lectureId = :lectureId ORDER BY slideNumber ASC")
    fun getSlidesForLectureFlow(lectureId: Long): Flow<List<LectureSlideEntity>>

    @Query("SELECT * FROM lecture_slides WHERE lectureId = :lectureId ORDER BY slideNumber ASC")
    suspend fun getSlidesForLecture(lectureId: Long): List<LectureSlideEntity>

    @Query("SELECT * FROM lecture_slides WHERE lectureId = :lectureId AND slideNumber = :slideNumber LIMIT 1")
    suspend fun getSlide(lectureId: Long, slideNumber: Int): LectureSlideEntity?

    @Query("SELECT * FROM lecture_slides WHERE lectureId = :lectureId AND status = 'FAILED' ORDER BY slideNumber ASC")
    suspend fun getFailedSlides(lectureId: Long): List<LectureSlideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlides(slides: List<LectureSlideEntity>)

    @Update
    suspend fun updateSlide(slide: LectureSlideEntity)

    @Query("UPDATE lecture_slides SET status = :status, errorMessage = :error, updatedAt = :updatedAt WHERE lectureId = :lectureId AND slideNumber = :slideNumber")
    suspend fun updateSlideStatus(
        lectureId: Long,
        slideNumber: Int,
        status: SlideStatus,
        error: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM lecture_slides WHERE lectureId = :lectureId")
    suspend fun deleteSlidesForLecture(lectureId: Long)
}
