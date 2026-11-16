package ir.vil3ntec.tohid.ui.v3.settings

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.ui.screens.ServerPulse
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel.Fix
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel.Problem
import ir.vil3ntec.tohid.update.AppMove
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  عاملِ e-newinstall — «همگام‌سازی کامل نشد» روی نصبِ تازه: هر شکست به زبانِ ساده می‌گوید چه شده و چه کنید.
 *
 *  پیش از این: ۴۰۳ ِ «برای این حساب دکانی ثبت نشده است» با حکمِ موتور «نشست» (AUTH ِ هر ۴۰۳) یا پس از بازگشایی «همگام‌سازی
 *  کامل نشد» (هیچ نشانه‌ای نخورد) دیده می‌شد؛ `AutoSync.needsShop` از وقتی دفتر با Sync v1 می‌رود هرگز روشن نمی‌شود. مهمان
 *  هیچ کارتی نداشت. «جزئیات» که متنِ کارت می‌گفت بفرستید، هیچ‌جا نبود.
 */
class SyncDiagnosisNewInstallTest {

  private val up = ServerPulse.State.UP

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun back() { I18n.source = { Lang.FA } }

  /** همان پیامی که لایهٔ شبکه از ۴۰۳ ِ `no_shop` ِ سرور می‌سازد (`ApiFailure.fromHttp`) */
  private val noShop = ApiFailure.fromHttp(403, """{"error":{"code":"no_shop","message":"برای این حساب دکانی ثبت نشده است"}}""", true)

  @Test fun `دکان ندارید — با کدِ ماندگار، با حکمِ AUTH ِ همان ۴۰۳، و پس از بازگشایی از روی متن`() {
    assertEquals("no_shop", noShop.code)
    val raw = noShop.userMessage
    //  همان دورِ شکست: حکمِ موتور AUTH (هر ۴۰۳) — پیش‌تر «نشستِ حساب تمام شده»
    val live = SyncModel.diagnose(raw, false, 0, false, up, SyncOutage.Kind.AUTH, code = "no_shop")
    assertEquals(Problem.NO_SHOP, live.problem)
    assertEquals(Fix.SHOP, live.fix)
    assertEquals(StrSettings.diagNoShopTitle, live.title)
    //  پس از بستن و باز کردن: رشتهٔ در حافظه نیست (حکمِ ERROR) ولی کد ماندگار است
    assertEquals(Problem.NO_SHOP, SyncModel.diagnose(raw, false, 0, false, up, SyncOutage.Kind.ERROR, code = "no_shop").problem)
    //  ساختِ پیشین بی کد: از روی متن — پیش‌تر UNKNOWN («همگام‌سازی کامل نشد»)
    assertEquals(Problem.NO_SHOP, SyncModel.diagnose(raw, false, 0, false, up, SyncOutage.Kind.ERROR).problem)
    assertEquals(Problem.NO_SHOP, SyncModel.classify(raw))
    assertEquals(StrSettings.diagNoShopTitle, SyncModel.reason(raw))
    //  راهنما: همان ایمیلِ حسابِ دکان (حسابِ تازهٔ اشتباه) یا ساختن/پیوستن
    assertTrue(live.action, live.action.contains("ایمیل"))
  }

