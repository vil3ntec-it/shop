package ir.vil3ntec.tohid.ui.v3.settings

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.ui.screens.ServerPulse
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.settings.devices.DeviceTab
import ir.vil3ntec.tohid.ui.v3.settings.devices.DevicesModel
import ir.vil3ntec.tohid.ui.v3.settings.sections.PasswordModel
import ir.vil3ntec.tohid.ui.v3.settings.sections.PinEntry
import ir.vil3ntec.tohid.ui.v3.settings.sections.PinModel
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** حالت‌های خالصِ صفحه‌های تنظیمات (G9): ردیف‌ها، رمز، رمزِ حساب، همگام‌سازی، دستگاه‌ها، مسیرها */
class SettingsModelsTest {

  @After fun resetLang() { I18n.source = { Lang.FA } }

  @Test
  fun `مقدارِ ردیف‌ها از روی حالِ موتورها`() {
    val f = SettingsFacts(
      debtLimit = 5000.0, theme = ThemeChoice.SYSTEM, digits = LocaleFormat.DIGITS_FA, symbol = LocaleFormat.SYMBOL_SIGN,
      signedIn = true, sessions = 3, blockExpired = false, printer = "XP-58", cameraGranted = true,
      sync = SyncState.OK, lastSyncOk = 1_000_000, now = 1_000_000 + 2 * 60_000, version = "2.14.3", lockOn = true,
    )
    assertEquals("قرض از 5,000 ؋ به بالا", SettingsIndexModel.value("هشدارها", f))
    assertEquals("خودکار", SettingsIndexModel.value("ظاهر", f))
    assertEquals("فارسی", SettingsIndexModel.value("زبان", f))
    assertEquals("رقمِ فارسی · ؋", SettingsIndexModel.value("تاریخ و رقم", f))
    assertEquals("3", SettingsIndexModel.value("دستگاه‌های وارد شده", f))
    assertEquals("فقط هشدار", SettingsIndexModel.value("کالای منقضی", f))
    //  S22: «با مجوزِ مدیر» همان گزینهٔ صفحه را نشان می‌دهد (پیش‌تر «فقط هشدار»)؛ «فروخته نشود» بر آن پیش است
    assertEquals(StrSettings.withApproval, SettingsIndexModel.value("کالای منقضی", f.copy(expiryApproval = true)))
    assertEquals(StrSettings.notSold, SettingsIndexModel.value("کالای منقضی", f.copy(expiryApproval = true, blockExpired = true)))
    assertEquals("XP-58", SettingsIndexModel.value(SettingsLayout.PRINTER, f))
    assertEquals("آماده", SettingsIndexModel.value(SettingsLayout.CAMERA, f))
    assertEquals("2 دقیقه پیش", SettingsIndexModel.value("همگام‌سازی", f))
    assertEquals("2.14.3", SettingsIndexModel.value(SettingsLayout.ABOUT_APP, f))
    assertEquals(true, SettingsIndexModel.toggle("قفل برنامه", f))
    assertNull(SettingsIndexModel.toggle("ظاهر", f))
    val out = SettingsFacts()
    assertEquals("خاموش", SettingsIndexModel.value("هشدارها", out))
    assertEquals("وارد نشده", SettingsIndexModel.value("دستگاه‌های وارد شده", out))
    assertEquals("وارد نشده", SettingsIndexModel.value("همگام‌سازی", out))
    assertEquals("هنوز نیست", SettingsIndexModel.value(SettingsLayout.PRINTER, out))
  }

  @Test
  fun `ردیف‌ها به زبانِ صفحه — انگلیسی`() {
    I18n.source = { Lang.EN }
    assertEquals("Security", SettingsLayout.groupLabel(SettingsLayout.SECURITY))
    assertEquals("App lock", SettingsLayout.labelOf("قفل برنامه"))
    assertEquals("Dark", SettingsIndexModel.themeLabel(ThemeChoice.DARK))
    assertEquals("5 minutes ago", SettingsIndexModel.since(1, 1 + 5 * 60_000))
  }

