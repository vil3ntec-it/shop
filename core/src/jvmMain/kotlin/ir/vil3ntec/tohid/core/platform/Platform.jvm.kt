package ir.vil3ntec.tohid.core.platform

import java.security.MessageDigest
import java.security.SecureRandom

private val random = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

internal actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private val lock = Any()

internal actual fun <T> locked(block: () -> T): T = synchronized(lock) { block() }
