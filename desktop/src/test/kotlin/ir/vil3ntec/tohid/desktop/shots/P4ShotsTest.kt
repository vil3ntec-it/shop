package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.orders.OrderFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3Notice
import ir.vil3ntec.tohid.ui.v3.online.orders.ManualStep
import ir.vil3ntec.tohid.ui.v3.online.orders.NewOrderModel
import ir.vil3ntec.tohid.ui.v3.online.orders.NewOrderScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderSetupModel
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderSetupSheet
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueActions
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueState
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «پس» ِ ۱۴۰۵/۰۸/۲۴ برای صاحب‌کار — صفحهٔ «سفارش‌ها» (خالی با تنها ردیفِ راه‌اندازی، با سفارش، برگهٔ فهرستِ کارها) و
 *  «سفارشِ دستی» (کاتالوگ، اقلامِ برگزیده، جزئیات)، روز و شب، گوشیِ ۳۶۰×۷۶۰dp فارسی. نوارِ «نسخهٔ پیش‌نمایش» ِ پوسته
 *  فراهم است تا دیده شود این صفحه آن را نمی‌کشد. فقط با `P4_SHOTS=<پوشه>`.
 */
@OptIn(ExperimentalTestApi::class)
class P4ShotsTest {

  private val out: File? = System.getenv("P4_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private val demoNow = 1_763_200_000_000L
  private val demoPerms = ir.vil3ntec.tohid.core.model.OrderPermsDto(manage = true, create = true, contact = true, refund = true)
  private val demoCounts = mapOf("new" to 3, "preparing" to 2, "ready" to 1, "on_the_way" to 1)
  private fun demoOrders() = listOf(
    ir.vil3ntec.tohid.core.model.OrderDto(
      id = "o1", orderNo = 1046, status = "new", tab = "new", next = listOf("accepted", "rejected"), source = "storefront",
      fulfillment = "delivery", paymentMethod = "cod", paymentStatus = "unpaid", customer = ir.vil3ntec.tohid.core.model.OrderCustomerDto("مریم احمدی", "0799•••412"),
      itemsCount = 3, itemsPreview = listOf("چای سبز", "شکر", "روغن"), totalMinor = 145_000, createdAt = demoNow - 6 * 60_000,
    ),
    ir.vil3ntec.tohid.core.model.OrderDto(
      id = "o2", orderNo = 1045, status = "new", tab = "new", next = listOf("accepted", "rejected"), source = "phone",
      fulfillment = "pickup", paymentMethod = "cash", paymentStatus = "paid", customer = ir.vil3ntec.tohid.core.model.OrderCustomerDto("احمد کریمی", "0700•••118"),
      itemsCount = 1, itemsPreview = listOf("برنج سیلا ۵ کیلو"), totalMinor = 832_000, createdAt = demoNow - 25 * 60_000,
    ),
    ir.vil3ntec.tohid.core.model.OrderDto(
      id = "o3", orderNo = 1044, status = "new", tab = "new", next = listOf("accepted", "rejected"), source = "whatsapp",
      fulfillment = "delivery", paymentMethod = "cod", paymentStatus = "unpaid", customer = ir.vil3ntec.tohid.core.model.OrderCustomerDto("فرید نوری", "0788•••905"),
      itemsCount = 5, itemsPreview = listOf("آرد", "صابون", "چای"), totalMinor = 410_500, createdAt = demoNow - 70 * 60_000,
    ),
  )

  private fun frame(file: String, dark: Boolean, heightDp: Int = 760, content: @Composable () -> Unit) =
    runDesktopComposeUiTest(720, heightDp * 2, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, heightDp),
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalV3Notice provides { PreviewStrip() }) { content() }
          }
        }
      }
      repeat(24) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(60)
      }
      ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, file))
    }

  @Composable
  private fun PreviewStrip() {
    Text(
      StrShell.previewBuild,
      modifier = Modifier.fillMaxWidth().background(Shop.colors.warningTint).padding(vertical = 2.dp),
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning, textAlign = TextAlign.Center,
    )
  }

  private val todo = OrderSetupModel.Status.Todo(listOf(OrderSetupModel.Task("storefront_missing", OrderFlow.Fix.STOREFRONT)))
  private val todo2 = OrderSetupModel.Status.Todo(
    listOf(OrderSetupModel.Task("storefront_hidden", OrderFlow.Fix.STOREFRONT), OrderSetupModel.Task("no_products", OrderFlow.Fix.PRODUCTS)),
  )

  @Composable
  private fun Queue(items: Boolean, setup: OrderSetupModel.Status) {
    OrdersScreen(
      state = QueueState(
        tab = "new", counts = if (items) demoCounts else emptyMap(),
        items = if (items) demoOrders() else emptyList(),
        loading = false, perms = demoPerms, now = demoNow,
      ),
      actions = QueueActions({}, {}, { _, _ -> }, {}, {}, {}),
      onBack = {},
      setup = setup,
    )
  }

  private val shop = StockDemo.full()
  private val cart = listOf(SalesEngine.CartLine("p4", 2.0), SalesEngine.CartLine("p2", 1.0), SalesEngine.CartLine("p14", 3.0))

  @Composable
  private fun Manual(step: ManualStep, form: OrderDraft.Form = OrderDraft.Form()) {
    NewOrderScreen(
      sources = NewOrderModel.DEFAULT_SOURCES, busy = false, failure = null, onSubmit = {}, onBack = {},
      d = shop, startCart = cart, startStep = step, start = form, onScan = {},
    )
  }

  @Test
  fun after() {
    Assume.assumeTrue("P4_SHOTS نیست", out != null)
    for (dark in listOf(false, true)) {
      val mode = if (dark) "dark" else "light"
      frame("after-orders-empty-$mode.png", dark) { Queue(items = false, setup = todo) }
      frame("after-orders-list-$mode.png", dark) { Queue(items = true, setup = OrderSetupModel.Status.Ready) }
      frame("after-checklist-$mode.png", dark) {
        Queue(items = false, setup = todo2)
        OrderSetupSheet(todo2.tasks, onFix = {}, onDismiss = {})
      }
      frame("after-manual-catalog-$mode.png", dark) { Manual(ManualStep.PICK) }
      frame("after-manual-items-$mode.png", dark) {
        Manual(ManualStep.DETAILS, OrderDraft.Form(lines = listOf(OrderDraft.LineInput("کیسهٔ اضافه", "1", "10"))))
      }
      frame("after-manual-details-$mode.png", dark, heightDp = 1500) {
        Manual(
          ManualStep.DETAILS,
          OrderDraft.Form(
            source = "whatsapp", fulfillment = "delivery", name = "مریم احمدی", phone = "0799123412", addressText = "کارتهٔ سه، کوچهٔ دوم",
            note = "پیش از آمدن زنگ بزنید", lines = listOf(OrderDraft.LineInput("کیسهٔ اضافه", "1", "10")),
          ),
        )
      }
    }
  }
}
