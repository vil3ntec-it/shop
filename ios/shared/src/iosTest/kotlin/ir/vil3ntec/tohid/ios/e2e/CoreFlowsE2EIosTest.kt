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
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
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
 *    ۰۱ بالا آمدن تا صفحهٔ ورود (انتخابِ «دکان دارم») ۰۲ ورود با ایمیل و رمز ۰۳ صندوق: کالای همگام‌شده ⇐ سبد ⇐ پرداختِ
 *    نقد ⇐ «ثبت فروش» (`CheckoutRoute` ⇐ `TillSale.record`) ⇐ فروش در دفتر و روی سرور ۰۴ «کالای تازه» ⇐ در دفتر و
 *    روی سرور ۰۵ «تازه‌ها» ۰۶ زبانِ دری (زبانه‌ها و فهرستِ «بیشتر» با واژهٔ دری).
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

    runSkikoComposeUiTest(size = Size(WIDTH * SCALE, HEIGHT * SCALE), density = Density(SCALE)) {
      val e = E2eDriver(this, env.shots)
      e.note("سرور: ${env.api} — نگاه: ${env.probe} — عکس‌ها: ${env.shots}")
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
        val home = Find.tab(NavCatalog.HOME)
        e.until("خانهٔ دکان پس از ورود", 60_000) {
          if (e.shown(Find.button(StrAuth.notNow))) {
            e.shot("02b-lock-offer")
            e.tap(Find.button(StrAuth.notNow), StrAuth.notNow)
          }
          e.shown(home)
        }
        assertTrue(!SyncStore(IosContext).accessToken.isNullOrBlank(), "پس از ورود نشستی نیست")
        e.shot("02c-home")
      }

      e.step("03-till", "صندوق: فروشِ نقدِ کالای همگام‌شده (TillSale.record)", needs = listOf("02-login")) {
        //  کالای «دستگاهِ دیگرِ» دکان (backend.mjs) با همگام‌سازیِ پس از ورود به آیفون می‌رسد
        e.until("کالای «${env.product}» از سرور به دفترِ آیفون", 90_000) {
          store.data.value.products.any { it.name == env.product }
        }
        e.toTab(NavCatalog.SALE)
        e.waitFor(Find.tile(env.product, StrSale.addOne), env.product, 30_000)
        e.shot("03a-till")
        val before = store.data.value.sales.map { it.id }.toSet()
        e.tap(Find.tile(env.product, StrSale.addOne), env.product)
        e.tap(Find.clickLabel(StrSale.openCart), StrSale.openCart)
        e.waitFor(Find.button(StrSale.pay), StrSale.pay)
        e.shot("03b-cart")
        e.tap(Find.button(StrSale.pay), StrSale.pay)
        e.waitFor(Find.button(StrSale.recordSale) and isEnabled(), StrSale.recordSale)
        e.shot("03c-checkout")
        e.tap(Find.button(StrSale.recordSale) and isEnabled(), StrSale.recordSale)
        e.waitFor(Find.button(StrSale.newSale), StrSale.newSale, 30_000)
        e.shot("03d-receipt")
        val sale = store.data.value.sales.singleOrNull { it.id !in before } ?: throw AssertionError("فروشِ تازه در دفتر نیست")
        assertEquals("cash", sale.paymentMethod, "روشِ پرداخت")
        assertEquals(env.price, sale.finalTotal, "مبلغِ نهایی")
        assertTrue(store.data.value.saleItems.any { it.saleId == sale.id }, "قلمِ فروش در دفتر نیست")
        //  همگام‌سازیِ آیفون فروش را به سرور می‌برد
        e.until("فروشِ ${env.price} روی سرور (sync_rows)", 90_000) {
          env.serverRows("sales").any { it["finalTotal"]?.jsonPrimitive?.doubleOrNull == env.price }
        }
        e.tap(Find.button(StrSale.newSale), StrSale.newSale)
        e.shot("03e-till-after")
      }

      val newProduct = "صابونِ آیفون"
      e.step("04-product", "افزودنِ کالای تازه", needs = listOf("02-login")) {
        e.toTab(NavCatalog.PRODUCTS)
        e.waitFor(Find.button(StrProducts.newProduct), StrProducts.newProduct)
        e.shot("04a-products")
        e.tap(Find.button(StrProducts.newProduct), StrProducts.newProduct)
        e.type(StrProducts.name, newProduct)
        //  دستهٔ تازه از برگهٔ دسته‌ها («دستهٔ تازه» ⇐ نام ⇐ «افزودن» ⇐ همان دسته برای همین ردیف)
        e.tap(Find.button(StrProducts.newCategory), StrProducts.newCategory)
        e.type(StrProducts.newCategory, "شوینده")
        e.tap(Find.button(StrKit.add) and isEnabled(), StrKit.add)
        e.waitFor(Find.button("شوینده"), "شوینده")
        e.tap(Find.button("عدد"), "عدد")
        e.type(StrProducts.purchasePrice, "60")
        e.type(StrProducts.salePrice, "85")
        e.shot("04b-new-product")
        e.tap(Find.button(StrProducts.saveAll) and isEnabled(), StrProducts.saveAll)
        e.until("کالای «$newProduct» در دفتر", 20_000) { store.data.value.products.any { it.name == newProduct } }
        val p = store.data.value.products.first { it.name == newProduct }
        assertEquals(85.0, p.salePrice, "قیمتِ فروش")
        assertEquals("شوینده", p.category, "دسته")
        e.shot("04c-saved")
        e.until("کالای «$newProduct» روی سرور (sync_rows)", 90_000) {
          env.serverRows("products").any { it["name"]?.jsonPrimitive?.content == newProduct }
        }
      }

      e.step("05-whats-new", "«تازه‌ها»", needs = listOf("02-login")) {
        val sections = WhatsNew.bundled.sections
        assertTrue(sections.isNotEmpty(), "«تازه‌ها» ِ همراهِ برنامه خالی است")
        e.toTab(NavCatalog.MORE)
        e.waitFor(Find.button(StrSettings.whatsNew), StrSettings.whatsNew)
        e.shot("05a-more")
        e.tap(Find.button(StrSettings.whatsNew), StrSettings.whatsNew)
        val first = sections.first().titleIn(I18n.source().code)
        e.waitFor(Find.text(first, substring = true), first)
        e.shot("05b-whats-new")
      }

      e.step("06-dari", "زبانِ دری", needs = listOf("02-login")) {
        val faWarehouse = Str.warehouse
        e.toTab(NavCatalog.MORE)
        e.tap(Find.button(StrSettingsHome.look), StrSettingsHome.look)
        e.tap(Find.button(StrSettings.language), StrSettings.language)
        e.waitFor(Find.button(Lang.PRS.native), Lang.PRS.native)
        e.shot("06a-language")
        e.tap(Find.button(Lang.PRS.native), Lang.PRS.native)
        e.until("زبانِ برنامه دری شد", 10_000) { LocaleChoice.lang == Lang.PRS }
        val dariWarehouse = Str.warehouse
        assertNotEquals(faWarehouse, dariWarehouse, "«انبار» در دری همان ماند")
        e.shot("06b-dari-selected")
        //  فهرستِ «بیشتر» (و همهٔ صفحه‌ها) همان لحظه دری است: «${faWarehouse}» ⇐ «${dariWarehouse}»
        e.toTab(NavCatalog.MORE)
        e.waitFor(Find.button(dariWarehouse), dariWarehouse)
        assertTrue(!e.shown(Find.text(faWarehouse)), "«$faWarehouse» ِ فارسی هنوز روی صفحه است")
        e.shot("06c-dari-more")
      }

      e.finish()
    }
  }

  private companion object {
    /** آیفونِ ۶٫۱ اینچی (۱۵/۱۶): ۳۹۰×۸۴۴ نقطه، ۳x */
    const val WIDTH = 390f
    const val HEIGHT = 844f
    const val SCALE = 3f
  }
}
