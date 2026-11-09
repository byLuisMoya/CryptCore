package io.github.byluismoya.cryptcore.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** One saved login (Netflix, Gmail…). Only [name] and [password] are required. */
@Serializable
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

/** Everything inside the encrypted payload. */
@Serializable
data class VaultContent(
    val version: Int = 1,
    val entries: List<Entry> = emptyList(),
)
