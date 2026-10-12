package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.AnkiPackageExporter
import com.example.data.local.entity.CardType
import com.example.data.local.entity.FlashcardEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnkiExportTest {

    @Test
    fun testAnkiApkgCompliance() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Create test image in anki_media
            val mediaDir = File(context.filesDir, "anki_media").apply { mkdirs() }
            val testImage1 = File(mediaDir, "histology_myocarditis.jpg")
            FileOutputStream(testImage1).use { it.write("fake_histology_jpeg_data".toByteArray()) }

            val cards = listOf(
                // 1. Basic card with image
                FlashcardEntity(
                    id = 1,
                    deckId = 10,
                    cardType = CardType.BASIC,
                    front = "<div><img src=\"histology_myocarditis.jpg\"><br>What histological finding confirms acute lymphocytic myocarditis?</div>",
                    back = "Dense interstitial lymphocytic infiltrate with myocyte necrosis.",
                    explanation = "Most commonly caused by Coxsackie B virus enterovirus infection.",
                    imagePath = testImage1.name
                ),
                // 2. Multi-Cloze card with c1 and c2
                FlashcardEntity(
                    id = 2,
                    deckId = 10,
                    cardType = CardType.CLOZE,
                    front = "In myocardial infarction, {{c1::Troponin}} peaks at 24 hours while {{c2::CK-MB}} normalizes within 48-72 hours.",
                    back = "Cardiac Biomarkers",
                    clozeText = "In myocardial infarction, {{c1::Troponin}} peaks at 24 hours while {{c2::CK-MB}} normalizes within 48-72 hours.",
                    explanation = "CK-MB's rapid clearance allows detection of re-infarction occurring within 3-4 days."
                ),
                // 3. Cloze card with c1 only
                FlashcardEntity(
                    id = 3,
                    deckId = 10,
                    cardType = CardType.CLOZE,
                    front = "The rate-limiting enzyme of catecholamine synthesis is {{c1::tyrosine hydroxylase}}.",
                    back = "Biochemistry",
                    clozeText = "The rate-limiting enzyme of catecholamine synthesis is {{c1::tyrosine hydroxylase}}.",
                    explanation = "Converts L-tyrosine to L-DOPA using tetrahydrobiopterin (BH4) cofactor."
                )
            )

            val apkgFile = AnkiPackageExporter.exportApkg(context, "Cardiology_Board_Review", cards)
            assertTrue("APKG file must exist", apkgFile.exists())
            assertTrue("APKG file must not be empty", apkgFile.length() > 0)

            // 1. Verify ZIP entries
            val zip = ZipFile(apkgFile)
            val colEntry = zip.getEntry("collection.anki2")
            val mediaEntry = zip.getEntry("media")
            assertNotNull("collection.anki2 must be present in zip", colEntry)
            assertNotNull("media file must be present in zip", mediaEntry)

            // 2. Verify media JSON mapping and files
            val mediaText = zip.getInputStream(mediaEntry).bufferedReader().readText()
            val mediaJson = JSONObject(mediaText)
            assertTrue("media map must not be empty", mediaJson.length() > 0)

            val mediaFileNames = mutableSetOf<String>()
            val keys = mediaJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val fileName = mediaJson.getString(key)
                mediaFileNames.add(fileName)
                // Assert numbered entry exists in zip
                val numberedZipEntry = zip.getEntry(key)
                assertNotNull("Zip must contain numbered media entry '$key' for '$fileName'", numberedZipEntry)
            }

            // 3. Extract and inspect collection.anki2 SQLite database
            val tempDb = File(context.cacheDir, "verify_compliance_col.anki2")
            zip.getInputStream(colEntry).use { input ->
                FileOutputStream(tempDb).use { output -> input.copyTo(output) }
            }
            zip.close()

            val db = SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            // Assert col.dconf contains default group "1"
            val cursorCol = db.rawQuery("SELECT dconf, models, decks, conf FROM col", null)
            assertTrue("col table must have at least one row", cursorCol.moveToFirst())

            val dconfStr = cursorCol.getString(0)
            val dconfJson = JSONObject(dconfStr)
            assertTrue("col.dconf must contain option group '1'", dconfJson.has("1"))
            val defaultGroup = dconfJson.getJSONObject("1")
            assertEquals("Default dconf ID must be 1", 1, defaultGroup.getInt("id"))
            assertEquals("Default dconf name must be 'Default'", "Default", defaultGroup.getString("name"))

            // Assert decks reference dconf 1 and contains Default deck 1
            val decksStr = cursorCol.getString(2)
            val decksJson = JSONObject(decksStr)
            assertTrue("decks must contain default deck '1'", decksJson.has("1"))

            // Assert models: cloze model has type = 1, basic has type = 0
            val modelsStr = cursorCol.getString(1)
            val modelsJson = JSONObject(modelsStr)
            var hasType0 = false
            var hasType1 = false
            val modelKeys = modelsJson.keys()
            while (modelKeys.hasNext()) {
                val mKey = modelKeys.next()
                val mObj = modelsJson.getJSONObject(mKey)
                if (mObj.getInt("type") == 0) hasType0 = true
                if (mObj.getInt("type") == 1) hasType1 = true
            }
            assertTrue("models must have basic model (type 0)", hasType0)
            assertTrue("models must have cloze model (type 1)", hasType1)
            cursorCol.close()

            // 4. Assert cards and notes counts
            val cursorNotes = db.rawQuery("SELECT id, flds, sfld, csum FROM notes", null)
            assertEquals("Must have exactly 3 notes", 3, cursorNotes.count)

            val noteIdsWithCards = mutableSetOf<Long>()
            val referencedImagesInNotes = mutableSetOf<String>()
            val imgRegex = Regex("""<img\s+[^>]*src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            val srcRegex = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

            while (cursorNotes.moveToNext()) {
                val noteId = cursorNotes.getLong(0)
                val flds = cursorNotes.getString(1)
                val sfld = cursorNotes.getString(2)
                val csum = cursorNotes.getLong(3)

                assertTrue("flds must contain delimiter 0x1f", flds.contains("\u001f"))
                assertTrue("sfld must not be blank", sfld.isNotBlank())
                assertTrue("csum must be non-zero", csum != 0L)

                imgRegex.findAll(flds).forEach { match ->
                    srcRegex.find(match.value)?.groupValues?.get(1)?.let {
                        referencedImagesInNotes.add(it)
                    }
                }
            }
            cursorNotes.close()

            // Assert: every referenced image in a note must exist in the media map!
            for (img in referencedImagesInNotes) {
                assertTrue("Every <img src='$img'> in notes must exist in media map", mediaFileNames.contains(img))
            }

            // 5. Assert cloze cards produced:
            // Note 1 (Basic) -> 1 card with ord 0
            // Note 2 (Cloze c1 + c2) -> 2 cards with ord 0 and ord 1
            // Note 3 (Cloze c1) -> 1 card with ord 0
            // Total cards = 4
            val cursorCards = db.rawQuery("SELECT nid, ord FROM cards ORDER BY id ASC", null)
            assertEquals("Total cards must be 4", 4, cursorCards.count)

            val ordList = mutableListOf<Int>()
            while (cursorCards.moveToNext()) {
                noteIdsWithCards.add(cursorCards.getLong(0))
                ordList.add(cursorCards.getInt(1))
            }
            cursorCards.close()

            // Assert: no note without cards!
            assertEquals("All 3 notes must have associated cards", 3, noteIdsWithCards.size)

            // Assert: ordinals sequence matches expected [0, 0, 1, 0]
            assertEquals(listOf(0, 0, 1, 0), ordList)

            db.close()
            tempDb.delete()
            apkgFile.delete()
        }
    }
}
