package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrBranchTransfers
import ir.vil3ntec.tohid.core.stock.BranchTransferLedger
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.data.repo.BranchTransfersRepository
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.branches.BranchTransferText
import ir.vil3ntec.tohid.ui.v3.branches.BranchTransfersState
import ir.vil3ntec.tohid.ui.v3.branches.DeskBranchTransfers
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  INV-11/S21 (فازِ ۴ ِ شعبه‌ها) — صفحه‌های «انتقال میانِ شعبه‌ها» با `ShellContext` ِ واقعی ([RouteUiHarness]) و سرورِ ساختگیِ
 *  با حالت ([FakeBranchTransfers]); این دستگاه شعبهٔ اصلی است:
 *   • فهرست: «به این شعبه» با خلاصه و ردیفِ «از الف»، «انتقالِ تازه» شناور؛ پالایهٔ پیش‌نویس فقط پیش‌نویس؛
 *   • دریافت: «دریافت» ⇒ «همه رسید» ⇒ «بستنِ دریافت» یک درخواست با یک کلید؛ دفترِ همین دستگاه +۳ با نشانِ انتقال (نه دو بار)؛
 *   • فرستنده: ارسالِ پیش‌نویس یک بار و دفتر −۲؛ لغوِ فرستاده بی دلیل نمی‌رود؛
 *   • فرم: مقصد، کالا از فهرست و «ارسال» ⇒ یک POST با همان کالا؛
 *   • کامپیوتر (`ui/desk`): جدول، پنلِ جزئیات با «دریافت».
 */
