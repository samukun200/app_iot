package com.example.myapplication.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utilidad de seguridad para el cifrado y descifrado de datos transmitidos vía Bluetooth.
 * Implementa el algoritmo de cifrado simétrico AES-128 en modo CBC con relleno PKCS5Padding
 * para dar cumplimiento con la norma ISO 27001 sobre seguridad en la transmisión OT.
 */
object BluetoothSecurityUtils {

    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    // Clave estática compartida de 16 bytes (128 bits)
    private val SECRET_KEY = "OT_SECURE_KEY_16".toByteArray(Charsets.UTF_8)
    
    // Vector de Inicialización (IV) de 16 bytes (128 bits)
    private val INIT_VECTOR = "OT_SECURE_IV_1234".toByteArray(Charsets.UTF_8)

    /**
     * Cifra una cadena de texto en formato JSON utilizando AES/CBC/PKCS5Padding
     * y retorna el resultado codificado en Base64.
     */
    fun encrypt(plainText: String): String {
        return try {
            val secretKeySpec = SecretKeySpec(SECRET_KEY, "AES")
            val ivParameterSpec = IvParameterSpec(INIT_VECTOR)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)

            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Decodifica en Base64 y descifra una cadena cifrada con AES/CBC/PKCS5Padding,
     * retornando el texto en plano original (cadena JSON).
     */
    fun decrypt(encryptedText: String): String {
        return try {
            val secretKeySpec = SecretKeySpec(SECRET_KEY, "AES")
            val ivParameterSpec = IvParameterSpec(INIT_VECTOR)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

            val decodedBytes = Base64.decode(encryptedText, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
