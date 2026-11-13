package edu.uph.m24si2.aitriageradiology.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a registered user account in the system
 */
data class User(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String = "",
    @SerializedName("full_name") val fullName: String,
    @SerializedName("role") val role: String = "Radiologist",
    @SerializedName("created_at") val createdAt: String? = null
)

data class LoginRequest(
    @SerializedName("doctor_email") val doctorEmail: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("doctor_email") val doctorEmail: String,
    @SerializedName("email") val email: String,
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("password") val password: String,
    @SerializedName("password_confirmation") val passwordConfirmation: String,
    @SerializedName("confirm_password") val confirmPassword: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("role") val role: String
)

data class AuthResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("user") val user: User? = null
)
