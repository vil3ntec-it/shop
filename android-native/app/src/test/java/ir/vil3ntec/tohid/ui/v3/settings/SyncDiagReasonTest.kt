package ir.vil3ntec.tohid.ui.v3.settings

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSyncPlain
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.ui.screens.ServerPulse
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel.Problem
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  عاملِ e-syncdiag — «سرور وصل است ●» کنارِ «همگام‌سازی ناموفق بود» و کارتِ «همگام‌سازی کامل نشد» که فقط «جزئیات» چرایش را
 *  می‌گفت. حالا هر گونهٔ شکست یک خطِ «چه شد» ِ خودش را روی کارتِ وضعیت دارد (`SyncModel.reasonLine`، بی متنِ فنی) و بخشِ
 *  «سرور» با آن ناسازگار نیست (`SyncModel.server`/`serverNote`).
 */
class SyncDiagReasonTest {

  private val up = ServerPulse.State.UP

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun back() { I18n.source = { Lang.FA } }

  private fun http(status: Int, code: String, message: String) = ApiFailure.fromHttp(status, """{"error":{"code":"$code","message":"$message"}}""", true)

  /** همان فراخوانِ `SyncScreen` برای شکستِ موتور با کد و وضعیتِ ماندگار (پس از بازگشایی: حکمِ ERROR) */
  private fun engine(raw: String, code: String = "", status: Int? = null, outage: SyncOutage.Kind = SyncOutage.Kind.ERROR,
                     holding: Boolean = false, dropped: Int = 0, server: ServerPulse.State = up) =
    SyncModel.diagnose(raw, holding, dropped, false, server, outage, code = code, queued = 4, status = status)

  private val latin = Regex("[A-Za-z]")

  @Test fun `هر گونهٔ شکست خطِ «چه شد» ِ خودش را دارد — ساده، بی متنِ فنی`() {
    val cases = linkedMapOf(
      "سرورِ عقب (۴۲۶)" to engine("سرور schema نسخهٔ 2 را می‌شناسد و برنامه نسخهٔ 3 می‌فرستد", holding = true),
      "دکان ندارد" to engine("برای این حساب دکانی ثبت نشده است", "no_shop", 403),
      "نشست" to engine("نشست شما تمام شد؛ دوباره وارد شوید", "session_expired", 401, SyncOutage.Kind.AUTH),
      "ردِ فنیِ سرور (۴۰۰)" to engine("schema_version لازم است", "bad_schema_version", 400),
      "خطای همین دستگاه" to engine("Expected JsonObject, but had JsonArray", status = SyncOutage.LOCAL),
      "دفترِ ناجور" to engine("ژورنالِ همگام‌سازی با سایه نخواند", status = SyncOutage.LOCAL),
      "خطای سرور" to engine("خطای داخلی سرور (کدِ پیگیری: a1b2)", "internal", 500),
      "به سرور نرسید" to engine("سرور در دسترس نیست", "unreachable", SyncOutage.NO_RESPONSE),
      "بی‌نت" to engine("اینترنت وصل نیست", "offline", SyncOutage.NO_RESPONSE, SyncOutage.Kind.OFFLINE),
      "ناشناخته" to engine("Expedited jobs cannot be delayed"),
      "ردشده‌ها" to engine("", dropped = 1),
    )
    val lines = cases.mapValues { (k, d) -> assertNotNull("خطِ «چه شد»: $k (${d.problem})", SyncModel.reasonLine(d)); SyncModel.reasonLine(d)!!.text }
    for ((k, t) in lines) {
      assertTrue("$k: «$t»", t.isNotBlank())
      assertFalse("$k بی حرفِ لاتین/متنِ فنی: «$t»", latin.containsMatchIn(t))
    }
    assertEquals("هر گونه خطِ خودش: $lines", lines.size, lines.values.toSet().size)
    assertTrue(lines["سرورِ عقب (۴۲۶)"]!!, lines["سرورِ عقب (۴۲۶)"]!!.contains("سرور را به‌روز کنید"))
    assertTrue(lines["دکان ندارد"]!!, lines["دکان ندارد"]!!.contains("این حساب دکان ندارد"))
    assertTrue(lines["نشست"]!!, lines["نشست"]!!.contains("دوباره وارد شوید"))
    assertEquals(Problem.DEVICE, cases["خطای همین دستگاه"]!!.problem)
    assertEquals(Problem.REFUSED, cases["ردِ فنیِ سرور (۴۰۰)"]!!.problem)
    //  متنِ خام فقط برای «جزئیات»/پشتیبانی می‌ماند
    assertEquals("Expected JsonObject, but had JsonArray", cases["خطای همین دستگاه"]!!.detail)
    assertFalse(cases["ردِ فنیِ سرور (۴۰۰)"]!!.body.contains("schema_version"))
    //  همگام و مهمان هیچ خطی ندارند
    assertNull(SyncModel.reasonLine(engine("")))
    assertNull(SyncModel.reasonLine(SyncModel.diagnose("", false, 0, false, up, signedIn = false, queued = 2)))
  }

