package edu.uph.m24si2.aitriageradiology

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import edu.uph.m24si2.aitriageradiology.data.model.User
import edu.uph.m24si2.aitriageradiology.data.repository.AuthRepository
import edu.uph.m24si2.aitriageradiology.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.registerMain) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        authRepository = AuthRepository(this)

        setupMedicalRolesDropdown()
        setupClickListeners()
    }

    private fun setupMedicalRolesDropdown() {
        val roles = arrayOf(
            "Senior Radiologist",
            "Thoracic Oncologist",
            "Pulmonologist",
            "Resident Radiologist",
            "Emergency Physician",
            "Medical Officer",
            "Radiologic Technologist"
        )
        val adapter = ArrayAdapter(this, R.layout.dropdown_item_medical, roles)
        binding.spinnerMedicalRole.setAdapter(adapter)
    }

    private fun setupClickListeners() {
        binding.tvLoginPrompt.text = Html.fromHtml(
            getString(R.string.prompt_already_have_account),
            Html.FROM_HTML_MODE_LEGACY
        )

        binding.btnRegister.setOnClickListener {
            performRegistration()
        }

        binding.tvLoginPrompt.setOnClickListener {
            finish()
        }
    }

    private fun performRegistration() {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val role = binding.spinnerMedicalRole.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Please enter your full doctor/clinician name"
            binding.etFullName.requestFocus()
            return
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter a valid email address"
            binding.etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email format"
            binding.etEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Please enter a password"
            binding.etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return
        }

        if (confirmPassword != password) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.etConfirmPassword.requestFocus()
            return
        }

        val newUser = User(
            email = email,
            password = password,
            fullName = fullName,
            role = if (role.isNotEmpty()) role else "Radiologist"
        )

        val (success, message) = authRepository.registerUser(newUser)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        if (success) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
