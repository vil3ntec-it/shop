package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrderFeedback
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.orders.OrderFlow
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.online.orders.MANUAL_FREE_LINE
import ir.vil3ntec.tohid.ui.v3.online.orders.ManualStep
import ir.vil3ntec.tohid.ui.v3.online.orders.NewOrderScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.ORDER_SETUP_ROW
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderSetupModel
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueActions
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 *  ۱۴۰۵/۰۸/۲۴ — صفحهٔ «سفارش‌ها» و «سفارشِ دستی» روی گره‌های واقعی، گوشیِ ۳۶۰dp (صاحب‌کار: «صد تا هشدار اومده… سفارشِ
 *  دستی چی کُفتی است؟»):
 *   • زیرِ برگه‌ها دست‌بالا **یک** ردیفِ وضعیت؛ پیش‌فرض‌های روشن ⇒ هیچ «از پشتیبانی بخواهید»، یادداشتِ «اعلانِ فوری» یا
 *     دکمهٔ «گزارش‌ها و نظرها» در بدنه؛
 *   • برگه‌های صف در ۳۶۰dp با فارسی، دری، پشتو و انگلیسی هیچ‌کدام بریده نیستند؛
 *   • «سفارشِ دستی» کالا را از فهرست با مقدار برمی‌دارد و سفارش با قلمِ کالادار ثبت می‌شود؛ قلمِ دستی هنوز هست؛ اعتبار.
 */
@OptIn(ExperimentalTestApi::class)
class OrdersP4UiTest {

  @get:Rule val rule = createComposeRule()

  @After fun lang() { I18n.source = { Lang.FA } }

  private val now = 1_763_200_000_000L
  private fun order(i: Int, tab: String) = OrderDto(
    id = "o$i", orderNo = 1040L + i, status = "new", tab = tab, next = listOf("accepted", "rejected"), source = "storefront",
    fulfillment = "pickup", paymentMethod = "cod", paymentStatus = "unpaid", customer = OrderCustomerDto("مشتری $i", "0799•••41$i"),
    itemsCount = 1, totalMinor = 145_000L, createdAt = now - i * 60_000,
  )
  private val noActions = QueueActions(onTab = {}, onOpen = {}, onStep = { _, _ -> }, onNew = {}, onRetry = {}, onMore = {})
  private val counts = mapOf("new" to 3, "preparing" to 2, "ready" to 1, "on_the_way" to 4, "done" to 12)

