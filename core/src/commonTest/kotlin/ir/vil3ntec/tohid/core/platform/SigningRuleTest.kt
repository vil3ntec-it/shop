package ir.vil3ntec.tohid.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SigningRuleTest {
  private val old = "a".repeat(64)
  private val new = "0123456789abcdef".repeat(4)

  @Test fun emptyListMeansCheckOff() {
    assertTrue(SigningRule.genuine("", ""))
    assertTrue(SigningRule.genuine("  ", old))
  }

  @Test fun singleKeyIsTodayBehaviour() {
    assertTrue(SigningRule.genuine(old, old))
    assertTrue(SigningRule.genuine(old, old.uppercase()))
    assertFalse(SigningRule.genuine(old, new))
    assertFalse(SigningRule.genuine(old, ""))
  }

  @Test fun rotationAcceptsOldAndNew() {
    val raw = "$old,$new"
    assertTrue(SigningRule.genuine(raw, old), "اندروید ۷/۸: امضای v2 ِ کلیدِ قدیم")
    assertTrue(SigningRule.genuine(raw, new), "اندروید ۹+: امضای فعلیِ کلیدِ تازه")
    assertFalse(SigningRule.genuine(raw, "b".repeat(64)))
  }

  @Test fun keytoolFormatAndJunk() {
    val colon = new.chunked(2).joinToString(":").uppercase()
    assertEquals(setOf(new), SigningRule.allowed(" $colon ; nonsense, 12ab "))
    assertTrue(SigningRule.genuine("$old, $colon", new))
    //  فهرستِ ناتهیِ بی هیچ اثرِ انگشتِ درست ⇒ رد، نه خاموش
    assertFalse(SigningRule.genuine("nonsense", old))
    assertFalse(SigningRule.genuine(",", old))
  }
}
