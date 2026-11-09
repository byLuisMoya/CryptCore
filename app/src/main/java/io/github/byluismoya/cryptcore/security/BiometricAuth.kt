package io.github.byluismoya.cryptcore.security

import android.content.Context
import android.content.ContextWrapper
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.byluismoya.cryptcore.crypto.Sealed
import io.github.byluismoya.cryptcore.crypto.VaultCodec
import io.github.byluismoya.cryptcore.data.VaultRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import kotlin.coroutines.resume

sealed interface BioResult {
    data class Success(val cipher: Cipher) : BioResult
    data object Cancelled : BioResult
    data class Error(val message: String) : BioResult
}

suspend fun FragmentActivity.authenticate(cipher: Cipher, title: String, subtitle: String): BioResult =
    suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authed = result.cryptoObject?.cipher
                    if (cont.isActive) {
                        cont.resume(authed?.let(BioResult::Success) ?: BioResult.Error("Autenticación sin clave"))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val r = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED,
                        -> BioResult.Cancelled
                        else -> BioResult.Error(errString.toString())
                    }
                    if (cont.isActive) cont.resume(r)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Usar contraseña")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }

object BiometricUnlock {
    /** @return an error message to show, or null on success / user cancel. */
    suspend fun unlock(activity: FragmentActivity, repo: VaultRepository): String? {
        val wrap = repo.biometricWrap() ?: run {
            repo.disableBiometric()
            return null
        }
        val cipher = try {
            BiometricKeys.decryptCipher(VaultCodec.unb64(wrap.iv))
        } catch (e: KeyPermanentlyInvalidatedException) {
            repo.disableBiometric()
            return "Tus datos biométricos han cambiado. Entra con la contraseña y vuelve a activarlo en Ajustes."
        } catch (e: GeneralSecurityException) {
            repo.disableBiometric()
            return "No se pudo usar la biometría. Vuelve a activarla en Ajustes."
        }
        return when (val r = activity.authenticate(cipher, "Desbloquear CryptCore", "Confirma que eres tú")) {
            is BioResult.Success -> {
                val dek = try {
                    r.cipher.doFinal(VaultCodec.unb64(wrap.ciphertext))
                } catch (e: GeneralSecurityException) {
                    repo.disableBiometric()
                    return "La biometría ya no es válida. Usa tu contraseña maestra."
                }
                if (repo.unlockWithKey(dek)) {
                    null
                } else {
                    repo.disableBiometric()
                    "La biometría ya no es válida. Usa tu contraseña maestra."
                }
            }
            BioResult.Cancelled -> null
            is BioResult.Error -> r.message
        }
    }

    /** Wraps the open vault's key with a new biometric key. @return error message or null. */
    suspend fun enable(activity: FragmentActivity, repo: VaultRepository): String? {
        val cipher = try {
            BiometricKeys.newEncryptCipher()
        } catch (e: Exception) {
            return "No se pudo crear la clave biométrica: registra una huella en los ajustes del sistema."
        }
        return when (val r = activity.authenticate(cipher, "Activar desbloqueo biométrico", "Confirma tu huella o rostro")) {
            is BioResult.Success -> {
                val dek = repo.keyCopy() ?: return "El almacén está bloqueado"
                try {
                    val ct = r.cipher.doFinal(dek)
                    repo.saveBiometricWrap(Sealed(VaultCodec.b64(r.cipher.iv), VaultCodec.b64(ct)))
                    null
                } catch (e: GeneralSecurityException) {
                    BiometricKeys.deleteKey()
                    "No se pudo activar la biometría"
                } finally {
                    dek.fill(0)
                }
            }
            BioResult.Cancelled -> { BiometricKeys.deleteKey(); null }
            is BioResult.Error -> { BiometricKeys.deleteKey(); r.message }
        }
    }
}

fun Context.findFragmentActivity(): FragmentActivity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("CryptCore UI must run inside a FragmentActivity")
}
