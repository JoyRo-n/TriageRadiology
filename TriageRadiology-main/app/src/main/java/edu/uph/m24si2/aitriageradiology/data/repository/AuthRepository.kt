package edu.uph.m24si2.aitriageradiology.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.uph.m24si2.aitriageradiology.data.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuthRepository(private val context: Context) {

    companion object {
        private const val PREFS_AUTH = "ai_triage_auth_prefs"
        private const val KEY_USERS_JSON = "registered_users_list"
        private const val KEY_ACTIVE_USER_EMAIL = "active_logged_in_email"

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
            saveActiveSession(foundUser.email)
            return Pair(true, "Welcome back, ${foundUser.fullName}!")
        }

        // Automatically register and sign in any user if not already in system
        val usernamePart = emailClean.substringBefore("@").replaceFirstChar { it.uppercase() }
        val autoName = if (usernamePart.lowercase() == "admin" || usernamePart.lowercase() == "doctor") {
            "Dr. Alexander Wright"
        } else {
            "Dr. $usernamePart"
        }

        val newUser = User(
            email = emailClean,
            password = passwordInput,
            fullName = autoName,
            role = "Senior Radiologist",
            createdAt = getCurrentTimestamp()
        )

        users.add(newUser)
        saveAllUsers(users)
        saveActiveSession(newUser.email)

        return Pair(true, "Welcome, ${newUser.fullName}!")
    }

    fun saveActiveSession(email: String) {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_USER_EMAIL, email.trim().lowercase(Locale.getDefault()))
            .apply()
    }

    fun getCurrentUser(): User? {
        val activeEmail = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_USER_EMAIL, null) ?: return null
        return loadAllUsers().find { it.email.equals(activeEmail, ignoreCase = true) }
    }

    fun isLoggedIn(): Boolean {
        return getCurrentUser() != null
    }

    fun logout() {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_USER_EMAIL)
            .apply()
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