  @Test fun `سرورِ قدیمی — ۴۲۶، ۴۰۴ ِ راهِ همگام‌سازی و opِ ناشناختهٔ سرور، همه می‌گویند سرور را به‌روز کنید`() {
    val behind = engine("x", holding = true)
    val missing = engine("این مسیر وجود ندارد", "not_found", 404)
    assertEquals(Problem.SERVER_BEHIND, behind.problem)
    assertEquals(Problem.SERVER_OLD, missing.problem)
    assertEquals(StrSyncPlain.reasonOldServer, SyncModel.reasonLine(behind)!!.text)
    assertEquals(StrSyncPlain.reasonOldServer, SyncModel.reasonLine(missing)!!.text)
    assertTrue(behind.action, behind.action.contains("مرکزِ فرمان"))
    val old = SyncModel.reasonLine(engine("", dropped = 2), mapOf("unknown_table" to 2))!!.text
    assertTrue(old, old.contains("سرور را به‌روز کنید") && old.contains("2"))
  }

  @Test fun `ردشده‌ها — دلیلِ سرور خطِ خودش را می‌گیرد`() {
    val d = engine("", dropped = 1)
    assertEquals(Problem.REJECTED, d.problem)
    assertEquals(StrSyncPlain.reasonRejectedInbox(1), SyncModel.reasonLine(d, mapOf("approval_required" to 1))!!.text)
    assertTrue(StrSyncPlain.reasonRejectedInbox(1).contains("سندهای ردشده"))
    assertEquals(StrSyncPlain.reasonRejectedLarge(1), SyncModel.reasonLine(d, mapOf("body_too_large" to 1))!!.text)
    assertEquals(StrSyncPlain.reasonRejected(3), SyncModel.reasonLine(d, mapOf("hash_mismatch" to 1, "op_id_taken" to 2))!!.text)
    //  چیرگی: سرورِ قدیمی بر بقیه
    assertEquals(StrSyncPlain.reasonRejectedOld(3), SyncModel.reasonLine(d, mapOf("approval_required" to 1, "unknown_table" to 2))!!.text)
    val book = listOf("unknown_table", "unknown_table", "", "approval_required").map { r ->
      buildJsonObject { put("op", buildJsonObject {}); put("reason", JsonPrimitive(r)); put("at", JsonPrimitive(1)) }
    }
    assertEquals(mapOf("unknown_table" to 2, "rejected" to 1, "approval_required" to 1), SyncModel.rejectedReasons(book))
  }

  @Test fun `بخشِ «سرور» — «وصل است» فقط وقتی همگام‌سازی هم می‌گذرد`() {
    //  سالم
    val ok = engine("")
    assertEquals(StrNav.serverUp, SyncModel.server(up, ok).text)
    assertEquals(V3Tone.SUCCESS, SyncModel.server(up, ok).tone)
    assertNull(SyncModel.serverNote(up, ok))
    //  در دسترس ولی رد می‌کند ⇒ «در دسترس است» + «ولی تغییرها را نمی‌پذیرد — چرا»
    for (d in listOf(engine("x", holding = true), engine("برای این حساب دکانی ثبت نشده است", "no_shop", 403),
                     engine("schema_version لازم است", "bad_schema_version", 400), engine("نشست شما تمام شد", "session_expired", 401, SyncOutage.Kind.AUTH))) {
      val line = SyncModel.server(up, d)
      assertEquals("${d.problem}", StrNav.serverReachable, line.text)
      assertEquals(V3Tone.WARNING, line.tone)
      val note = SyncModel.serverNote(up, d)!!
      assertTrue(note, note.startsWith("ولی تغییرها را نمی‌پذیرد"))
      assertTrue(note, note.contains(SyncModel.reasonLine(d)!!.text))
      assertNotEquals(StrNav.serverUp, line.text)
    }
    //  حالِ صاحب‌کار (علتِ ناروشن) ⇒ «در دسترس است»، نه «وصل است ●» ِ سبز
    val owner = SyncModel.diagnose("Expedited jobs cannot be delayed", false, 0, false, up, null, queued = 4)
    assertEquals(Problem.UNKNOWN, owner.problem)
    assertEquals(StrNav.serverReachable, SyncModel.server(up, owner).text)
    assertEquals(StrSyncPlain.serverNotSyncing, SyncModel.serverNote(up, owner))
    //  گرهِ خودِ این دستگاه یا ردِ تک‌تکِ opها: سرور سالم است
    assertEquals(StrNav.serverUp, SyncModel.server(up, engine("boom", status = SyncOutage.LOCAL)).text)
    assertEquals(StrNav.serverUp, SyncModel.server(up, engine("", dropped = 1)).text)
    //  سرورِ نرسیدنی همان پیشین
    assertEquals(StrNav.serverDown, SyncModel.server(ServerPulse.State.DOWN, owner).text)
    assertNull(SyncModel.serverNote(ServerPulse.State.DOWN, owner))
  }

