package com.example.myapplication.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.bluetooth.AcceptThread
import com.example.myapplication.bluetooth.ConnectedThread
import com.example.myapplication.databinding.ActivitySupervisorBinding
import com.example.myapplication.util.BluetoothSecurityUtils
import org.json.JSONObject

/**
 * Modo Supervisor (Monitoreo - Servidor Bluetooth).
 * Escucha conexiones entrantes, descifra tramas AES en tiempo real y actualiza la UI.
 */
class SupervisorActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupervisorBinding
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var acceptThread: AcceptThread? = null
    private var connectedThread: ConnectedThread? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBluetoothServer()
        } else {
            Toast.makeText(this, "Permisos de Bluetooth requeridos para el servidor", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupervisorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        checkPermissionsAndStartServer()
    }

    private fun checkPermissionsAndStartServer() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no soportado en este dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Por favor active el Bluetooth para iniciar el servidor", Toast.LENGTH_SHORT).show()
            return
        }

        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startBluetoothServer()
        }
    }

    /**
     * Inicia el Hilo Servidor Bluetooth (AcceptThread) a la escucha de peticiones del Operador.
     */
    private fun startBluetoothServer() {
        binding.tvServerStatus.text = "Servidor Bluetooth: Esperando conexión del Operador..."
        binding.tvServerStatus.setTextColor(Color.parseColor("#FB8C00"))

        acceptThread?.cancel()
        connectedThread?.cancel()

        val adapter = bluetoothAdapter ?: return

        acceptThread = AcceptThread(
            bluetoothAdapter = adapter,
            onClientConnected = { socket ->
                runOnUiThread {
                    @SuppressLint("MissingPermission")
                    val clientName = socket.remoteDevice.name ?: socket.remoteDevice.address
                    binding.tvServerStatus.text = "Servidor Bluetooth: CLIENTE CONECTADO"
                    binding.tvServerStatus.setTextColor(Color.parseColor("#4CAF50"))
                    binding.tvClientInfo.text = "Cliente Conectado: $clientName"
                    Toast.makeText(this, "Operador Conectado: $clientName", Toast.LENGTH_SHORT).show()
                }

                // Iniciar hilo de transmisión continua
                connectedThread = ConnectedThread(
                    socket = socket,
                    onDataReceived = { encryptedMessage ->
                        // Procesar mensaje encriptado recibido por Bluetooth
                        processEncryptedData(encryptedMessage)
                    },
                    onDisconnected = { errorMsg ->
                        runOnUiThread {
                            handleDisconnection("Conexión perdida: $errorMsg")
                        }
                    }
                ).apply { start() }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    binding.tvServerStatus.text = "Error del Servidor: $errorMsg"
                    binding.tvServerStatus.setTextColor(Color.RED)
                }
            }
        ).apply { start() }
    }

    /**
     * Recibe la trama encriptada en AES, la desencripta y actualiza la interfaz gráfica usando runOnUiThread.
     */
    private fun processEncryptedData(encryptedData: String) {
        try {
            // 1. Descifrar datos AES con la clave compartida
            val decryptedJson = BluetoothSecurityUtils.decrypt(encryptedData)

            if (decryptedJson.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this, "Error al descifrar la trama AES recibida", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // 2. Parsear JSON desencriptado
            val jsonObject = JSONObject(decryptedJson)
            val isMachineOn = jsonObject.optBoolean("status", false)
            val rpmValue = jsonObject.optInt("rpm", 0)

            // 3. Actualizar Interfaz de Usuario en el Hilo Principal (runOnUiThread)
            runOnUiThread {
                try {
                    binding.tvEncryptedData.text = encryptedData

                    // Actualizar Estado de la Máquina
                    if (isMachineOn) {
                        binding.tvMachineStatus.text = "MAQUINARIA: ENCENDIDA"
                        binding.tvMachineStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
                        binding.tvMachineStatus.setTextColor(Color.parseColor("#2E7D32"))
                    } else {
                        binding.tvMachineStatus.text = "MAQUINARIA: APAGADA"
                        binding.tvMachineStatus.setBackgroundColor(Color.parseColor("#FFEBEE"))
                        binding.tvMachineStatus.setTextColor(Color.parseColor("#C62828"))
                    }

                    // Actualizar Gauge / Circular ProgressBar de RPM
                    binding.progressRpmCircular.progress = rpmValue.coerceIn(0, 100)
                    binding.tvRpmDisplay.text = "$rpmValue RPM"

                } catch (e: Exception) {
                    Toast.makeText(this, "Error al actualizar UI: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Excepción en el procesamiento de datos: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Maneja la desconexión del cliente, captura excepciones con try-catch y muestra un Toast.
     */
    private fun handleDisconnection(reason: String) {
        try {
            binding.tvServerStatus.text = "Servidor Bluetooth: Desconectado"
            binding.tvServerStatus.setTextColor(Color.RED)
            binding.tvClientInfo.text = "Cliente: Ninguno conectado"
            binding.tvMachineStatus.text = "MAQUINARIA: DESCONECTADA"
            binding.tvMachineStatus.setBackgroundColor(Color.parseColor("#EEEEEE"))
            binding.tvMachineStatus.setTextColor(Color.GRAY)

            Toast.makeText(this, "Se perdió la señal con el Operador: $reason", Toast.LENGTH_LONG).show()

            // Reiniciar servidor a la escucha
            startBluetoothServer()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al reiniciar el servidor: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        acceptThread?.cancel()
        connectedThread?.cancel()
    }
}
