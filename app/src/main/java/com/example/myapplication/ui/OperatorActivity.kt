package com.example.myapplication.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.bluetooth.ConnectThread
import com.example.myapplication.bluetooth.ConnectedThread
import com.example.myapplication.databinding.ActivityOperatorBinding
import com.example.myapplication.util.BluetoothSecurityUtils
import org.json.JSONObject

/**
 * Modo Operador (Activador - Cliente Bluetooth).
 * Permite conectarse a una maquinaria y transmitir comandos cifrados en AES.
 */
class OperatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOperatorBinding
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    private var isConnected = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showPairedDevicesDialog()
        } else {
            Toast.makeText(this, "Permisos de Bluetooth denegados", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOperatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setupUI()
    }

    private fun setupUI() {
        // Botón Buscar / Conectar Bluetooth
        binding.btnConnectBluetooth.setOnClickListener {
            checkPermissionsAndConnect()
        }

        // Switch Encender/Apagar Maquinaria
        binding.switchMachineStatus.setOnCheckedChangeListener { _, isChecked ->
            binding.switchMachineStatus.text = if (isChecked) "ENCENDIDO" else "APAGADO"
            sendMachineControlPayload()
        }

        // Slider RPM / Potencia (0 a 100)
        binding.sliderRpm.addOnChangeListener { _, value, _ ->
            val rpmValue = value.toInt()
            binding.tvRpmValue.text = "$rpmValue RPM"
            sendMachineControlPayload()
        }

        // Botón de Envío Manual
        binding.btnSendPayload.setOnClickListener {
            sendMachineControlPayload()
        }
    }

    private fun checkPermissionsAndConnect() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Por favor active el Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
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
            showPairedDevicesDialog()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevicesDialog() {
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
        val optionsList = mutableListOf<String>()
        val deviceMap = mutableListOf<BluetoothDevice?>()

        pairedDevices.forEach { device ->
            optionsList.add("${device.name ?: "Dispositivo"}\n${device.address}")
            deviceMap.add(device)
        }

        optionsList.add("🔌 Conectar por dirección MAC (Emulador/Directo)")
        deviceMap.add(null)

        AlertDialog.Builder(this)
            .setTitle("Seleccionar Dispositivo Maquinaria (Servidor)")
            .setItems(optionsList.toTypedArray()) { _, which ->
                val selectedDevice = deviceMap[which]
                if (selectedDevice != null) {
                    connectToDevice(selectedDevice)
                } else {
                    showManualAddressDialog()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showManualAddressDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "02:00:00:BB:BB:BB"
            setText("02:00:00:BB:BB:BB")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Conectar por Dirección MAC")
            .setMessage("Ingrese la dirección MAC del Servidor Supervisor (en emuladores Netsim es 02:00:00:BB:BB:BB):")
            .setView(input)
            .setPositiveButton("Conectar") { _, _ ->
                val address = input.text.toString().trim()
                if (BluetoothAdapter.checkBluetoothAddress(address)) {
                    val device = bluetoothAdapter?.getRemoteDevice(address)
                    if (device != null) {
                        connectToDevice(device)
                    } else {
                        Toast.makeText(this, "Dispositivo no encontrado", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Dirección MAC no válida", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        val deviceName = device.name ?: device.address
        updateConnectionStatus(connected = false, message = "Conectando a $deviceName...")

        connectThread?.cancel()
        connectedThread?.cancel()

        connectThread = ConnectThread(
            device = device,
            onConnectionSuccess = { socket ->
                runOnUiThread {
                    isConnected = true
                    updateConnectionStatus(connected = true, message = "Conectado a $deviceName")
                    Toast.makeText(this, "Conexión establecida con la Maquinaria", Toast.LENGTH_SHORT).show()
                }

                connectedThread = ConnectedThread(
                    socket = socket,
                    onDataReceived = { _ -> },
                    onDisconnected = { errorMsg ->
                        runOnUiThread {
                            isConnected = false
                            updateConnectionStatus(connected = false, message = "Desconectado: $errorMsg")
                            Toast.makeText(this, "Conexión perdida con la Maquinaria", Toast.LENGTH_LONG).show()
                        }
                    }
                ).apply { start() }
            },
            onConnectionFailed = { errorMsg ->
                runOnUiThread {
                    isConnected = false
                    updateConnectionStatus(connected = false, message = "Error: $errorMsg")
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        ).apply { start() }
    }

    /**
     * Genera un objeto JSON con los valores de la máquina, lo encripta en AES y lo transmite por Bluetooth.
     */
    private fun sendMachineControlPayload() {
        if (!isConnected || connectedThread == null) {
            Toast.makeText(this, "Debe conectarse a una maquinaria primero", Toast.LENGTH_SHORT).show()
            return
        }

        val isMachineOn = binding.switchMachineStatus.isChecked
        val rpmVal = binding.sliderRpm.value.toInt()

        // 1. Crear Objeto JSON con los valores
        val jsonPayload = JSONObject().apply {
            put("status", isMachineOn)
            put("rpm", rpmVal)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        // 2. Encriptar usando AES con clave estática compartida (ISO 27001)
        val encryptedData = BluetoothSecurityUtils.encrypt(jsonPayload)

        // 3. Enviar datos por el socket Bluetooth
        connectedThread?.write(encryptedData)
        Toast.makeText(this, "Comando AES enviado (RPM: $rpmVal, Estado: $isMachineOn)", Toast.LENGTH_SHORT).show()
    }

    private fun updateConnectionStatus(connected: Boolean, message: String) {
        binding.tvConnectionStatus.text = message
        binding.tvConnectionStatus.setTextColor(
            if (connected) ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        connectThread?.cancel()
        connectedThread?.cancel()
    }
}