  @Test fun `کدِ HTTP ِ ماندگار — پیامِ JSON ِ محلی دیگر «نشست» نیست و ساختِ پیشین همان رفتار را دارد`() {
    val json = "Unexpected JSON token at offset 12: Expected start of the object"
    assertEquals(Problem.DEVICE, engine(json, status = SyncOutage.LOCAL).problem)
    //  بی کدِ ماندگار (ساختِ پیشین): همان نشانه‌های متنیِ پیشین
    assertEquals(SyncModel.classify(json), engine(json, status = null).problem)
    //  نشانهٔ «اینترنت» ِ خطای محلی همان «بی‌نت»
    assertEquals(Problem.OFFLINE, engine("بازیابیِ بایگانی اینترنت می‌خواهد", status = SyncOutage.LOCAL).problem)
    //  ۴xx با پیامِ سادهٔ سرور همان پیام را روی کارت دارد
    val sub = http(403, "subscription_required", "اشتراک این دکان تمام شده است")
    val d = engine(sub.userMessage, sub.code, 403, SyncOutage.Kind.AUTH)
    assertEquals(Problem.REFUSED, d.problem)
    assertTrue(d.body.startsWith("اشتراک این دکان تمام شده است"))
    assertEquals(StrSyncPlain.reasonRefusedSaid("اشتراک این دکان تمام شده است"), SyncModel.reasonLine(d)!!.text)
    //  ۵xx پس از بازگشایی (بی رشتهٔ شکست) «سرور»، نه «ناشناخته»
    assertEquals(Problem.SERVER, engine("خطای داخلی", "internal", 503).problem)
    //  «جزئیات» کدِ HTTP را هم دارد
    assertTrue(SyncModel.detailsText(d, 4, 0, 0, 1, status = 403).contains("status: 403"))
  }

  @Test fun `سرخطِ کارت — «همه‌چیز روی سرور نشسته» ِ سبز فقط وقتی راست است`() {
    val ok = ir.vil3ntec.tohid.sync.AutoSync.Health.OK
    assertEquals(StrNav.syncAllOnServer, SyncModel.headline(ok, false, 0).text)
    //  چهار تغییر در صف (سرورِ عقب صف را نگه داشته) ⇒ «۴ مورد در انتظار» ِ زرد
    val behind = engine("x", holding = true)
    val queued = SyncModel.headline(ok, false, 4, failed = behind.tone == V3Tone.ERROR, held = SyncModel.holds(behind))
    assertEquals(StrNav.pendingItems(4), queued.text)
    assertEquals(V3Tone.WARNING, queued.tone)
    //  گرهِ زرد بی صف ⇒ «آخرین تلاش ناموفق بود» ِ زرد
    val noShop = engine("برای این حساب دکانی ثبت نشده است", "no_shop", 403)
    assertEquals(SyncModel.Line(StrNav.lastAttemptFailed, V3Tone.WARNING), SyncModel.headline(ok, false, 0, held = SyncModel.holds(noShop)))
    //  ردشده‌ها ⇒ «۱ تغییر پذیرفته نشد»
    assertEquals(StrSyncPlain.rejectedTitle(1), SyncModel.headline(ok, false, 0, held = SyncModel.holds(engine("", dropped = 1)), rejected = 1).text)
    //  سرخِ موتور همان «آخرین تلاش ناموفق بود» ِ سرخ
    assertEquals(V3Tone.ERROR, SyncModel.headline(ok, false, 4, failed = true).tone)
    assertFalse(SyncModel.holds(engine("")))
  }

  @Test fun `دری — خطِ «چه شد» از لایهٔ واژه می‌گذرد`() {
    I18n.source = { Lang.PRS }
    assertEquals("قابل دسترس است", StrNav.serverReachable)
    assertEquals("سرورِ دکان کهنه است — نسخهٔ برنامه جدیدتر است؛ سرور را به‌روز کنید", StrSyncPlain.reasonOldServer)
    assertEquals("سرور 2 تغییر را قبول نکرد؛ باقی رفتند", StrSyncPlain.reasonRejected(2))
  }
}
