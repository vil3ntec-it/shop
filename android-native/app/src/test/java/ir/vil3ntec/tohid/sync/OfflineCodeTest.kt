package ir.vil3ntec.tohid.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  بردارها را `server/src/lib/shop-offline-codes.js` با یک کلیدِ آزمونی ساخته
 *  (`body` + `encode`)؛ برنامه باید بایت‌به‌بایت همان را بخواند.
 */
class OfflineCodeTest {
  private val key = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEHVJd3FWjKoNDwytFx7PN4SdqUqmytKkH211+Sks0XMO2qG/a3lVGuEi1QMtFxhyfDVQUrZApb7/dlUt5IYVfBQ=="
  private val fp = "win-test-fingerprint-1"
  private val sub = "040G2-0FGT1-1BX1V-40000-00010-81G80-Z80NW-DXWBF-65KHJ-VP8ZY-78949-XXQH8-XMCWZ-5S1XW-GJAJV-PED62-1TCAC-JFR87-RVPYS-7WVDW-PBN6G-SD8GS-0BD5C-V8QB3-19CS7-M6QZK-H9H74-W90"
  private val permAcct = "041G2-0FGT1-1BX1V-4ND3K-GZR10-81G80-Z8ZZZ-J0BSW-BMH0K-WN2X1-N801N-ZZM0W-7KKP5-FE44Y-1DXBC-73RKP-31VGV-T1GQV-Z80GQ-44FH3-3KH7Z-HAH4Q-8WHPD-08S78-6XEG4-4HD92-V3N6W-ZDW"
  private val day = 86_400_000L
  private val now = (19_723L + 1100) * day

  @Test fun computerIdMatchesServer() {
    assertEquals("040Z-1M22-QT3P-9BVG", OfflineCode.computerId(fp))
  }

  @Test fun validCodeOnSameComputer() {
    val v = OfflineCode.verify(sub, key, fp, "", now)
    assertTrue(v.toString(), v is OfflineCode.Verdict.Ok)
    val p = (v as OfflineCode.Verdict.Ok).code
    assertEquals("sub", p.plan)
    assertEquals("01020304", p.serial)
    assertEquals((19_723L + 1400) * day, p.endsAt)
  }

  @Test fun otherComputerRejected() {
    assertEquals(OfflineCode.Verdict.Bad("computer"), OfflineCode.verify(sub, key, "win-other", "", now))
  }

  @Test fun expiredRejected() {
    assertEquals(OfflineCode.Verdict.Bad("expired"), OfflineCode.verify(sub, key, fp, "", (19_723L + 1400) * day))
  }

  @Test fun tamperedRejected() {
    val raw = sub.replace("-", "")
    val bad = raw.substring(0, 5) + (if (raw[5] == 'A') 'B' else 'A') + raw.substring(6)
    assertEquals(OfflineCode.Verdict.Bad("signature"), OfflineCode.verify(bad, key, fp, "", now))
    assertEquals(OfflineCode.Verdict.Bad("format"), OfflineCode.verify("ABC", key, fp, "", now))
  }

  @Test fun accountBound() {
    assertEquals("ab47387f", OfflineCode.accountTag("user_abc").joinToString("") { "%02x".format(it.toInt() and 0xFF) })
    assertTrue(OfflineCode.verify(permAcct, key, fp, "user_abc", now * 3) is OfflineCode.Verdict.Ok)
    assertEquals(OfflineCode.Verdict.Bad("account"), OfflineCode.verify(permAcct, key, fp, "user_zzz", now))
  }

  @Test fun persianDigitsAndLowercase() {
    val typed = sub.lowercase().replace("0", "۰").replace("-", " ")
    assertTrue(OfflineCode.verify(typed, key, fp, "", now) is OfflineCode.Verdict.Ok)
  }
}