  @Test fun `سرخطِ صفحه با کارتِ سرخِ موتور «آخرین تلاش ناموفق بود» است، نه «همه‌چیز روی سرور نشسته»`() {
    val ok = ir.vil3ntec.tohid.sync.AutoSync.Health.OK
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrNav.syncAllOnServer, SyncModel.headline(ok, false, 0).text)
    val failed = SyncModel.headline(ok, false, 0, failed = true)
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrNav.lastAttemptFailed, failed.text)
    assertEquals(ir.vil3ntec.tohid.ui.theme.V3Tone.ERROR, failed.tone)
  }

  @Test fun `مهمان — «وارد حساب نشده‌اید» با دکمهٔ ورود و شمارِ تغییرهای نگه‌داشته`() {
    val d = SyncModel.diagnose("", false, 0, false, up, signedIn = false, queued = 4)
    assertEquals(Problem.NOT_SIGNED_IN, d.problem)
    assertEquals(Fix.SIGN_IN, d.fix)
    assertEquals("وارد حساب نشده‌اید", d.title)
    assertTrue(d.body, d.body.contains("4"))
    assertNull("مهمان چیزی برای پشتیبانی ندارد", d.detail)
    //  نشستِ تمام‌شده هم دکمهٔ ورود دارد
    assertEquals(Fix.SIGN_IN, SyncModel.diagnose("نشست شما تمام شد؛ دوباره وارد شوید", false, 0, false, up, SyncOutage.Kind.AUTH, code = "session_expired").fix)
  }

  @Test fun `ردِ سرور با پیامِ سادهٔ خودش — همان پیام روی کارت، نه «همگام‌سازی کامل نشد» و نه «نشست»`() {
    val locked = ApiFailure.fromHttp(403, """{"error":{"code":"feature_locked","message":"این قابلیت در اشتراکِ فعلی نیست"}}""", true)
    val d = SyncModel.diagnose(locked.userMessage, false, 0, false, up, SyncOutage.Kind.AUTH, code = locked.code)
    assertEquals(Problem.REFUSED, d.problem)
    assertEquals(StrSettings.diagRefusedTitle, d.title)
    assertTrue(d.body, d.body.startsWith("این قابلیت در اشتراکِ فعلی نیست"))
    assertEquals(Fix.NONE, d.fix)
    //  پس از بازگشایی (بی حکم) همان
    assertEquals(Problem.REFUSED, SyncModel.diagnose(locked.userMessage, false, 0, false, up, SyncOutage.Kind.ERROR, code = locked.code).problem)
    //  متنِ فنی هرگز بدنهٔ کارت نمی‌شود — همان UNKNOWN با «جزئیات»
    val tech = SyncModel.diagnose("java.lang.IllegalStateException: boom", false, 0, false, up, SyncOutage.Kind.ERROR)
    assertEquals(Problem.UNKNOWN, tech.problem)
    assertFalse(tech.body.contains("boom"))
    assertEquals("java.lang.IllegalStateException: boom", tech.detail)
    //  ۴۰۳ ِ بی کد (حکمِ AUTH ِ کهنه) همان «نشست» ِ پیشین
    assertEquals(Problem.SIGN_IN, SyncModel.diagnose("x", false, 0, false, up, SyncOutage.Kind.AUTH).problem)
  }

  @Test fun `جزئیات — متنِ پشتیبانی پوشانده، بی نسخه و Schema و راهِ API، با گره و کد`() {
    val d = SyncModel.diagnose("Unable to resolve host \"api.vill3n.top\": 192.168.1.20:8443 https://api.vill3n.top/api/v1/sync/v1/push",
      false, 3, false, up, SyncOutage.Kind.ERROR, code = "weird_code")
    val text = SyncModel.detailsText(d, queued = 4, dropped = 3, lastOkAt = 0, now = 1_700_000_000_000)
    assertFalse(text, Regex("""vill3n|192\.168""").containsMatchIn(text))
    assertTrue(text, text.contains("code: weird_code") && text.contains("queued: 4") && text.contains("dropped: 3"))
    assertTrue(text, text.startsWith("sync: " + d.problem.name.lowercase()))
    assertFalse(text, Regex("""(?i)schema|serverVersion|apiPrefix""").containsMatchIn(text))
    //  گرهِ بی متنِ خام (ردشده‌ها) جزئیات ندارد
    assertNull(SyncModel.diagnose("", false, 2, false, up).detail)
  }

  @Test fun `جابه‌جایی به برنامهٔ تازه — گام‌ها به ترتیب، پشتیبان فقط وقتی چیزی مانده`() {
    assertEquals(listOf(AppMove.Step.KEEP_OLD, AppMove.Step.SAME_ACCOUNT, AppMove.Step.BACKUP_IF_STUCK), AppMove.steps(AppMove.Advice.PENDING))
    assertEquals(listOf(AppMove.Step.KEEP_OLD, AppMove.Step.SAME_ACCOUNT, AppMove.Step.BACKUP_IF_STUCK), AppMove.steps(AppMove.Advice.NOT_SIGNED_IN))
    assertEquals(listOf(AppMove.Step.KEEP_OLD, AppMove.Step.SAME_ACCOUNT), AppMove.steps(AppMove.Advice.SAFE))
    val keep = ir.vil3ntec.tohid.core.i18n.StrUpdate.movedStepKeep
    assertTrue(keep, keep.contains(ir.vil3ntec.tohid.core.i18n.StrNav.syncAllOnServer))
    assertTrue(ir.vil3ntec.tohid.core.i18n.StrUpdate.movedStepBackup.contains(StrSettings.backupRow))
    assertTrue(ir.vil3ntec.tohid.core.i18n.StrUpdate.movedStepAccount.contains("ادامه بدون حساب"))
  }
}
