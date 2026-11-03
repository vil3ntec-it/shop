package ir.vil3ntec.tohid.ios.e2e

import android.content.IosContext
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.guide.WhatsNew
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSettingsHome
import ir.vil3ntec.tohid.ios.IosBoot
import ir.vil3ntec.tohid.ios.IosNetRoute
import ir.vil3ntec.tohid.ios.IosRoot
import ir.vil3ntec.tohid.ios.keychain.Keychain
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.AutoSync
import kotlinx.coroutines.runBlocking
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.v3.modes.ModeState
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
 *    روی سرور ۰۵ «کالای تازه» ⇐ در دفتر و روی سرور ۰۶ «تازه‌ها» ۰۷ زبانِ دری (فهرستِ «بیشتر» با واژهٔ دری).
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
    ModeState.chosen.value = null
    //  کلیددانِ iOS (توکنِ ورود، کلیدِ دفتر): test.kexe اجازه‌اش را از `e2e/test.entitlements` دارد؛ اگر شبیه‌ساز نپذیرفت
    //  (errSecMissingEntitlement)، کلیددانِ درونِ حافظهٔ همین فرآیند — محیطِ آزمون، نه رفتارِ برنامه (در summary.md)
    val keychain = keychainProbe()

    runSkikoComposeUiTest(size = Size(WIDTH * SCALE, HEIGHT * SCALE), density = Density(SCALE)) {
      val e = E2eDriver(this, env.shots)
      e.note("سرور: ${env.api} — نگاه: ${env.probe} — عکس‌ها: ${env.shots}")
      e.fact(keychain)
      //  همهٔ گام‌ها با بیشینهٔ مهلت‌هایشان زیرِ ده دقیقه‌اند؛ گیرِ رشتهٔ اصلی ⇐ سرخِ روشن، نه کارِ CI ِ بی‌پایان
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
        //  ورود روی دیسکِ آیفون می‌نشاند — همان‌جا سنجیده می‌شود (نمونهٔ تازه، مثلِ خودِ کارگر). دفترِ صفحه با پرچمِ خاموشِ
        //  `shop.ledger_merge` تا بازکردنِ دوبارهٔ برنامه همان دفترِ پیشین است (#407، همهٔ سکوها؛ §۰.۱۳)
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

      e.step("07-dari", "زبانِ دری", needs = listOf("02-login")) {
        val faWarehouse = Str.warehouse
        e.toTab(NavCatalog.MORE)
        e.tap(Find.button(StrSettingsHome.look), StrSettingsHome.look)
        e.tap(Find.button(StrSettings.language), StrSettings.language)
        e.waitFor(Find.button(Lang.PRS.native), Lang.PRS.native)
        e.shot("07a-language")
        e.tap(Find.button(Lang.PRS.native), Lang.PRS.native)
        e.until("زبانِ برنامه دری شد", 10_000) { LocaleChoice.lang == Lang.PRS }
        val dariWarehouse = Str.warehouse
        assertNotEquals(faWarehouse, dariWarehouse, "«انبار» در دری همان ماند")
        e.shot("07b-dari-selected")
        //  فهرستِ «بیشتر» (و همهٔ صفحه‌ها) همان لحظه دری است: «${faWarehouse}» ⇐ «${dariWarehouse}»
        e.toTab(NavCatalog.MORE)
        e.waitFor(Find.button(dariWarehouse), dariWarehouse)
        assertTrue(!e.shown(Find.text(faWarehouse)), "«$faWarehouse» ِ فارسی هنوز روی صفحه است")
        e.shot("07c-dari-more")
      }

      e.finish()
    }
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
    const val WATCHDOG_S = 15 * 60L
    const val FREE_LINE = "شارژِ آیفون"
    const val FREE_PRICE = 120.0
  }
}