  @Test
  fun `حالِ همگام‌سازی — بیرون از حساب خاموش، خطا پیش از انتظار`() {
    assertEquals(SyncState.OFF, SettingsIndexModel.syncState(signedIn = false, failed = true, waiting = true))
    assertEquals(SyncState.FAILED, SettingsIndexModel.syncState(true, failed = true, waiting = true))
    assertEquals(SyncState.WAITING, SettingsIndexModel.syncState(true, failed = false, waiting = true))
    assertEquals(SyncState.OK, SettingsIndexModel.syncState(true, failed = false, waiting = false))
    assertEquals("هنوز همگام نشده", SettingsIndexModel.since(0, 10))
    assertEquals("همین حالا", SettingsIndexModel.since(1000, 1000 + 30_000))
    assertEquals("3 ساعت پیش", SettingsIndexModel.since(0 + 1, 1 + 3 * 3_600_000))
  }

  @Test
  fun `رمزِ برنامه — دو بار، ناهمسان ⇐ از نو و لرزش`() {
    fun typeAll(e: PinEntry, s: String) = s.fold(e) { acc, ch -> PinModel.type(acc, ch) }
    val first = typeAll(PinEntry(), "123456")
    assertTrue(first.confirming); assertEquals("", first.typed); assertNull(first.done)
    val ok = typeAll(first, "123456")
    assertEquals("123456", ok.done)
    val bad = typeAll(first, "123450")
    assertFalse(bad.confirming); assertEquals(1, bad.mismatch); assertEquals("", bad.first); assertNull(bad.done)
    assertEquals("12", PinModel.erase(typeAll(PinEntry(), "123")).typed)
    assertEquals("", PinModel.type(PinEntry(), 'x').typed)
    assertEquals(ok, PinModel.type(ok, '9'))
    assertEquals(6, PinModel.LENGTH)
  }

  @Test
  fun `رمزِ حساب — هشت نویسه، تکرارِ یکسان، رمزِ فعلی لازم`() {
    assertFalse(PasswordModel.check("", "abcdefgh", "abcdefgh").ready)
    assertTrue(PasswordModel.check("old", "abcdefgh", "abcdefgh").ready)
    assertEquals(StrSettings.pwdMin8, PasswordModel.check("old", "short", "").freshError)
    assertEquals(Str.passwordsDiffer, PasswordModel.check("old", "abcdefgh", "abcdefgX").repeatError)
    assertNull(PasswordModel.check("old", "", "").freshError)
    assertFalse(PasswordModel.check("old", "abcdefgh", "").ready)
  }

  @Test
  fun `همگام‌سازی و سرور — همان نوشته‌ها و رنگِ همراهِ نوشته`() {
    assertEquals(V3Tone.SUCCESS, SyncModel.headline(AutoSync.Health.OK, false, 0).tone)
    assertEquals("3 مورد در انتظار", SyncModel.headline(AutoSync.Health.WAITING, false, 3).text)
    assertEquals(Str.sending, SyncModel.headline(AutoSync.Health.WAITING, true, 3).text)
    assertEquals(V3Tone.ERROR, SyncModel.headline(AutoSync.Health.FAILED, false, 0).tone)
    assertEquals("وصل است", SyncModel.server(ServerPulse.State.UP).text)
    assertEquals(V3Tone.WARNING, SyncModel.server(ServerPulse.State.NO_NET).tone)
    assertNull(SyncModel.server(ServerPulse.State.UNKNOWN).tone)
    assertEquals(V3Tone.SUCCESS, SyncModel.queue(0).tone)
    assertEquals("2 تغییر در صف", SyncModel.queue(2).text)
  }

