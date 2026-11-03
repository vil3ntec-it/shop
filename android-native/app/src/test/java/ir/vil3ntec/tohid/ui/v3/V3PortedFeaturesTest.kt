package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.ui.AppLockKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  چهار کاری که تا پیش از این فقط از رابطِ قدیم می‌رسید، حالا در نسخهٔ ۳ (پیش از پاک شدنِ رابطِ قدیم):
 *  ۱) «ثبتِ سریع» (S03/POS-17) در صندوقِ گوشی؛ ۲) پروندهٔ مشتری: فروشِ امانی، شمارهٔ دوم و برچسب (POS-27، CRM-02/11)؛
 *  ۳) «چه چیزی تازه است» پس از به‌روزرسانی (OPS-05)؛ ۴) Ctrl+L ⇒ قفلِ برنامه وقتی «تعویضِ کاربر» خاموش است (S46).
 *  بخشِ Compose با آزمونِ JVM ساخته نمی‌شود، پس سیم‌کشی با متنِ همان پرونده‌ها سنجیده می‌شود؛ منطق در آزمون‌های مدل
 *  (`QuickTillModelTest`، `CustomerFileV3Test`) و همین‌جا (`AppLockKey`).
 */
class V3PortedFeaturesTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `ثبتِ سریع — صندوق، فروشِ سریع و برگهٔ اسکنِ گوشی همان قاعده و همان ثبتِ کامپیوتر را می‌گیرند`() {
    val feedback = src("ui/v3/sales/cart/CartFeedback.kt")
    assertTrue(feedback.contains("QuickTillModel.unknown(s.code, ir.vil3ntec.tohid.ui.components.pos.quickNext(context))"))
    assertTrue(feedback.contains("open(path)"))
    for (rel in listOf("ui/v3/sales/cart/SaleRoute.kt", "ui/v3/sales/quick/QuickSaleRoute.kt")) {
      assertTrue(rel, src(rel).contains("CartFeedback(context, cart, toasts, snackbar, scope) { path -> nav.open(path) }"))
    }
    assertTrue(src("ui/v3/sales/scan/SaleScanRoute.kt").contains("onAction = { path -> nav.replace(path) }"))
    assertTrue(src("ui/v3/sales/scan/SaleScanPanel.kt").contains("V3Button(label, { onAction(path) }"))
    val routes = src("ui/v3/sales/SalesRoutes.kt")
    assertTrue(routes.contains("V3Route(SalesPaths.QUICK_REGISTER, RouteKind.SHEET, { StrQuickRegister.title }, parent = SalesPaths.SALE, chrome = Chrome.OWN) { QuickRegisterRoute() }"))
    val route = src("ui/v3/sales/cart/QuickRegisterRouteAndroid.kt")
    //  مسیر فقط با تنظیمِ روشن و نقشِ مجاز باز است؛ ثبت روی دفترِ همین لحظه و همان گامِ پایانیِ کامپیوتر
    assertTrue(route.contains("quickNext(context) == QuickRegister.Next.QUICK"))
    assertTrue(route.contains("QuickRegister.register(store.data.value, code, draft,"))
    assertTrue(route.contains("saveQuick(r, store, cart)"))
    //  برگهٔ مشترک بی وابستگیِ سکو (آیفون همان را می‌سازد)؛ تا ثبت تمام نشده بسته نمی‌شود
    val sheet = src("ui/v3/sales/cart/QuickRegisterSheet.kt")
    assertTrue(sheet.contains("dismissible = !saving"))
    assertFalse(sheet.contains("LocalContext") || sheet.contains("System.currentTimeMillis"))
    //  پنجرهٔ کامپیوتر همان `saveQuick` را صدا می‌زند — منطقِ ثبت یک جاست
    val shared = src("ui/components/pos/QuickRegisterAndroid.kt")
    assertTrue(shared.contains("scope.launch { onDone(saveQuick(r, store, cartStore)?.message) }"))
    assertEquals(1, Regex("""SalesEngine\.addToCartCapped\(""").findAll(shared).count())
    assertTrue(shared.contains("QuickRegister.next(on = true, may = may, freeLineOpen = freeOpen)"))
  }

  @Test
  fun `پروندهٔ مشتری — امانی، امتیاز و قیمتِ گروه در حسابِ گوشی و امانی در پروندهٔ کامپیوتر`() {
    val extras = src("ui/v3/money/DebtorFileExtras.kt")
    assertTrue(extras.contains("LayawayCustomerPanel(d, store, debtorId, toast)"))
    assertTrue(extras.contains("LoyaltyHistory(d, debtorId,"))
    assertTrue(extras.contains("CrmPricingButton(store, d, debtorId, toast,"))
    assertTrue(src("ui/v3/money/MoneyRoutes.kt").contains("extras = { DebtorFileExtras(data, store, id) { toasts.show(it) } },"))
    val ledger = src("ui/v3/money/DebtorLedgerScreen.kt")
    assertTrue(ledger.contains("if (extras != null) item(key = \"extras\") { extras() }"))
    assertEquals("هر دو چیدمان (باریک و دوستونه)", 2, Regex("""ledgerItems\(rows, book, account, actions, stagger, extras,""").findAll(ledger).count())
    assertTrue(src("ui/screens/DeskCustomers.kt").contains("LayawayCustomerPanel(d, store, id, ::toast)"))
  }

  /** CRM-16 — دکمهٔ «کارتِ مشتری» در پروندهٔ مشتریِ گوشیِ نسخهٔ ۳، همان جزءِ پروندهٔ کامپیوتر (خاموش ⇒ هیچ) */
  @Test
  fun `پروندهٔ مشتری — کارتِ مشتری در حسابِ گوشیِ نسخهٔ ۳ همان دکمهٔ کامپیوتر`() {
    val extras = src("ui/v3/money/DebtorFileExtras.kt")
    assertTrue(extras.contains("import ir.vil3ntec.tohid.ui.screens.CustomerCardButton"))
    assertTrue(extras.contains("CustomerCardButton(store, d, debtorId, toast,"))
    assertTrue(src("ui/screens/DeskCustomers.kt").contains("CustomerCardButton(store, d, id, ::toast,"))
    //  کلید و مشتریِ فعال در خودِ دکمه سنجیده می‌شوند ⇒ خاموش یا ادغام‌شده هیچ چیز نمی‌کشد
    val button = src("ui/screens/CustomerCardAndroid.kt")
    assertTrue(button.contains("if (!on) return"))
    assertTrue(button.contains("if (!CustomerCrm.isActive(debtor)) return"))
  }

  @Test
  fun `پروندهٔ مشتری — شمارهٔ دوم و برچسب در کشوی حساب، صافیِ برچسب در فهرست`() {
    val parts = src("ui/v3/money/DebtorLedgerParts.kt")
    //  CRM-15: همان خط، با مجوزِ دیدنِ شماره (`customers.contact`؛ بی آن شمارهٔ دوم پوشیده)
    assertTrue(parts.contains("CustomerProfileForm.extraLine(account.debtor, contacts)"))
    assertTrue(parts.contains("val contacts = ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()"))
    assertTrue(parts.contains("if (profile.isNotBlank()) Text(profile,"))
    val list = src("ui/v3/money/DebtorsListScreen.kt")
    assertTrue(list.contains("DebtorListModel.rows(d, term, sort, System.currentTimeMillis(), tag)"))
    assertTrue(list.contains("V3Chip(StrMoney.allTags, tag == null, { tagPick = null })"))
    assertTrue(list.contains("DebtorListModel.keptTag(d, tagPick)"))
    //  ویرایشِ هر دو در برگهٔ نسخهٔ ۳ (#393) هست
    val form = src("ui/v3/money/DebtorFormSheets.kt")
    assertTrue(form.contains("StrMoney.phone2Optional") && form.contains("StrMoney.tagsField"))
  }

  @Test
  fun `تازه‌ها — یک بار پس از به‌روزرسانی در پوستهٔ نسخهٔ ۳، پشتِ قفل‌ها نه، و همهٔ تغییرها در دربارهٔ برنامه`() {
    val root = src("ui/AppRoot.kt")
    val call = root.indexOf("ir.vil3ntec.tohid.ui.screens.WhatsNewOnUpdate()")
    assertTrue("WhatsNewOnUpdate در پوستهٔ نسخهٔ ۳ نیست", call > 0)
    //  پس از همهٔ دروازه‌ها (قفلِ برنامه، قفلِ صندوق، دکان، رمز) — روی صفحهٔ قفل باز نمی‌شود
    for (gate in listOf("if (locked) { AppLockScreen", "if (tillLocked) {", "if (needsShop) {", "if (askLock) {")) {
      val at = root.indexOf(gate)
      assertTrue(gate, at in 1 until call)
    }
    //  «دیدنِ همهٔ تغییرها» در «دربارهٔ برنامه» همان صفحهٔ «بیشتر» ← «تازه‌ها» را باز می‌کند
    assertTrue(src("ui/v3/settings/sections/AboutScreen.kt").contains("onClick = { openWhatsNew(nav) }"))
    val pref = src("data/WhatsNewPrefAndroid.kt")
    assertTrue(pref.contains("const val DEFAULT_ON = true"))
    assertTrue(pref.contains("getBoolean(KEY_ON, true)"))
    //  نخستین اجرا هیچ نشان نمی‌دهد (`WhatsNew.plan`: همه «دیده‌شده» همان دم، `WhatsNewTest`) و «فهمیدم» دیده‌شده را می‌نویسد
    val boundary = src("ui/screens/WhatsNewAndroid.kt")
    assertTrue(boundary.contains("plan.storeNow?.let { WhatsNewPref.setKeys(context, it) }"))
    assertTrue(boundary.contains("WhatsNewPref.setKeys(context, WhatsNew.ack(sections, WhatsNewPref.keys(context)))"))
    //  متن از `docs/WHATS-NEW-fa.md` ِ همراهِ برنامه، نه فهرستِ درونِ کد
    assertTrue(boundary.contains("WhatsNew.bundled.sections"))
  }

  @Test
  fun `Ctrl+L — اول PIN ِ شخصی، وگرنه قفلِ برنامه، بی رمز فقط راهش`() {
    assertEquals(AppLockKey.Then.TILL, AppLockKey.after(tillLocked = true, appLock = true))
    assertEquals(AppLockKey.Then.TILL, AppLockKey.after(tillLocked = true, appLock = false))
    assertEquals(AppLockKey.Then.APP, AppLockKey.after(tillLocked = false, appLock = true))
    assertEquals(AppLockKey.Then.HINT, AppLockKey.after(tillLocked = false, appLock = false))
    val root = src("ui/AppRoot.kt")
    assertTrue(root.contains("AppLockKey.after(ir.vil3ntec.tohid.data.TillUser.lock(context, lockStore.enabled), lockStore.enabled)"))
    assertTrue(root.contains("AppLockKey.Then.APP -> unlocked = false"))
    assertTrue(root.contains("StrTill.lockNeedsPassword"))
    //  کامپیوتر Ctrl+L را به همان `AppNav.lock()` می‌دهد
    assertTrue(src("ui/AppNav.kt").contains("fun lock() { lockNow.intValue++ }"))
  }

  @Test
  fun `جزءهای آمده هیچ‌کدام زیرِ رابطِ قدیم یا با پسوندِ آن نیستند`() {
    val moved = listOf(
      "ui/v3/sales/cart/QuickTillModel.kt", "ui/v3/sales/cart/QuickRegisterSheet.kt", "ui/v3/sales/cart/QuickRegisterRouteAndroid.kt",
      "ui/v3/money/DebtorFileExtras.kt",
      "ui/AppLockKey.kt", "core/i18n/StrQuickRegister.kt",
    )
    for (rel in moved) {
      val text = src(rel)
      assertFalse(rel, rel.contains("legacy", ignoreCase = true))
      assertFalse(rel, Regex("""\b\w+Legacy\b""").containsMatchIn(text))
      assertFalse(rel, text.contains("ui.legacy"))
    }
  }
}
