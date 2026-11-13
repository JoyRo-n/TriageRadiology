package edu.uph.m24si2.aitriageradiology.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.uph.m24si2.aitriageradiology.data.api.ApiClient
import edu.uph.m24si2.aitriageradiology.data.model.AuthResponse
import edu.uph.m24si2.aitriageradiology.data.model.LoginRequest
import edu.uph.m24si2.aitriageradiology.data.model.RegisterRequest
import edu.uph.m24si2.aitriageradiology.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuthRepository(private val context: Context) {

    companion object {
        private const val PREFS_AUTH = "ai_triage_auth_prefs"
        private const val KEY_USERS_JSON = "registered_users_list"
        private const val KEY_ACTIVE_USER_EMAIL = "active_logged_in_email"
        private const val KEY_AUTH_TOKEN = "active_session_token"

        const val DEFAULT_DEMO_EMAIL = "doctor@hospital.com"
        const val DEFAULT_DEMO_PASS = "password123"
        const val DEFAULT_DEMO_NAME = "Dr. Alexander Wright"
        const val DEFAULT_DEMO_ROLE = "Senior Radiologist"
    }

    init {
        ensureDefaultUsersExist()
    }

    private fun ensureDefaultUsersExist() {
        val users = loadAllUsers().toMutableList()
        if (users.isEmpty()) {
            val demoUser = User(
                email = DEFAULT_DEMO_EMAIL,
                password = DEFAULT_DEMO_PASS,
                fullName = DEFAULT_DEMO_NAME,
                role = DEFAULT_DEMO_ROLE,
                createdAt = getCurrentTimestamp()
            )
            users.add(demoUser)
            saveAllUsers(users)
        }
    }

    fun loadAllUsers(): List<User> {
        return try {
            val json = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .getString(KEY_USERS_JSON, null) ?: return emptyList()
            val type = object : TypeToken<List<User>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAllUsers(users: List<User>) {
        try {
            val json = Gson().toJson(users)
            context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_USERS_JSON, json)
                .apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun upsertLocalUser(user: User, plainPassword: String) {
        val users = loadAllUsers().toMutableList()
        val emailClean = user.email.trim().lowercase(Locale.getDefault())
        users.removeAll { it.email.equals(emailClean, ignoreCase = true) }
        users.add(
            user.copy(
                email = emailClean,
                password = plainPassword,
                createdAt = user.createdAt ?: getCurrentTimestamp()
            )
        )
        saveAllUsers(users)
    }

    /**
     * Registers a new doctor account on the FastAPI backend (POST /register),
     * with local fallback only if the backend server is unreachable.
     */
    suspend fun registerUserOnline(user: User, confirmPassword: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val emailClean = user.email.trim().lowercase(Locale.getDefault())
            try {
                val service = ApiClient.getService(context)
                val req = RegisterRequest(
                    doctorEmail = emailClean,
                    email = emailClean,
                    newPassword = user.password,
                    password = user.password,
                    passwordConfirmation = confirmPassword,
                    confirmPassword = confirmPassword,
                    fullName = user.fullName,
                    role = user.role
                )
                val response = service.registerDoctor(req)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val syncedUser = body.user ?: user.copy(
                        email = emailClean,
                        createdAt = getCurrentTimestamp()
                    )
                    upsertLocalUser(syncedUser, user.password)
                    saveActiveSession(syncedUser.email, body.token)
                    return@withContext Pair(true, body.message ?: "Account created successfully!")
                } else {
                    val errJson = response.errorBody()?.string()
                    val parsedErr = try {
                        Gson().fromJson(errJson, AuthResponse::class.java)
                    } catch (e: Exception) {
                        null
                    }
                    val errMsg = parsedErr?.message ?: parsedErr?.detail ?: "Registration failed (${response.code()})"
                    return@withContext Pair(false, errMsg)
                }
            } catch (e: Exception) {
                // Offline fallback if server is not reachable
                return@withContext registerUser(user)
            }
        }

    /**
     * Authenticates a doctor account against the FastAPI backend (POST /login),
     * verifying email and password.
     */
    suspend fun loginUserOnline(emailInput: String, passwordInput: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val emailClean = emailInput.trim().lowercase(Locale.getDefault())
            if (emailClean.isEmpty()) {
                return@withContext Pair(false, "Please enter your email address.")
            }
            if (passwordInput.isEmpty()) {
                return@withContext Pair(false, "Please enter your password.")
            }

            try {
                val service = ApiClient.getService(context)
                val req = LoginRequest(
                    doctorEmail = emailClean,
                    email = emailClean,
                    password = passwordInput
                )
                val response = service.loginDoctor(req)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val syncedUser = body.user ?: User(
                        email = emailClean,
                        password = passwordInput,
                        fullName = DEFAULT_DEMO_NAME,
                        role = DEFAULT_DEMO_ROLE,
                        createdAt = getCurrentTimestamp()
                    )
                    upsertLocalUser(syncedUser, passwordInput)
                    saveActiveSession(syncedUser.email, body.token)
                    return@withContext Pair(true, body.message ?: "Welcome back, ${syncedUser.fullName}!")
                } else {
                    val errJson = response.errorBody()?.string()
                    val parsedErr = try {
                        Gson().fromJson(errJson, AuthResponse::class.java)
                    } catch (e: Exception) {
                        null
                    }
                    val errMsg = parsedErr?.message ?: parsedErr?.detail ?: "Invalid doctor email or password."
                    return@withContext Pair(false, errMsg)
                }
            } catch (e: Exception) {
                // Offline fallback if server is unreachable
                return@withContext loginUser(emailClean, passwordInput)
            }
        }

    fun registerUser(user: User): Pair<Boolean, String> {
        val users = loadAllUsers().toMutableList()
        val existing = users.find { it.email.equals(user.email.trim(), ignoreCase = true) }
        if (existing != null) {
            return Pair(false, "An account with this email already exists.")
        }

        val newUser = user.copy(
            email = user.email.trim().lowercase(Locale.getDefault()),
            createdAt = getCurrentTimestamp()
        )
        users.add(newUser)
        saveAllUsers(users)
        saveActiveSession(newUser.email)
        return Pair(true, "Account created successfully!")
    }

    fun loginUser(emailInput: String, passwordInput: String): Pair<Boolean, String> {
        val emailClean = emailInput.trim().lowercase(Locale.getDefault())
        if (emailClean.isEmpty()) {
            return Pair(false, "Please enter an email address.")
        }

        val users = loadAllUsers().toMutableList()
        val foundUser = users.find { it.email.equals(emailClean, ignoreCase = true) }

        if (foundUser != null) {
            if (foundUser.password.isNotEmpty() && foundUser.password != passwordInput) {
                return Pair(false, "Invalid doctor email or password.")
            }
            saveActiveSession(foundUser.email)
            return Pair(true, "Welcome back, ${foundUser.fullName}!")
        }

        return Pair(false, "Invalid doctor email or password. Please Sign Up first.")
    }

    fun saveActiveSession(email: String, token: String? = null) {
        val editor = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_USER_EMAIL, email.trim().lowercase(Locale.getDefault()))
        if (token != null) {
            editor.putString(KEY_AUTH_TOKEN, token)
        }
        editor.apply()
    }

    fun getCurrentUser(): User? {
        val activeEmail = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_USER_EMAIL, null) ?: return null
        return loadAllUsers().find { it.email.equals(activeEmail, ignoreCase = true) }
    }

    fun isLoggedIn(): Boolean {
        return getCurrentUser() != null
    }

    suspend fun logoutOnline() = withContext(Dispatchers.IO) {
        val token = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .getString(KEY_AUTH_TOKEN, null)
        try {
            if (!token.isNullOrEmpty()) {
                ApiClient.getService(context).logoutDoctor("Bearer $token")
            }
        } catch (e: Exception) {
            // Ignore network error on logout
        } finally {
            logout()
        }
    }

    fun logout() {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_USER_EMAIL)
            .remove(KEY_AUTH_TOKEN)
            .apply()
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