  @Test
  fun `گرهِ همگام‌سازی به زبانِ آدم — چه شده و چه کنید`() {
    val up = ServerPulse.State.UP
    assertEquals(SyncModel.Problem.NONE, SyncModel.diagnose("", false, 0, false, up).problem)
    assertEquals(SyncModel.Problem.SERVER_BEHIND, SyncModel.diagnose("upgrade", true, 0, false, up).problem)
    assertEquals(SyncModel.Problem.NO_SHOP, SyncModel.diagnose("x", false, 0, true, up).problem)
    assertEquals(SyncModel.Problem.OFFLINE, SyncModel.diagnose("whatever", false, 0, false, ServerPulse.State.NO_NET).problem)
    assertEquals(SyncModel.Problem.OFFLINE, SyncModel.classify("Unable to resolve host \"vill3n.top\""))
    assertEquals(SyncModel.Problem.SIGN_IN, SyncModel.classify("HTTP 401 Unauthorized"))
    assertEquals(SyncModel.Problem.SERVER, SyncModel.classify("HTTP 503"))
    assertEquals(SyncModel.Problem.LOCAL, SyncModel.classify("ژورنالِ همگام‌سازی با سایه نخواند"))
    assertEquals(SyncModel.Problem.UNKNOWN, SyncModel.classify("boom"))
    val rejected = SyncModel.diagnose("", false, 2, false, up)
    assertEquals(SyncModel.Problem.REJECTED, rejected.problem)
    assertTrue(rejected.title.contains("2"))
    val unknown = SyncModel.diagnose("boom", false, 0, false, up)
    assertEquals("boom", unknown.detail)
    assertTrue(unknown.action.isNotBlank() && unknown.body.isNotBlank())
  }

  @Test
  fun `SYN-12 — حکمِ موتور جای نشانهٔ متنی — قطعیِ کوتاه آبی، ماندگار و سرور زرد، نشست سرخ`() {
    val up = ServerPulse.State.UP
    val raw = "به سرور نرسیدیم"
    val retrying = SyncModel.diagnose(raw, false, 0, false, up, SyncOutage.Kind.RETRYING)
    assertEquals(SyncModel.Problem.RETRYING, retrying.problem)
    assertEquals(V3Tone.INFO, retrying.tone)
    assertEquals(raw, retrying.detail)
    val down = SyncModel.diagnose(raw, false, 0, false, up, SyncOutage.Kind.UNREACHABLE)
    assertEquals(SyncModel.Problem.UNREACHABLE, down.problem)
    assertEquals(V3Tone.WARNING, down.tone)
    assertTrue(down.body.isNotBlank() && down.action.isNotBlank())
    val server = SyncModel.diagnose("سرور الان شلوغ است", false, 0, false, up, SyncOutage.Kind.SERVER)
    assertEquals(SyncModel.Problem.SERVER, server.problem)
    assertEquals(V3Tone.WARNING, server.tone)
    assertEquals(V3Tone.ERROR, SyncModel.diagnose("HTTP 503", false, 0, false, up).tone)   // بی حکم: همان پیشین
    assertEquals(SyncModel.Problem.SIGN_IN, SyncModel.diagnose("x", false, 0, false, up, SyncOutage.Kind.AUTH).problem)
    assertEquals(SyncModel.Problem.OFFLINE, SyncModel.diagnose(raw, false, 0, false, up, SyncOutage.Kind.OFFLINE).problem)
    //  «خطا» ⇒ همان نشانه‌های متنی؛ نگه‌داشتِ سرور و نبودِ دکان مقدم‌اند؛ بی خطا ⇒ حکم بی‌اثر
    assertEquals(SyncModel.Problem.LOCAL, SyncModel.diagnose("ژورنالِ همگام‌سازی با سایه نخواند", false, 0, false, up, SyncOutage.Kind.ERROR).problem)
    assertEquals(SyncModel.Problem.SERVER_BEHIND, SyncModel.diagnose(raw, true, 0, false, up, SyncOutage.Kind.UNREACHABLE).problem)
    assertEquals(SyncModel.Problem.NO_SHOP, SyncModel.diagnose(raw, false, 0, true, up, SyncOutage.Kind.UNREACHABLE).problem)
    assertEquals(SyncModel.Problem.NONE, SyncModel.diagnose("", false, 0, false, up, SyncOutage.Kind.OFFLINE).problem)
  }

