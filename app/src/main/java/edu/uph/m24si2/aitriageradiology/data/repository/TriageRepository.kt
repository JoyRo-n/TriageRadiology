package edu.uph.m24si2.aitriageradiology.data.repository

import android.content.Context
import android.net.Uri
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

    suspend fun checkBackendConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = ApiClient.getService(context)
            // Try PyTorch / FastAPI / Flask health, root, or docs endpoints
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
                // Ignore and attempt JSON endpoint or mock fallback below
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

        // 3. Fallback to mock simulation so user can always evaluate the UI
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

    fun generateMockTriageResponse(presetCondition: String?): TriageResponse {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val conditionLower = presetCondition?.lowercase(Locale.getDefault()) ?: ""

        return when {
            conditionLower.contains("adenocarcinoma") || conditionLower.contains("critical") -> {
                TriageResponse(
                    status = "success",
                    triageCategory = "CRITICAL",
                    primaryFinding = "Adenocarcinoma (Primary Lung CT Lesion)",
                    confidence = 0.952,
                    riskScore = 9.7,
                    recommendation = "STAT Radiologist alert! Solid peripheral pulmonary nodule with spiculation detected on CT scan. Immediate oncology consultation and biopsy planning required.",
                    findings = listOf(
                        FindingItem("Adenocarcinoma Nodule", 0.952, "HIGH"),
                        FindingItem("Mediastinal Lymphadenopathy", 0.420, "MEDIUM"),
                        FindingItem("Pleural Thickening", 0.210, "LOW")
                    ),
                    processedAt = now
                )
            }
            conditionLower.contains("large_cell") || conditionLower.contains("large.cell") -> {
                TriageResponse(
                    status = "success",
                    triageCategory = "CRITICAL",
                    primaryFinding = "Large Cell Neuroendocrine Carcinoma (Hilum Mass)",
                    confidence = 0.938,
                    riskScore = 9.4,
                    recommendation = "STAT Radiologist alert! Large central necrotic pulmonary mass with hilar involvement. Urgent thoracic surgery and oncology review.",
                    findings = listOf(
                        FindingItem("Large Cell Carcinoma", 0.938, "HIGH"),
                        FindingItem("Hilar Infiltration", 0.680, "HIGH"),
                        FindingItem("Central Necrosis", 0.450, "MEDIUM")
                    ),
                    processedAt = now
                )
            }
            conditionLower.contains("squamous") || conditionLower.contains("urgent") -> {
                TriageResponse(
                    status = "success",
                    triageCategory = "URGENT",
                    primaryFinding = "Squamous Cell Carcinoma (Bronchial Cavitary Nodule)",
                    confidence = 0.912,
                    riskScore = 8.5,
                    recommendation = "Urgent consultation within 2 hours. Thick-walled cavitary lesion in proximal bronchial airway on CT scan.",
                    findings = listOf(
                        FindingItem("Squamous Cell Carcinoma", 0.912, "HIGH"),
                        FindingItem("Cavitary Nodule", 0.730, "MEDIUM"),
                        FindingItem("Atelectasis", 0.310, "LOW")
                    ),
                    processedAt = now
                )
            }
            else -> {
                TriageResponse(
                    status = "success",
                    triageCategory = "NORMAL",
                    primaryFinding = "Normal Thoracic CT Scan (No Malignancy)",
                    confidence = 0.968,
                    riskScore = 1.1,
                    recommendation = "Standard routine queue. Lung parenchymal attenuation normal, no pulmonary nodules or mediastinal lymphadenopathy.",
                    findings = listOf(
                        FindingItem("Normal Thoracic CT", 0.968, "LOW"),
                        FindingItem("Calcified Granuloma", 0.040, "LOW")
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
            timestamp = response.processedAt ?: now,
            imageUriString = imageUriString,
            isSamplePreset = isSamplePreset,
            samplePresetName = samplePresetName
        )
    }
}
