package ir.vil3ntec.tohid.core.platform

import java.security.MessageDigest
import java.security.SecureRandom

/*
 *  جانشینِ JVM ِ `core/src/commonMain/.../core/platform/Platform.kt` برای این ابزار.
 *  این پروژه KMP نیست و expect/actual ندارد؛ پس همان بدنهٔ actual ِ JVM ِ core
 *  (`core/src/jvmMain/.../Platform.jvm.kt`) این‌جا تابعِ معمولی است. امضاها باید
 *  با expect ِ core یکی بمانند تا `Ulid.kt` ِ کپی‌شده بی تغییر کامپایل شود.
 */

private val random = SecureRandom()

internal fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

internal fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

internal fun currentTimeMillis(): Long = System.currentTimeMillis()

private val lock = Any()

internal fun <T> locked(block: () -> T): T = synchronized(lock) { block() }
