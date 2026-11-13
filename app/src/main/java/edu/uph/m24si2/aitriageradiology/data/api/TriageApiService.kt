package edu.uph.m24si2.aitriageradiology.data.api

import edu.uph.m24si2.aitriageradiology.data.model.AuthResponse
import edu.uph.m24si2.aitriageradiology.data.model.LoginRequest
import edu.uph.m24si2.aitriageradiology.data.model.RegisterRequest
import edu.uph.m24si2.aitriageradiology.data.model.TriageRequest
import edu.uph.m24si2.aitriageradiology.data.model.TriageResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TriageApiService {

    @GET("api/health")
    suspend fun checkHealthApi(): Response<ResponseBody>

    @GET(".")
    suspend fun checkHealthRoot(): Response<ResponseBody>

    @GET("docs")
    suspend fun checkHealthDocs(): Response<ResponseBody>

    @POST("login")
    suspend fun loginDoctor(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @POST("register")
    suspend fun registerDoctor(
        @Body request: RegisterRequest
    ): Response<AuthResponse>

    @POST("logout")
    suspend fun logoutDoctor(
        @Header("Authorization") bearerToken: String? = null
    ): Response<ResponseBody>

    /**
     * Swagger UI endpoint: POST /analyze-scan (multipart/form-data)
     * Form field: file (UploadFile)
     */
    @Multipart
    @POST("analyze-scan")
    suspend fun analyzeScanPyTorch(
        @Part file: MultipartBody.Part
    ): Response<TriageResponse>

    @POST("api/triage")
    suspend fun analyzeTriageJson(
        @Body request: TriageRequest
    ): Response<TriageResponse>

    @Multipart
    @POST("api/triage/upload")
    suspend fun analyzeTriageMultipart(
        @Part("patient_id") patientId: RequestBody,
        @Part("patient_name") patientName: RequestBody,
        @Part("age") age: RequestBody,
        @Part("gender") gender: RequestBody,
        @Part("modality") modality: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<TriageResponse>
}
