package com.example.data.remote

import android.util.Base64
import android.util.Log
import com.example.data.local.entity.CardType
import com.example.data.local.entity.YieldLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ConceptItem(val title: String, val explanation: String, val highYieldPearl: String)
data class DefinitionItem(val term: String, val definition: String, val context: String)
data class MechanismItem(val name: String, val pathwaySteps: List<String>, val clinicalRelevance: String)
data class ClassificationItem(val category: String, val subcategories: List<String>, val criteria: String)
data class ImportantDetailItem(val topic: String, val point: String, val pitfalls: String)
data class ClinicalCorrelationItem(val condition: String, val classicTriadOrSigns: String, val usmlePearl: String)
data class ExamQuestionItem(
    val vignette: String,
    val question: String,
    val options: List<String>,
    val correctOption: String,
    val explanation: String
)

data class DiagramInfo(
    val name: String,
    val description: String,
    val type: String = "ANATOMY", // "ANATOMY", "PATHWAY", "HISTOLOGY", "RADIOLOGY", "ECG", "TABLE"
    val keyStructures: List<String> = emptyList(),
    val isHighYield: Boolean = true,
    val boundingBox: List<Int>? = null // [ymin, xmin, ymax, xmax] 0-1000 scale
)

data class SlideStructuredData(
    val slideNumber: Int,
    val title: String,
    val extractedText: String,
    val importantConcepts: List<String>,
    val mechanisms: List<String>,
    val clinicalCorrelations: List<String>,
    val tables: List<String>,
    val diagrams: List<DiagramInfo>,
    val imageDescription: String,
    val examRelevance: String,
    val sourceImagePath: String = ""
)

data class SlideBatchResult(
    val sectionTitle: String,
    val sectionSummary: String,
    val slides: List<SlideStructuredData>
)

data class AcademicAnalysisResult(
    val summary: String,
    val mainConcepts: List<ConceptItem>,
    val definitions: List<DefinitionItem>,
    val mechanisms: List<MechanismItem>,
    val classifications: List<ClassificationItem>,
    val importantDetails: List<ImportantDetailItem>,
    val clinicalCorrelations: List<ClinicalCorrelationItem>,
    val examQuestions: List<ExamQuestionItem>,
    val slides: List<SlideStructuredData> = emptyList()
)

