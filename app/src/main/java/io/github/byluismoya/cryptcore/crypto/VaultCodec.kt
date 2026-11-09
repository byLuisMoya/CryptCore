package io.github.byluismoya.cryptcore.crypto

import io.github.byluismoya.cryptcore.data.VaultContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * Vault file layout (JSON, every binary field Base64):
 *
 *   kdf         Argon2id parameters + salt used to turn the master password into a KEK
 *   wrappedKey  random 256-bit data key (DEK) sealed with the KEK      (AES-256-GCM)
 *   data        VaultContent JSON sealed with the DEK                  (AES-256-GCM)
 *
 * Changing the master password only re-wraps the DEK, so the biometric copy of the DEK stays valid.
 * The KDF parameters are bound to the wrapped key as AAD, so they cannot be swapped silently.
 */

@Serializable
data class KdfParams(
    val algorithm: String,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: String,
)

@Serializable
data class Sealed(val iv: String, val ciphertext: String)

@Serializable
data class VaultEnvelope(
    val format: Int,
    val kdf: KdfParams,
    val wrappedKey: Sealed,
    val data: Sealed,
)

class WrongPasswordException : Exception("Wrong master password")
class CorruptVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

object VaultCodec {
    const val FORMAT = 1
    private const val ARGON2ID = "argon2id"
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val DATA_AAD = "cryptcore/data/v1".toByteArray()

    data class KdfCost(val memoryKiB: Int, val iterations: Int, val parallelism: Int)

    /** ~46 MiB, 2 passes: OWASP-level cost that still fits the heap of low-end phones. */
    val DEFAULT_COST = KdfCost(memoryKiB = 46 * 1024, iterations = 2, parallelism = 1)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val random = SecureRandom()

    class Created(val envelope: VaultEnvelope, val key: ByteArray)

    fun create(password: CharArray, content: VaultContent, cost: KdfCost = DEFAULT_COST): Created {
        val key = randomBytes(KEY_BYTES)
        val (kdf, wrapped) = wrapKey(key, password, cost)
        return Created(VaultEnvelope(FORMAT, kdf, wrapped, sealContent(key, content)), key)
    }

    /** Returns the DEK or throws [WrongPasswordException]. */
    fun unwrapKey(envelope: VaultEnvelope, password: CharArray): ByteArray {
        validate(envelope)
        val kek = deriveKek(password, envelope.kdf)
        try {
            return open(kek, envelope.wrappedKey, keyAad(envelope.kdf))
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        } catch (e: IllegalArgumentException) {
            throw CorruptVaultException("Clave envuelta ilegible", e)
        } finally {
            kek.fill(0)
        }
    }

    fun decryptContent(envelope: VaultEnvelope, key: ByteArray): VaultContent {
        val plain = try {
            open(key, envelope.data, DATA_AAD)
        } catch (e: GeneralSecurityException) {
            throw CorruptVaultException("No se pudo descifrar el contenido", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptVaultException("Contenido ilegible", e)
        }
        try {
            return json.decodeFromString(VaultContent.serializer(), plain.decodeToString())
        } catch (e: SerializationException) {
            throw CorruptVaultException("Contenido con formato inválido", e)
        } finally {
            plain.fill(0)
        }
    }

    fun withContent(envelope: VaultEnvelope, key: ByteArray, content: VaultContent): VaultEnvelope =
        envelope.copy(data = sealContent(key, content))

    /** New master password: fresh salt and current default cost, same DEK and data. */
    fun rewrap(envelope: VaultEnvelope, key: ByteArray, newPassword: CharArray): VaultEnvelope {
        val (kdf, wrapped) = wrapKey(key, newPassword, DEFAULT_COST)
        return envelope.copy(kdf = kdf, wrappedKey = wrapped)
    }

    fun encode(envelope: VaultEnvelope): ByteArray =
        json.encodeToString(VaultEnvelope.serializer(), envelope).encodeToByteArray()

    fun decode(bytes: ByteArray): VaultEnvelope {
        val envelope = try {
            json.decodeFromString(VaultEnvelope.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw CorruptVaultException("No es un almacén de CryptCore", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptVaultException("No es un almacén de CryptCore", e)
        }
        validate(envelope)
        return envelope
    }

    fun sealRaw(key: ByteArray, plaintext: ByteArray, aad: ByteArray): Sealed {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return Sealed(b64(iv), b64(cipher.doFinal(plaintext)))
    }

    private fun open(key: ByteArray, sealed: Sealed, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, unb64(sealed.iv)))
        cipher.updateAAD(aad)
        return cipher.doFinal(unb64(sealed.ciphertext))
    }

    private fun sealContent(key: ByteArray, content: VaultContent): Sealed {
        val plain = json.encodeToString(VaultContent.serializer(), content).encodeToByteArray()
        try {
            return sealRaw(key, plain, DATA_AAD)
        } finally {
            plain.fill(0)
        }
    }

    private fun wrapKey(key: ByteArray, password: CharArray, cost: KdfCost): Pair<KdfParams, Sealed> {
        val kdf = KdfParams(ARGON2ID, cost.memoryKiB, cost.iterations, cost.parallelism, b64(randomBytes(SALT_BYTES)))
        val kek = deriveKek(password, kdf)
        try {
            return kdf to sealRaw(kek, key, keyAad(kdf))
        } finally {
            kek.fill(0)
        }
    }

    private fun deriveKek(password: CharArray, kdf: KdfParams): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(kdf.memoryKiB)
            .withIterations(kdf.iterations)
            .withParallelism(kdf.parallelism)
            .withSalt(unb64(kdf.salt))
            .build()
        val out = ByteArray(KEY_BYTES)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out)
        return out
    }

    private fun keyAad(kdf: KdfParams): ByteArray =
        "cryptcore/key/v1|${kdf.algorithm}|${kdf.memoryKiB}|${kdf.iterations}|${kdf.parallelism}|${kdf.salt}"
            .toByteArray()

    /** Rejects unknown formats and absurd KDF costs (a crafted backup must not OOM or hang the app). */
    private fun validate(envelope: VaultEnvelope) {
        if (envelope.format != FORMAT) throw CorruptVaultException("Formato de almacén no soportado")
        val kdf = envelope.kdf
        val saltLen = runCatching { unb64(kdf.salt).size }.getOrDefault(0)
        val sane = kdf.algorithm == ARGON2ID &&
            kdf.memoryKiB in 8 * 1024..256 * 1024 &&
            kdf.iterations in 1..10 &&
            kdf.parallelism in 1..4 &&
            saltLen in 16..64
        if (!sane) throw CorruptVaultException("Parámetros de cifrado no válidos")
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
