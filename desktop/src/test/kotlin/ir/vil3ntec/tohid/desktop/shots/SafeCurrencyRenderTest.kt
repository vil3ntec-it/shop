package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.safe.SafeCurrency
import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.SafeBookPref
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.data.SafeCurrencyPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.screens.SafePanel
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.shell.HeroMoney
import ir.vil3ntec.tohid.ui.v3.shell.HeroMoneySheet
import ir.vil3ntec.tohid.ui.v3.shell.HomeHero
import ir.vil3ntec.tohid.ui.v3.shell.HomeScreen
import ir.vil3ntec.tohid.ui.v3.shell.HomeShot
import ir.vil3ntec.tohid.ui.v3.shell.SafeCurrencyAddForm
import ir.vil3ntec.tohid.ui.v3.shell.SafeCurrencyChoice
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume
import org.junit.Test

/**
 *  عکسِ گاوصندوقِ چندارزی (فقط گزارش، هرگز سد؛ [ScreenRender]): کادرِ بالای خانه روی «گاوصندوق» با تراشهٔ ارز (افغانی و
 *  دالر)، برگهٔ «+» با کادرِ «ارز» روی دالر، فرمِ «+ ارزِ دیگر» و کارتِ «گاوصندوق» ِ تاریخچهٔ فروش با ماندهٔ هر ارز —
 *  فارسی، روشن و تیره، گوشیِ ۳۶۰dp. `TOHID_RENDER_OUT=<پوشه>` ⇒ PNG ها؛ زمانِ نخستین قاب هر حالت چاپ می‌شود.
 */
class SafeCurrencyRenderTest {

  private fun ledger(): ShopData {
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    var n = 0
    val ids = { "01SHOTSCUR${(++n).toString().padStart(16, '0')}" }
    var d = ShopData()
    fun step(r: SafeBook.Result) { (r as? SafeBook.Result.Ok)?.let { d = it.data } }
    step(SafeBook.count(d, 6_000_000, null, "صاحب", now - 6 * day, ids))
    step(SafeBook.deposit(d, 1_500_000, "deposit", null, "صاحب", now - 5 * day, ids))
    step(SafeBook.withdraw(d, 800_000, "owner_withdrawal", null, "صاحب", now - 3 * day, ids))
    step(SafeBook.deposit(d, 90_000, "USD", "deposit", null, "صاحب", now - 4 * day, ids))
    step(SafeBook.deposit(d, 45_050, "USD", "deposit", null, "صاحب", now - 2 * day, ids))
    step(SafeBook.withdraw(d, 20_000, "USD", "owner_withdrawal", null, "صاحب", now - day, ids))
    step(SafeBook.deposit(d, 12_500, "USD", "deposit", null, "صاحب", now - 2 * 60 * 60 * 1000, ids))
    step(SafeBook.deposit(d, 30_000, "EUR", "deposit", null, "صاحب", now - 2 * day, ids))
    step(SafeBook.deposit(d, 900_000, "deposit", null, "صاحب", now - 2 * 60 * 60 * 1000, ids))
    return d
  }

  private fun storeOf(d: ShopData): ShopStore = ShopStore(DesktopContext).also { s ->
    @Suppress("UNCHECKED_CAST")
    (ShopStore::class.java.getDeclaredField("_data").apply { isAccessible = true }.get(s) as MutableStateFlow<ShopData>).value = d
  }

  private val usd = SafeCurrencyChoice("USD", SafeCurrencies.all(emptyList()), { SafeCurrencies.name(it) }, {}) { _, _ -> null }

  @Composable
  private fun Bg(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) { content() }

  private fun shot(name: String, dark: Boolean, before: () -> Unit = {}, content: @Composable () -> Unit) {
    before()
    //  یک بار گرم (بارگذاریِ کلاس‌ها)، بارِ دوم زمانِ نخستین قابِ واقعی
    ScreenRender.render("$name-warm", 360, 760, en = false, dark = dark, frames = 2, content = content)
    val r = ScreenRender.render(name, 360, 760, en = false, dark = dark, content = content)
    println("RENDER $name first-frame ${r.firstFrameMs}ms findings=${r.findings.filter { it.kind in RenderReport.DEFECTS }}")
    RenderReport.out?.let { java.io.File(it, "$name-warm.png").delete() }
  }

  @Test fun safeScreensFaLightAndDark() {
    RenderReport.skipReason()?.let { why -> println("RENDER کنار رفت: $why"); Assume.assumeTrue(why, false) }
    val d = ledger()
    val afnOnly = d.copy(safeEntries = d.safeEntries.filter { it.currency == null })
    try {
      SafeBookPref.set(DesktopContext, true)
      for (dark in listOf(false, true)) {
        val t = if (dark) "dark" else "light"
        //  پایه: دفترِ فقط‌افغانی ⇒ همان کادرِ امروز، بی تراشه (برای سنجیدنِ زمانِ نخستین قاب در کنارِ حالتِ چندارزی)
        shot("safe-home-afn-only-$t", dark) {
          Bg { HomeScreen(afnOnly, {}, PhoneNav.RESERVE, HomeShot(reveal = 1f, drawerPage = 1)) }
        }
        shot("safe-home-afn-$t", dark, { SafeCurrencyPref.setViewed(DesktopContext, SafeCurrency.AFN) }) {
          Bg { HomeScreen(d, {}, PhoneNav.RESERVE, HomeShot(reveal = 1f, drawerPage = 1)) }
        }
        shot("safe-home-usd-$t", dark, { SafeCurrencyPref.setViewed(DesktopContext, "USD") }) {
          Bg { HomeScreen(d, {}, PhoneNav.RESERVE, HomeShot(reveal = 1f, drawerPage = 1)) }
        }
        shot("safe-form-usd-$t", dark) {
          Bg {
            HeroMoneySheet(
              page = HomeHero.Page.SAFE, shiftClosed = false, busy = false, error = null, onSubmit = { _, _, _, _ -> },
              onOpenDrawer = {}, onClose = {}, start = HeroMoney.Dir.ADD, startAmount = "125.50", currency = usd,
            )
          }
        }
        shot("safe-form-add-$t", dark) {
          Bg { Column(Modifier.padding(16.dp)) { SafeCurrencyAddForm(usd) {} } }
        }
        val store = storeOf(d)
        shot("safe-panel-$t", dark) {
          Bg { Column(Modifier.padding(12.dp)) { SafePanel(d, store, "صاحب") {} } }
        }
      }
    } finally {
      SafeBookPref.set(DesktopContext, false)
      SafeCurrencyPref.setViewed(DesktopContext, SafeCurrency.AFN)
    }
  }
}
