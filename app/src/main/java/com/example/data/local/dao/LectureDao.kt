package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LectureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureDao {
    @Query("SELECT * FROM lectures ORDER BY createdAt DESC")
    fun getAllLectures(): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE id = :id")
    suspend fun getLectureById(id: Long): LectureEntity?

    @Query("SELECT * FROM lectures WHERE id = :id")
    fun getLectureFlow(id: Long): Flow<LectureEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: LectureEntity): Long

    @Update
    suspend fun updateLecture(lecture: LectureEntity)

    @Delete
    suspend fun deleteLecture(lecture: LectureEntity)

    @Query("DELETE FROM lectures WHERE id = :id")
    suspend fun deleteLectureById(id: Long)

    @Query("UPDATE lectures SET cardCount = (SELECT COUNT(*) FROM flashcards WHERE lectureId = :lectureId) WHERE id = :lectureId")
    suspend fun refreshCardCount(lectureId: Long)
}
