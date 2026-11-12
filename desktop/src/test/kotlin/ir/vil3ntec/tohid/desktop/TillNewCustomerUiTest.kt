package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrCustomerCard
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrTouch
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.ui.components.pos.PosTouchPrefs
import ir.vil3ntec.tohid.ui.screens.DebtorPicker
import ir.vil3ntec.tohid.ui.screens.tillNewCustomer
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  S18 گامِ ۴ (§۷.۱) «یک مشتری تازه را با نام دری ثبت کن» — روی کامپیوترِ لمسی (POS-31، `PosTouchPrefs.extras()`) در پنجرهٔ
 *  Compose ِ همان گزینشِ مشتریِ صندوق (`DebtorPicker` با `onAdd` ِ `DeskSale`: `tillNewCustomer` و `ShopStore` ِ واقعی روی
 *  دیسکِ آزمون): زدنِ کادرِ نام صفحه‌کلیدِ حروف را باز می‌کند (§۵.۳)، نامِ دری با نیم‌فاصله فقط با کلیدهای روی‌صفحه، و
 *  دو زدنِ تندِ «مشتریِ تازه» فقط **یک** مشتری می‌سازد (یافتهٔ بازبین؛ پیش‌تر دو مشتریِ هم‌نام).
 */
@OptIn(ExperimentalTestApi::class)
class TillNewCustomerUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext

  private fun clean() {
    HeartbeatCache(ctx).forget()
    SoftLock.refresh(ctx)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
  }

  @Before fun before() {
    clean()
    I18n.source = { Lang.PRS }
    PosTouchPrefs.on.value = true
    PosTouchPrefs.active.value = true
  }

  @After fun after() {
    PosTouchPrefs.on.value = false
    PosTouchPrefs.active.value = true
    I18n.source = { Lang.FA }
    clean()
  }

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private fun type(word: String) = word.forEach { ch ->
    rule.onNodeWithContentDescription(if (ch.toString() == "‌") StrTouch.halfSpace else ch.toString()).performClick()
  }

  /** همان سیمِ `DeskSale`: دفترِ زندهٔ فروشگاه، `tillNewCustomer` روی همان دفتر، گزینِ شناسهٔ تازه */
  private class Wire { var calls = 0; var ends = 0; val picked = mutableListOf<String>() }

  private fun showTill(store: ShopStore, w: Wire) = rule.setContent {
    CompositionLocalProvider(LocalContext provides DesktopContext) {
      TohidTheme(ThemeChoice.LIGHT) {
        val d by store.data.collectAsState()
        DebtorPicker(d = d, selected = null, onClose = {}, onPick = {}, touch = PosTouchPrefs.extras(), onAdd = { name ->
          w.calls++
          try { tillNewCustomer(d, name, store) { id -> w.picked += id } } finally { w.ends++ }
        })
      }
    }
  }

  @Test fun dariNameOnTheOnScreenKeysAndADoubleTapSaveOneCustomer() {
    val store = open()
    assertTrue(runBlocking { store.save(ShopData(debtors = listOf(Debtor(id = "a", name = "احمد")))) })
    val w = Wire()
    showTill(store, w)
    rule.waitForIdle()
    //  پیش از زدنِ کادر، صفحه‌کلید بسته است؛ زدنِ کادرِ نام آن را باز می‌کند (بی کلیدِ «حروف»)
    rule.onAllNodesWithContentDescription("ذ").assertCountEquals(0)
    rule.onAllNodes(hasSetTextAction())[0].performClick()
    rule.waitForIdle()
    val name = "ذبیح‌الله"
    type(name)
    rule.waitForIdle()
    val button = rule.onNode(hasText(StrSale.newCustomerNamed(name)))
    button.assertIsEnabled()
    //  دو زدن در همان نوبتِ رابط (پیش از هر بازسازی و پیش از پایانِ نوشتن) — دستِ تندِ صندوق‌دار
    val click = button.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
    rule.runOnUiThread { click(); click() }
    rule.waitUntil(15_000) { w.calls > 0 && w.ends == w.calls }
    rule.waitForIdle()
    assertEquals("زدنِ دوم هیچ", 1, w.calls)
    val made = store.data.value.debtors.filter { it.name == name }
    assertEquals(1, made.size)
    assertEquals(listOf(made.single().id), w.picked)
    //  روی دیسک هم همان یک مشتری (و «احمد» ِ پیشین سرِ جایش)
    assertEquals(listOf("احمد", name), open().data.value.debtors.map { it.name })
  }

  @Test fun theGuardHoldsWhileSavingAndAFailureFreesIt() {
    val gate = CompletableDeferred<String?>()
    var calls = 0
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          DebtorPicker(d = ShopData(), selected = null, onClose = {}, onPick = {}, touch = true, onAdd = { calls++; gate.await() })
        }
      }
    }
    rule.waitForIdle()
    rule.onAllNodes(hasSetTextAction())[0].performClick()
    rule.waitForIdle()
    type("نجیب")
    val button = rule.onNode(hasText(StrSale.newCustomerNamed("نجیب")))
    button.performClick()
    rule.waitForIdle()
    //  در حالِ نوشتن: دکمه خاموش و زدنِ دوباره هیچ
    button.assertIsNotEnabled()
    button.performClick()
    rule.waitForIdle()
    assertEquals(1, calls)
    //  نوشته نشد ⇒ پیام زیرِ دکمه و دکمه دوباره روشن (`finally`)
    gate.complete(StrCustomerCard.notSaved)
    rule.waitUntil(5_000) { rule.onAllNodes(hasText(StrCustomerCard.notSaved)).fetchSemanticsNodes().isNotEmpty() }
    button.assertIsEnabled()
    button.performClick()
    rule.waitForIdle()
    assertEquals(2, calls)
  }

  @Test fun anExceptionAlsoFreesTheGuard() {
    var calls = 0
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          DebtorPicker(d = ShopData(), selected = null, onClose = {}, onPick = {}, touch = true, onAdd = {
            calls++
            if (calls == 1) throw kotlinx.coroutines.CancellationException("برگه بسته شد") else null
          })
        }
      }
    }
    rule.waitForIdle()
    rule.onAllNodes(hasSetTextAction())[0].performClick()
    rule.waitForIdle()
    type("گل")
    val button = rule.onNode(hasText(StrSale.newCustomerNamed("گل")))
    button.performClick()
    rule.waitForIdle()
    button.assertIsEnabled()
    button.performClick()
    rule.waitForIdle()
    assertEquals(2, calls)
    //  موفق ⇒ تا بسته شدنِ برگه بسته می‌ماند (همان نام دوباره ساخته نمی‌شود)
    button.assertIsNotEnabled()
  }

  @Test fun deskSaleWiresTheSuspendingHelperOnlyForTheTouchTill() {
    val src = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/DeskSale.kt").readText()
    val call = src.substringAfter("DebtorPicker(d = d, selected = cartDebtorId").substringBefore("if (lineSellerOpen")
    assertTrue(call.contains("onAdd = if (!touchExtras) null else { name -> tillNewCustomer(d, name, store) { id ->"))
    val picker = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt").readText()
      .substringAfter("fun DebtorPicker(").substringBefore("fun CartLineDialog(")
    assertTrue(picker.contains("Modifier.weight(1f).touchKeysOnFocus(keys)"))
  }
}
