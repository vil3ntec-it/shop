package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchaseWorkflow
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.desktop.shots.StockDemo
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.ui.theme.PhoneV3Light
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.money.PURCHASE_FILTER_ROW
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueActions
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  ۶۸c — «ردیفِ فهرستِ داده» روی گره‌های واقعی، گوشیِ ۳۶۰dp (صاحب‌کار: «این شلوغی‌هات رو … درست کن دقیق و منظم»):
 *   • هر ردیفِ خریدها، مصارف، رسیدها، تاریخچهٔ فروش، کالاها، انبار و صفِ سفارش‌ها دست‌بالا
 *     **یک** قرصِ کار دارد؛ عنوانش یک خطِ ۱۶ و خطِ دومش یک خطِ ۱۳ است؛
 *   • خطِ مبلغ یک خط است و جز رنگِ نوشته و کم‌رنگ دست‌بالا یک رنگ (خطر) دارد — هیچ زرد/کهربایی؛
 *   • ردیفِ پالایهٔ خریدها در ۳۶۰dp (فارسی و انگلیسی) هیچ کادرِ بریده‌ای ندارد و «فیلتر» برگهٔ همان سه کادر را باز می‌کند.
 *  نگهبانِ متنی: `V3DataRowGuardTest`.
 */
@OptIn(ExperimentalTestApi::class)
class DataRowsE68cUiTest {

  @get:Rule val rule = createComposeRule()
  private lateinit var h: RouteUiHarness

  @Before fun up() {
    I18n.source = { Lang.FA }
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    h = RouteUiHarness(rule)
  }

  @After fun down() {
    h.close()
    I18n.source = { Lang.FA }
  }

