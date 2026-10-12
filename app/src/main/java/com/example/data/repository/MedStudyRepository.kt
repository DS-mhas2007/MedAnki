package com.example.data.repository

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
import com.example.data.remote.AcademicAnalysisResult
import com.example.data.remote.ClinicalCorrelationItem
import com.example.data.remote.ConceptItem
import com.example.data.remote.DefinitionItem
import com.example.data.remote.DiagramInfo
import com.example.data.remote.GeminiService
import com.example.data.remote.GeneratedCardDto
import com.example.data.remote.MechanismItem
import com.example.data.remote.SlideStructuredData
import com.example.data.sample.MedicalLectureSamples
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

class MedStudyRepository(
    private val lectureDao: LectureDao,
    private val deckDao: DeckDao,
    private val flashcardDao: FlashcardDao,
    private val slideDao: SlideDao,
    private val geminiService: GeminiService
) {

    val allLectures: Flow<List<LectureEntity>> = lectureDao.getAllLectures()
    val allDecks: Flow<List<DeckEntity>> = deckDao.getAllDecks()
    val allCards: Flow<List<FlashcardEntity>> = flashcardDao.getAllCards()

    fun getDeckCards(deckId: Long): Flow<List<FlashcardEntity>> = flashcardDao.getCardsForDeck(deckId)
    fun getLectureCards(lectureId: Long): Flow<List<FlashcardEntity>> = flashcardDao.getCardsForLecture(lectureId)
    fun getLectureFlow(lectureId: Long): Flow<LectureEntity?> = lectureDao.getLectureFlow(lectureId)
    fun getLectureSlidesFlow(lectureId: Long): Flow<List<LectureSlideEntity>> = slideDao.getSlidesForLectureFlow(lectureId)

    suspend fun getIncludedCardsForDeck(deckId: Long): List<FlashcardEntity> =
        flashcardDao.getIncludedCardsForDeck(deckId)

    suspend fun createLecture(
        title: String,
        subject: String,
        rawContent: String,
        pageCount: Int,
        slideImages: List<String> = emptyList()
    ): Long {
        val slideImagesJson = JSONArray(slideImages).toString()
        val lecture = LectureEntity(
            title = title,
            subject = subject,
            rawContent = rawContent,
            pageCount = pageCount,
            slideImagesJson = slideImagesJson
        )
        val lectureId = lectureDao.insertLecture(lecture)

        // Seed per-slide Room entities for tracking and crash recovery
        if (slideImages.isNotEmpty()) {
            val slideEntities = slideImages.mapIndexed { index, path ->
                LectureSlideEntity(
                    lectureId = lectureId,
                    slideNumber = index + 1,
                    imagePath = path,
                    title = "Slide ${index + 1}",
                    status = SlideStatus.PENDING
                )
            }
            slideDao.insertSlides(slideEntities)
        }

        return lectureId
    }

    suspend fun deleteLecture(lectureId: Long) {
        slideDao.deleteSlidesForLecture(lectureId)
        flashcardDao.deleteCardsByLectureId(lectureId)
        lectureDao.deleteLectureById(lectureId)
    }

    suspend fun deleteDeck(deckId: Long) {
        flashcardDao.deleteCardsByDeckId(deckId)
        deckDao.deleteDeckById(deckId)
    }

    suspend fun createDeck(name: String, subject: String, lectureId: Long?): Long {
        val deck = DeckEntity(
            name = name,
            subject = subject,
            lectureId = lectureId
        )
        return deckDao.insertDeck(deck)
    }

    suspend fun getFailedSlides(lectureId: Long): List<LectureSlideEntity> {
        return slideDao.getFailedSlides(lectureId)
    }

    suspend fun retryFailedSlides(
        lectureId: Long,
        apiKey: String,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): Result<AcademicAnalysisResult> {
        val failed = slideDao.getFailedSlides(lectureId)
        for (slide in failed) {
            slideDao.updateSlideStatus(lectureId, slide.slideNumber, SlideStatus.PENDING, null)
        }
        return analyzeLecture(lectureId, apiKey, onProgress)
    }

    /**
     * Phase 1 & 4: Multimodal slide batch pipeline with crash resume and slide persistence.
     */
    suspend fun analyzeLecture(
        lectureId: Long,
        apiKey: String,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): Result<AcademicAnalysisResult> {
        val lecture = lectureDao.getLectureById(lectureId)
            ?: return Result.failure(IllegalArgumentException("Lecture not found"))

        var slides = slideDao.getSlidesForLecture(lectureId)
        val slideImages = parseSlideImages(lecture.slideImagesJson)

        // Ensure slide records exist in Room if not seeded before
        if (slides.isEmpty() && slideImages.isNotEmpty()) {
            val newEntities = slideImages.mapIndexed { index, path ->
                LectureSlideEntity(
                    lectureId = lectureId,
                    slideNumber = index + 1,
                    imagePath = path,
                    title = "Slide ${index + 1}",
                    status = SlideStatus.PENDING
                )
            }
            slideDao.insertSlides(newEntities)
            slides = newEntities
        }

        if (slides.isNotEmpty()) {
            return analyzeSlidesPipeline(lecture, slides, apiKey, onProgress)
        } else {
            // Text-only lecture (e.g. pasted transcript)
            onProgress(1, 1, "Analyzing text in context-aware chunks...")
            val result = geminiService.analyzeTextChunked(
                apiKey = apiKey,
                lectureTitle = lecture.title,
                content = lecture.rawContent
            )
            if (result.isSuccess) {
                val res = result.getOrThrow()
                lectureDao.updateLecture(
                    lecture.copy(
                        summary = res.summary,
                        academicAnalysisJson = serializeAnalysisResult(res)
                    )
                )
            }
            return result
        }
    }

    private suspend fun analyzeSlidesPipeline(
        lecture: LectureEntity,
        slides: List<LectureSlideEntity>,
        apiKey: String,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): Result<AcademicAnalysisResult> {
        val totalSlides = slides.size
        val batchSize = 6

        var runningSectionContext = ""
        val completedSlideData = mutableListOf<SlideStructuredData>()

        // Preload already completed slides from Room for crash recovery / resumption
        for (slide in slides) {
            if (slide.status == SlideStatus.COMPLETED && slide.structuredJson.isNotBlank()) {
                parseSlideStructuredData(slide.structuredJson, slide.imagePath)?.let {
                    completedSlideData.add(it)
                }
            }
        }

        // Chunk slides into batches of 6 with 1 overlap page
        val slideBatches = slides.chunked(batchSize)

        for ((bIdx, batch) in slideBatches.withIndex()) {
            // Check if all slides in this batch are already completed
            val pendingInBatch = batch.filter { it.status != SlideStatus.COMPLETED }
            if (pendingInBatch.isEmpty()) {
                continue
            }

            val startNum = batch.first().slideNumber
            val endNum = batch.last().slideNumber
            onProgress(endNum, totalSlides, "Analyzing slides $startNum-$endNum of $totalSlides...")

            // 1 page of overlap from previous batch for context continuity
            val overlapSlide = if (bIdx > 0) {
                val prevBatch = slideBatches[bIdx - 1]
                prevBatch.lastOrNull()?.let { Pair(it.slideNumber, it.imagePath) }
            } else null

            val targetPairs = batch.map { Pair(it.slideNumber, it.imagePath) }

            val batchResult = geminiService.analyzeSlideBatch(
                apiKey = apiKey,
                lectureTitle = lecture.title,
                targetSlides = targetPairs,
                overlapSlide = overlapSlide,
                runningSectionContext = runningSectionContext
            )

            if (batchResult.isSuccess) {
                val res = batchResult.getOrThrow()
                if (res.sectionSummary.isNotBlank()) {
                    runningSectionContext = "${res.sectionTitle}: ${res.sectionSummary}"
                }

                // Update Room for each slide
                for (slideData in res.slides) {
                    val matchingEntity = batch.firstOrNull { it.slideNumber == slideData.slideNumber }
                    if (matchingEntity != null) {
                        val json = serializeSlideStructuredData(slideData)
                        slideDao.updateSlide(
                            matchingEntity.copy(
                                title = slideData.title,
                                extractedText = slideData.extractedText,
                                structuredJson = json,
                                status = SlideStatus.COMPLETED,
                                errorMessage = null,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    completedSlideData.removeAll { it.slideNumber == slideData.slideNumber }
                    completedSlideData.add(slideData)
                }
            } else {
                val errorMsg = batchResult.exceptionOrNull()?.localizedMessage ?: "Analysis failed"
                // Mark batch slides as failed in Room so user can retry
                for (slideEntity in batch) {
                    if (slideEntity.status != SlideStatus.COMPLETED) {
                        slideDao.updateSlideStatus(
                            lecture.id,
                            slideEntity.slideNumber,
                            SlideStatus.FAILED,
                            errorMsg
                        )
                    }
                }
            }
        }

        // Sort completed slides by slide number
        completedSlideData.sortBy { it.slideNumber }

        val failedSlides = slideDao.getFailedSlides(lecture.id)
        if (completedSlideData.isEmpty() && failedSlides.isNotEmpty()) {
            return Result.failure(Exception("Failed analyzing slides. Tap 'Retry failed slides' to try again."))
        }

        // Synthesize academic analysis result
        val unifiedAnalysis = synthesizeAnalysisFromSlides(lecture.title, completedSlideData)
        lectureDao.updateLecture(
            lecture.copy(
                summary = unifiedAnalysis.summary,
                academicAnalysisJson = serializeAnalysisResult(unifiedAnalysis)
            )
        )

        return Result.success(unifiedAnalysis)
    }

    /**
     * Phase 1 & 4 & 5: Generates cards per section, merges in lecture order, and deduplicates.
     */
    suspend fun generateCardsForLecture(
        lectureId: Long,
        deckId: Long?,
        apiKey: String,
        mediaDir: File,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): Result<List<FlashcardEntity>> {
        val lecture = lectureDao.getLectureById(lectureId)
            ?: return Result.failure(IllegalArgumentException("Lecture not found"))

        val slides = slideDao.getSlidesForLecture(lectureId)
        val completedSlides = slides.filter { it.status == SlideStatus.COMPLETED }
            .mapNotNull { parseSlideStructuredData(it.structuredJson, it.imagePath) }
            .sortedBy { it.slideNumber }

        val targetDeckId = deckId ?: run {
            val existingDecks = deckDao.getAllDecks().first()
            val matching = existingDecks.firstOrNull { it.lectureId == lectureId }
            matching?.id ?: createDeck(
                name = "${lecture.subject}: ${lecture.title.take(30)}",
                subject = lecture.subject,
                lectureId = lectureId
            )
        }

        val allGeneratedCards = mutableListOf<GeneratedCardDto>()

        if (completedSlides.isNotEmpty()) {
            // Group slides into logical sections (chunks of 4-6 slides)
            val sectionChunks = completedSlides.chunked(5)
            for ((secIdx, sectionSlides) in sectionChunks.withIndex()) {
                val startSlide = sectionSlides.first().slideNumber
                val endSlide = sectionSlides.last().slideNumber
                val sectionTitle = sectionSlides.first().title.ifBlank { "Section ${secIdx + 1}" }

                onProgress(secIdx + 1, sectionChunks.size, "Generating cards for slides $startSlide-$endSlide...")

                val secResult = geminiService.generateCardsForSection(
                    apiKey = apiKey,
                    lectureTitle = lecture.title,
                    subject = lecture.subject,
                    sectionTitle = sectionTitle,
                    sectionSlides = sectionSlides,
                    mediaOutputDir = mediaDir
                )

                secResult.getOrNull()?.let { allGeneratedCards.addAll(it) }
            }
        } else {
            // Text-only fallback
            onProgress(1, 1, "Generating cards from lecture text...")
            val existingAnalysis = parseStoredAnalysis(lecture.academicAnalysisJson)
            val res = geminiService.generateCardsFromTextChunked(
                apiKey = apiKey,
                lectureTitle = lecture.title,
                subject = lecture.subject,
                content = lecture.rawContent,
                analysisResult = existingAnalysis
            )
            res.getOrNull()?.let { allGeneratedCards.addAll(it) }
        }

        // Deduplication & insertion in lecture order
        val currentCards = flashcardDao.getCardsForDeck(targetDeckId).first()
        val existingSignatures = currentCards.map { generateSignature(it.front, it.back) }.toMutableSet()

        val entitiesToInsert = mutableListOf<FlashcardEntity>()
        for (dto in allGeneratedCards) {
            val signature = generateSignature(dto.front, dto.back)
            if (existingSignatures.contains(signature)) {
                continue
            }
            existingSignatures.add(signature)

            entitiesToInsert.add(
                FlashcardEntity(
                    deckId = targetDeckId,
                    lectureId = lectureId,
                    cardType = dto.cardType,
                    front = dto.front,
                    back = dto.back,
                    clozeText = dto.clozeText,
                    explanation = dto.explanation,
                    clinicalCorrelation = dto.clinicalCorrelation,
                    yieldLevel = dto.yieldLevel,
                    tags = dto.tags.joinToString(","),
                    imagePath = dto.imageFileName,
                    occlusionMasksJson = JSONArray(dto.occlusionLabels).toString(),
                    isIncluded = true,
                    isDuplicate = false,
                    duplicateSignature = signature
                )
            )
        }

        flashcardDao.insertCards(entitiesToInsert)
        deckDao.refreshDeckCardCount(targetDeckId)
        lectureDao.refreshCardCount(lectureId)

        return Result.success(entitiesToInsert)
    }

    suspend fun updateCard(card: FlashcardEntity) {
        flashcardDao.updateCard(card)
        deckDao.refreshDeckCardCount(card.deckId)
    }

    suspend fun deleteCard(card: FlashcardEntity) {
        flashcardDao.deleteCard(card)
        deckDao.refreshDeckCardCount(card.deckId)
        card.lectureId?.let { lectureDao.refreshCardCount(it) }
    }

    suspend fun setCardIncluded(cardId: Long, included: Boolean) {
        flashcardDao.setCardIncluded(cardId, included)
    }

    suspend fun setAllCardsIncluded(deckId: Long, included: Boolean) {
        flashcardDao.setAllCardsIncludedInDeck(deckId, included)
    }

    suspend fun insertCustomCard(card: FlashcardEntity): Long {
        val id = flashcardDao.insertCard(card)
        deckDao.refreshDeckCardCount(card.deckId)
        card.lectureId?.let { lectureDao.refreshCardCount(it) }
        return id
    }

    suspend fun ensureSampleDataLoaded() {
        val existingLectures = lectureDao.getAllLectures().first()
        if (existingLectures.isEmpty()) {
            for (sample in MedicalLectureSamples.samples) {
                val lectureId = lectureDao.insertLecture(
                    LectureEntity(
                        title = sample.title,
                        subject = sample.subject,
                        rawContent = sample.content,
                        pageCount = 4,
                        summary = sample.description
                    )
                )

                val deckId = deckDao.insertDeck(
                    DeckEntity(
                        name = "${sample.subject} - High Yield",
                        subject = sample.subject,
                        lectureId = lectureId
                    )
                )

                // Seed high yield sample cards
                seedSampleCards(deckId, lectureId, sample.subject)
                deckDao.refreshDeckCardCount(deckId)
                lectureDao.refreshCardCount(lectureId)
            }
        }
    }

    private suspend fun seedSampleCards(deckId: Long, lectureId: Long, subject: String) {
        if (subject == "Cardiology") {
            flashcardDao.insertCards(
                listOf(
                    FlashcardEntity(
                        deckId = deckId,
                        lectureId = lectureId,
                        cardType = CardType.BASIC,
                        front = "What is the initial diagnostic step for a 62-year-old male presenting with exertional syncope, angina, and a late-peaking crescendo-decrescendo systolic murmur at the right 2nd intercostal space?",
                        back = "Transthoracic Echocardiography (TTE) to evaluate for severe Aortic Stenosis.",
                        explanation = "Classic triad of Aortic Stenosis (SAD: Syncope, Angina, Dyspnea). Echocardiography measures aortic valve area (<1.0 cm² = severe) and transvalvular mean gradient (>40 mmHg).",
                        clinicalCorrelation = "Once symptoms develop in severe AS, average survival is <2-3 years without valve replacement (SAVR or TAVR).",
                        yieldLevel = YieldLevel.VERY_HIGH,
                        tags = "Cardiology,ValvularDisease,USMLE_Step2"
                    ),
                    FlashcardEntity(
                        deckId = deckId,
                        lectureId = lectureId,
                        cardType = CardType.CLOZE,
                        front = "In myocardial infarction, {{c1::Troponin I and T}} rise at 4 hours and peak at {{c2::24 hours}}, remaining elevated for 7-10 days, while {{c3::CK-MB}} normalizes within 48-72 hours.",
                        back = "Troponin vs CK-MB Kinetics",
                        clozeText = "In myocardial infarction, {{c1::Troponin I and T}} rise at 4 hours and peak at {{c2::24 hours}}, remaining elevated for 7-10 days, while {{c3::CK-MB}} normalizes within 48-72 hours.",
                        explanation = "CK-MB's rapid clearance makes it the preferred marker to detect early re-infarction when chest pain recurs within 3-4 days of index event.",
                        clinicalCorrelation = "High-yield USMLE Step 1 distinction: recurrence of pain on post-MI day 3 requires CK-MB check, not troponin.",
                        yieldLevel = YieldLevel.VERY_HIGH,
                        tags = "Cardiology,Pathology,Biomarkers"
                    )
                )
            )
        }
    }

    fun generateSignature(front: String, back: String): String {
        val normFront = front.lowercase(Locale.ROOT)
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("""\{\{c\d+::(.*?)\}\}"""), "$1")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replace(Regex("[^a-z0-9]"), "")
        val normBack = back.lowercase(Locale.ROOT)
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replace(Regex("[^a-z0-9]"), "")
        val combined = "$normFront||$normBack"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(combined.toByteArray(StandardCharsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            combined
        }
    }

    private fun synthesizeAnalysisFromSlides(
        title: String,
        slides: List<SlideStructuredData>
    ): AcademicAnalysisResult {
        val allConcepts = mutableListOf<ConceptItem>()
        val allDefs = mutableListOf<DefinitionItem>()
        val allMechs = mutableListOf<MechanismItem>()
        val allCorrs = mutableListOf<ClinicalCorrelationItem>()

        for (s in slides) {
            for (c in s.importantConcepts) {
                allConcepts.add(ConceptItem(c, "Concept from slide ${s.slideNumber}: ${s.title}", s.examRelevance))
            }
            for (m in s.mechanisms) {
                allMechs.add(MechanismItem(m, listOf(m), s.examRelevance))
            }
            for (cr in s.clinicalCorrelations) {
                allCorrs.add(ClinicalCorrelationItem(cr, cr, s.examRelevance))
            }
        }

        return AcademicAnalysisResult(
            summary = "Multimodal slide analysis of $title across ${slides.size} slides.",
            mainConcepts = allConcepts.distinctBy { it.title },
            definitions = allDefs.distinctBy { it.term },
            mechanisms = allMechs.distinctBy { it.name },
            classifications = emptyList(),
            importantDetails = emptyList(),
            clinicalCorrelations = allCorrs.distinctBy { it.condition },
            examQuestions = emptyList(),
            slides = slides
        )
    }

    private fun serializeSlideStructuredData(data: SlideStructuredData): String {
        val root = JSONObject()
        root.put("slideNumber", data.slideNumber)
        root.put("title", data.title)
        root.put("extractedText", data.extractedText)
        root.put("imageDescription", data.imageDescription)
        root.put("examRelevance", data.examRelevance)
        root.put("sourceImagePath", data.sourceImagePath)

        val cArr = JSONArray()
        for (c in data.importantConcepts) cArr.put(c)
        root.put("importantConcepts", cArr)

        val mArr = JSONArray()
        for (m in data.mechanisms) mArr.put(m)
        root.put("mechanisms", mArr)

        val crArr = JSONArray()
        for (cr in data.clinicalCorrelations) crArr.put(cr)
        root.put("clinicalCorrelations", crArr)

        val tArr = JSONArray()
        for (t in data.tables) tArr.put(t)
        root.put("tables", tArr)

        val dArr = JSONArray()
        for (d in data.diagrams) {
            dArr.put(JSONObject().apply {
                put("name", d.name)
                put("description", d.description)
                put("type", d.type)
                put("isHighYield", d.isHighYield)
                val ksArr = JSONArray()
                for (ks in d.keyStructures) ksArr.put(ks)
                put("keyStructures", ksArr)
                if (d.boundingBox != null) {
                    val bbArr = JSONArray()
                    for (b in d.boundingBox) bbArr.put(b)
                    put("boundingBox", bbArr)
                }
            })
        }
        root.put("diagrams", dArr)

        return root.toString()
    }

    private fun parseSlideStructuredData(jsonStr: String, fallbackPath: String): SlideStructuredData? {
        if (jsonStr.isBlank()) return null
        return try {
            val obj = JSONObject(jsonStr)
            val slideNum = obj.optInt("slideNumber", 1)
            val title = obj.optString("title", "Slide $slideNum")
            val text = obj.optString("extractedText", "")
            val imgDesc = obj.optString("imageDescription", "")
            val examRel = obj.optString("examRelevance", "")
            val path = obj.optString("sourceImagePath", fallbackPath)

            val cList = mutableListOf<String>()
            val cArr = obj.optJSONArray("importantConcepts") ?: JSONArray()
            for (i in 0 until cArr.length()) cList.add(cArr.optString(i))

            val mList = mutableListOf<String>()
            val mArr = obj.optJSONArray("mechanisms") ?: JSONArray()
            for (i in 0 until mArr.length()) mList.add(mArr.optString(i))

            val crList = mutableListOf<String>()
            val crArr = obj.optJSONArray("clinicalCorrelations") ?: JSONArray()
            for (i in 0 until crArr.length()) crList.add(crArr.optString(i))

            val tList = mutableListOf<String>()
            val tArr = obj.optJSONArray("tables") ?: JSONArray()
            for (i in 0 until tArr.length()) tList.add(tArr.optString(i))

            val dList = mutableListOf<DiagramInfo>()
            val dArr = obj.optJSONArray("diagrams") ?: JSONArray()
            for (i in 0 until dArr.length()) {
                val dObj = dArr.optJSONObject(i) ?: continue
                val ks = mutableListOf<String>()
                val ksArr = dObj.optJSONArray("keyStructures") ?: JSONArray()
                for (k in 0 until ksArr.length()) ks.add(ksArr.optString(k))

                val bbList = mutableListOf<Int>()
                val bbArr = dObj.optJSONArray("boundingBox")
                if (bbArr != null && bbArr.length() >= 4) {
                    for (b in 0 until 4) bbList.add(bbArr.optInt(b))
                }

                dList.add(
                    DiagramInfo(
                        name = dObj.optString("name", "Diagram"),
                        description = dObj.optString("description", ""),
                        type = dObj.optString("type", "ANATOMY"),
                        keyStructures = ks,
                        isHighYield = dObj.optBoolean("isHighYield", true),
                        boundingBox = if (bbList.size == 4) bbList else null
                    )
                )
            }

            SlideStructuredData(
                slideNumber = slideNum,
                title = title,
                extractedText = text,
                importantConcepts = cList,
                mechanisms = mList,
                clinicalCorrelations = crList,
                tables = tList,
                diagrams = dList,
                imageDescription = imgDesc,
                examRelevance = examRel,
                sourceImagePath = path
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun serializeAnalysisResult(res: AcademicAnalysisResult): String {
        val root = JSONObject()
        root.put("summary", res.summary)

        val cArr = JSONArray()
        for (c in res.mainConcepts) {
            cArr.put(JSONObject().apply {
                put("title", c.title)
                put("explanation", c.explanation)
                put("highYieldPearl", c.highYieldPearl)
            })
        }
        root.put("mainConcepts", cArr)

        val dArr = JSONArray()
        for (d in res.definitions) {
            dArr.put(JSONObject().apply {
                put("term", d.term)
                put("definition", d.definition)
                put("context", d.context)
            })
        }
        root.put("definitions", dArr)

        val mArr = JSONArray()
        for (m in res.mechanisms) {
            val sArr = JSONArray()
            for (step in m.pathwaySteps) sArr.put(step)
            mArr.put(JSONObject().apply {
                put("name", m.name)
                put("pathwaySteps", sArr)
                put("clinicalRelevance", m.clinicalRelevance)
            })
        }
        root.put("mechanisms", mArr)

        val crArr = JSONArray()
        for (cr in res.clinicalCorrelations) {
            crArr.put(JSONObject().apply {
                put("condition", cr.condition)
                put("classicTriadOrSigns", cr.classicTriadOrSigns)
                put("usmlePearl", cr.usmlePearl)
            })
        }
        root.put("clinicalCorrelations", crArr)

        return root.toString()
    }

    fun parseStoredAnalysis(jsonString: String): AcademicAnalysisResult? {
        if (jsonString.isBlank()) return null
        return try {
            val root = JSONObject(jsonString)
            val summary = root.optString("summary", "")

            val concepts = mutableListOf<ConceptItem>()
            val cArr = root.optJSONArray("mainConcepts") ?: JSONArray()
            for (i in 0 until cArr.length()) {
                val obj = cArr.optJSONObject(i) ?: continue
                concepts.add(ConceptItem(obj.optString("title"), obj.optString("explanation"), obj.optString("highYieldPearl")))
            }

            val defs = mutableListOf<DefinitionItem>()
            val dArr = root.optJSONArray("definitions") ?: JSONArray()
            for (i in 0 until dArr.length()) {
                val obj = dArr.optJSONObject(i) ?: continue
                defs.add(DefinitionItem(obj.optString("term"), obj.optString("definition"), obj.optString("context")))
            }

            val mechs = mutableListOf<MechanismItem>()
            val mArr = root.optJSONArray("mechanisms") ?: JSONArray()
            for (i in 0 until mArr.length()) {
                val obj = mArr.optJSONObject(i) ?: continue
                val steps = mutableListOf<String>()
                val sArr = obj.optJSONArray("pathwaySteps") ?: JSONArray()
                for (j in 0 until sArr.length()) steps.add(sArr.optString(j))
                mechs.add(MechanismItem(obj.optString("name"), steps, obj.optString("clinicalRelevance")))
            }

            val corrs = mutableListOf<ClinicalCorrelationItem>()
            val crArr = root.optJSONArray("clinicalCorrelations") ?: JSONArray()
            for (i in 0 until crArr.length()) {
                val obj = crArr.optJSONObject(i) ?: continue
                corrs.add(ClinicalCorrelationItem(obj.optString("condition"), obj.optString("classicTriadOrSigns"), obj.optString("usmlePearl")))
            }

            AcademicAnalysisResult(
                summary = summary,
                mainConcepts = concepts,
                definitions = defs,
                mechanisms = mechs,
                classifications = emptyList(),
                importantDetails = emptyList(),
                clinicalCorrelations = corrs,
                examQuestions = emptyList()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSlideImages(jsonString: String): List<String> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val path = arr.optString(i)
                if (path.isNotBlank()) list.add(path)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
