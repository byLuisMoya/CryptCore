package io.github.byluismoya.cryptcore.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AES key in the Android Keystore that can only be used right after a strong
 * biometric match. It wraps a copy of the vault's data key. Enrolling a new fingerprint
 * invalidates it, forcing the master password again.
 */
object BiometricKeys {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "cryptcore_biometric_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun availability(context: Context): Int =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)

    /** Creates a fresh key and returns a cipher ready to encrypt after authentication. */
    fun newEncryptCipher(): Cipher {
        deleteKey()
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** @throws KeyPermanentlyInvalidatedException if biometrics changed or the key is gone. */
    fun decryptCipher(iv: ByteArray): Cipher {
        val key = keyStore().getKey(ALIAS, null) as? SecretKey
            ?: throw KeyPermanentlyInvalidatedException("Biometric key missing")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
    }

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }
}