@OptIn(ExperimentalTestApi::class)
class BranchTransfersRoutesUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var h: RouteUiHarness
  private lateinit var fake: FakeBranchTransfers
  private var role: String = ""

  private val data = ShopData(
    products = listOf(Product("tea", "چای سبز", "خوراکی", "دانه"), Product("rice", "برنج", "خوراکی", "کیلو")),
    warehouseEntries = listOf(WarehouseEntry(id = "w-tea", productId = "tea", units = 20.0), WarehouseEntry(id = "w-rice", productId = "rice", units = 9.0)),
  )

  @Before fun up() {
    I18n.source = { Lang.FA }
    role = ShopRole.current(DesktopContext)
    ShopRole.remember(DesktopContext, ShopRole.MANAGER)
    h = RouteUiHarness(rule)
    fake = FakeBranchTransfers(h.server)
    fake.seed(
      FakeBranchTransfers.T(FakeBranchTransfers.id(1), out = false, status = "in_transit", lines = listOf(Triple("tea", 3000L, null))),
      FakeBranchTransfers.T(FakeBranchTransfers.id(2), out = true, status = "draft", lines = listOf(Triple("rice", 2000L, null)), sentAt = null),
      FakeBranchTransfers.T(FakeBranchTransfers.id(3), out = true, status = "in_transit", lines = listOf(Triple("rice", 1000L, null))),
    )
  }

  @After fun down() {
    h.close()
    ShopRole.remember(DesktopContext, role.ifBlank { ShopRole.OWNER })
  }

  /** ردیف‌های آینهٔ همین انتقال (پوشهٔ دادهٔ آزمون میانِ آزمون‌ها مشترک است) */
  private fun ledgerRows(id: Int) = h.store!!.data.value.warehouseEntries.filter { it.adjKind == BranchTransferLedger.ADJ_KIND && it.refId == FakeBranchTransfers.id(id) }

  /** دفترِ واقعیِ پوشهٔ دادهٔ آزمون — آینهٔ انتقال با `ShopStore.update` می‌نویسد (همان راهِ `CatalogPhoneP2UiTest`) */
  private fun disk(): ShopStore = ShopStore(DesktopContext).also { s -> runBlocking { s.load(); assertTrue(s.save(data, replace = true)) } }

  @Test fun listShowsIncomingWithSummaryAndDraftFilter() {
    h.show("branches/transfers", data)
    h.await("از الف")
    assertTrue(h.has(StrBranchTransfers.incomingLabel))
    assertTrue(h.has(StrBranchTransfers.newTransfer))
    val stray = rule.onAllNodesWithText("به ب").fetchSemanticsNodes().map { it.config.toString() }
    assertTrue("پیش‌نویس در «به این شعبه» نیست: $stray", stray.isEmpty())
    h.click(StrBranchTransfers.drafts, substring = true)
    h.await("به ب")
    //  ردیفِ پیش‌نویس وضعیتش را کنارِ مبلغ دارد («۲ • پیش‌نویس»)
    val draftRow = hasText("به ب") and hasText(BranchTransferText.status(ir.vil3ntec.tohid.core.stock.BranchTransfer.Status.DRAFT), substring = true)
    assertTrue(rule.onAllNodes(draftRow).fetchSemanticsNodes().isNotEmpty())
    assertFalse(h.has("از الف"))
    assertTrue("فهرست هیچ نمی‌نویسد", fake.writes().isEmpty())
  }

  @Test fun receivingEverythingGoesOnceAndTheLedgerGetsTheItemsOnce() {
    h.show("branches/transfers/${FakeBranchTransfers.id(1)}", disk())
    h.await(StrBranchTransfers.receive)
    //  باز کردنِ صفحه پیش از دریافت هیچ ردیفی نمی‌نویسد (گیرنده تا دریافت هیچ ندارد)
    assertTrue(ledgerRows(1).isEmpty())
    h.click(StrBranchTransfers.receive)
    h.await(StrBranchTransfers.allArrived)
    h.click(StrBranchTransfers.allArrived)
    val fin = { rule.onAllNodes(hasText(StrBranchTransfers.receiveFinal) and hasClickAction())[0] }
    fin().performSemanticsAction(SemanticsActions.OnClick)
    runCatching { fin().performSemanticsAction(SemanticsActions.OnClick) }
    h.awaitThat { fake.get(FakeBranchTransfers.id(1))!!.status == "received" && ledgerRows(1).singleOrNull()?.units == 3.0 }
    val w = fake.writes("/receive")
    assertTrue("یک گام", w.map { it.key }.toSet().size == 1)
    assertEquals(true, w.first().body["final"]!!.jsonPrimitive.content.toBoolean())
    assertEquals("3000", w.first().body["lines"]!!.jsonArray.single().jsonObject["receivedMilli"]!!.jsonPrimitive.content)
    val row = ledgerRows(1).single()
    assertEquals(3.0, row.units, 0.0)
    assertEquals(FakeBranchTransfers.id(1), row.refId)
    assertEquals("tea", row.productId)
    h.await(BranchTransferText.status(ir.vil3ntec.tohid.core.stock.BranchTransfer.Status.RECEIVED))
  }

  @Test fun senderSendsTheDraftOnceAndCannotCancelASentTransferWithoutAReason() {
    h.show("branches/transfers/${FakeBranchTransfers.id(2)}", disk())
    h.await(StrBranchTransfers.send)
    h.click(StrBranchTransfers.send)
    h.awaitThat { fake.get(FakeBranchTransfers.id(2))!!.status == "in_transit" && ledgerRows(2).singleOrNull()?.units == -2.0 }
    assertEquals(1, fake.writes("/send").size)
    assertEquals(1, ledgerRows(2).size)
    //  لغوِ «در راه»: از «…»، بی دلیل دکمه بسته است و هیچ درخواستی نمی‌رود
    h.awaitThat { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(ir.vil3ntec.tohid.core.i18n.StrKit.more) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(ir.vil3ntec.tohid.core.i18n.StrKit.more) and hasClickAction())[0]
      .performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    h.click(StrBranchTransfers.cancelSent)
    h.await(StrBranchTransfers.cancelReason)
    assertTrue(fake.writes("/cancel").isEmpty())
    h.type(StrBranchTransfers.cancelReason, "اشتباه شد")
    rule.onAllNodes(hasText(StrBranchTransfers.cancelSent) and hasClickAction()).let { it[it.fetchSemanticsNodes().size - 1] }
      .performSemanticsAction(SemanticsActions.OnClick)
    h.awaitThat { fake.get(FakeBranchTransfers.id(2))!!.status == "cancelled" }
    assertEquals("اشتباه شد", fake.writes("/cancel").single().body["reason"]!!.jsonPrimitive.content)
    //  لغوِ فرستاده ⇒ همه برگشت ⇒ ردیفِ دفتر صفر (همان ردیف، نه ردیفِ دوم)
    h.awaitThat { ledgerRows(2).singleOrNull()?.units == 0.0 }
  }

  @Test fun theFormSendsOneTransferWithThePickedItem() {
    h.show("branches/transfers/new", data)
    h.await(StrBranchTransfers.destination)
    h.click("ب")
    h.click(StrBranchTransfers.addProduct)
    h.await("برنج")
    h.click("برنج")
    rule.waitForIdle()
    h.click(StrBranchTransfers.send)
    h.awaitThat { fake.writes().any { it.method == "POST" && it.path == FakeBranchTransfers.ROOT } }
    val w = fake.writes().single { it.path == FakeBranchTransfers.ROOT }
    assertEquals(FakeBranchTransfers.B, w.body["toBranch"]!!.jsonPrimitive.content)
    assertEquals("rice", w.body["lines"]!!.jsonArray.single().jsonObject["productId"]!!.jsonPrimitive.content)
    assertEquals(null, w.body["draft"])
    assertNotNull(w.key)
  }

  @Test fun desktopTableOpensThePaneWithReceive() {
    val repo = BranchTransfersRepository(h.backend.api)
    val state = BranchTransfersState(repo, "")
    runBlocking { state.load() }
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1366, 768),
        LocalDeskLayout provides true,
      ) {
        TohidTheme(ThemeChoice.LIGHT) { DeskBranchTransfers(state, repo, data, "", {}, move = true, onRefresh = {}) }
      }
    }
    h.await("از الف")
    assertTrue(h.has(StrBranchTransfers.newTransfer))
    h.click("از الف")
    h.await(StrBranchTransfers.receive)
    assertTrue(h.has(StrBranchTransfers.reject))
  }
}
