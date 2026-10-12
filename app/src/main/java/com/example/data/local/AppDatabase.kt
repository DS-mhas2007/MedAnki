package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.local.dao.DeckDao
import com.example.data.local.dao.FlashcardDao
import com.example.data.local.dao.LectureDao
import com.example.data.local.dao.SlideDao
import com.example.data.local.entity.CardType
import com.example.data.local.entity.DeckEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.LectureEntity
import com.example.data.local.entity.LectureSlideEntity
import com.example.data.local.entity.SlideStatus
import com.example.data.local.entity.YieldLevel

class Converters {
    @TypeConverter
    fun fromCardType(value: CardType): String = value.name

    @TypeConverter
    fun toCardType(value: String): CardType = try {
        CardType.valueOf(value)
    } catch (_: Exception) {
        CardType.BASIC
    }

    @TypeConverter
    fun fromYieldLevel(value: YieldLevel): String = value.name

    @TypeConverter
    fun toYieldLevel(value: String): YieldLevel = try {
        YieldLevel.valueOf(value)
    } catch (_: Exception) {
        YieldLevel.HIGH
    }

    @TypeConverter
    fun fromSlideStatus(value: SlideStatus): String = value.name

    @TypeConverter
    fun toSlideStatus(value: String): SlideStatus = try {
        SlideStatus.valueOf(value)
    } catch (_: Exception) {
        SlideStatus.PENDING
    }
}

@Database(
    entities = [
        LectureEntity::class,
        DeckEntity::class,
        FlashcardEntity::class,
        LectureSlideEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lectureDao(): LectureDao
    abstract fun deckDao(): DeckDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun slideDao(): SlideDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "med_anki_database.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
