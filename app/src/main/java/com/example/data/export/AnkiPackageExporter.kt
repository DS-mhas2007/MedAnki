package com.example.data.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.data.local.entity.CardType
import com.example.data.local.entity.FlashcardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AnkiPackageExporter {

    suspend fun exportApkg(
        context: Context,
        deckName: String,
        cards: List<FlashcardEntity>
    ): File = withContext(Dispatchers.IO) {
        val sanitizedDeckName = deckName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val tempDir = File(context.cacheDir, "anki_export_${System.currentTimeMillis()}").apply { mkdirs() }

        val dbFile = File(tempDir, "collection.anki2")
        if (dbFile.exists()) dbFile.delete()

        val mediaDirs = listOf(
            File(context.filesDir, "anki_media"),
            File(context.filesDir, "slides"),
            context.filesDir,
            context.cacheDir
        )

        val imageRegex = Regex("""<img\s+[^>]*src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        val imageSrcExtractRegex = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        // 1. First scan all image references in cards
        val candidateImages = mutableSetOf<String>()
        for (card in cards) {
            if (card.imagePath.isNotBlank()) {
                candidateImages.add(File(card.imagePath).name)
            }
            imageRegex.findAll(card.front).forEach { match ->
                imageSrcExtractRegex.find(match.value)?.groupValues?.get(1)?.let { candidateImages.add(it) }
            }
            imageRegex.findAll(card.back).forEach { match ->
                imageSrcExtractRegex.find(match.value)?.groupValues?.get(1)?.let { candidateImages.add(it) }
            }
        }

        // 2. Resolve actual files and build media map
        val mediaMap = mutableMapOf<String, String>() // "0" -> "filename.jpg"
        val mediaFiles = mutableMapOf<String, File>() // "0" -> File
        var mediaCounter = 0

        for (imgName in candidateImages) {
            val resolvedFile = mediaDirs.firstNotNullOfOrNull { dir ->
                val f = File(dir, imgName)
                if (f.exists()) f else null
            } ?: File(imgName).takeIf { it.exists() }

            if (resolvedFile != null && resolvedFile.exists()) {
                val key = mediaCounter.toString()
                mediaMap[key] = imgName
                mediaFiles[key] = resolvedFile
                mediaCounter++
            }
        }

        val availableImageNames = mediaMap.values.toSet()

        // 3. Build Anki SQLite database collection.anki2
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            buildAnkiDatabase(db, deckName, cards, availableImageNames)
        } finally {
            db.close()
        }

        // 4. Build media JSON: {"0": "filename.jpg", "1": "filename2.jpg"}
        val mediaJson = JSONObject()
        for ((key, filename) in mediaMap) {
            mediaJson.put(key, filename)
        }
        val mediaFile = File(tempDir, "media")
        mediaFile.writeText(mediaJson.toString(), StandardCharsets.UTF_8)

        val outputFile = File(
            context.cacheDir,
            "${sanitizedDeckName}_${System.currentTimeMillis()}.apkg"
        )
        if (outputFile.exists()) outputFile.delete()

        ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
            // Add collection.anki2
            val colEntry = ZipEntry("collection.anki2")
            zipOut.putNextEntry(colEntry)
            dbFile.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()

            // Add media JSON mapping
            val mediaEntry = ZipEntry("media")
            zipOut.putNextEntry(mediaEntry)
            mediaFile.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()

            // Add actual media files indexed by their numeric keys according to Anki spec
            for ((key, file) in mediaFiles) {
                val fileEntry = ZipEntry(key)
                zipOut.putNextEntry(fileEntry)
                file.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }

        // Clean up temporary SQLite and media file
        dbFile.delete()
        mediaFile.delete()
        tempDir.delete()

        outputFile
    }

    suspend fun exportTsv(
        context: Context,
        deckName: String,
        cards: List<FlashcardEntity>
    ): File = withContext(Dispatchers.IO) {
        val sanitizedDeckName = deckName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(
            context.cacheDir,
            "${sanitizedDeckName}_anki_import_${System.currentTimeMillis()}.txt"
        )

        val sb = StringBuilder()
        sb.append("#separator:tab\n")
        sb.append("#html:true\n")
        sb.append("#deck:$deckName\n")
        sb.append("#tags column:5\n")
        sb.append("#columns:Front\tBack\tClozeText\tExplanation\tTags\n")

        for (card in cards) {
            val frontClean = sanitizeForTsv(
                if (card.cardType == CardType.CLOZE) {
                    card.clozeText.ifBlank { card.front }
                } else {
                    card.front
                }
            )
            val backClean = sanitizeForTsv(card.back)
            val clozeClean = sanitizeForTsv(card.clozeText)
            val expClean = sanitizeForTsv(
                buildString {
                    append(card.explanation)
                    if (card.clinicalCorrelation.isNotBlank()) {
                        append("<br><br><b>Clinical Pearl / USMLE:</b> ")
                        append(card.clinicalCorrelation)
                    }
                }
            )
            val tagsClean = sanitizeForTsv(card.tags.replace(",", " "))
            sb.append("$frontClean\t$backClean\t$clozeClean\t$expClean\t$tagsClean\n")
        }

        outputFile.writeText(sb.toString(), StandardCharsets.UTF_8)
        outputFile
    }

    private fun sanitizeForTsv(text: String): String {
        return text
            .replace("\t", " ")
            .replace("\r\n", "<br>")
            .replace("\n", "<br>")
    }

    private fun buildAnkiDatabase(
        db: SQLiteDatabase,
        deckName: String,
        cards: List<FlashcardEntity>,
        availableImageNames: Set<String>
    ) {
        db.execSQL(
            """
            CREATE TABLE col (
                id integer primary key,
                crt integer,
                mod integer,
                scm integer,
                ver integer,
                dty integer,
                usn integer,
                ls integer,
                conf text,
                models text,
                decks text,
                dconf text,
                tags text
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE notes (
                id integer primary key,
                guid text,
                mid integer,
                mod integer,
                usn integer,
                tags text,
                flds text,
                sfld text,
                csum integer,
                flags integer,
                data text
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE cards (
                id integer primary key,
                nid integer,
                did integer,
                ord integer,
                mod integer,
                usn integer,
                type integer,
                queue integer,
                due integer,
                ivl integer,
                factor integer,
                reps integer,
                lapses integer,
                left integer,
                odue integer,
                odid integer,
                flags integer,
                data text
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE graves (
                usn integer,
                oid integer,
                type integer
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE revlog (
                id integer primary key,
                cid integer,
                usn integer,
                ease integer,
                ivl integer,
                lastIvl integer,
                factor integer,
                time integer,
                type integer
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX ix_notes_usn on notes (usn);")
        db.execSQL("CREATE INDEX ix_cards_usn on cards (usn);")
        db.execSQL("CREATE INDEX ix_cards_nid on cards (nid);")
        db.execSQL("CREATE INDEX ix_cards_sched on cards (did, queue, due);")

        val nowSeconds = System.currentTimeMillis() / 1000
        val deckId = 1590000000000L + (Math.random() * 999999).toLong()

        // Build models JSON (Basic and Cloze model)
        val basicModelId = 1600000000001L
        val clozeModelId = 1600000000002L

        val modelsJson = JSONObject().apply {
            put(
                basicModelId.toString(),
                JSONObject(
                    """
                    {
                        "id": $basicModelId,
                        "name": "MedAnki Basic",
                        "type": 0,
                        "mod": $nowSeconds,
                        "usn": -1,
                        "sortf": 0,
                        "did": $deckId,
                        "tmpls": [
                            {
                                "name": "Card 1",
                                "ord": 0,
                                "qfmt": "<div class='med-front'>{{Front}}</div>",
                                "afmt": "{{FrontSide}}<hr id='answer'><div class='med-back'>{{Back}}</div><br><div class='med-explanation'><b>Explanation:</b><br>{{Explanation}}</div>",
                                "did": null
                            }
                        ],
                        "flds": [
                            {"name": "Front", "ord": 0, "sticky": false, "rtl": false, "font": "Arial", "size": 20},
                            {"name": "Back", "ord": 1, "sticky": false, "rtl": false, "font": "Arial", "size": 20},
                            {"name": "Explanation", "ord": 2, "sticky": false, "rtl": false, "font": "Arial", "size": 16}
                        ],
                        "css": ".card { font-family: arial; font-size: 20px; text-align: left; color: black; background-color: white; padding: 20px; } .med-explanation { color: #333; font-size: 16px; background: #f0f4f8; padding: 12px; border-radius: 8px; } img { max-width: 100%; height: auto; border-radius: 8px; margin: 8px 0; }"
                    }
                    """.trimIndent()
                )
            )
            put(
                clozeModelId.toString(),
                JSONObject(
                    """
                    {
                        "id": $clozeModelId,
                        "name": "MedAnki Cloze",
                        "type": 1,
                        "mod": $nowSeconds,
                        "usn": -1,
                        "sortf": 0,
                        "did": $deckId,
                        "tmpls": [
                            {
                                "name": "Cloze",
                                "ord": 0,
                                "qfmt": "<div class='med-cloze'>{{cloze:Text}}</div>",
                                "afmt": "<div class='med-cloze'>{{cloze:Text}}</div><hr id='answer'><div class='med-extra'>{{Extra}}</div><br><div class='med-explanation'><b>Explanation:</b><br>{{Explanation}}</div>",
                                "did": null
                            }
                        ],
                        "flds": [
                            {"name": "Text", "ord": 0, "sticky": false, "rtl": false, "font": "Arial", "size": 20},
                            {"name": "Extra", "ord": 1, "sticky": false, "rtl": false, "font": "Arial", "size": 20},
                            {"name": "Explanation", "ord": 2, "sticky": false, "rtl": false, "font": "Arial", "size": 16}
                        ],
                        "css": ".card { font-family: arial; font-size: 20px; text-align: left; color: black; background-color: white; padding: 20px; } .cloze { font-weight: bold; color: #0077B6; } .med-explanation { color: #333; font-size: 16px; background: #f0f4f8; padding: 12px; border-radius: 8px; } img { max-width: 100%; height: auto; border-radius: 8px; margin: 8px 0; }"
                    }
                    """.trimIndent()
                )
            )
        }

        // Decks JSON including Default deck 1 and custom deck, referencing dconf 1
        val decksJson = JSONObject().apply {
            put(
                "1",
                JSONObject(
                    """
                    {
                        "id": 1,
                        "name": "Default",
                        "desc": "",
                        "mod": $nowSeconds,
                        "usn": 0,
                        "collapsed": false,
                        "browserCollapsed": false,
                        "dconf": 1
                    }
                    """.trimIndent()
                )
            )
            put(
                deckId.toString(),
                JSONObject(
                    """
                    {
                        "id": $deckId,
                        "name": "$deckName",
                        "desc": "Medical study deck generated by MedAnki assistant",
                        "mod": $nowSeconds,
                        "usn": -1,
                        "collapsed": false,
                        "browserCollapsed": false,
                        "dconf": 1
                    }
                    """.trimIndent()
                )
            )
        }

        // Valid col.dconf option group with key "1" (id 1, name "Default")
        val dconfJson = JSONObject().apply {
            put(
                "1",
                JSONObject(
                    """
                    {
                        "id": 1,
                        "mod": $nowSeconds,
                        "name": "Default",
                        "usn": 0,
                        "maxTaken": 60,
                        "autoplay": true,
                        "timer": 0,
                        "replayq": true,
                        "new": {
                            "bury": false,
                            "delays": [1.0, 10.0],
                            "initialFactor": 2500,
                            "ints": [1, 4, 0],
                            "order": 1,
                            "perDay": 20
                        },
                        "rev": {
                            "bury": false,
                            "ease4": 1.3,
                            "ivlFct": 1.0,
                            "maxIvl": 36500,
                            "perDay": 200
                        },
                        "lapse": {
                            "delays": [10.0],
                            "leechAction": 0,
                            "leechFails": 8,
                            "minInt": 1,
                            "mult": 0.0
                        }
                    }
                    """.trimIndent()
                )
            )
        }

        // Valid col.conf
        val confJson = JSONObject().apply {
            put("nextPos", 1)
            put("estTimes", true)
            put("activeDecks", JSONArray().apply { put(deckId) })
            put("sortType", "noteFld")
            put("timeLim", 0)
            put("sortBackwards", false)
            put("addToCur", true)
            put("curDeck", deckId)
            put("curModel", basicModelId)
        }

        // Insert col row
        db.execSQL(
            """
            INSERT INTO col (
                id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags
            ) VALUES (
                1, $nowSeconds, $nowSeconds, $nowSeconds, 11, 0, 0, 0,
                '${confJson.toString().replace("'", "''")}',
                '${modelsJson.toString().replace("'", "''")}',
                '${decksJson.toString().replace("'", "''")}',
                '${dconfJson.toString().replace("'", "''")}',
                '{}'
            )
            """.trimIndent()
        )

        var noteId = 1700000000000L
        var cardId = 1800000000000L

        for (card in cards) {
            noteId++
            val isCloze = card.cardType == CardType.CLOZE
            val mid = if (isCloze) clozeModelId else basicModelId

            val guid = UUID.randomUUID().toString().substring(0, 10)
            val tagTokens = card.tags
                .split(",")
                .map { it.trim().replace(" ", "_") }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            val tagsFormatted = if (tagTokens.isNotEmpty()) " $tagTokens " else ""

            val explanationHtml = buildString {
                append(card.explanation.replace("\n", "<br>"))
                if (card.clinicalCorrelation.isNotBlank()) {
                    append("<br><br><b>Clinical Pearl / USMLE:</b> ")
                    append(card.clinicalCorrelation.replace("\n", "<br>"))
                }
            }

            // Clean image tags: only include images that exist in availableImageNames
            var cleanFront = filterValidImages(card.front, availableImageNames).replace("\n", "<br>")
            val cleanBack = filterValidImages(card.back, availableImageNames).replace("\n", "<br>")
            var cleanCloze = filterValidImages(card.clozeText, availableImageNames).replace("\n", "<br>")

            // If card has an imagePath that is available and not already in HTML, prepend it
            if (card.imagePath.isNotBlank()) {
                val imgFileName = File(card.imagePath).name
                if (availableImageNames.contains(imgFileName)) {
                    if (!cleanFront.contains(imgFileName) && !cleanBack.contains(imgFileName)) {
                        val imgTag = "<img src=\"$imgFileName\"><br>"
                        cleanFront = imgTag + cleanFront
                        if (cleanCloze.isNotBlank() && !cleanCloze.contains(imgFileName)) {
                            cleanCloze = imgTag + cleanCloze
                        }
                    }
                }
            }

            val flds = if (isCloze) {
                val text = if (cleanCloze.isNotBlank()) cleanCloze else cleanFront
                val extra = cleanBack
                "$text\u001f$extra\u001f$explanationHtml"
            } else {
                "$cleanFront\u001f$cleanBack\u001f$explanationHtml"
            }

            val sortField = if (isCloze) {
                cleanCloze.ifBlank { cleanFront }
            } else {
                cleanFront
            }

            val csum = calculateCsum(sortField)

            // Insert note
            val noteStmt = db.compileStatement(
                """
                INSERT INTO notes (
                    id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, '')
                """.trimIndent()
            )
            noteStmt.bindLong(1, noteId)
            noteStmt.bindString(2, guid)
            noteStmt.bindLong(3, mid)
            noteStmt.bindLong(4, nowSeconds)
            noteStmt.bindLong(5, -1)
            noteStmt.bindString(6, tagsFormatted)
            noteStmt.bindString(7, flds)
            noteStmt.bindString(8, sortField)
            noteStmt.bindLong(9, csum)
            noteStmt.executeInsert()

            // Multi-Cloze expansion: parse all distinct c(\d+) indices
            val ordinals = if (isCloze) {
                val clozeSource = cleanCloze.ifBlank { cleanFront }
                val clozeMatches = Regex("""\{\{c(\d+)::""").findAll(clozeSource)
                val indices = clozeMatches.mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
                if (indices.isEmpty()) listOf(0) else indices.map { it - 1 }.sorted()
            } else {
                listOf(0)
            }

            // Insert card rows (guaranteed at least one card per note)
            for (ord in ordinals) {
                cardId++
                val cardStmt = db.compileStatement(
                    """
                    INSERT INTO cards (
                        id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data
                    ) VALUES (?, ?, ?, ?, ?, -1, 0, 0, ?, 0, 2500, 0, 0, 0, 0, 0, 0, '')
                    """.trimIndent()
                )
                cardStmt.bindLong(1, cardId)
                cardStmt.bindLong(2, noteId)
                cardStmt.bindLong(3, deckId)
                cardStmt.bindLong(4, ord.toLong())
                cardStmt.bindLong(5, nowSeconds)
                cardStmt.bindLong(6, (cardId % 10000))
                cardStmt.executeInsert()
            }
        }
    }

    private fun filterValidImages(html: String, availableImages: Set<String>): String {
        val imgRegex = Regex("""<img\s+[^>]*src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        return imgRegex.replace(html) { match ->
            val srcMatch = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(match.value)
            val fileName = srcMatch?.groupValues?.get(1)?.let { File(it).name }
            if (fileName != null && availableImages.contains(fileName)) {
                "<img src=\"$fileName\">"
            } else {
                "" // strip invalid / missing image tags so no broken media references exist
            }
        }
    }

    private fun calculateCsum(text: String): Long {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(text.toByteArray(StandardCharsets.UTF_8))
            val hex = digest.take(4).joinToString("") { "%02x".format(it) }
            hex.toLong(16)
        } catch (_: Exception) {
            (text.hashCode().toLong()) and 0xFFFFFFFFL
        }
    }
}