  @Test
  fun `SYN-12 — نوارِ وضعیتِ کامپیوتر — قطعیِ بی هشدار برچسبِ حکمِ موتور را دارد، نه «در صف»`() {
    assertEquals(StrSettings.diagRetryingTitle, SyncModel.quietHead(SyncOutage.Kind.RETRYING))
    assertEquals(StrSettings.diagUnreachableTitle, SyncModel.quietHead(SyncOutage.Kind.UNREACHABLE))
    assertEquals(StrSettings.diagServerTitle, SyncModel.quietHead(SyncOutage.Kind.SERVER))
    //  بی شکست (صفِ عادی)، بی‌نت، نشست و خطای واقعی ⇒ همان سرخطِ پیشین («در صف»، خاکستری، سرخ)
    for (k in listOf(SyncOutage.Kind.OK, SyncOutage.Kind.OFFLINE, SyncOutage.Kind.AUTH, SyncOutage.Kind.ERROR, null)) {
      assertNull(k.toString(), SyncModel.quietHead(k))
    }
    //  همان واژه‌های `OUTAGE_LABEL` ِ وب (license/sync-core.js)
    I18n.source = { Lang.FA }
    assertEquals("دوباره می‌کوشد", SyncModel.quietHead(SyncOutage.Kind.RETRYING))
    assertEquals("به سرور نمی‌رسیم", SyncModel.quietHead(SyncOutage.Kind.UNREACHABLE))
    assertEquals("سرور الان جواب نمی‌دهد", SyncModel.quietHead(SyncOutage.Kind.SERVER))
  }

  @Test
  fun `دستگاه‌های جانبی — برگه از پیوند، و راهِ وصلِ چاپگر`() {
    assertEquals(DeviceTab.PRINTERS, DevicesModel.tab("printers"))
    assertEquals(DeviceTab.PHONE, DevicesModel.tab(" Phone "))
    assertNull(DevicesModel.tab(null)); assertNull(DevicesModel.tab("nope"))
    assertEquals(ThermalPrinter.Link.Network("192.168.1.9", 9100), DevicesModel.link(DevicesModel.Kind.NETWORK, null, " 192.168.1.9 ", "x", null))
    assertEquals(ThermalPrinter.Link.Network("10.0.0.2", 9200), DevicesModel.link(DevicesModel.Kind.NETWORK, null, "10.0.0.2", "9200", null))
    assertNull(DevicesModel.link(DevicesModel.Kind.NETWORK, null, " ", "9100", null))
    assertEquals(ThermalPrinter.Link.Bluetooth("AA:BB"), DevicesModel.link(DevicesModel.Kind.BLUETOOTH, "AA:BB", "", "", null))
    assertNull(DevicesModel.link(DevicesModel.Kind.USB, null, "", "", null))
    assertEquals("123 456", DevicesModel.grouped("123456"))
  }

  @Test
  fun `مسیرها — «دکان و امنیت» صفحهٔ خودش، «درباره» برای همه، باقی فقط با اجازهٔ مدیریت`() {
    val table = V3Modules.table
    val clerk = Access(granted = emptySet())
    assertEquals("settings/{group}", table.resolve("settings/security")!!.route.pattern)
    assertEquals("settings/{group}", table.resolve("settings/shop")!!.route.pattern)
    assertEquals(listOf("settings", "settings/shop", "settings/shop/app-lock", SettingsPaths.PIN), table.chain(SettingsPaths.PIN))
    assertTrue(table.resolve(SettingsPaths.ABOUT)!!.allowed(clerk))
    assertEquals(SettingsPaths.SYNC, table.resolve(SettingsPaths.SYNC)!!.route.pattern)
    assertTrue("وضعیتِ همگام‌سازی برای کارمند هم", table.resolve(SettingsPaths.SYNC)!!.allowed(clerk))
    assertFalse(table.resolve("settings")!!.allowed(clerk))
    assertFalse(table.resolve("settings/data/backup")!!.allowed(clerk))
    assertTrue(table.resolve("settings/data/backup")!!.allowed(Access(granted = setOf(Need.MANAGE))))
    assertEquals("devices?tab=printers", SettingsPaths.devices("printers"))
    assertEquals("devices", SettingsPaths.devices())
  }

  @Test
  fun `پیوندِ سردِ «امنیت» — صفحهٔ امنیت، برگشت ⇐ تنظیمات ⇐ خانه`() {
    val nav = V3Nav(V3Modules.table, "home", NavStack.of("home"))
    nav.openDeep("settings/security")
    assertEquals("settings/security", nav.current)
    assertTrue(nav.back()); assertEquals("settings", nav.current)
    assertTrue(nav.back()); assertEquals("home", nav.current)
    nav.openDeep(SettingsPaths.SYNC)
    assertEquals(SettingsPaths.SYNC, nav.current)
    assertTrue(nav.back()); assertEquals("home", nav.current)
  }
}
