package edu.uph.m24si2.aitriageradiology.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request payload sent to AI Triage Backend
 */
data class TriageRequest(
    @SerializedName("patient_id") val patientId: String,
    @SerializedName("patient_name") val patientName: String,
    @SerializedName("age") val age: Int,
    @SerializedName("gender") val gender: String,
    @SerializedName("modality") val modality: String = "Chest X-Ray",
    @SerializedName("image_base64") val imageBase64: String? = null
)

/**
 * Finding item representation
 */
data class FindingItem(
    @SerializedName("condition") val condition: String,
    @SerializedName("probability") val probability: Double,
    @SerializedName("severity") val severity: String // HIGH, MEDIUM, LOW
)

/**
 * Response returned from AI Triage Backend
 */
data class TriageResponse(
    @SerializedName("status") val status: String, // "success" or "error"
    @SerializedName("triage_category") val triageCategory: String, // "CRITICAL", "URGENT", "NORMAL"
    @SerializedName("primary_finding") val primaryFinding: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("risk_score") val riskScore: Double,
    @SerializedName("recommendation") val recommendation: String,
    @SerializedName("findings") val findings: List<FindingItem> = emptyList(),
    @SerializedName("processed_at") val processedAt: String? = null,
    @SerializedName("message") val message: String? = null
)

/**
 * Entity representing a triaged record stored in local scan history
 */
data class PatientScanRecord(
    val id: String,
    val patientId: String,
    val patientName: String,
    val age: Int,
    val gender: String,
    val modality: String,
    val triageCategory: String,
    val primaryFinding: String,
    val confidence: Double,
    val recommendation: String,
    val timestamp: String,
    val imageUriString: String? = null,
    val isSamplePreset: Boolean = false,
    val samplePresetName: String? = null
)
