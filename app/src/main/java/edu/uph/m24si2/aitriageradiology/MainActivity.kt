package edu.uph.m24si2.aitriageradiology

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import edu.uph.m24si2.aitriageradiology.data.api.ApiClient
import edu.uph.m24si2.aitriageradiology.data.model.PatientScanRecord
import edu.uph.m24si2.aitriageradiology.data.model.TriageRequest
import edu.uph.m24si2.aitriageradiology.data.model.TriageResponse
import edu.uph.m24si2.aitriageradiology.data.repository.TriageRepository
import edu.uph.m24si2.aitriageradiology.data.repository.TriageResult
import edu.uph.m24si2.aitriageradiology.databinding.ActivityMainBinding
import edu.uph.m24si2.aitriageradiology.ui.HistoryAdapter
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TriageRepository
    private lateinit var historyAdapter: HistoryAdapter

    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null
    private var selectedPresetCondition: String? = "pneumothorax"
    private var lastTriageResponse: TriageResponse? = null
    private var lastTriageRequest: TriageRequest? = null
    private val historyRecords = mutableListOf<PatientScanRecord>()

    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            selectedPresetCondition = null
            binding.ivScanPreview.setImageURI(it)
            binding.tvPresetLabel.visibility = View.VISIBLE
            binding.tvPresetLabel.text = getString(R.string.btn_select_image)
            binding.chipGroupSamples.clearCheck()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraImageUri != null) {
            selectedImageUri = cameraImageUri
            selectedPresetCondition = null
            binding.ivScanPreview.setImageURI(cameraImageUri)
            binding.tvPresetLabel.visibility = View.VISIBLE
            binding.tvPresetLabel.text = getString(R.string.camera_photo_captured)
            binding.chipGroupSamples.clearCheck()
            Toast.makeText(this, "CT Scan photo captured!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repository = TriageRepository(this)

        setupDropdowns()
        setupHistoryRecyclerView()
        setupSampleChips()
        setupClickListeners()
        checkBackendStatus()
    }

    private fun setupDropdowns() {
        val genders = arrayOf("Male", "Female", "Other")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        binding.spinnerGender.setAdapter(genderAdapter)

        val modalities = arrayOf("Chest X-Ray (CXR)", "CT Thorax", "Chest MRI", "Abdominal X-Ray")
        val modalityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modalities)
        binding.spinnerModality.setAdapter(modalityAdapter)
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(this, historyRecords) { record ->
            Toast.makeText(this, "Selected record: ${record.patientId} (${record.primaryFinding})", Toast.LENGTH_SHORT).show()
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
        updateHistoryVisibility()
    }

    private fun setupSampleChips() {
        // Default selection: Adenocarcinoma CT Sample
        binding.chipSampleCritical.isChecked = true
        applyPreset("adenocarcinoma", "RAD-2026-101", "Alexander Wright", 58, "Male", "Preset: Adenocarcinoma CT")

        binding.chipSampleCritical.setOnClickListener {
            applyPreset("adenocarcinoma", "RAD-2026-101", "Alexander Wright", 58, "Male", "Preset: Adenocarcinoma CT")
        }
        binding.chipSamplePneumonia.setOnClickListener {
            applyPreset("large_cell", "RAD-2026-102", "Elena Rostova", 42, "Female", "Preset: Large Cell Carcinoma CT")
        }
        binding.chipSampleTB.setOnClickListener {
            applyPreset("squamous", "RAD-2026-103", "Marcus Vance", 63, "Male", "Preset: Squamous Cell Carcinoma CT")
        }
        binding.chipSampleNormal.setOnClickListener {
            applyPreset("normal", "RAD-2026-104", "Sophia Lin", 29, "Female", "Preset: Normal CT Scan")
        }
    }

    private fun applyPreset(
        conditionKey: String,
        patientId: String,
        patientName: String,
        age: Int,
        gender: String,
        labelText: String
    ) {
        selectedPresetCondition = conditionKey
        selectedImageUri = null
        binding.etPatientId.setText(patientId)
        binding.etPatientName.setText(patientName)
        binding.etAge.setText(age.toString())
        binding.spinnerGender.setText(gender, false)
        binding.tvPresetLabel.visibility = View.VISIBLE
        binding.tvPresetLabel.text = labelText

        when (conditionKey) {
            "adenocarcinoma" -> binding.ivScanPreview.setImageResource(R.drawable.sample_ct_adenocarcinoma)
            "large_cell" -> binding.ivScanPreview.setImageResource(R.drawable.sample_ct_large_cell)
            "squamous" -> binding.ivScanPreview.setImageResource(R.drawable.sample_ct_squamous)
            else -> binding.ivScanPreview.setImageResource(R.drawable.sample_ct_normal)
        }
    }

    private fun setupClickListeners() {
        binding.btnTakePhoto.setOnClickListener {
            launchCameraCapture()
        }

        binding.btnPickImage.setOnClickListener {
            getContentLauncher.launch("image/*")
        }

        binding.btnServerSettings.setOnClickListener {
            showServerConfigDialog()
        }

        binding.btnAnalyze.setOnClickListener {
            performTriageAnalysis()
        }

        binding.btnSaveRecord.setOnClickListener {
            saveCurrentResultToHistory()
        }

        binding.tvClearHistory.setOnClickListener {
            historyRecords.clear()
            historyAdapter.clearAll()
            updateHistoryVisibility()
            Toast.makeText(this, "Triage history cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCameraCapture() {
        try {
            val photoFile = File(cacheDir, "ct_scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            cameraImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkBackendStatus() {
        lifecycleScope.launch {
            val isConnected = repository.checkBackendConnection()
            if (isConnected) {
                binding.tvConnectionStatus.text = getString(R.string.server_status_online)
                binding.tvConnectionStatus.setBackgroundColor(getColor(R.color.triage_normal))
            } else {
                binding.tvConnectionStatus.text = getString(R.string.server_status_offline)
                binding.tvConnectionStatus.setBackgroundColor(getColor(R.color.triage_urgent))
            }
        }
    }

    private fun showServerConfigDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_config, null)
        val etUrl = dialogView.findViewById<EditText>(R.id.etServerUrl)
        val btnPort8000 = dialogView.findViewById<Button>(R.id.btnQuickPort8000)
        val btnPort5000 = dialogView.findViewById<Button>(R.id.btnQuickPort5000)

        val currentUrl = ApiClient.getBaseUrl(this)
        etUrl.setText(currentUrl)

        btnPort8000.setOnClickListener { etUrl.setText("http://10.0.2.2:8000/") }
        btnPort5000.setOnClickListener { etUrl.setText("http://10.0.2.2:5000/") }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_server_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val newUrl = etUrl.text.toString()
                ApiClient.setBaseUrl(this, newUrl)
                Toast.makeText(this, "Base URL saved: ${ApiClient.getBaseUrl(this)}", Toast.LENGTH_SHORT).show()
                checkBackendStatus()
            }
            .setNeutralButton(getString(R.string.btn_reset_default)) { _, _ ->
                ApiClient.setBaseUrl(this, ApiClient.DEFAULT_BASE_URL)
                Toast.makeText(this, "Reset to default: ${ApiClient.DEFAULT_BASE_URL}", Toast.LENGTH_SHORT).show()
                checkBackendStatus()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun performTriageAnalysis() {
        val patientId = binding.etPatientId.text.toString().trim()
        val patientName = binding.etPatientName.text.toString().trim()
        val ageStr = binding.etAge.text.toString().trim()
        val gender = binding.spinnerGender.text.toString().trim()
        val modality = binding.spinnerModality.text.toString().trim()

        if (patientId.isEmpty() || patientName.isEmpty() || ageStr.isEmpty()) {
            Toast.makeText(this, "Please fill in all patient details", Toast.LENGTH_SHORT).show()
            return
        }

        val age = ageStr.toIntOrNull() ?: 45
        val request = TriageRequest(
            patientId = patientId,
            patientName = patientName,
            age = age,
            gender = gender,
            modality = modality
        )
        lastTriageRequest = request

        binding.btnAnalyze.isEnabled = false
        binding.btnAnalyze.text = getString(R.string.btn_analyzing)
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = repository.analyzeTriage(
                request = request,
                presetCondition = selectedPresetCondition,
                imageUri = selectedImageUri
            )

            binding.btnAnalyze.isEnabled = true
            binding.btnAnalyze.text = getString(R.string.btn_analyze)
            binding.progressBar.visibility = View.GONE

            when (result) {
                is TriageResult.Success -> {
                    lastTriageResponse = result.response
                    displayTriageResult(result.response, result.isMockFallback)
                }
                is TriageResult.Error -> {
                    Toast.makeText(this@MainActivity, "Triage error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun displayTriageResult(response: TriageResponse, isMockFallback: Boolean) {
        binding.cardResult.visibility = View.VISIBLE

        val categoryUpper = response.triageCategory.uppercase(Locale.getDefault())
        when {
            categoryUpper.contains("CRITICAL") -> {
                binding.tvTriageBadge.text = getString(R.string.triage_critical)
                binding.tvTriageBadge.setBackgroundResource(R.drawable.bg_badge_critical)
                binding.tvRiskScore.setTextColor(getColor(R.color.triage_critical))
            }
            categoryUpper.contains("URGENT") -> {
                binding.tvTriageBadge.text = getString(R.string.triage_urgent)
                binding.tvTriageBadge.setBackgroundResource(R.drawable.bg_badge_urgent)
                binding.tvRiskScore.setTextColor(getColor(R.color.triage_urgent))
            }
            else -> {
                binding.tvTriageBadge.text = getString(R.string.triage_normal)
                binding.tvTriageBadge.setBackgroundResource(R.drawable.bg_badge_normal)
                binding.tvRiskScore.setTextColor(getColor(R.color.triage_normal))
            }
        }

        binding.tvPrimaryFinding.text = "Primary Finding: ${response.primaryFinding}"

        val confPercent = (response.confidence * 100).toInt()
        val confFormatted = String.format(Locale.getDefault(), "%.1f%%", response.confidence * 100)
        binding.tvConfidenceText.text = "AI Model Confidence: $confFormatted"
        binding.progressConfidence.progress = confPercent

        val riskFormatted = String.format(Locale.getDefault(), "%.1f/10", response.riskScore)
        binding.tvRiskScore.text = "Risk Score: $riskFormatted"

        binding.tvRecommendation.text = response.recommendation

        if (isMockFallback) {
            Toast.makeText(this, "Analyzed scan (Mock Simulator Active)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Analyzed scan (Live PyTorch Backend Response)", Toast.LENGTH_SHORT).show()
        }

        // Smooth scroll to result
        binding.main.post {
            binding.cardResult.getParent()?.let {
                binding.cardResult.requestRectangleOnScreen(
                    android.graphics.Rect(0, 0, binding.cardResult.width, binding.cardResult.height),
                    true
                )
            }
        }
    }

    private fun saveCurrentResultToHistory() {
        val req = lastTriageRequest ?: return
        val resp = lastTriageResponse ?: return

        val record = repository.createScanRecord(
            request = req,
            response = resp,
            imageUriString = selectedImageUri?.toString(),
            isSamplePreset = selectedPresetCondition != null,
            samplePresetName = selectedPresetCondition
        )

        historyAdapter.addRecord(record)
        updateHistoryVisibility()
        Toast.makeText(this, "Saved patient scan to history", Toast.LENGTH_SHORT).show()
    }

    private fun updateHistoryVisibility() {
        if (historyAdapter.itemCount > 0) {
            binding.tvEmptyHistory.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
        } else {
            binding.tvEmptyHistory.visibility = View.GONE
            binding.rvHistory.visibility = View.GONE
        }
    }
}
