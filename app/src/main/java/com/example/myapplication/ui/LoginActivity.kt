package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

/**
 * Pantalla de inicio de sesión conectada a Firebase Authentication.
 * Incluye un manejo seguro de excepciones para evitar cierres inesperados si falta google-services.json.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var auth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Firebase de forma segura
        initFirebase()

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.btnBypassLogin.setOnClickListener {
            Toast.makeText(this, "Ingresando en Modo Demostración", Toast.LENGTH_SHORT).show()
            navigateToRoleSelection()
        }
    }

    private fun initFirebase() {
        try {
            FirebaseApp.initializeApp(this)
            auth = FirebaseAuth.getInstance()

            if (auth?.currentUser != null) {
                navigateToRoleSelection()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Firebase no está configurado con google-services.json o está offline
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.tilEmail.error = "Ingrese su correo electrónico"
            return
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingrese su contraseña"
            return
        } else {
            binding.tilPassword.error = null
        }

        val firebaseAuth = auth
        if (firebaseAuth == null) {
            Toast.makeText(
                this,
                "Firebase no está configurado con google-services.json. Accediendo en Modo Demo...",
                Toast.LENGTH_LONG
            ).show()
            navigateToRoleSelection()
            return
        }

        showLoading(true)

        try {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    showLoading(false)
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Autenticación exitosa", Toast.LENGTH_SHORT).show()
                        navigateToRoleSelection()
                    } else {
                        val errorMessage = task.exception?.localizedMessage ?: "Error de autenticación"
                        Toast.makeText(this, "Error de Ingreso: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
        } catch (e: Exception) {
            showLoading(false)
            Toast.makeText(this, "Modo Demo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            navigateToRoleSelection()
        }
    }

    private fun navigateToRoleSelection() {
        val intent = Intent(this, RoleSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
    }
}
