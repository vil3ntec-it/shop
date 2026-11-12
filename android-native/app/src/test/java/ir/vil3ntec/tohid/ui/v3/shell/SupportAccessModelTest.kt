package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.data.repo.SupportAccessDto
import ir.vil3ntec.tohid.data.repo.SupportGrantDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TEN-04 — قاعده‌های نوارِ «پشتیبانی در حالِ مشاهده» (`SupportAccessModel`) و خواندنِ همان `shape` ِ `lib/support-access.js`. */
class SupportAccessModelTest {

  private val now = 10_000_000L
  private fun g(id: String, status: String, consent: String = "tenant", expiresAt: Long? = null, req: Long? = null, created: Long = 0) =
    SupportGrantDto(id = id, status = status, consent = consent, expiresAt = expiresAt, requestExpiresAt = req, createdAt = created, scope = "read_private")

  @Test fun `زنده فقط active ِ تمام‌نشده؛ زودپایان‌ترین اول`() {
    val list = listOf(g("a", "active", expiresAt = now + 600_000), g("b", "active", expiresAt = now + 60_000), g("c", "active", expiresAt = now),
      g("d", "revoked", expiresAt = now + 600_000), g("e", "expired", expiresAt = now - 1))
    assertEquals(listOf("b", "a"), SupportAccessModel.active(list, now).map { it.id })
  }

  @Test fun `درخواستِ باز فقط pending ِ نگذشته؛ تازه‌ترین اول`() {
    val list = listOf(g("p1", "pending", req = now + 1, created = 1), g("p2", "pending", req = now + 5, created = 2), g("p3", "pending", req = now, created = 3),
      g("x", "denied", created = 4))
    assertEquals(listOf("p2", "p1"), SupportAccessModel.pending(list, now).map { it.id })
  }

  @Test fun `«شکستنِ شیشه» را دکان نمی‌بندد؛ دقیقه‌ها رو به بالا و هرگز صفر`() {
    assertTrue(SupportAccessModel.canRevoke(g("a", "active")))
    assertFalse(SupportAccessModel.canRevoke(g("a", "active", consent = "break_glass")))
    assertEquals(1L, SupportAccessModel.minutesLeft(g("a", "active", expiresAt = now + 1), now))
    assertEquals(1L, SupportAccessModel.minutesLeft(g("a", "active", expiresAt = now + 60_000), now))
    assertEquals(2L, SupportAccessModel.minutesLeft(g("a", "active", expiresAt = now + 60_001), now))
    assertEquals(60L, SupportAccessModel.minutesLeft(g("a", "active", expiresAt = now + 3_600_000), now))
  }

  @Test fun `با اجازهٔ زنده یا درخواستِ باز هر دقیقه، وگرنه هر پنج دقیقه`() {
    assertEquals(300_000L, SupportAccessModel.pollMs(emptyList(), now))
    assertEquals(60_000L, SupportAccessModel.pollMs(listOf(g("a", "active", expiresAt = now + 5)), now))
    assertEquals(60_000L, SupportAccessModel.pollMs(listOf(g("p", "pending", req = now + 5)), now))
    assertEquals(300_000L, SupportAccessModel.pollMs(listOf(g("x", "revoked", expiresAt = now + 5)), now))
  }

  @Test fun `پاسخِ سرور خوانده می‌شود`() {
    val raw = """{"grants":[{"id":"agr_1","scope":"export","consent":"tenant","status":"pending","reason":"بررسیِ پشتیبانِ خراب","ticketId":"",
      "minutes":60,"requestedBy":"پشتیبانِ یک","grantedBy":"","revokedBy":"","revokeReason":"","createdAt":5,"decidedAt":null,"startsAt":null,
      "expiresAt":null,"revokedAt":null,"notifiedAt":6,"uses":0,"requestExpiresAt":86400005}],"policy":{"defaultMinutes":60}}"""
    val d = ApiJson.decode<SupportAccessDto>(Json.parseToJsonElement(raw) as JsonObject)
    val x = d.grants.single()
    assertEquals("agr_1", x.id)
    assertEquals(60, x.minutes)
    assertEquals(86_400_005L, x.requestExpiresAt)
    assertTrue(SupportAccessModel.waiting(x, 100))
  }
}
