package ir.vil3ntec.tohid.ios.e2e

import android.content.IosContext
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.guide.WhatsNew
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.i18n.StrHero
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrderFlow
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSettingsHome
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.ios.IosBoot
import ir.vil3ntec.tohid.ios.IosHaptics
import ir.vil3ntec.tohid.ios.jvm.System
import ir.vil3ntec.tohid.ios.IosNetRoute
import ir.vil3ntec.tohid.ios.IosRoot
import ir.vil3ntec.tohid.ios.keychain.Keychain
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.sync.AutoSync
import kotlinx.coroutines.runBlocking
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.v3.modes.ModeState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 *  گامِ ۸ِ آیفون — آزمونِ سرتاسریِ جریان‌های اصلی روی شبیه‌سازِ iOS (docs/IOS-PLAN-fa.md §۰.۱۳).
 *
 *  همان ریشهٔ برنامه (`IosRoot`، همان که `MainViewController` به SwiftUI می‌دهد) با همهٔ shimهای iOS، روی شبیه‌ساز،
 *  در برابرِ سرورِ واقعیِ مخزن روی PGlite (`ios/e2e/backend.mjs`، روی همین مک؛ `IosNetRoute` فقط لایهٔ حمل را
 *  به آن می‌برد). هر گام با زدن و نوشتن روی خودِ صفحه، نه صدا زدنِ مستقیمِ کد؛ پس از هر گام عکسِ صفحهٔ گوشی
 *  (۳۹۰×۸۴۴، ۳x) و درختِ دسترس‌پذیری در `TOHID_E2E_SHOTS` (مصنوعِ `ios-e2e-shots` ِ CI).
 *
 *    ۰۱ بالا آمدن تا صفحهٔ ورود (انتخابِ «دکان دارم») ۰۲ ورود با ایمیل و رمز ۰۳ همگام‌سازی: کالای دستگاهِ دیگر روی دفترِ
 *    آیفون ۰۴ صندوق: «قلمِ آزاد» ⇐ سبد ⇐ پرداختِ نقد ⇐ «ثبت فروش» (`CheckoutRoute` ⇐ `TillSale.record`) ⇐ فروش در دفتر و
 *    روی سرور ۰۵ «کالای تازه» ⇐ در دفتر و روی سرور ۰۶ «تازه‌ها»
 *  گامِ ۱۰ (§۰.۱۵): ۰۷ نسیهٔ بی قرض‌دار رد ⇐ «انتخابِ مشتری» ⇐ قرض‌دارِ تازه ⇐ نسیه ثبت (`CreditDebtor`، #906) ۰۸ خانه: کشیدن از بالا
 *    ⇐ کادرِ صندوق با موجودی (و لرزشِ «آستانه») ۰۹ «+» ِ کادر: باز کردنِ صندوق و ورودِ نقد ⇐ موجودی بالا رفت، در دفتر و روی
 *    سرور ۱۰ سفارشِ ویترین (backend.mjs) ⇐ کاشیِ «سفارش‌های آنلاین» ⇐ صف ⇐ قبول ⇐ «بسته شد و فاکتور» ⇐ یک فاکتور (#910)
 *  ۱۱ زبانِ دری (فهرستِ «بیشتر» با واژهٔ دری) — آخر، تا گام‌های پیشین همان برچسب‌های فارسی را ببینند.
 *
 *  سخت‌گیر: هر گامِ سرخ (یا اجرانشده چون پیش‌نیازش سرخ بود) آزمون را سرخ می‌کند؛ کارِ CI «گزارش» است، نه این آزمون.
 */
@OptIn(ExperimentalTestApi::class)
class CoreFlowsE2EIosTest {
  @Test
  fun coreFlowsOnTheSimulatorAgainstTheRealServer() {
    val env = E2eEnv.load()
    //  پیش از نخستین درخواستِ برنامه (`IosBoot`: سوکتِ زنده، همگام‌سازی) — هیچ درخواستی به سرورِ واقعی نمی‌رود
    IosNetRoute.toLocal(AppConfig.buildTimeBaseUrl, env.api)
    //  قلم‌ها و نشانِ برنامه کنارِ test.kexe (`ios/shared/build.gradle.kts`) — همان «بستهٔ برنامه» ِ ResFont/PainterRes
    assertTrue(
      NSBundle.mainBundle.pathForResource("vazirmatn_regular", ofType = "ttf") != null,
      "قلمِ Vazirmatn کنارِ test.kexe نیست: ${NSBundle.mainBundle.bundlePath}",
    )

    //  نصبِ تازه: انتخابِ حالت و «خوش‌آمد» (پروندهٔ `tohid`) را آزمون‌های پیشینِ همین فرآیند گذاشته‌اند (پیوندِ
    //  `Phase7IosTest` حالتِ «دکان» را برمی‌گزیند) ⇐ پاک، تا برنامه مثلِ بارِ اولِ نصب از «از برنامه چه می‌خواهید؟» بالا بیاید
    IosContext.deleteSharedPreferences("tohid")
    //  کادرِ بالای خانه (باز/پنهان، راهنمای کشیدن) هم مثلِ نصبِ تازه: پنهان تا کشیدن (گامِ ۰۸)
    IosContext.deleteSharedPreferences("tohid-shell")
    ModeState.chosen.value = null
    //  کلیددانِ iOS (توکنِ ورود، کلیدِ دفتر): test.kexe اجازه‌اش را از `e2e/test.entitlements` دارد؛ اگر شبیه‌ساز نپذیرفت
    //  (errSecMissingEntitlement)، کلیددانِ درونِ حافظهٔ همین فرآیند — محیطِ آزمون، نه رفتارِ برنامه (در summary.md)
    val keychain = keychainProbe()

    runSkikoComposeUiTest(size = Size(WIDTH * SCALE, HEIGHT * SCALE), density = Density(SCALE)) {
      val e = E2eDriver(this, env.shots)
      e.note("سرور: ${env.api} — نگاه: ${env.probe} — عکس‌ها: ${env.shots}")
      e.fact(keychain)
      //  گیرِ رشتهٔ اصلی ⇐ سرخِ روشن، نه کارِ CI ِ بی‌پایان (یازده گام در اجرای سبز زیرِ هشت دقیقه)
      e.armWatchdog(WATCHDOG_S)
      //  ساعتِ Compose را راننده جلو می‌برد (همپای زمانِ واقعی): چرخنده‌ها و انیمیشن‌های بی‌پایان آزمون را قفل نکنند
      mainClock.autoAdvance = false
      setContent { E2eHost { IosRoot() } }
      val store = IosBoot.store

      e.step("01-launch", "بالا آمدن تا صفحهٔ ورود") {
        e.pump(1_500)
        e.waitFor(Find.text(StrKit.modeTitle), StrKit.modeTitle, 30_000)
        e.shot("01a-mode")
        e.tap(Find.button(StrKit.modeShop), StrKit.modeShop)
        e.tap(Find.button(StrKit.continueLabel) and isEnabled(), StrKit.continueLabel)
        e.waitFor(Find.field(StrAuth.email), StrAuth.email)
        e.waitFor(Find.button(StrAuth.signInToAccount), StrAuth.signInToAccount)
        e.shot("01b-login")
      }

      e.step("02-login", "ورود با ایمیل و رمز به سرورِ آزمون", needs = listOf("01-launch")) {
        e.type(StrAuth.email, env.email)
        e.type(StrAuth.password, env.password)
        e.shot("02a-login-filled")
        e.tap(Find.button(StrAuth.signInToAccount) and isEnabled(), StrAuth.signInToAccount)
        //  پس از ورود: «رمزِ برنامه» یک بار پیشنهاد می‌شود (`LockSetupGate`) ⇐ «فعلاً نه»؛ سپس خانه با نوارِ پایین
        //  (`shown(home)` تنها کافی نیست: در جابه‌جاییِ AnimatedContent خانه و دروازه هر دو در درخت‌اند)
        val home = Find.tab(NavCatalog.HOME)
        e.until("خانهٔ دکان پس از ورود", 60_000) {
          if (e.shown(Find.button(StrAuth.notNow))) {
            e.shot("02b-lock-offer")
            e.tap(Find.button(StrAuth.notNow), StrAuth.notNow)
          }
          e.shown(home) && !e.shown(Find.field(StrAuth.email)) && !e.shown(Find.button(StrAuth.notNow))
        }
        assertTrue(Backend.tokens(IosContext).signedIn, "پس از ورود نشستی نیست (TokenStore)")
        assertTrue(!SyncStore(IosContext).accessToken.isNullOrBlank(), "SyncStore توکن را نمی‌بیند")
        e.shot("02c-home")
      }

      e.step("03-pull", "همگام‌سازی: کالای دستگاهِ دیگر از سرور به دفترِ آیفون", needs = listOf("02-login")) {
        //  کالای «دستگاهِ دیگرِ» دکان (backend.mjs) را کارگرِ همگام‌سازی (`SyncV1Worker`، نمونهٔ `ShopStore` ِ خودش) پس از
        //  ورود روی دیسکِ آیفون می‌نشاند — همان‌جا سنجیده می‌شود (نمونهٔ تازه، مثلِ خودِ کارگر). دفترِ صفحه هم با پرچمِ
        //  `shop.ledger_merge` (از ۱۴۰۵/۰۸/۱۸ پیش‌فرض روشن) همان را می‌گیرد؛ دیسک سنجه است چون با پرچمِ خاموش نمی‌گیرد (#407؛ §۰.۱۳)
        e.until("کالای «${env.product}» در دفترِ دیسکِ آیفون", 90_000) {
          e.pump(1_500)
          diskLedger().products.any { it.name == env.product }
        }
        e.note("همگام‌سازی: خطا=${AutoSync.lastError} — آخرین موفق=${AutoSync.lastOk}")
      }

      e.step("04-till", "صندوق: فروشِ نقدِ «قلمِ آزاد» (TillSale.record) و رسیدنش به سرور", needs = listOf("02-login")) {
        e.toTab(NavCatalog.SALE)
        //  صندوق نوارِ پایین ندارد: تا رفتنِ نوار صبر (دکمهٔ «بیشتر» ِ صندوق هم‌نامِ زبانهٔ «بیشتر» است)
        e.gone(Find.tab(NavCatalog.HOME), "نوارِ پایین")
        e.waitFor(Find.iconButton(StrKit.more), StrKit.more)
        e.shot("04a-till")
        val before = store.data.value.sales.map { it.id }.toSet()
        e.tap(Find.iconButton(StrKit.more), StrKit.more)
        e.tap(Find.button(StrSale.freeItem), StrSale.freeItem)
        e.type(StrSale.nameLabel, FREE_LINE)
        e.type(StrSale.unitPrice, FREE_PRICE.toInt().toString())
        e.shot("04b-free-line")
        e.tap(Find.button(StrKit.add) and isEnabled(), StrKit.add)
        e.gone(Find.field(StrSale.nameLabel), StrSale.freeItem)
        e.tap(Find.clickLabel(StrSale.openCart), StrSale.openCart)
        e.waitFor(Find.button(StrSale.pay), StrSale.pay)
        e.shot("04c-cart")
        e.tap(Find.button(StrSale.pay), StrSale.pay)
        e.waitFor(Find.button(StrSale.recordSale) and isEnabled(), StrSale.recordSale)
        e.shot("04d-checkout")
        e.tap(Find.button(StrSale.recordSale) and isEnabled(), StrSale.recordSale)
        e.waitFor(Find.button(StrSale.newSale), StrSale.newSale, 30_000)
        e.shot("04e-receipt")
        val sale = store.data.value.sales.singleOrNull { it.id !in before } ?: throw AssertionError("فروشِ تازه در دفتر نیست")
        assertEquals("cash", sale.paymentMethod, "روشِ پرداخت")
        assertEquals(FREE_PRICE, sale.finalTotal, "مبلغِ نهایی")
        assertTrue(store.data.value.saleItems.any { it.saleId == sale.id }, "قلمِ فروش در دفتر نیست")
        //  همگام‌سازیِ آیفون فروش را به سرور می‌برد
        e.until("فروشِ $FREE_PRICE روی سرور (sync_rows)", 90_000) {
          env.serverRows("sales").any { it["finalTotal"]?.jsonPrimitive?.doubleOrNull == FREE_PRICE }
        }
        e.tap(Find.button(StrSale.newSale), StrSale.newSale)
        e.gone(Find.button(StrSale.newSale), StrSale.newSale)
        e.shot("04f-till-after")
      }

      val newProduct = "صابونِ آیفون"
      e.step("05-product", "افزودنِ کالای تازه", needs = listOf("02-login")) {
        e.toTab(NavCatalog.PRODUCTS)
        e.waitFor(Find.button(StrProducts.newProduct), StrProducts.newProduct)
        e.shot("05a-products")
        e.tap(Find.button(StrProducts.newProduct), StrProducts.newProduct)
        e.type(StrProducts.name, newProduct)
        //  دستهٔ تازه از برگهٔ دسته‌ها («دستهٔ تازه» ⇐ نام ⇐ «افزودن» ⇐ همان دسته برای همین ردیف). تا برگه کامل نرفته چیزی
        //  زده نمی‌شود: زدن روی پردهٔ برگهٔ در حالِ رفتن «بستنِ دوباره» است و فرمِ زیرش را هم می‌بندد (اجرای چهارم)
        e.tap(Find.button(StrProducts.newCategory), StrProducts.newCategory)
        e.type(StrProducts.newCategory, "شوینده")
        e.tap(Find.button(StrKit.add) and isEnabled(), StrKit.add)
        e.gone(Find.text(StrProducts.pickCategory), StrProducts.pickCategory)
        e.waitFor(Find.button("شوینده"), "شوینده")
        e.tap(Find.button("عدد"), "عدد")
        e.type(StrProducts.purchasePrice, "60")
        e.type(StrProducts.salePrice, "85")
        e.shot("05b-new-product")
        e.tap(Find.button(StrProducts.saveAll) and isEnabled(), StrProducts.saveAll)
        e.until("کالای «$newProduct» در دفتر", 20_000) { store.data.value.products.any { it.name == newProduct } }
        val p = store.data.value.products.first { it.name == newProduct }
        assertEquals(85.0, p.salePrice, "قیمتِ فروش")
        assertEquals("شوینده", p.category, "دسته")
        e.shot("05c-saved")
        e.until("کالای «$newProduct» روی سرور (sync_rows)", 90_000) {
          env.serverRows("products").any { it["name"]?.jsonPrimitive?.content == newProduct }
        }
      }

      e.step("06-whats-new", "«تازه‌ها»", needs = listOf("02-login")) {
        val sections = WhatsNew.bundled.sections
        assertTrue(sections.isNotEmpty(), "«تازه‌ها» ِ همراهِ برنامه خالی است")
        e.toTab(NavCatalog.MORE)
        e.waitFor(Find.button(StrSettings.whatsNew), StrSettings.whatsNew)
        e.shot("06a-more")
        e.tap(Find.button(StrSettings.whatsNew), StrSettings.whatsNew)
        val first = sections.first().titleIn(I18n.source().code)
        e.waitFor(Find.text(first, substring = true), first)
        e.shot("06b-whats-new")
      }

      e.step("07-credit", "نسیهٔ بی قرض‌دار رد ⇐ «انتخابِ مشتری» ⇐ قرض‌دارِ تازه ⇐ نسیه ثبت (CreditDebtor)", needs = listOf("02-login")) {
        e.toTab(NavCatalog.SALE)
        e.gone(Find.tab(NavCatalog.HOME), "نوارِ پایین")
        e.waitFor(Find.iconButton(StrKit.more), StrKit.more)
        val before = store.data.value.sales.map { it.id }.toSet()
        e.tap(Find.iconButton(StrKit.more), StrKit.more)
        e.tap(Find.button(StrSale.freeItem), StrSale.freeItem)
        e.type(StrSale.nameLabel, CREDIT_LINE)
        e.type(StrSale.unitPrice, CREDIT_PRICE.toInt().toString())
        e.tap(Find.button(StrKit.add) and isEnabled(), StrKit.add)
        e.gone(Find.field(StrSale.nameLabel), StrSale.freeItem)
        e.tap(Find.clickLabel(StrSale.openCart), StrSale.openCart)
        e.tap(Find.button(StrSale.pay), StrSale.pay)
        e.waitFor(Find.button(StrSale.recordSale) and isEnabled(), StrSale.recordSale)
        e.tap(Find.button(StrSale.creditPay), StrSale.creditPay)
        //  ⛔ نسیه بی مشتری: «ثبت فروش» خاموش با پیامِ روشن و میان‌برِ «انتخابِ مشتری»؛ زدنِ خودِ دکمهٔ خاموش هم هیچ نمی‌نویسد
        val blocked = Find.button(StrSale.recordSale) and isNotEnabled()
        e.waitFor(Find.text(StrTillPay.creditNeedsCustomer), "پیامِ «نسیه بی مشتری ثبت نمی‌شود»")
        e.waitFor(blocked, "«${StrSale.recordSale}» ِ خاموش")
        e.shot("07a-credit-blocked")
        e.pressDisabled(blocked, "«${StrSale.recordSale}» ِ خاموش")
        assertTrue(store.data.value.sales.none { it.id !in before }, "نسیهٔ بی قرض‌دار در دفتر نشست")
        assertTrue(e.shown(blocked), "«${StrSale.recordSale}» بی قرض‌دار روشن شد")
        //  «انتخابِ مشتری» ⇐ «قرض‌دارِ تازه» ⇐ نام ⇐ ذخیره ⇐ همان قرض‌دار روی برگهٔ پرداخت
        e.tap(Find.button(StrSale.pickCustomer), StrSale.pickCustomer)
        e.tap(Find.button(StrSale.newDebtor), StrSale.newDebtor)
        e.type(StrMoney.name, DEBTOR)
        e.shot("07b-new-debtor")
        e.tap(Find.button(StrKit.save) and isEnabled(), StrKit.save)
        e.gone(Find.field(StrMoney.name), StrSale.newDebtor)
        e.until("قرض‌دارِ «$DEBTOR» روی برگهٔ پرداخت و «${StrSale.recordSale}» روشن", 20_000) {
          e.shown(Find.text(DEBTOR)) && e.shown(Find.button(StrSale.recordSale) and isEnabled())
        }
        assertTrue(!e.shown(Find.text(StrTillPay.creditNeedsCustomer)), "پیامِ «نسیه بی مشتری» با قرض‌دار ماند")
        e.shot("07c-credit-debtor")
        IosHaptics.clearRecent()
        e.tap(Find.button(StrSale.recordSale) and isEnabled(), StrSale.recordSale)
        e.waitFor(Find.button(StrSale.newSale), StrSale.newSale, 30_000)
        e.shot("07d-credit-receipt")
        val d = store.data.value
        val debtor = d.debtors.singleOrNull { it.name == DEBTOR } ?: throw AssertionError("قرض‌دارِ «$DEBTOR» در دفتر نیست")
        val sale = d.sales.singleOrNull { it.id !in before } ?: throw AssertionError("نسیهٔ تازه در دفتر نیست")
        assertEquals("credit", sale.paymentMethod, "روشِ پرداخت")
        assertEquals(debtor.id, sale.debtorId, "قرض‌دارِ فروش")
        assertEquals(CREDIT_PRICE, sale.finalTotal, "مبلغِ نهایی")
        assertEquals(CREDIT_PRICE, sale.remaining, "باقی‌مانده")
        assertEquals(CREDIT_PRICE, ShopStore.debt(d, debtor.id), "بدهیِ قرض‌دار")
        assertTrue(IosHaptics.Event.SUCCESS in IosHaptics.recent, "لرزشِ «ثبت شد» نرسید: ${IosHaptics.recent}")
        e.until("نسیهٔ $CREDIT_PRICE با قرض‌دار روی سرور (sync_rows)", 90_000) {
          env.serverRows("sales").any { str(it, "debtorId") == debtor.id && str(it, "paymentMethod") == "credit" }
        }
        e.until("قرض‌دارِ «$DEBTOR» روی سرور", 90_000) { env.serverRows("debtors").any { str(it, "name") == DEBTOR } }
        e.tap(Find.button(StrSale.newSale), StrSale.newSale)
        e.gone(Find.button(StrSale.newSale), StrSale.newSale)
      }

      e.step("08-hero-pull", "خانه: کشیدن از بالا ⇐ کادرِ «صندوق» با موجودی", needs = listOf("02-login")) {
        e.toTab(NavCatalog.HOME)
        e.waitFor(Find.button(StrHome.showDrawer), StrHome.showDrawer)
        assertTrue(!e.shown(Find.button(StrHero.addOrTake)), "کادرِ صندوق پیش از کشیدن پیداست")
        e.shot("08a-home")
        IosHaptics.clearRecent()
        //  لمسِ واقعی (نه دستگیره): انگشت از زیرِ دستگیره ~۳۷۰ نقطه پایین ⇐ گذر از آستانه (۸۴ نقطهٔ کادرِ دیدنی) و رها
        e.pullDown(Find.button(StrHome.showDrawer), "کادرِ پنهانِ خانه", distance = 1_100f, ms = 900)
        e.until("کادرِ صندوق پس از کشیدن", 10_000) { e.shown(Find.button(StrHero.addOrTake)) && e.shown(Find.button(StrHome.hideDrawer)) }
        assertTrue(IosHaptics.Event.THRESHOLD in IosHaptics.recent, "لرزشِ «آستانه» ِ کشیدن نرسید: ${IosHaptics.recent}")
        val (label, cash) = tillOnHero(store.data.value)
        e.waitFor(Find.text(label), label)
        e.until("موجودیِ «$label» = ${money(cash)} روی کادر", 10_000) { e.shown(Find.text(money(cash))) }
        e.shot("08b-hero")
      }

      e.step("09-hero-cash-in", "«+» ِ کادرِ صندوق: باز کردنِ صندوق و ورودِ نقد", needs = listOf("02-login")) {
        e.toTab(NavCatalog.HOME)
        if (!e.shown(Find.button(StrHero.addOrTake))) {
          e.note("کادر باز نیست (گامِ ۰۸ سرخ؟) ⇐ دستگیره")
          e.tap(Find.button(StrHome.showDrawer), StrHome.showDrawer)
        }
        e.tap(Find.button(StrHero.addOrTake), StrHero.addOrTake)
        //  صندوقِ بسته ⇐ همان برگه فقط «باز کردنِ صندوق» دارد (پولِ اول ⇐ «باز کن») و دوباره «+»
        if (CashShift.current(store.data.value) == null) {
          e.waitFor(Find.text(StrSafe.shiftClosed), StrSafe.shiftClosed)
          e.shot("09a-shift-closed")
          e.tap(Find.button(StrSale.openDrawer), StrSale.openDrawer)
          e.type(StrSale.openingCashField, OPENING.toInt().toString())
          e.tap(Find.button(StrSale.openIt) and isEnabled(), StrSale.openIt)
          e.until("صندوقِ باز در دفتر", 20_000) { CashShift.current(store.data.value) != null }
          e.gone(Find.field(StrSale.openingCashField), StrSale.openDrawer)
          e.tap(Find.button(StrHero.addOrTake), StrHero.addOrTake)
        }
        val shift = CashShift.current(store.data.value) ?: throw AssertionError("صندوق باز نیست")
        val before = CashShift.totalsOf(store.data.value, shift, System.currentTimeMillis()).expected
        e.type(StrSafe.amount, CASH_IN.toInt().toString())
        e.shot("09b-cash-in")
        //  دکمهٔ ثبت (برچسبِ کلیکِ «اضافه»)، نه بخشِ «اضافه» ِ انتخابگرِ بالای برگه
        e.tap(Find.clickLabel(StrHero.add) and isEnabled(), StrHero.add)
        e.gone(Find.field(StrSafe.amount), StrHero.tillSheet)
        val row = store.data.value.auditLog.singleOrNull { it.type == "cash_in" && it.amount == CASH_IN }
          ?: throw AssertionError("ورودِ نقدِ $CASH_IN در دفتر نیست")
        assertEquals(shift.open.id, row.refId, "ورودِ نقد روی همان صندوقِ باز")
        val after = CashShift.totalsOf(store.data.value, shift, System.currentTimeMillis()).expected
        assertEquals(before + CASH_IN, after, "موجودیِ صندوق پس از ورودِ نقد")
        e.waitFor(Find.text(StrHero.tillBalance), StrHero.tillBalance)
        e.until("موجودیِ تازهٔ ${money(after)} روی کادر", 10_000) { e.shown(Find.text(money(after))) }
        e.shot("09c-till-after")
        e.until("ورودِ نقدِ $CASH_IN روی سرور (sync_rows)", 90_000) {
          env.serverRows("auditLog").any { str(it, "type") == "cash_in" && it["amount"]?.jsonPrimitive?.doubleOrNull == CASH_IN }
        }
      }

      e.step("10-online-order", "سفارشِ آنلاین: کاشیِ خانه ⇐ صف ⇐ قبول ⇐ یک فاکتور", needs = listOf("02-login")) {
        //  مشتری از ویترین سفارش می‌دهد (بارِ اول بخشِ سفارش برای همین دکان روشن می‌شود)؛ صاحب به برنامه برمی‌گردد
        val order = env.placeOrder()
        e.note("سفارشِ ویترین: #${order.orderNo} (${order.id}) — ${order.product}، ${order.totalMinor / 100} ؋")
        E2eApp.background()
        e.pump(1_500)
        E2eApp.foreground()
        e.toTab(NavCatalog.HOME)
        val tile = Find.clickLabelStarting(StrOrdersHub.tile)
        e.waitFor(tile, StrOrdersHub.tile, 60_000)
        e.until("کاشی سفارشِ تازه را می‌شمارد", 60_000) { e.shown(Find.clickLabel(StrOrdersHub.tileFresh(1))) }
        e.shot("10a-home-orders")
        e.tap(tile, StrOrdersHub.tile)
        val card = Find.button(StrOnline.orderTitle(order.orderNo))
        e.waitFor(card, StrOnline.orderTitle(order.orderNo), 60_000)
        e.shot("10b-queue")
        e.tap(card, StrOnline.orderTitle(order.orderNo))
        //  قبول: دکمهٔ نوارِ پایین ⇐ برگهٔ «زمانِ آماده» (۱۵ دقیقه از پیش) با دکمهٔ هم‌نام ⇐ همان
        val accept = Find.button(StrOnline.orderAction("accepted")) and isEnabled()
        e.waitFor(accept, StrOnline.orderAction("accepted"), 30_000)
        e.shot("10c-detail")
        e.tap(accept, StrOnline.orderAction("accepted"))
        //  برگهٔ کار («زمانِ آماده») روی جزئیات؛ دکمهٔ هم‌نامِ برگه پس از دکمهٔ نوار در درخت می‌آید ⇐ آخرین
        e.waitFor(Find.text(StrOnline.readyIn), StrOnline.readyIn)
        e.until("دکمهٔ «${StrOnline.orderAction("accepted")}» ِ برگه", 10_000) { e.count(accept) >= 2 }
        e.shot("10d-accept")
        e.tap(accept, "${StrOnline.orderAction("accepted")} (برگه)", lastOne = true)
        e.until("سفارش روی سرور «accepted»", 30_000) { env.orderState(order.id).first == "accepted" }
        //  «بسته شد و فاکتور» ⇐ «ثبتِ فاکتور»: بسته‌بندی، «فاکتور شد» ِ سرور (یک بار، زیرِ قفل) و فروشِ `o-<سفارش>` در دفتر
        e.tap(Find.button(StrOrderFlow.packAndInvoice) and isEnabled(), StrOrderFlow.packAndInvoice, 30_000)
        e.shot("10e-invoice-ask")
        e.tap(Find.button(StrOrderFlow.confirmInvoice) and isEnabled(), StrOrderFlow.confirmInvoice)
        val saleId = OrderFulfillment.saleIdOf(order.id)
        e.until("فروشِ سفارش در دفتر", 30_000) { store.data.value.sales.any { it.id == saleId } }
        val invoiced = StrOrderFlow.invoiced("", "").substringBefore(':').trim()
        e.waitFor(Find.text(invoiced, substring = true), invoiced)
        e.shot("10f-invoiced")
        //  یک فاکتور: یک فروش در دفتر، یک «فاکتور شد» روی سرور، و دیگر هیچ دکمهٔ فاکتوری
        val sales = store.data.value.sales.filter { it.orderId == order.id }
        assertEquals(listOf(saleId), sales.map { it.id }, "فروش‌های همین سفارش در دفتر")
        assertEquals(order.totalMinor / 100.0, sales.single().finalTotal, "مبلغِ فاکتور = مبلغِ سفارش")
        for (b in listOf(StrOrderFlow.packAndInvoice, StrOrderFlow.toInvoice, StrOrderFlow.invoiceAndHandOver, StrOrderFlow.confirmInvoice)) {
          assertTrue(!e.shown(Find.button(b)), "پس از فاکتور هنوز «$b» هست")
        }
        val (status, invoices) = env.orderState(order.id)
        assertEquals("packed", status, "وضعیتِ سفارش روی سرور")
        assertEquals(1, invoices, "«فاکتور شد» ِ سرور")
        e.until("فروشِ سفارش روی سرور (sync_rows)", 90_000) { env.serverRows("sales").count { str(it, "orderId") == order.id } == 1 }
      }

      e.step("11-dari", "زبانِ دری", needs = listOf("02-login")) {
        val faWarehouse = Str.warehouse
        e.toTab(NavCatalog.MORE)
        e.tap(Find.button(StrSettingsHome.look), StrSettingsHome.look)
        e.tap(Find.button(StrSettings.language), StrSettings.language)
        e.waitFor(Find.button(Lang.PRS.native), Lang.PRS.native)
        e.shot("11a-language")
        e.tap(Find.button(Lang.PRS.native), Lang.PRS.native)
        e.until("زبانِ برنامه دری شد", 10_000) { LocaleChoice.lang == Lang.PRS }
        val dariWarehouse = Str.warehouse
        assertNotEquals(faWarehouse, dariWarehouse, "«انبار» در دری همان ماند")
        e.shot("11b-dari-selected")
        //  فهرستِ «بیشتر» (و همهٔ صفحه‌ها) همان لحظه دری است: «${faWarehouse}» ⇐ «${dariWarehouse}»
        e.toTab(NavCatalog.MORE)
        e.waitFor(Find.button(dariWarehouse), dariWarehouse)
        assertTrue(!e.shown(Find.text(faWarehouse)), "«$faWarehouse» ِ فارسی هنوز روی صفحه است")
        e.shot("11c-dari-more")
      }

      e.finish()
    }
  }

  private fun str(row: JsonObject, key: String): String? = row[key]?.jsonPrimitive?.content

  /**
   *  برچسب و عددِ کادرِ «صندوق» ِ خانه — همان `rememberHomeView`: صندوقِ باز ⇐ «موجودیِ صندوق» = «باید باشد» ِ همان صندوق؛
   *  بسته ⇐ «نقدِ امروز · صندوق بسته» = گردشِ نقدِ امروز (`CashShift`، همان موتور، بی حسابِ تازه)
   */
  private fun tillOnHero(d: ShopData): Pair<String, Double> {
    val now = System.currentTimeMillis()
    val shift = CashShift.current(d)
    if (shift != null) return StrHero.tillBalance to CashShift.totalsOf(d, shift, now).expected
    val start = java.util.Calendar.getInstance().apply {
      set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
      set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    return StrHome.todayCashClosed to CashShift.totals(d, start, now, 0.0).expected
  }

  /** دفترِ روی دیسکِ آیفون — نمونهٔ تازهٔ `ShopStore`، همان کاری که `SyncV1Worker` پیش از هر دور می‌کند */
  private fun diskLedger(): ShopData = runBlocking { ShopStore(IosContext).also { it.load() }.data.value }

  /** نوشتن، خواندن و پاک کردنِ یک قلمِ کلیددان؛ شکست ⇐ کلیددانِ درونِ حافظه (`Keychain.memory`) و دلیلش */
  private fun keychainProbe(): String {
    val probe = byteArrayOf(7, 1, 4, 0, 5)
    val error = runCatching {
      Keychain.put("e2e-probe", probe)
      check(Keychain.read("e2e-probe")?.contentEquals(probe) == true) { "خواندن همان نبود" }
      Keychain.delete("e2e-probe")
    }.exceptionOrNull() ?: return "کلیددان: کلیددانِ واقعیِ iOS ِ شبیه‌ساز"
    Keychain.memory = HashMap()
    return "کلیددان: کلیددانِ iOS در test.kexe نشد (${error.message}) ⇐ کلیددانِ درونِ حافظهٔ همین فرآیند"
  }

  private companion object {
    /** آیفونِ ۶٫۱ اینچی (۱۵/۱۶): ۳۹۰×۸۴۴ نقطه، ۳x */
    const val WIDTH = 390f
    const val HEIGHT = 844f
    const val SCALE = 3f
    const val WATCHDOG_S = 20 * 60L
    const val FREE_LINE = "شارژِ آیفون"
    const val FREE_PRICE = 120.0
    const val CREDIT_LINE = "قرضِ آیفون"
    const val CREDIT_PRICE = 90.0
    const val DEBTOR = "مشتریِ نسیهٔ آیفون"
    const val OPENING = 500.0
    const val CASH_IN = 250.0
  }
}
