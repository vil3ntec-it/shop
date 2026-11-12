package ir.vil3ntec.tohid.desktop

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.sync.v1.SoftLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  OPS-02 — «پاک‌سازیِ کاملِ اطلاعات» در صفحهٔ زندهٔ نسخهٔ ۳ (`settings/data/backup` ⇐ `BackupScreen`) با `ShellContext` ِ واقعی
 *  ([RouteUiHarness])؛ همان نگهبان‌های رابطِ پیشین (`ui/screens/Settings.kt` + `WipeAllDialog`):
 *   • ردیف را فقط **صاحبِ** دکان می‌بیند — مدیر (که تنظیمات را باز می‌کند، `settings.write`) نه؛
 *   • دو مرحله: هشدار و «ادامه»، سپس نوشتنِ دقیقِ نامِ دکان؛ «پاک کن» تا نامِ درست بسته است و زدنش هم پاک نمی‌کند؛
 *   • «پاک شد» فقط پس از نوشتنِ موفقِ `replaceAll`؛ ننوشت (قفلِ نرمِ پایانِ اشتراک) ⇒ «پاک نشد» و دفتر دست‌نخورده.
 */
@OptIn(ExperimentalTestApi::class)
class WipeAllUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val shop = "دکان احمد"
  private val prefs get() = ctx.getSharedPreferences("tohid", Context.MODE_PRIVATE)
  private var nameBefore: String? = null
  private lateinit var h: RouteUiHarness

  private fun clean() {
    HeartbeatCache(ctx).forget()
    SoftLock.refresh(ctx)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
  }

  @Before fun up() {
    clean()
    nameBefore = prefs.getString("store_name", null)
    prefs.edit().putString("store_name", shop).commit()
    ShopRole.remember(ctx, ShopRole.OWNER)
    h = RouteUiHarness(rule)
  }

  @After fun down() {
    h.close()
    clean()
    ShopRole.remember(ctx, ShopRole.OWNER)
    prefs.edit().apply { if (nameBefore == null) remove("store_name") else putString("store_name", nameBefore) }.commit()
  }

  private val store get() = h.store!!

  /** تا ردیفِ پاک‌سازی (آخرِ فهرستِ تنبل) پایین می‌رود؛ نبود ⇒ `AssertionError` */
  private fun scrollToWipe() {
    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(StrSettings.wipeAll))
    rule.waitForIdle()
  }

  private fun eraseButton() = rule.onAllNodes(hasText(StrSettings.wipe) and hasClickAction())[0]

  private fun openNameStep() {
    h.show("settings/data/backup", MoneyDemo.full())
    h.await(StrSettings.fullBackup)
    scrollToWipe()
    h.click(StrSettings.wipeAll)
    h.await(StrSettings.wipeQ)
    assertTrue("مرحلهٔ یکم هشدارِ «همهٔ دستگاه‌ها و سرور» را می‌گوید", h.has(StrSettings.wipeWarn))
    assertFalse("مرحلهٔ یکم دکمهٔ پاک کردن ندارد", h.has(StrSettings.wipe))
    h.click(StrSettings.wipeNext)
    h.await(StrSettings.wipeNameTitle)
  }

  @Test fun managerOpensSettingsButNeverSeesTheEraseRow() {
    ShopRole.remember(ctx, ShopRole.MANAGER)
    h.show("settings/data/backup", MoneyDemo.full())
    h.await(StrSettings.fullBackup)
    val reached = runCatching { scrollToWipe() }.isSuccess
    assertFalse("مدیر ردیفِ «پاک‌سازیِ کامل» را نمی‌بیند", reached || h.has(StrSettings.wipeAll))
  }

  @Test fun ownerErasesOnlyAfterTypingTheExactShopName() {
    openNameStep()
    assertTrue(h.has(StrSettings.wipeNameHint(shop)))
    eraseButton().assertIsNotEnabled()

    h.type(StrSettings.wipeNameLabel, "دکان محمود")
    eraseButton().assertIsNotEnabled()
    //  دکمهٔ بسته هم زده شود: هیچ چیزی پاک نمی‌شود
    eraseButton().performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    assertTrue("نامِ نادرست پاک نکرد", store.data.value.products.isNotEmpty())
    assertFalse(h.has(StrSettings.wiped))
  }

  @Test fun ownerTypesTheNameAndOnlyThenItIsErasedAndSaysSo() {
    openNameStep()
    //  همان نام با نیم‌فاصله و «ي» ِ عربی (`WipeAll.matches`)
    h.type(StrSettings.wipeNameLabel, "  دکان‌احمد ")
    eraseButton().assertIsEnabled()
    eraseButton().performSemanticsAction(SemanticsActions.OnClick)
    h.await(StrSettings.wiped)
    assertEquals(0, store.data.value.products.size)
    assertEquals(0, store.data.value.sales.size)
  }

  @Test fun aRefusedWriteSaysNotErasedAndKeepsTheLedger() {
    //  اشتراکِ تمام‌شده (قفلِ نرم): `replaceAll` فروش و قرض را نمی‌نویسد ⇒ `false`
    HeartbeatCache(ctx).write(buildJsonObject {
      put("subscription", buildJsonObject { put("active", JsonPrimitive(false)); put("status", JsonPrimitive("expired")) })
    })
    assertTrue(SoftLock.refresh(ctx))
    openNameStep()
    val before = store.data.value
    h.type(StrSettings.wipeNameLabel, shop)
    eraseButton().performSemanticsAction(SemanticsActions.OnClick)
    h.await(StrSettings.wipeFailed)
    assertFalse("«پاک شد» دروغ نگوید", h.has(StrSettings.wiped))
    assertEquals("دفتر دست‌نخورده", before, store.data.value)
  }
}
