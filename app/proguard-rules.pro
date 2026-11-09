# kotlinx.serialization and Compose ship their own R8 rules.
# Only Bouncy Castle's Argon2 classes are used; the rest of the provider is stripped.
-dontwarn org.bouncycastle.**
