package io.github.byluismoya.cryptcore.data

import android.content.Context
import android.util.AtomicFile
import io.github.byluismoya.cryptcore.crypto.CorruptVaultException
import io.github.byluismoya.cryptcore.crypto.Sealed
import io.github.byluismoya.cryptcore.crypto.VaultCodec
import io.github.byluismoya.cryptcore.crypto.VaultEnvelope
import io.github.byluismoya.cryptcore.crypto.WrongPasswordException
import io.github.byluismoya.cryptcore.security.BiometricKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.Collator
import java.util.Locale

sealed interface VaultState {
    data object NoVault : VaultState
    data object Locked : VaultState
    data class Unlocked(val entries: List<Entry>) : VaultState
}

sealed interface ImportResult {
    data class Ok(val added: Int, val updated: Int) : ImportResult
    data object WrongPassword : ImportResult
    data object InvalidFile : ImportResult
    data object VaultLocked : ImportResult
}

/**
 * Single source of truth for the encrypted vault. Files live in noBackupFilesDir, so they are never
 * included in cloud backups or device-to-device transfers.
 *
 * The data key is only held in memory while unlocked; [lock] wipes it.
 */
class VaultRepository(context: Context) {
    private val dir = context.noBackupFilesDir
    private val vaultFile = AtomicFile(File(dir, "vault.ccv"))
    private val biometricFile = AtomicFile(File(dir, "biometric.json"))

    /** Serializes every disk write so two saves can never interleave. */
    private val writeMutex = Mutex()

    /** Guards [envelope], [key], [content] and transitions of [_state]. */
    private val guard = Any()
    private var envelope: VaultEnvelope? = null
    private var key: ByteArray? = null
    private var content = VaultContent()

    private val _state = MutableStateFlow(
        if (vaultFile.baseFile.exists()) VaultState.Locked else VaultState.NoVault,
    )
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(biometricFile.baseFile.exists())
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    suspend fun create(password: CharArray) {
        val created = withContext(Dispatchers.Default) { VaultCodec.create(password, VaultContent()) }
        writeMutex.withLock {
            withContext(Dispatchers.IO) { write(vaultFile, VaultCodec.encode(created.envelope)) }
        }
        disableBiometric() // a leftover biometric wrap would belong to a previous vault
        setUnlocked(created.envelope, created.key, VaultContent())
    }

    /** @return false on wrong password. Throws [CorruptVaultException] if the file is damaged. */
    suspend fun unlock(password: CharArray): Boolean {
        val env = writeMutex.withLock { withContext(Dispatchers.IO) { readEnvelope() } }
        val dek = try {
            withContext(Dispatchers.Default) { VaultCodec.unwrapKey(env, password) }
        } catch (e: WrongPasswordException) {
            return false
        }
        val decrypted = try {
            withContext(Dispatchers.Default) { VaultCodec.decryptContent(env, dek) }
        } catch (e: CorruptVaultException) {
            dek.fill(0)
            throw e
        }
        setUnlocked(env, dek, decrypted)
        return true
    }

    /** Unlock with a DEK recovered through the biometric key. Takes ownership of [dek]. */
    suspend fun unlockWithKey(dek: ByteArray): Boolean {
        return try {
            val env = writeMutex.withLock { withContext(Dispatchers.IO) { readEnvelope() } }
            val decrypted = withContext(Dispatchers.Default) { VaultCodec.decryptContent(env, dek) }
            setUnlocked(env, dek, decrypted)
            true
        } catch (e: CorruptVaultException) {
            dek.fill(0)
            false
        } catch (e: IOException) {
            dek.fill(0)
            false
        }
    }

    fun lock() {
        synchronized(guard) {
            key?.fill(0)
            key = null
            envelope = null
            content = VaultContent()
            if (_state.value is VaultState.Unlocked) _state.value = VaultState.Locked
        }
    }

    suspend fun upsert(entry: Entry): Boolean = mutate { c ->
        c.copy(entries = c.entries.filterNot { it.id == entry.id } + entry)
    }

    suspend fun delete(id: String): Boolean = mutate { c ->
        c.copy(entries = c.entries.filterNot { it.id == id })
    }

    suspend fun setFavorite(id: String, favorite: Boolean): Boolean = mutate { c ->
        c.copy(entries = c.entries.map { if (it.id == id) it.copy(favorite = favorite) else it })
    }

