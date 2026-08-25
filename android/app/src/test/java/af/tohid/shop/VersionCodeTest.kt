package af.tohid.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * versionCode باید با هر نسخه‌ی تازه بزرگ‌تر شود، وگرنه اندروید
 * اجازه‌ی نصب به‌روزرسانی روی نسخه‌ی قبلی را نمی‌دهد.
 * (همان فرمولی که در build.gradle.kts است، اینجا بازبینی می‌شود.)
 */
class VersionCodeTest {

    private fun codeOf(name: String): Int {
        val parts = name.trim().removePrefix("v").split('.', '-')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return major * 1_000_000 + minor * 1_000 + patch
    }

    @Test fun mapsVersionToCode() {
        assertEquals(1_000_000, codeOf("1.0.0"))
        assertEquals(1_002_003, codeOf("1.2.3"))
        assertEquals(2_000_000, codeOf("v2.0.0"))
    }

    @Test fun newerVersionAlwaysHasBiggerCode() {
        val ordered = listOf("1.0.0", "1.0.1", "1.0.9", "1.1.0", "1.9.9", "2.0.0", "10.0.0")
        for (i in 1 until ordered.size) {
            assertTrue(
                "${ordered[i]} باید versionCode بزرگ‌تری از ${ordered[i - 1]} داشته باشد",
                codeOf(ordered[i]) > codeOf(ordered[i - 1]),
            )
        }
    }

    @Test fun staysInsideIntRange() {
        // تا نسخه ۲۱۴۷ جا دارد؛ عملاً برای همیشه کافی است
        assertTrue(codeOf("2147.0.0") > 0)
    }
}