  private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }

  private fun SemanticsNode.tag(): String? = config.getOrNull(SemanticsProperties.TestTag)

  private fun SemanticsNode.layout(): TextLayoutResult? {
    val action = config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
    val list = ArrayList<TextLayoutResult>()
    action.invoke(list)
    return list.firstOrNull()
  }

  /** همان مسیرِ برنامه در پهنای واقعیِ گوشی (۳۶۰dp، نه پنجرهٔ پهنِ آزمون؛ بلندیِ ۲۰۰۰ تا ردیف‌های زیرِ خلاصهٔ باز هم کشیده شوند) — سرور همان `TestBackend` ِ [h] */
  private fun phone(path: String, data: ShopData) {
    val store = RouteUiHarness.loadedStore(data)
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides if (I18n.lang == Lang.EN) LayoutDirection.Ltr else LayoutDirection.Rtl) {
            Box(Modifier.requiredSize(360.dp, 2000.dp)) { V3RoutePreview(path, store, dark = false) }
          }
        }
      }
    }
  }

  private fun rows(): List<SemanticsNode> = rule.onAllNodesWithTag(V3DataRowSpec.ROW_TAG, useUnmergedTree = true).fetchSemanticsNodes()

  /** کالبدِ هر ردیفِ دیدنی: یک قرص، عنوانِ یک‌خطیِ ۱۶، خطِ دومِ یک‌خطیِ ۱۳، خطِ مبلغِ یک‌خطی با دست‌بالا یک رنگ و بی زرد */
  private fun assertAnatomy(where: String, minRows: Int) {
    rule.waitForIdle()
    val all = rows()
    assertTrue("$where: دست‌کم $minRows ردیف (${all.size})", all.size >= minRows)
    val c = PhoneV3Light
    val warning = PhoneV3Roles.status(c, V3Status.WARNING).fg
    val bad = mutableListOf<String>()
    for (row in all) {
      val inside = row.descendants()
      val actions = inside.count { it.tag() == V3DataRowSpec.ACTION_TAG }
      if (actions > 1) bad += "$where: $actions قرص در یک ردیف"
      val metaNodes = inside.filter { it.tag() == V3DataRowSpec.META_TAG }.flatMap { listOf(it) + it.descendants() }.toSet()
      val layouts = inside.filter { it !in metaNodes }.mapNotNull { it.layout() }
      //  عنوان: نوشتهٔ ۱۶ — یکی و یک خط
      val titles = layouts.filter { it.layoutInput.style.fontSize.value == V3DataRowSpec.TITLE_SP.toFloat() }
      if (titles.isEmpty()) bad += "$where: ردیف بی عنوانِ ۱۶"
      titles.filter { it.lineCount != 1 }.forEach { bad += "$where: عنوانِ «${it.layoutInput.text}» ${it.lineCount} خط" }
      //  خطِ دوم: نوشته‌های ۱۳ — هر کدام یک خط
      layouts.filter { it.layoutInput.style.fontSize.value == V3DataRowSpec.SECONDARY_SP.toFloat() && it.lineCount != 1 }
        .forEach { bad += "$where: خطِ دومِ «${it.layoutInput.text}» ${it.lineCount} خط" }
      //  خطِ مبلغ: هر نوشته یک خط؛ در کلِ ردیف جز نوشته و کم‌رنگ دست‌بالا یک رنگ و هیچ زرد
      val loud = mutableSetOf<Color>()
      for (n in metaNodes) {
        val t = n.layout() ?: continue
        if (t.lineCount != 1) bad += "$where: خطِ مبلغِ «${t.layoutInput.text}» ${t.lineCount} خط"
        val spans = t.layoutInput.text.spanStyles.map { it.item.color }.filter { it != Color.Unspecified }
        val colors = spans.ifEmpty { listOf(t.layoutInput.style.color) }
        if (warning in colors) bad += "$where: زرد در «${t.layoutInput.text}»"
        loud += colors.filter { it != c.ink && it != c.inkMuted && it != Color.Unspecified }
      }
      if (loud.size > 1) bad += "$where: ${loud.size} رنگ در یک ردیف"
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }

  @Test fun purchasesRowsHaveOneActionOneLineMetaAndNoYellow() {
    phone(MoneyPaths.PURCHASING, PurchaseDemo.full())
    h.await(StrPurchases.tabPurchases)
    assertAnatomy("خریدها", 4)
  }

  @Test fun supplierListUsesSeparateAccountTilesWithLongPressActions() {
    phone(MoneyPaths.purchasing(tab = "suppliers"), PurchaseDemo.full())
    h.await("شرکت پخش البرز")
    for (name in listOf("شرکت پخش البرز", "عمده‌فروشی پامیر")) {
      val tile = hasText(name) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
      assertEquals(name, 1, rule.onAllNodes(tile).fetchSemanticsNodes().size)
    }
  }

  @Test fun supplierLedgerHasTappablePurchasesAndPayments() {
    phone(MoneyPaths.supplier(PurchaseDemo.ALBORZ), PurchaseDemo.full())
    h.await(StrPurchaseWorkflow.statement)
    for (kind in listOf(StrPurchases.purchase, StrMoney.payment)) {
      assertTrue(kind, rule.onAllNodes(hasContentDescription(kind) and hasClickAction()).fetchSemanticsNodes().isNotEmpty())
    }
  }

  @Test fun expensesFollowTheAnatomy() = route("expenses", MoneyDemo.full(), "مصارف", 2)

  @Test fun receiptsFollowTheAnatomy() = route(MoneyPaths.RECEIPTS, MoneyDemo.full(), "رسیدها", 1)

  @Test fun salesHistoryFollowsTheAnatomy() = route("sales", ir.vil3ntec.tohid.desktop.shots.HistoryDemo.busy(), "تاریخچه", 3)

  @Test fun productsFollowTheAnatomy() = route("products", StockDemo.full(), "کالاها", 3)

  @Test fun stockFollowsTheAnatomy() = route("stock", StockDemo.full(), "انبار", 3)

  private fun route(path: String, data: ShopData, where: String, minRows: Int) {
    phone(path, data)
    rule.waitUntil(15_000) { rows().size >= minRows }
    assertAnatomy(where, minRows)
  }

  @Test fun theOrderQueueIsCalmRowsWithOneNeutralStepEach() {
    val now = 1_763_200_000_000L
    val orders = (1..3).map { i ->
      OrderDto(
        id = "q$i", orderNo = 1040L + i, status = "new", tab = "new", next = listOf("accepted", "rejected"), source = "storefront",
        fulfillment = "delivery", paymentMethod = "cod", paymentStatus = "unpaid", customer = OrderCustomerDto("مشتری $i", "0799•••41$i"),
        itemsCount = i + 1, totalMinor = 145_000L * i, createdAt = now - i * 6 * 60_000, isTest = i == 2,
      )
    }
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.requiredSize(360.dp, 760.dp)) { OrdersScreen(
              state = QueueState(items = orders, loading = false, perms = OrderPermsDto(manage = true), now = now),
              actions = QueueActions(onTab = {}, onOpen = {}, onStep = { _, _ -> }, onNew = {}, onRetry = {}, onMore = {}),
              onBack = {},
            ) }
          }
        }
      }
    }
    assertAnatomy("صفِ سفارش‌ها", 3)
  }

  /** ردیفِ پالایه: هیچ فرزندی بیرون از ردیف و بیرون از ۳۶۰dp نیست — در فارسی و انگلیسی */
  private fun assertFilterRowFits() {
    rule.waitUntil(15_000) { rule.onAllNodesWithTag(PURCHASE_FILTER_ROW, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    val row = rule.onAllNodesWithTag(PURCHASE_FILTER_ROW, useUnmergedTree = true).fetchSemanticsNodes().single()
    val screen = 360f * rule.density.density
    val box: Rect = row.boundsInRoot
    assertTrue("ردیف در ۳۶۰dp: $box", box.left >= -0.5f && box.right <= screen + 0.5f)
    val bad = row.descendants().filter { n ->
      val b = n.boundsInRoot
      b.width > 0f && (b.left < box.left - 0.5f || b.right > box.right + 0.5f)
    }.map { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: it.boundsInRoot.toString() }
    assertTrue("بریده: $bad", bad.isEmpty())
    //  نوشتهٔ هر کادر کامل یا با «…» در جا — هیچ نوشته‌ای پهنای صفر ندارد
    row.descendants().mapNotNull { it.layout() }.forEach { t -> assertTrue("${t.layoutInput.text}", t.size.width > 0) }
  }

  @Test fun thePurchaseFilterRowIsNeverCutAt360AndFilterOpensTheSheet() {
    phone(MoneyPaths.PURCHASING, PurchaseDemo.full())
    h.await(StrHistory.filter)
    assertFilterRowFits()
    h.click(StrHistory.filter)
    h.await(StrPurchases.payLabel)
    assertTrue(h.has(StrPurchases.receiptLabel))
    assertTrue(h.has(StrPurchases.allSuppliers))
  }

  @Test fun thePurchaseFilterRowFitsInEnglishToo() {
    I18n.source = { Lang.EN }
    phone(MoneyPaths.PURCHASING, PurchaseDemo.full())
    rule.waitForIdle()
    assertFilterRowFits()
    assertEquals(1, rule.onAllNodesWithTag(PURCHASE_FILTER_ROW, useUnmergedTree = true).fetchSemanticsNodes().size)
  }
}
