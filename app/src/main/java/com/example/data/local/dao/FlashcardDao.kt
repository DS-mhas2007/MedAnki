package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY id DESC")
    fun getAllCards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY id ASC")
    fun getCardsForDeck(deckId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND isIncluded = 1 ORDER BY id ASC")
    suspend fun getIncludedCardsForDeck(deckId: Long): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE lectureId = :lectureId ORDER BY id ASC")
    fun getCardsForLecture(lectureId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getCardById(id: Long): FlashcardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<FlashcardEntity>): List<Long>

    @Update
    suspend fun updateCard(card: FlashcardEntity)

    @Delete
    suspend fun deleteCard(card: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteCardById(id: Long)

    @Query("DELETE FROM flashcards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeckId(deckId: Long)

    @Query("DELETE FROM flashcards WHERE lectureId = :lectureId")
    suspend fun deleteCardsByLectureId(lectureId: Long)

    @Query("UPDATE flashcards SET isIncluded = :included WHERE deckId = :deckId")
    suspend fun setAllCardsIncludedInDeck(deckId: Long, included: Boolean)

    @Query("UPDATE flashcards SET isIncluded = :included WHERE id = :cardId")
    suspend fun setCardIncluded(cardId: Long, included: Boolean)

    @Query("SELECT COUNT(*) FROM flashcards WHERE deckId = :deckId")
    suspend fun countCardsInDeck(deckId: Long): Int
}
