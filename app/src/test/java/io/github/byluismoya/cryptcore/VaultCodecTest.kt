package io.github.byluismoya.cryptcore

import io.github.byluismoya.cryptcore.core.PasswordGenerator
import io.github.byluismoya.cryptcore.core.PasswordStrength
import io.github.byluismoya.cryptcore.core.Strength
import io.github.byluismoya.cryptcore.crypto.CorruptVaultException
import io.github.byluismoya.cryptcore.crypto.VaultCodec
import io.github.byluismoya.cryptcore.crypto.WrongPasswordException
import io.github.byluismoya.cryptcore.data.Entry
import io.github.byluismoya.cryptcore.data.VaultContent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCodecTest {
    private val cheap = VaultCodec.KdfCost(memoryKiB = 8 * 1024, iterations = 1, parallelism = 1)
    private val sample = VaultContent(
        entries = listOf(
            Entry(name = "Netflix", email = "usuario@ejemplo.com", password = "s3cr3t!"),
            Entry(name = "Gmail", username = "usuario", password = "otra-clave ñ€"),
        ),
    )

    @Test
    fun roundTripWithDefaultCost() {
        val created = VaultCodec.create("maestra-123".toCharArray(), sample)
        val decoded = VaultCodec.decode(VaultCodec.encode(created.envelope))
        val key = VaultCodec.unwrapKey(decoded, "maestra-123".toCharArray())
        assertArrayEquals(created.key, key)
        assertEquals(sample, VaultCodec.decryptContent(decoded, key))
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        val created = VaultCodec.create("correcta".toCharArray(), sample, cheap)
        VaultCodec.unwrapKey(created.envelope, "incorrecta".toCharArray())
    }

    @Test
    fun plaintextNeverAppearsInFile() {
        val created = VaultCodec.create("pw-pw-pw".toCharArray(), sample, cheap)
        val text = VaultCodec.encode(created.envelope).decodeToString()
        listOf("Netflix", "Gmail", "s3cr3t", "ejemplo.com", "usuario").forEach {
            assertFalse("'$it' leaked", text.contains(it))
        }
    }

    @Test
    fun rewrapKeepsDataAndInvalidatesOldPassword() {
        val created = VaultCodec.create("vieja-clave".toCharArray(), sample, cheap)
        val rewrapped = VaultCodec.rewrap(created.envelope, created.key, "nueva-clave".toCharArray())
        val key = VaultCodec.unwrapKey(rewrapped, "nueva-clave".toCharArray())
        assertEquals(sample, VaultCodec.decryptContent(rewrapped, key))
        assertTrue(runCatching { VaultCodec.unwrapKey(rewrapped, "vieja-clave".toCharArray()) }.exceptionOrNull() is WrongPasswordException)
    }

    @Test
    fun tamperedKdfParamsDoNotUnlock() {
        val created = VaultCodec.create("clave-ok".toCharArray(), sample, cheap)
        val tampered = created.envelope.copy(kdf = created.envelope.kdf.copy(iterations = 2))
        assertTrue(runCatching { VaultCodec.unwrapKey(tampered, "clave-ok".toCharArray()) }.exceptionOrNull() is WrongPasswordException)
    }

    @Test(expected = CorruptVaultException::class)
    fun tamperedDataIsDetected() {
        val created = VaultCodec.create("clave-ok".toCharArray(), sample, cheap)
        val other = VaultCodec.create("clave-ok".toCharArray(), VaultContent(), cheap)
        VaultCodec.decryptContent(created.envelope.copy(data = other.envelope.data), created.key)
    }

    @Test(expected = CorruptVaultException::class)
    fun absurdKdfCostIsRejected() {
        val created = VaultCodec.create("x".repeat(8).toCharArray(), sample, cheap)
        val evil = created.envelope.copy(kdf = created.envelope.kdf.copy(memoryKiB = 4 * 1024 * 1024))
        VaultCodec.decode(VaultCodec.encode(evil))
    }

    @Test(expected = CorruptVaultException::class)
    fun garbageIsRejected() {
        VaultCodec.decode("no soy un almacén".toByteArray())
    }

    @Test
    fun generatorHonoursOptions() {
        repeat(200) {
            val pw = PasswordGenerator.generate(PasswordGenerator.Options(length = 12, symbols = false, avoidAmbiguous = true))
            assertEquals(12, pw.length)
            assertTrue(pw.any { it.isUpperCase() } && pw.any { it.isLowerCase() } && pw.any { it.isDigit() })
            assertTrue(pw.all { it.isLetterOrDigit() })
            assertTrue(pw.none { it in "Il1O0o" })
        }
        assertEquals(PasswordGenerator.MIN_LENGTH, PasswordGenerator.generate(PasswordGenerator.Options(length = 1)).length)
    }

    @Test
    fun strengthOrdering() {
        assertEquals(Strength.VeryWeak, PasswordStrength.estimate("123456"))
        assertEquals(Strength.VeryWeak, PasswordStrength.estimate("aaaaaaaa"))
        assertEquals(Strength.VeryStrong, PasswordStrength.estimate(PasswordGenerator.generate(PasswordGenerator.Options())))
    }
}
