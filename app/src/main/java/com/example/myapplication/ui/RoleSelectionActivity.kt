package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityRoleSelectionBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Pantalla de selección de rol después del inicio de sesión.
 */
class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón Operador (Activador - Cliente Bluetooth)
        binding.btnOperatorRole.setOnClickListener {
            val intent = Intent(this, OperatorActivity::class.java)
            startActivity(intent)
        }

        // Botón Supervisor (Monitor - Servidor Bluetooth)
        binding.btnSupervisorRole.setOnClickListener {
            val intent = Intent(this, SupervisorActivity::class.java)
            startActivity(intent)
        }

        // Botón Cerrar Sesión
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
