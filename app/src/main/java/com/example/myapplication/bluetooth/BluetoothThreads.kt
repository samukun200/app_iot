package com.example.myapplication.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.IOException
import java.util.UUID

/**
 * Constantes compartidas para la comunicación Bluetooth Serial (RFCOMM).
 */
object BluetoothConstants {
    const val SERVICE_NAME = "OT_MAQUINARIA_SERVICE"
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
}

/**
 * Hilo del Servidor (Supervisor - Modo Monitor).
 * Permanece a la escucha de conexiones entrantes desde dispositivos clientes (Operador).
 */
class AcceptThread(
    bluetoothAdapter: BluetoothAdapter,
    private val onClientConnected: (BluetoothSocket) -> Unit,
    private val onError: (String) -> Unit
) : Thread() {

    private var serverSocket: BluetoothServerSocket? = null

    init {
        try {
            @SuppressLint("MissingPermission")
            val tmpSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                BluetoothConstants.SERVICE_NAME,
                BluetoothConstants.SPP_UUID
            )
            serverSocket = tmpSocket
        } catch (e: IOException) {
            onError("Error al iniciar el Servidor Bluetooth: ${e.localizedMessage}")
        }
    }

    override fun run() {
        var socket: BluetoothSocket?
        while (!isInterrupted) {
            socket = try {
                serverSocket?.accept()
            } catch (e: IOException) {
                onError("Error al aceptar la conexión del servidor: ${e.localizedMessage}")
                break
            }

            if (socket != null) {
                onClientConnected(socket)
                try {
                    serverSocket?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                break
            }
        }
    }

    fun cancel() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

/**
 * Hilo del Cliente (Operador - Modo Activador).
 * Intenta establecer una conexión RFCOMM Socket con un dispositivo Servidor.
 */
class ConnectThread(
    private val device: BluetoothDevice,
    private val onConnectionSuccess: (BluetoothSocket) -> Unit,
    private val onConnectionFailed: (String) -> Unit
) : Thread() {

    private var socket: BluetoothSocket? = null

    init {
        try {
            @SuppressLint("MissingPermission")
            val tmpSocket = device.createRfcommSocketToServiceRecord(BluetoothConstants.SPP_UUID)
            socket = tmpSocket
        } catch (e: IOException) {
            onConnectionFailed("Error al crear el socket del cliente: ${e.localizedMessage}")
        }
    }

    override fun run() {
        try {
            @SuppressLint("MissingPermission")
            socket?.connect()
            socket?.let { onConnectionSuccess(it) }
        } catch (e: IOException) {
            try {
                socket?.close()
            } catch (closeException: IOException) {
                closeException.printStackTrace()
            }
            onConnectionFailed("No se pudo conectar con el dispositivo: ${e.localizedMessage}")
        }
    }

    fun cancel() {
        try {
            socket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

/**
 * Hilo de Comunicación Activa (ConnectedThread).
 * Maneja el envío y recepción de flujos de datos (Data Stream) entre cliente y servidor.
 */
class ConnectedThread(
    private val socket: BluetoothSocket,
    private val onDataReceived: (String) -> Unit,
    private val onDisconnected: (String) -> Unit
) : Thread() {

    private var inputStreamReader: BufferedReader? = null
    private var outputStream: OutputStream? = null

    init {
        try {
            inputStreamReader = BufferedReader(InputStreamReader(socket.inputStream))
            outputStream = socket.outputStream
        } catch (e: IOException) {
            onDisconnected("Error al obtener los flujos I/O: ${e.localizedMessage}")
        }
    }

    override fun run() {
        while (!isInterrupted) {
            try {
                val message = inputStreamReader?.readLine()
                if (message != null) {
                    onDataReceived(message)
                } else {
                    onDisconnected("El dispositivo remoto cerró la conexión.")
                    break
                }
            } catch (e: IOException) {
                onDisconnected("Conexión perdida con la maquinaria: ${e.localizedMessage}")
                break
            }
        }
    }

    /**
     * Envía una cadena cifrada a través del Socket Bluetooth.
     * Agrega un salto de línea '\n' para facilitar la lectura bufferizada en el receptor.
     */
    fun write(encryptedData: String) {
        try {
            val bytesWithNewline = (encryptedData + "\n").toByteArray(Charsets.UTF_8)
            outputStream?.write(bytesWithNewline)
            outputStream?.flush()
        } catch (e: IOException) {
            onDisconnected("Error al enviar comandos por Bluetooth: ${e.localizedMessage}")
        }
    }

    fun cancel() {
        try {
            socket.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
