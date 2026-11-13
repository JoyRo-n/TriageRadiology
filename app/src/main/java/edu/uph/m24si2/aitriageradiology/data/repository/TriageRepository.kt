package edu.uph.m24si2.aitriageradiology.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.uph.m24si2.aitriageradiology.data.api.ApiClient
import edu.uph.m24si2.aitriageradiology.data.model.FindingItem
import edu.uph.m24si2.aitriageradiology.data.model.PatientScanRecord
import edu.uph.m24si2.aitriageradiology.data.model.TriageRequest
import edu.uph.m24si2.aitriageradiology.data.model.TriageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class TriageResult {
    data class Success(val response: TriageResponse, val isMockFallback: Boolean = false) : TriageResult()
    data class Error(val message: String) : TriageResult()
}

class TriageRepository(private val context: Context) {

    companion object {
        private const val PREFS_HISTORY = "ai_triage_history_prefs"
        private const val KEY_HISTORY_DATA = "history_records_json"
    }

    fun saveHistoryRecords(context: Context, records: List<PatientScanRecord>) {
        try {
            val json = Gson().toJson(records)
            context.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY_DATA, json)
                .apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun loadHistoryRecords(context: Context): List<PatientScanRecord> {
        return try {
            val json = context.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
                .getString(KEY_HISTORY_DATA, null) ?: return emptyList()
            val type = object : TypeToken<List<PatientScanRecord>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun checkBackendConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = ApiClient.getService(context)
            val resRoot = try { service.checkHealthRoot() } catch (e: Exception) { null }
            if (resRoot != null && (resRoot.isSuccessful || resRoot.code() == 404)) return@withContext true

            val resDocs = try { service.checkHealthDocs() } catch (e: Exception) { null }
            if (resDocs != null && (resDocs.isSuccessful || resDocs.code() == 404)) return@withContext true

            val resApi = try { service.checkHealthApi() } catch (e: Exception) { null }
            if (resApi != null && (resApi.isSuccessful || resApi.code() == 404)) return@withContext true

            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun analyzeTriage(
        request: TriageRequest,
        presetCondition: String? = null,
        imageUri: Uri? = null
    ): TriageResult = withContext(Dispatchers.IO) {
        val service = ApiClient.getService(context)

        // 1. If an image file or camera capture Uri exists, send POST /analyze-scan (multipart/form-data)
        if (imageUri != null) {
            try {
                val filePart = uriToMultipartBodyPart(imageUri, "file")
                if (filePart != null) {
                    val response = service.analyzeScanPyTorch(filePart)
                    if (response.isSuccessful && response.body() != null) {
                        return@withContext TriageResult.Success(response.body()!!, isMockFallback = false)
                    }
                }
            } catch (e: Exception) {
                // Fallback below
            }

            // Local Lung CT validation if backend is offline when scanning a custom photo
            if (!isValidLungCtBitmap(imageUri)) {
                val wrongMsg = "Wrong image please try again"
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                return@withContext TriageResult.Success(
                    TriageResponse(
                        status = "error",
                        triageCategory = wrongMsg,
                        primaryFinding = wrongMsg,
                        confidence = 0.0,
                        riskScore = 0.0,
                        recommendation = wrongMsg,
                        cancerLocation = wrongMsg,
                        cancerExplanation = wrongMsg,
                        findings = emptyList(),
                        processedAt = now,
                        message = wrongMsg
                    ),
                    isMockFallback = false
                )
            }
        }

        // 2. Try POST /api/triage JSON endpoint
        try {
            val response = service.analyzeTriageJson(request)
            if (response.isSuccessful && response.body() != null) {
                return@withContext TriageResult.Success(response.body()!!, isMockFallback = false)
            }
        } catch (e: Exception) {
            // Server offline / unreachable
        }

        // 3. Fallback to mock simulation with full lung cancer explanations
        val mock = generateMockTriageResponse(presetCondition ?: request.patientId)
        TriageResult.Success(mock, isMockFallback = true)
    }

    private fun uriToMultipartBodyPart(uri: Uri, paramName: String): MultipartBody.Part? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return null
            inputStream.close()

            val mediaType = (context.contentResolver.getType(uri) ?: "image/jpeg").toMediaTypeOrNull()
            val requestBody = bytes.toRequestBody(mediaType)
            val filename = "ct_scan_${System.currentTimeMillis()}.jpg"

            MultipartBody.Part.createFormData(paramName, filename, requestBody)
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidLungCtBitmap(uri: Uri): Boolean {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return false
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            if (bmp == null || bmp.width < 64 || bmp.height < 64) return false

            val w = bmp.width
            val h = bmp.height
            var totalSpread = 0.0
            var colorfulCount = 0
            var totalGray = 0.0
            var darkCount = 0
            var brightCount = 0
            var sampleCount = 0

            val stepX = (w / 64).coerceAtLeast(1)
            val stepY = (h / 64).coerceAtLeast(1)

            for (y in 0 until h step stepY) {
                for (x in 0 until w step stepX) {
                    val px = bmp.getPixel(x, y)
                    val r = Color.red(px) / 255.0
                    val g = Color.green(px) / 255.0
                    val b = Color.blue(px) / 255.0
                    val maxC = maxOf(r, g, b)
                    val minC = minOf(r, g, b)
                    val spread = maxC - minC
                    totalSpread += spread
                    if (spread > 0.16) colorfulCount++

                    val gray = (r + g + b) / 3.0
                    totalGray += gray
                    if (gray < 0.28) darkCount++
                    if (gray > 0.85) brightCount++
                    sampleCount++
                }
            }
            if (sampleCount == 0) return false
            val meanSpread = totalSpread / sampleCount
            val colorfulRatio = colorfulCount.toDouble() / sampleCount
            val meanGray = totalGray / sampleCount
            val darkRatio = darkCount.toDouble() / sampleCount
            val brightRatio = brightCount.toDouble() / sampleCount

            if (meanSpread > 0.085 || colorfulRatio > 0.075) return false
            if (meanGray < 0.07 || meanGray > 0.52) return false
            if (darkRatio < 0.25 || darkRatio > 0.88) return false
            if (brightRatio > 0.22) return false
            true
        } catch (e: Exception) {
            false
        }
    }

    fun generateMockTriageResponse(presetCondition: String?): TriageResponse {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val conditionLower = presetCondition?.lowercase(Locale.getDefault()) ?: ""

        return when {
            conditionLower.contains("adenocarcinoma") || conditionLower.contains("sample 1") -> {
                TriageResponse(
                    status = "success",
                    triageCategory = "CRITICAL",
                    primaryFinding = "Adenocarcinoma (Mucus-Producing Cell Nodule)",
                    confidence = 0.952,
                    riskScore = 9.7,
                    recommendation = "STAT Radiologist alert! Solid peripheral pulmonary nodule with spiculation detected in outer lung field. Immediate oncology referral & biopsy required.",
                    cancerLocation = "Outer Areas of the Lungs (Mucus-Producing Cells)",
                    cancerExplanation = "This is the most common type of lung cancer. It begins in the cells that produce mucus, usually developing in the outer areas of the lungs. While it is often caused by smoking, it is also the most common type of lung cancer found in people who have never smoked.",
                    findings = listOf(
                        FindingItem("Adenocarcinoma Nodule", 0.952, "HIGH"),
                        FindingItem("Peripheral Spiculation", 0.780, "HIGH"),
                        FindingItem("Mediastinal Lymphadenopathy", 0.350, "MEDIUM")
                    ),
                    processedAt = now
                )
            }
            conditionLower.contains("large_cell") || conditionLower.contains("large cell") || conditionLower.contains("sample 2") -> {
                TriageResponse(
                    status = "success",
                    triageCategory = "CRITICAL",
                    primaryFinding = "Large Cell Carcinoma (Rapid Growth Mass)",
                    confidence = 0.938,
                    riskScore = 9.4,
                    recommendation = "STAT Radiologist alert! Large unusually shaped pulmonary lesion detected. Rapid growth risk; immediate thoracic surgery and oncology review.",
                    cancerLocation = "Can Start Anywhere in the Lungs",
                    cancerExplanation = "This type is named for how the cancer cells look under a microscope—large and unusually shaped. It can start anywhere in the lungs and is known to grow and spread very quickly, which can make it more challenging to treat.",
                    findings = listOf(
                        FindingItem("Large Cell Carcinoma Mass", 0.938, "HIGH"),
                        FindingItem("Central Necrosis", 0.650, "HIGH"),
                        FindingItem("Hilar Lymphadenopathy", 0.420, "MEDIUM")
                    ),
                    processedAt = now
                )
            }
            conditionLower.contains("squamous") || conditionLower.contains("sample 3") -> {
                TriageResponse(
                    status = "success",
                    triageCategory = "URGENT",
                    primaryFinding = "Squamous Cell Carcinoma (Central Airways Lesion)",
                    confidence = 0.912,
                    riskScore = 8.5,
                    recommendation = "Urgent Radiologist consultation within 2 hours. Thick-walled cavitary lesion in central bronchial airways. Strong association with smoking history.",
                    cancerLocation = "Central Part of the Lungs / Main Airways (Bronchi)",
                    cancerExplanation = "This cancer starts in the thin, flat cells (squamous cells) that line the inside of the main airways (bronchi). It is typically found in the central part of the lungs and is very strongly linked to a history of smoking.",
                    findings = listOf(
                        FindingItem("Squamous Cell Carcinoma", 0.912, "HIGH"),
                        FindingItem("Bronchial Cavitary Lesion", 0.730, "MEDIUM"),
                        FindingItem("Bronchial Obstruction", 0.410, "MEDIUM")
                    ),
                    processedAt = now
                )
            }
            else -> {
                TriageResponse(
                    status = "success",
                    triageCategory = "NORMAL",
                    primaryFinding = "Normal Thoracic CT Scan (No Lung Cancer Detected)",
                    confidence = 0.968,
                    riskScore = 1.1,
                    recommendation = "Standard routine review queue. Lung parenchymal attenuation is normal with clear bilateral lung fields and no pulmonary nodules.",
                    cancerLocation = "Clear Bilateral Lung Fields",
                    cancerExplanation = "Normal Thoracic CT Scan. Lung parenchymal attenuation is normal, with clear bilateral lung fields and no suspicious pulmonary nodules, masses, or mediastinal lymphadenopathy.",
                    findings = listOf(
                        FindingItem("Normal Thoracic CT Scan", 0.968, "LOW"),
                        FindingItem("No Pulmonary Nodules", 0.980, "LOW")
                    ),
                    processedAt = now
                )
            }
        }
    }

    fun createScanRecord(
        request: TriageRequest,
        response: TriageResponse,
        imageUriString: String? = null,
        isSamplePreset: Boolean = false,
        samplePresetName: String? = null
    ): PatientScanRecord {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return PatientScanRecord(
            id = UUID.randomUUID().toString(),
            patientId = request.patientId,
            patientName = request.patientName,
            age = request.age,
            gender = request.gender,
            modality = request.modality,
            triageCategory = response.triageCategory,
            primaryFinding = response.primaryFinding,
            confidence = response.confidence,
            recommendation = response.recommendation,
            cancerExplanation = response.cancerExplanation,
            cancerLocation = response.cancerLocation,
            timestamp = response.processedAt ?: now,
            imageUriString = imageUriString,
            isSamplePreset = isSamplePreset,
            samplePresetName = samplePresetName
        )
    }
}
