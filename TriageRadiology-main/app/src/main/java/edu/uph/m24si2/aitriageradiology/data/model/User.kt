package edu.uph.m24si2.aitriageradiology.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a registered user account in the system
 */
data class User(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("role") val role: String = "Radiologist",
    @SerializedName("created_at") val createdAt: String? = null
)
