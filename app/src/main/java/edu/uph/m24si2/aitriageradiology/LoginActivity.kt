package edu.uph.m24si2.aitriageradiology

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import edu.uph.m24si2.aitriageradiology.data.repository.AuthRepository
import edu.uph.m24si2.aitriageradiology.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.loginMain) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        authRepository = AuthRepository(this)

        if (authRepository.isLoggedIn()) {
            navigateToMain()
            return
        }

        setupViews()
    }

    private fun setupViews() {
        binding.tvSignUpPrompt.text = Html.fromHtml(
            getString(R.string.prompt_dont_have_account),
            Html.FROM_HTML_MODE_LEGACY
        )

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.tvSignUpPrompt.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter your email address"
            binding.etEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Please enter your password"
            binding.etPassword.requestFocus()
            return
        }

        binding.btnLogin.isEnabled = false
        lifecycleScope.launch {
            val (success, message) = authRepository.loginUserOnline(email, password)
            binding.btnLogin.isEnabled = true
            Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()

            if (success) {
                navigateToMain()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