  @Composable
  private fun Phone(content: @Composable () -> Unit) {
    CompositionLocalProvider(
      LocalContext provides DesktopContext,
      LocalView provides remember { android.view.View(DesktopContext) },
      LocalConfiguration provides Configuration(360, 760),
    ) {
      TohidTheme(ThemeChoice.LIGHT) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Box(Modifier.requiredSize(360.dp, 760.dp)) { content() }
        }
      }
    }
  }

  private fun texts(): List<String> = rule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
    .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

  @Test
  fun `زیرِ برگه‌ها دست‌بالا یک ردیف و هیچ «از پشتیبانی بخواهید»`() {
    var setup by androidx.compose.runtime.mutableStateOf<OrderSetupModel.Status>(
      OrderSetupModel.Status.Todo(listOf(OrderSetupModel.Task("storefront_missing", OrderFlow.Fix.STOREFRONT))),
    )
    var opened = 0
    rule.setContent {
      Phone {
        OrdersScreen(
          state = QueueState(tab = "new", counts = counts, items = emptyList(), loading = false, perms = OrderPermsDto(manage = true, create = true), now = now),
          actions = noActions, onBack = {}, setup = setup, onSetup = { opened++ },
        )
      }
    }
    rule.waitForIdle()
    assertEquals(1, rule.onAllNodesWithTag(ORDER_SETUP_ROW, useUnmergedTree = true).fetchSemanticsNodes().size)
    val shown = texts()
    for (banned in listOf("از پشتیبانی بخواهید", StrOnline.liveNote, StrOrderFeedback.title, "همهٔ دستگاه‌های دکان را به‌روز کنید")) {
      assertFalse("در صف نباید باشد: $banned — $shown", shown.any { it.contains(banned) })
    }
    rule.onAllNodesWithTag(ORDER_SETUP_ROW, useUnmergedTree = true)[0].performClick()
    assertEquals(1, opened)

    //  پیش‌فرض‌های روشن و ویترینِ ساخته ⇒ هیچ ردیفی
    setup = OrderSetupModel.Status.Ready
    rule.waitForIdle()
    assertEquals(0, rule.onAllNodesWithTag(ORDER_SETUP_ROW, useUnmergedTree = true).fetchSemanticsNodes().size)
    //  خاموشِ صریحِ همین دکان ⇒ باز هم یک ردیف («خاموش است» با «پشتیبانی»)
    setup = OrderSetupModel.Status.Off("storefront_off")
    rule.waitForIdle()
    assertEquals(1, rule.onAllNodesWithTag(ORDER_SETUP_ROW, useUnmergedTree = true).fetchSemanticsNodes().size)
    assertFalse(texts().any { it.contains("از پشتیبانی بخواهید") })
  }

  @Test
  fun `برگه‌های صف در ۳۶۰dp در چهار زبان بریده نیستند`() {
    for (lang in listOf(Lang.FA, Lang.PRS, Lang.PS, Lang.EN)) runDesktopComposeUiTest(360, 760, testTimeout = 2.minutes) {
      I18n.source = { lang }
      setContent {
        Phone {
          OrdersScreen(
            state = QueueState(tab = "done", counts = counts, items = listOf(order(1, "done")), loading = false, perms = OrderPermsDto(manage = true), now = now),
            actions = noActions, onBack = {},
          )
        }
      }
      waitForIdle()
      val tabs = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
      assertEquals("$lang: پنج برگه", 5, tabs.size)
      val width = 360f * density.density
      for (t in tabs) {
        val b = t.boundsInRoot
        assertTrue("$lang: برگهٔ بریده $b (پهنا $width)", b.left >= -0.5f && b.right <= width + 0.5f && b.width > 0f)
      }
      I18n.source = { Lang.FA }
    }
  }

  private val d = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنجِ سیلا", unit = "بوجی", salePrice = 1450.0, category = "خواربار"),
      Product(id = "p2", name = "چای سبز", unit = "پاکت", salePrice = 125.0, category = "نوشیدنی"),
    ),
    productCategories = listOf("خواربار", "نوشیدنی"),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 20.0), WarehouseEntry(id = "w2", productId = "p2", units = 20.0)),
  )

  @Test
  fun `سفارشِ دستی - کالا از فهرست با مقدار، قلمِ دستی، اعتبار و ثبت با قلمِ کالادار`() {
    var sent: OrderDraft.Form? = null
    rule.setContent {
      Phone {
        NewOrderScreen(
          sources = listOf("phone", "manual"), busy = false, failure = null, onSubmit = { sent = it }, onBack = {}, d = d,
          start = OrderDraft.Form(lines = listOf(OrderDraft.LineInput("کیسه", "1", "10"))), startStep = ManualStep.PICK,
        )
      }
    }
    rule.waitForIdle()
    //  «قلمِ دستی (بیرون از فهرست)» در همان کاتالوگ
    assertEquals(1, rule.onAllNodesWithTag(MANUAL_FREE_LINE, useUnmergedTree = true).fetchSemanticsNodes().size)
    //  دو بار «برنج» و یک بار «چای» ⇒ سه قلمِ سبد (نوارِ پایین: «۳ قلم · …»)
    rule.onNodeWithText("برنجِ سیلا").performClick()
    rule.onNodeWithText("برنجِ سیلا").performClick()
    rule.onNodeWithText("چای سبز").performClick()
    rule.waitForIdle()
    assertTrue(texts().joinToString(), texts().any { it.startsWith(StrOnline.linesSummary(3, "").substringBefore("·").trim()) })
    rule.onNodeWithText(StrOrdersHub.pickNext).performClick()
    rule.waitForIdle()

    //  بی نام ⇒ ثبت نمی‌شود و خطای همان کادر
    rule.onNodeWithText(StrOnline.placeOrder).performClick()
    rule.waitForIdle()
    assertNull(sent)
    assertTrue(texts().any { it == StrOnline.errName })

    rule.onAllNodes(hasSetTextAction())[0].performTextInput("مریم احمدی")
    rule.onNodeWithText(StrOnline.placeOrder).performClick()
    rule.waitForIdle()
    val form = assertNotNullForm(sent)
    val body = (OrderDraft.build(form) as OrderDraft.Result.Ok).body
    assertEquals("مریم احمدی", body.customerName)
    assertEquals(listOf("p1", "p2", ""), body.lines.map { it.productId })
    assertEquals(listOf(2_000L, 1_000L, 1_000L), body.lines.map { it.qtyMilli })
    assertEquals(listOf(145_000L, 12_500L, 1_000L), body.lines.map { it.unitPriceMinor })
    assertEquals(listOf("بوجی", "پاکت", ""), body.lines.map { it.unit })
    assertEquals("کیسه", body.lines[2].name)
  }

  @Test
  fun `قلمِ دستی از کاتالوگ برگهٔ نام و مقدار و فی را باز می‌کند`() {
    rule.setContent {
      Phone { NewOrderScreen(sources = listOf("phone"), busy = false, failure = null, onSubmit = {}, onBack = {}, d = d) }
    }
    rule.waitForIdle()
    rule.onAllNodesWithTag(MANUAL_FREE_LINE, useUnmergedTree = true)[0].performClick()
    rule.waitForIdle()
    assertTrue(rule.onAllNodes(hasText(StrOnline.lineName, substring = true)).fetchSemanticsNodes().isNotEmpty())
  }

  private fun assertNotNullForm(f: OrderDraft.Form?): OrderDraft.Form { assertNotNull("ثبت نشد", f); return f!! }
}