    /** @return false if [current] is wrong or the vault is locked. The DEK (and biometrics) survive. */
    suspend fun changePassword(current: CharArray, new: CharArray): Boolean = writeMutex.withLock {
        val (env, dek, _) = snapshot() ?: return@withLock false
        try {
            try {
                withContext(Dispatchers.Default) { VaultCodec.unwrapKey(env, current) }.fill(0)
            } catch (e: WrongPasswordException) {
                return@withLock false
            }
            val updated = withContext(Dispatchers.Default) { VaultCodec.rewrap(env, dek, new) }
            withContext(Dispatchers.IO) { write(vaultFile, VaultCodec.encode(updated)) }
            synchronized(guard) { if (key != null) envelope = updated }
            true
        } finally {
            dek.fill(0)
        }
    }

    /** Copies the encrypted vault file as-is: it stays protected by the current master password. */
    suspend fun exportTo(out: OutputStream) {
        val bytes = writeMutex.withLock { withContext(Dispatchers.IO) { vaultFile.readFully() } }
        withContext(Dispatchers.IO) { out.write(bytes) }
    }

    /** Merges a backup into the open vault: new entries are added, newer versions replace older ones. */
    suspend fun importBackup(bytes: ByteArray, password: CharArray): ImportResult {
        val backup = try {
            withContext(Dispatchers.Default) {
                val env = VaultCodec.decode(bytes)
                val backupKey = VaultCodec.unwrapKey(env, password)
                try {
                    VaultCodec.decryptContent(env, backupKey)
                } finally {
                    backupKey.fill(0)
                }
            }
        } catch (e: WrongPasswordException) {
            return ImportResult.WrongPassword
        } catch (e: CorruptVaultException) {
            return ImportResult.InvalidFile
        }

        var added = 0
        var updated = 0
        val ok = mutate { current ->
            added = 0
            updated = 0
            val byId = LinkedHashMap<String, Entry>()
            current.entries.forEach { byId[it.id] = it }
            for (incoming in backup.entries) {
                val existing = byId[incoming.id]
                when {
                    existing == null -> { byId[incoming.id] = incoming; added++ }
                    incoming.updatedAt > existing.updatedAt -> { byId[incoming.id] = incoming; updated++ }
                }
            }
            current.copy(entries = byId.values.toList())
        }
        return if (ok) ImportResult.Ok(added, updated) else ImportResult.VaultLocked
    }

    // --- Biometrics -------------------------------------------------------------------------

    /** Copy of the DEK to be wrapped by the biometric key. Caller must wipe it. */
    fun keyCopy(): ByteArray? = synchronized(guard) { key?.copyOf() }

    suspend fun biometricWrap(): Sealed? = withContext(Dispatchers.IO) {
        runCatching {
            VaultCodec.json.decodeFromString(Sealed.serializer(), biometricFile.readFully().decodeToString())
        }.getOrNull()
    }

    suspend fun saveBiometricWrap(wrap: Sealed) {
        withContext(Dispatchers.IO) {
            write(biometricFile, VaultCodec.json.encodeToString(Sealed.serializer(), wrap).encodeToByteArray())
        }
        _biometricEnabled.value = true
    }

    fun disableBiometric() {
        biometricFile.delete()
        BiometricKeys.deleteKey()
        _biometricEnabled.value = false
    }

    // --- Internals ----------------------------------------------------------------------------

    private suspend fun mutate(transform: (VaultContent) -> VaultContent): Boolean = writeMutex.withLock {
        val (env, dek, current) = snapshot() ?: return@withLock false
        try {
            val updated = transform(current)
            val newEnvelope = withContext(Dispatchers.Default) { VaultCodec.withContent(env, dek, updated) }
            withContext(Dispatchers.IO) { write(vaultFile, VaultCodec.encode(newEnvelope)) }
            synchronized(guard) {
                // If the vault was locked meanwhile, the file is saved but nothing is kept in memory.
                if (key != null) {
                    envelope = newEnvelope
                    content = updated
                    _state.value = VaultState.Unlocked(sorted(updated.entries))
                }
            }
            true
        } finally {
            dek.fill(0)
        }
    }

    /**
     * Consistent copy of envelope, key and content. The key is copied so a concurrent [lock]
     * can't zero it in the middle of an encryption. Caller must wipe the key.
     */
    private fun snapshot(): Triple<VaultEnvelope, ByteArray, VaultContent>? = synchronized(guard) {
        val e = envelope
        val k = key
        if (e == null || k == null) null else Triple(e, k.copyOf(), content)
    }

    private fun setUnlocked(env: VaultEnvelope, dek: ByteArray, decrypted: VaultContent) {
        synchronized(guard) {
            key?.fill(0)
            key = dek
            envelope = env
            content = decrypted
            _state.value = VaultState.Unlocked(sorted(decrypted.entries))
        }
    }

    private fun readEnvelope(): VaultEnvelope = VaultCodec.decode(vaultFile.readFully())

    private fun write(file: AtomicFile, bytes: ByteArray) {
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }

    private fun sorted(entries: List<Entry>): List<Entry> {
        val collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }
        return entries.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
}