data class GeneratedCardDto(
    val cardType: CardType,
    val front: String,
    val back: String,
    val clozeText: String,
    val explanation: String,
    val clinicalCorrelation: String,
    val yieldLevel: YieldLevel,
    val tags: List<String>,
    val occlusionLabels: List<String> = emptyList(),
    val imageFileName: String = "",
    val sourceSlideNumber: Int? = null
)

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val model = "gemini-2.5-flash"

    /**
     * Executes an HTTP request with up to maxAttempts tries using exponential backoff (2s, 4s, 8s).
     * Never logs the API key.
     */
    private suspend fun executeWithRetry(
        request: Request,
        maxAttempts: Int = 3
    ): Pair<Int, String> {
        var lastCode = 0
        var lastBody = ""
        for (attempt in 1..maxAttempts) {
            try {
                val response = client.newCall(request).execute()
                lastCode = response.code
                lastBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    return Pair(lastCode, lastBody)
                }
                // Retry on rate limit (429) or server errors (5xx)
                if (lastCode == 429 || lastCode >= 500) {
                    if (attempt < maxAttempts) {
                        val backoffMs = attempt * 2000L
                        delay(backoffMs)
                        continue
                    }
                } else {
                    // Non-retriable client error (e.g. 400 Bad Request, 403 Forbidden)
                    break
                }
            } catch (e: Exception) {
                lastCode = -1
                lastBody = e.localizedMessage ?: "Network error"
                if (attempt < maxAttempts) {
                    val backoffMs = attempt * 2000L
                    delay(backoffMs)
                }
            }
        }
        return Pair(lastCode, lastBody)
    }

    /**
     * Phase 1: Analyzes a batch of 6-8 slide images with 1 overlap page for context.
     * Extracts structured JSON per slide with diagrams, bounding boxes, pathways, and exam relevance.
     */
    suspend fun analyzeSlideBatch(
        apiKey: String,
        lectureTitle: String,
        targetSlides: List<Pair<Int, String>>, // (slideNumber, imagePath) to extract
        overlapSlide: Pair<Int, String>? = null, // (slideNumber, imagePath) previous slide for context
        runningSectionContext: String = ""
    ): Result<SlideBatchResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }
        if (targetSlides.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No target slides provided for batch."))
        }

        val targetSlideNumbers = targetSlides.map { it.first }
        val minSlide = targetSlideNumbers.minOrNull() ?: 1
        val maxSlide = targetSlideNumbers.maxOrNull() ?: minSlide

        val prompt = buildString {
            append("You are an expert academic medical school professor and USMLE instructor analyzing university medical lecture slides.\n")
            append("Lecture Title: \"$lectureTitle\"\n")
            if (runningSectionContext.isNotBlank()) {
                append("Prior Lecture Context: $runningSectionContext\n")
            }
            if (overlapSlide != null) {
                append("Note: Slide ${overlapSlide.first} is attached first as overlap context only.\n")
            }
            append("Analyze and return structured medical information for slides $minSlide through $maxSlide.\n\n")
            append("Examine each slide visually and textually:\n")
            append("- Exact transcribed text, headings, bullet points\n")
            append("- Anatomical illustrations, histology micrographs, radiology (CT/X-ray/MRI), ECG tracings\n")
            append("- Physiological & pharmacological pathways, receptors, mechanisms of action\n")
            append("- Classifications, staging criteria, and diagnostic algorithms\n")
            append("- High-yield clinical correlations and USMLE Board exam question angles\n")
            append("- For any visual diagram/image/table, specify a bounding box in normalized coordinates [ymin, xmin, ymax, xmax] on a 0-1000 scale.\n\n")
            append("Return STRICTLY a JSON object matching this exact schema:\n")
            append("""
{
  "section_title": "Descriptive section title for these slides",
  "section_summary": "1-2 sentence academic summary of this batch",
  "slides": [
    {
      "slide_number": $minSlide,
      "title": "Slide Title",
      "extracted_text": "Complete slide text content",
      "important_concepts": ["Concept 1", "Concept 2"],
      "mechanisms": ["Step-by-step pathway or physiological mechanism"],
      "clinical_correlations": ["Clinical vignette / diagnostic correlation"],
      "tables": ["Table content if present"],
      "diagrams": [
        {
          "name": "Diagram Name",
          "description": "Visual diagram, anatomy, or pathway description",
          "type": "ANATOMY or PATHWAY or HISTOLOGY or RADIOLOGY or ECG or TABLE",
          "key_structures": ["Structure A", "Structure B"],
          "is_high_yield": true,
          "bounding_box": [100, 150, 850, 900]
        }
      ],
      "image_description": "Detailed description of microscopic, anatomical, or clinical image",
      "exam_relevance": "High-yield Board question pearl or trap"
    }
  ]
}
            """.trimIndent())
        }

        val partsArray = JSONArray()
        partsArray.put(JSONObject().apply { put("text", prompt) })

        // Attach overlap slide if present
        if (overlapSlide != null) {
            val file = File(overlapSlide.second)
            if (file.exists()) {
                val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", b64)
                    })
                })
            }
        }

        // Attach target slide images
        for ((_, imagePath) in targetSlides) {
            val file = File(imagePath)
            if (file.exists()) {
                val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", b64)
                    })
                })
            }
        }

        val requestPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply { put("parts", partsArray) })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestPayload.toString().toRequestBody(jsonMediaType))
            .build()

        val (statusCode, responseBody) = executeWithRetry(request, maxAttempts = 3)
        if (statusCode != 200) {
            return@withContext Result.failure(
                Exception("Gemini analysis error ($statusCode): ${responseBody.take(300)}")
            )
        }

        try {
            val jsonResponse = JSONObject(responseBody)
            val rawText = jsonResponse.optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""

            val cleanText = cleanJsonString(rawText)
            val batchJson = JSONObject(cleanText)

            val sectionTitle = batchJson.optString("section_title", "Section (Slides $minSlide-$maxSlide)")
            val sectionSummary = batchJson.optString("section_summary", "")

            val parsedSlides = mutableListOf<SlideStructuredData>()
            val slidesArr = batchJson.optJSONArray("slides") ?: JSONArray()

            val imagePathMap = targetSlides.toMap()

            for (i in 0 until slidesArr.length()) {
                val sObj = slidesArr.optJSONObject(i) ?: continue
                val slideNum = sObj.optInt("slide_number", targetSlideNumbers.getOrElse(i) { minSlide + i })
                val imagePath = imagePathMap[slideNum] ?: targetSlides.getOrNull(i)?.second ?: ""

                val diagList = mutableListOf<DiagramInfo>()
                val dArr = sObj.optJSONArray("diagrams") ?: JSONArray()
                for (d in 0 until dArr.length()) {
                    val dObj = dArr.optJSONObject(d) ?: continue
                    val keyStructures = mutableListOf<String>()
                    val ksArr = dObj.optJSONArray("key_structures") ?: JSONArray()
                    for (k in 0 until ksArr.length()) keyStructures.add(ksArr.optString(k))

                    val bboxList = mutableListOf<Int>()
                    val bboxArr = dObj.optJSONArray("bounding_box")
                    if (bboxArr != null && bboxArr.length() >= 4) {
                        for (b in 0 until 4) bboxList.add(bboxArr.optInt(b))
                    }

                    diagList.add(
                        DiagramInfo(
                            name = dObj.optString("name", "Medical Diagram"),
                            description = dObj.optString("description", ""),
                            type = dObj.optString("type", "ANATOMY").uppercase(),
                            keyStructures = keyStructures,
                            isHighYield = dObj.optBoolean("is_high_yield", true),
                            boundingBox = if (bboxList.size == 4) bboxList else null
                        )
                    )
                }

                val impConcepts = mutableListOf<String>()
                val cArr = sObj.optJSONArray("important_concepts") ?: JSONArray()
                for (c in 0 until cArr.length()) impConcepts.add(cArr.optString(c))

                val mechs = mutableListOf<String>()
                val mArr = sObj.optJSONArray("mechanisms") ?: JSONArray()
                for (m in 0 until mArr.length()) mechs.add(mArr.optString(m))

                val corrs = mutableListOf<String>()
                val crArr = sObj.optJSONArray("clinical_correlations") ?: JSONArray()
                for (cr in 0 until crArr.length()) corrs.add(crArr.optString(cr))

                val tables = mutableListOf<String>()
                val tArr = sObj.optJSONArray("tables") ?: JSONArray()
                for (t in 0 until tArr.length()) tables.add(tArr.optString(t))

                parsedSlides.add(
                    SlideStructuredData(
                        slideNumber = slideNum,
                        title = sObj.optString("title", "Slide $slideNum"),
                        extractedText = sObj.optString("extracted_text", ""),
                        importantConcepts = impConcepts,
                        mechanisms = mechs,
                        clinicalCorrelations = corrs,
                        tables = tables,
                        diagrams = diagList,
                        imageDescription = sObj.optString("image_description", ""),
                        examRelevance = sObj.optString("exam_relevance", ""),
                        sourceImagePath = imagePath
                    )
                )
            }

            Result.success(
                SlideBatchResult(
                    sectionTitle = sectionTitle,
                    sectionSummary = sectionSummary,
                    slides = parsedSlides
                )
            )
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed parsing slide batch JSON", e)
            Result.failure(e)
        }
    }

    /**
     * Phase 1 & 4 & 5: Generates cards for a logical section of slides.
     * Incorporates image cards (histology, radiology, ECG, anatomy, pathways) and text/cloze cards.
     */
    suspend fun generateCardsForSection(
        apiKey: String,
        lectureTitle: String,
        subject: String,
        sectionTitle: String,
        sectionSlides: List<SlideStructuredData>,
        mediaOutputDir: File
    ): Result<List<GeneratedCardDto>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        mediaOutputDir.mkdirs()
        val sectionCards = mutableListOf<GeneratedCardDto>()

        // 1. Generate Image-Based Cards for slides with important visuals
        for (slide in sectionSlides) {
            if (slide.sourceImagePath.isBlank()) continue
            val sourceFile = File(slide.sourceImagePath)
            if (!sourceFile.exists()) continue

            for ((diagIdx, diagram) in slide.diagrams.withIndex()) {
                val diagType = diagram.type.uppercase()
                val isHistology = diagType.contains("HISTOLOGY") || diagType.contains("RADIOLOGY") || diagType.contains("ECG")
                val isPathway = diagType.contains("PATHWAY")
                val isAnatomy = diagType.contains("ANATOMY")
                val isTable = diagType.contains("TABLE")

                if (!isHistology && !isPathway && !isAnatomy && !isTable && !diagram.isHighYield) {
                    continue
                }

                // Phase 5: Try region cropping with validated bounding box; fall back to full page JPEG
                var targetImageFile: File = sourceFile
                var mediaFileName = "med_slide_${slide.slideNumber}_visual_${diagIdx + 1}.jpg"

                if (diagram.boundingBox != null && diagram.boundingBox.size == 4) {
                    val croppedName = com.example.util.PdfTextExtractor.cropBoundingBoxFromPage(
                        sourceImagePath = slide.sourceImagePath,
                        outputDir = mediaOutputDir,
                        baseName = "crop_s${slide.slideNumber}_d${diagIdx + 1}",
                        box = diagram.boundingBox
                    )
                    if (croppedName != null) {
                        mediaFileName = croppedName
                        targetImageFile = File(mediaOutputDir, croppedName)
                    } else {
                        // Fall back to full page JPEG
                        val destMedia = File(mediaOutputDir, mediaFileName)
                        if (!destMedia.exists()) sourceFile.copyTo(destMedia, overwrite = true)
                    }
                } else {
                    val destMedia = File(mediaOutputDir, mediaFileName)
                    if (!destMedia.exists()) sourceFile.copyTo(destMedia, overwrite = true)
                }

                val structuresJoined = diagram.keyStructures.joinToString(", ")
                val (frontPrompt, targetBack, explanation) = when {
                    isHistology -> Triple(
                        "<div><img src=\"$mediaFileName\"><br><b>Diagnostic Slide:</b> What is the diagnosis or pathognomonic microscopic/radiological finding shown above?</div>",
                        if (slide.title.isNotBlank()) slide.title else diagram.name,
                        "Key findings: ${diagram.description}. Diagnostic structures: ${structuresJoined.ifBlank { "See visual" }}. ${slide.examRelevance}"
                    )
                    isPathway -> Triple(
                        "<div><img src=\"$mediaFileName\"><br><b>Pathway Analysis:</b> Identify the rate-limiting step, primary enzyme, or mechanism illustrated above.</div>",
                        structuresJoined.ifBlank { diagram.name },
                        "Pathway cascade: ${diagram.description}. Clinical correlation: ${slide.clinicalCorrelations.firstOrNull() ?: slide.examRelevance}"
                    )
                    isAnatomy -> Triple(
                        "<div><img src=\"$mediaFileName\"><br><b>Anatomy Recall:</b> Identify the highlighted anatomical structure(s) and their physiological function.</div>",
                        structuresJoined.ifBlank { diagram.name },
                        "Anatomical description: ${diagram.description}. Clinical relevance: ${slide.examRelevance}"
                    )
                    else -> Triple(
                        "<div><img src=\"$mediaFileName\"><br><b>Clinical Visual:</b> ${diagram.name}</div>",
                        structuresJoined.ifBlank { slide.title },
                        diagram.description.ifBlank { slide.examRelevance }
                    )
                }

                sectionCards.add(
                    GeneratedCardDto(
                        cardType = CardType.IMAGE_OCCLUSION,
                        front = frontPrompt,
                        back = targetBack,
                        clozeText = "",
                        explanation = explanation,
                        clinicalCorrelation = slide.clinicalCorrelations.firstOrNull() ?: slide.examRelevance,
                        yieldLevel = if (diagram.isHighYield) YieldLevel.VERY_HIGH else YieldLevel.HIGH,
                        tags = listOf(subject, "$subject::$diagType", "ImageRecall", "USMLE_Step1"),
                        imageFileName = mediaFileName,
                        sourceSlideNumber = slide.slideNumber
                    )
                )
            }
        }

        // 2. Generate Active Recall and Cloze Flashcards for this section's academic content
        val sectionContent = buildString {
            append("Section: $sectionTitle\n")
            for (s in sectionSlides) {
                append("Slide ${s.slideNumber}: ${s.title}\n")
                if (s.extractedText.isNotBlank()) append("Text: ${s.extractedText}\n")
                if (s.mechanisms.isNotEmpty()) append("Mechanisms: ${s.mechanisms.joinToString("; ")}\n")
                if (s.clinicalCorrelations.isNotEmpty()) append("Clinical: ${s.clinicalCorrelations.joinToString("; ")}\n")
                if (s.examRelevance.isNotBlank()) append("Exam Relevance: ${s.examRelevance}\n")
                append("\n")
            }
        }

        val textCardResult = generateCardBatch(
            apiKey = apiKey,
            lectureTitle = lectureTitle,
            subject = subject,
            contentChunk = sectionContent,
            sectionName = sectionTitle
        )

        textCardResult.getOrNull()?.let { sectionCards.addAll(it) }

        Result.success(sectionCards)
    }

    /**
     * Phase 1 (Rule 9): Text chunking and analysis for pasted syllabus texts and lecture transcripts.
     * Replaces any truncation with structured chunking by paragraph/heading (10-12k chars).
     */
    suspend fun analyzeTextChunked(
        apiKey: String,
        lectureTitle: String,
        content: String
    ): Result<AcademicAnalysisResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        val textChunks = chunkTextLogically(content, maxChunkChars = 11000)
        val allConcepts = mutableListOf<ConceptItem>()
        val allDefs = mutableListOf<DefinitionItem>()
        val allMechs = mutableListOf<MechanismItem>()
        val allClassifications = mutableListOf<ClassificationItem>()
        val allDetails = mutableListOf<ImportantDetailItem>()
        val allCorrelations = mutableListOf<ClinicalCorrelationItem>()
        val allExamQuestions = mutableListOf<ExamQuestionItem>()
        val summaryBuilder = StringBuilder()

        for ((idx, chunkText) in textChunks.withIndex()) {
            val prompt = """
You are an expert academic medical school professor and USMLE preparation instructor.
Analyze Section ${idx + 1} of ${textChunks.size} from the medical lecture "$lectureTitle".

CONTENT:
$chunkText

Perform an in-depth academic medical analysis:
1. Summary: 1-2 sentences.
2. Main Concepts: title, explanation, highYieldPearl.
3. Definitions: term, definition, context.
4. Mechanisms: name, pathwaySteps, clinicalRelevance.
5. Classifications: category, subcategories, criteria.
6. Important Details: topic, point, pitfalls.
7. Clinical Correlations: condition, classicTriadOrSigns, usmlePearl.
8. Exam Questions: vignette, question, options, correctOption, explanation.

Return STRICTLY a JSON object matching this schema.
            """.trimIndent()

            val requestPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val (code, body) = executeWithRetry(request, maxAttempts = 3)
            if (code == 200) {
                try {
                    val jsonResponse = JSONObject(body)
                    val rawText = jsonResponse.optJSONArray("candidates")
                        ?.optJSONObject(0)?.optJSONObject("content")
                        ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""

                    val parsed = parseAcademicAnalysisJson(rawText)
                    if (summaryBuilder.isNotEmpty()) summaryBuilder.append(" ")
                    summaryBuilder.append(parsed.summary)

                    allConcepts.addAll(parsed.mainConcepts)
                    allDefs.addAll(parsed.definitions)
                    allMechs.addAll(parsed.mechanisms)
                    allClassifications.addAll(parsed.classifications)
                    allDetails.addAll(parsed.importantDetails)
                    allCorrelations.addAll(parsed.clinicalCorrelations)
                    allExamQuestions.addAll(parsed.examQuestions)
                } catch (e: Exception) {
                    Log.e("GeminiService", "Failed parsing text chunk $idx", e)
                }
            }
        }

        Result.success(
            AcademicAnalysisResult(
                summary = summaryBuilder.toString().ifBlank { "Comprehensive analysis complete." },
                mainConcepts = allConcepts.distinctBy { it.title },
                definitions = allDefs.distinctBy { it.term },
                mechanisms = allMechs.distinctBy { it.name },
                classifications = allClassifications.distinctBy { it.category },
                importantDetails = allDetails.distinctBy { it.topic },
                clinicalCorrelations = allCorrelations.distinctBy { it.condition },
                examQuestions = allExamQuestions
            )
        )
    }

    /**
     * Context-aware text chunking card generation for pasted transcripts.
     */
    suspend fun generateCardsFromTextChunked(
        apiKey: String,
        lectureTitle: String,
        subject: String,
        content: String,
        analysisResult: AcademicAnalysisResult?
    ): Result<List<GeneratedCardDto>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        val textChunks = chunkTextLogically(content, maxChunkChars = 11000)
        val allCards = mutableListOf<GeneratedCardDto>()

        for ((idx, chunkText) in textChunks.withIndex()) {
            val chunkContext = "Section ${idx + 1}/${textChunks.size} of lecture: $lectureTitle\n\n$chunkText"
            val result = generateCardBatch(
                apiKey = apiKey,
                lectureTitle = lectureTitle,
                subject = subject,
                contentChunk = chunkContext,
                sectionName = "Section ${idx + 1}"
            )
            result.getOrNull()?.let { allCards.addAll(it) }
        }

        Result.success(allCards)
    }

    private suspend fun generateCardBatch(
        apiKey: String,
        lectureTitle: String,
        subject: String,
        contentChunk: String,
        sectionName: String
    ): Result<List<GeneratedCardDto>> {
        val prompt = """
You are an expert medical educator creating high-yield Anki flashcards for USMLE Step 1/2 and medical exams.
Convert this medical content into active recall and cloze flashcards.

PRINCIPLES:
1. Active Recall: Clinical vignettes ("A 45-year-old male presents with..."), mechanisms, differential diagnoses.
2. Card Types:
   - "BASIC": Active recall question in 'front', concise target in 'back', pathophysiological rationale in 'explanation'.
   - "CLOZE": Complete clinical sentence using Anki cloze syntax {{c1::cloze term}} (and {{c2::...}} where clinically linked).
3. Tags: High-yield tags like ["$subject", "${subject}::Pathology", "USMLE_Step1", "HighYield"].

LECTURE: "$lectureTitle"
SUBJECT: "$subject"
SECTION: "$sectionName"
CONTENT:
$contentChunk

Generate 6-12 high-yield flashcards. Return STRICTLY a valid JSON array of card objects matching:
[
  {
    "cardType": "BASIC",
    "front": "What is the primary mechanism of action of...",
    "back": "Competitive inhibition of...",
    "clozeText": "",
    "explanation": "Cellular and biochemical rationale...",
    "clinicalCorrelation": "Board pearl or clinical vignette relevance...",
    "yieldLevel": "VERY_HIGH",
    "tags": ["$subject", "USMLE_Step1"]
  },
  {
    "cardType": "CLOZE",
    "front": "",
    "back": "",
    "clozeText": "In acute decompensated heart failure, {{c1::Nesiritide}} acts as a recombinant {{c2::B-type natriuretic peptide}} promoting natriuresis.",
    "explanation": "Increases cGMP in vascular smooth muscle and renal collecting ducts.",
    "clinicalCorrelation": "Counter-regulatory hormone in heart failure.",
    "yieldLevel": "VERY_HIGH",
    "tags": ["$subject", "Cardiology::HeartFailure"]
  }
]
        """.trimIndent()

        val requestPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestPayload.toString().toRequestBody(jsonMediaType))
            .build()

        val (statusCode, responseBody) = executeWithRetry(request, maxAttempts = 3)
        if (statusCode != 200) {
            return Result.failure(Exception("Card generation failed ($statusCode): ${responseBody.take(200)}"))
        }

        return try {
            val jsonResponse = JSONObject(responseBody)
            val rawText = jsonResponse.optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""
            val cards = parseGeneratedCardsJson(rawText)
            Result.success(cards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun chunkTextLogically(text: String, maxChunkChars: Int): List<String> {
        if (text.length <= maxChunkChars) return listOf(text)

        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n\n")
        val currentChunk = StringBuilder()

        for (para in paragraphs) {
            if (currentChunk.length + para.length > maxChunkChars && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk.clear()
            }
            currentChunk.append(para).append("\n\n")
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks
    }

    private fun parseAcademicAnalysisJson(rawJson: String): AcademicAnalysisResult {
        val clean = cleanJsonString(rawJson)
        val json = JSONObject(clean)

        val summary = json.optString("summary", json.optString("chunkSummary", "Comprehensive medical analysis."))

        val concepts = mutableListOf<ConceptItem>()
        val conceptsArr = json.optJSONArray("mainConcepts") ?: JSONArray()
        for (i in 0 until conceptsArr.length()) {
            val obj = conceptsArr.optJSONObject(i) ?: continue
            concepts.add(
                ConceptItem(
                    title = obj.optString("title"),
                    explanation = obj.optString("explanation"),
                    highYieldPearl = obj.optString("highYieldPearl")
                )
            )
        }

        val defs = mutableListOf<DefinitionItem>()
        val defsArr = json.optJSONArray("definitions") ?: JSONArray()
        for (i in 0 until defsArr.length()) {
            val obj = defsArr.optJSONObject(i) ?: continue
            defs.add(
                DefinitionItem(
                    term = obj.optString("term"),
                    definition = obj.optString("definition"),
                    context = obj.optString("context")
                )
            )
        }

        val mechanisms = mutableListOf<MechanismItem>()
        val mechsArr = json.optJSONArray("mechanisms") ?: JSONArray()
        for (i in 0 until mechsArr.length()) {
            val obj = mechsArr.optJSONObject(i) ?: continue
            val stepsList = mutableListOf<String>()
            val stepsArr = obj.optJSONArray("pathwaySteps") ?: JSONArray()
            for (j in 0 until stepsArr.length()) stepsList.add(stepsArr.optString(j))
            mechanisms.add(
                MechanismItem(
                    name = obj.optString("name"),
                    pathwaySteps = stepsList,
                    clinicalRelevance = obj.optString("clinicalRelevance")
                )
            )
        }

        val classifications = mutableListOf<ClassificationItem>()
        val classArr = json.optJSONArray("classifications") ?: JSONArray()
        for (i in 0 until classArr.length()) {
            val obj = classArr.optJSONObject(i) ?: continue
            val subsList = mutableListOf<String>()
            val subsArr = obj.optJSONArray("subcategories") ?: JSONArray()
            for (j in 0 until subsArr.length()) subsList.add(subsArr.optString(j))
            classifications.add(
                ClassificationItem(
                    category = obj.optString("category"),
                    subcategories = subsList,
                    criteria = obj.optString("criteria")
                )
            )
        }

        val details = mutableListOf<ImportantDetailItem>()
        val detArr = json.optJSONArray("importantDetails") ?: JSONArray()
        for (i in 0 until detArr.length()) {
            val obj = detArr.optJSONObject(i) ?: continue
            details.add(
                ImportantDetailItem(
                    topic = obj.optString("topic"),
                    point = obj.optString("point"),
                    pitfalls = obj.optString("pitfalls")
                )
            )
        }

        val correlations = mutableListOf<ClinicalCorrelationItem>()
        val corrArr = json.optJSONArray("clinicalCorrelations") ?: JSONArray()
        for (i in 0 until corrArr.length()) {
            val obj = corrArr.optJSONObject(i) ?: continue
            correlations.add(
                ClinicalCorrelationItem(
                    condition = obj.optString("condition"),
                    classicTriadOrSigns = obj.optString("classicTriadOrSigns"),
                    usmlePearl = obj.optString("usmlePearl")
                )
            )
        }

        val examQs = mutableListOf<ExamQuestionItem>()
        val examArr = json.optJSONArray("examQuestions") ?: JSONArray()
        for (i in 0 until examArr.length()) {
            val obj = examArr.optJSONObject(i) ?: continue
            val optsList = mutableListOf<String>()
            val optsArr = obj.optJSONArray("options") ?: JSONArray()
            for (j in 0 until optsArr.length()) optsList.add(optsArr.optString(j))
            examQs.add(
                ExamQuestionItem(
                    vignette = obj.optString("vignette"),
                    question = obj.optString("question"),
                    options = optsList,
                    correctOption = obj.optString("correctOption"),
                    explanation = obj.optString("explanation")
                )
            )
        }

        return AcademicAnalysisResult(
            summary = summary,
            mainConcepts = concepts,
            definitions = defs,
            mechanisms = mechanisms,
            classifications = classifications,
            importantDetails = details,
            clinicalCorrelations = correlations,
            examQuestions = examQs
        )
    }

    private fun parseGeneratedCardsJson(rawJson: String): List<GeneratedCardDto> {
        val clean = cleanJsonString(rawJson)
        val arr = JSONArray(clean)
        val list = mutableListOf<GeneratedCardDto>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val typeStr = obj.optString("cardType", "BASIC").uppercase()
            val cardType = when (typeStr) {
                "CLOZE" -> CardType.CLOZE
                "IMAGE_OCCLUSION", "OCCLUSION" -> CardType.IMAGE_OCCLUSION
                else -> CardType.BASIC
            }

            val yieldStr = obj.optString("yieldLevel", "HIGH").uppercase()
            val yieldLevel = when (yieldStr) {
                "VERY_HIGH" -> YieldLevel.VERY_HIGH
                "MEDIUM" -> YieldLevel.MEDIUM
                else -> YieldLevel.HIGH
            }

            val tagList = mutableListOf<String>()
            val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
            for (t in 0 until tagsArr.length()) {
                val tag = tagsArr.optString(t).trim()
                if (tag.isNotEmpty()) tagList.add(tag)
            }

            val front = obj.optString("front")
            val back = obj.optString("back")
            val cloze = obj.optString("clozeText")
            val explanation = obj.optString("explanation")
            val correlation = obj.optString("clinicalCorrelation")

            list.add(
                GeneratedCardDto(
                    cardType = cardType,
                    front = front,
                    back = back,
                    clozeText = if (cloze.isNotBlank()) cloze else front,
                    explanation = explanation,
                    clinicalCorrelation = correlation,
                    yieldLevel = yieldLevel,
                    tags = tagList,
                    occlusionLabels = emptyList(),
                    imageFileName = obj.optString("imageFileName", "")
                )
            )
        }
        return list
    }

    private fun cleanJsonString(text: String): String {
        var str = text.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json").trim()
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```").trim()
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```").trim()
        }
        return str
    }
}
