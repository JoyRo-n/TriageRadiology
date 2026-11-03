package edu.uph.m24si2.aitriageradiology.data.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"
    const val ALT_PORT_5000_URL = "http://10.0.2.2:5000/"
    private const val PREFS_NAME = "ai_triage_settings"
    private const val KEY_BASE_URL = "backend_base_url"

    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var cachedService: TriageApiService? = null

    fun getBaseUrl(context: Context): String {
        val prefs = getPrefs(context)
        val url = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        return sanitizeUrl(url)
    }

    fun setBaseUrl(context: Context, newUrl: String) {
        val sanitized = sanitizeUrl(newUrl)
        getPrefs(context).edit().putString(KEY_BASE_URL, sanitized).apply()
        currentBaseUrl = sanitized
        cachedService = null
    }

    fun getService(context: Context): TriageApiService {
        val targetUrl = getBaseUrl(context)
        if (cachedService != null && currentBaseUrl == targetUrl) {
            return cachedService!!
        }

        currentBaseUrl = targetUrl
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(targetUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(TriageApiService::class.java)
        cachedService = service
        return service
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun sanitizeUrl(url: String): String {
        var trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        if (!trimmed.endsWith("/")) {
            trimmed = "$trimmed/"
        }
        return trimmed
    }
}
