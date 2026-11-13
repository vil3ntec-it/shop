package ir.vil3ntec.tohid.core.keymap

import ir.vil3ntec.tohid.ui.AppLockKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  سناریوی S46 (§۷.۵) — تعویضِ کاربر روی صندوق (STF-04).
 *
 *  «Ctrl+L صندوق را قفل می‌کند. کاربرِ دوم با PIN وارد می‌شود. فاکتورِ نیمه‌کارهٔ کاربرِ اول نگه داشته می‌شود
 *  و سابقه‌ها به نامِ کاربرِ درست ثبت می‌شوند.»
 *
 *  سنجیده: سطرِ نخست — Ctrl+L در keymap.json کارِ سراسریِ «قفلِ صندوق» است، کلیدِ دیگری نیست، برنامهٔ کامپیوتر
 *  آن را به `AppNav.lock()` می‌دهد و پوستهٔ نسخهٔ ۳ (`AppRoot`) با تنظیمِ «تعویضِ کاربر روی صندوق»
 *  (`TillUser`، پیش‌فرض خاموش) صفحهٔ قفلِ PIN ِ شخصی (`TillLockScreen`) را می‌اندازد، وگرنه با رمزِ برنامه قفلِ
 *  برنامه (`AppLockScreen`) و بی آن فقط راهش (`AppLockKey`) — از متنِ منبع، چون AppNav وضعیتِ Compose دارد
 *  (همان روشِ DeskMenuTest). نقشِ برنامه از `TillUser.role` است
 *  (هرگز بالاتر از حسابِ دستگاه) و فروشنده و درخواست‌کنندهٔ تأیید از `TillUser.me`/`meId`.
 *  رفتار روی دیسکِ واقعی (PIN با `ApproverVault`، سبدِ جدای هر کاربر، سابقه به نامِ درست، نقش):
 *  `desktop/…/ScenarioS46DesktopTest`؛ قاعده‌ها: core ‏`TillSwitchTest`.
 *
 *  ساخته‌نشده (سند، نه assert):
 *   • سرور هنوز opها را به نامِ حسابِ دستگاه (توکن) می‌نویسد؛ «سابقه به نامِ درست» نامِ فروشنده (`Sale.seller`)
 *     و ردیف‌های سابقهٔ همین دستگاه است (`till_user` با شناسهٔ کاربر).
 *   • فاکتورهای نگه‌داشته (F6) هنوز میانِ کاربرانِ صندوق مشترک‌اند؛ فقط سبدِ باز جداست.
 */
class ScenarioS46Test {

  private fun source(vararg rel: String): File? = rel.flatMap { r ->
    listOf(r, "../$r", "../../$r")
  }.map { File(it) }.firstOrNull { it.isFile }

  @Test
  fun `S46 Ctrl+L کارِ سراسریِ قفلِ صندوق است و کلیدِ دیگری نیست`() {
    val lock = KeyMap.ALL.single { it.id == "global.lock" }
    assertEquals("global", lock.scope)
    assertEquals(listOf(Chord(KeyName.L, mod = true)), lock.keys)
    val others = KeyMap.ALL.filter { it.id != lock.id && Chord(KeyName.L, mod = true) in it.keys }
    assertEquals("Ctrl+L جای دیگری هم هست", emptyList<String>(), others.map { it.id })
  }

  @Test
  fun `S46 برنامهٔ کامپیوتر Ctrl+L را به قفلِ برنامه می‌رساند`() {
    val main = source("desktop/src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt", "src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt")
    assertNotNull("desktop/Main.kt پیدا نشد", main)
    assertTrue(Regex("""mod && e\.key == Key\.L -> \{[^}]*AppNav\.lock\(\)""").containsMatchIn(main!!.readText()))
    val nav = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/AppNav.kt", "src/main/java/ir/vil3ntec/tohid/ui/AppNav.kt")
    assertNotNull(nav)
    assertTrue(nav!!.readText().contains("fun lock() { lockNow.intValue++ }"))
    //  هر بالا رفتنِ lockNow (یک بار، نه با هر ساختِ دوباره) ⇒ نخست قفلِ PIN ِ شخصی (`TillUser`) و صفحهٔ قفلِ صندوق؛
    //  آن خاموش یا بی راهِ برگشت ⇒ قفلِ برنامه با رمزِ برنامه، وگرنه فقط راهش (`AppLockKey`، #851) — پوستهٔ نسخهٔ ۳
    //  (رابطِ قدیم ۱۴۰۵/۰۸/۱۱ پاک شد)
    val v3 = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/AppRoot.kt", "src/main/java/ir/vil3ntec/tohid/ui/AppRoot.kt")!!.readText()
    assertTrue("lockAsk از AppNav.lockNow نیست", v3.contains("val lockAsk by AppNav.lockNow"))
    assertTrue(v3.contains("var lockSeen by rememberSaveable { mutableStateOf(AppNav.lockNow.intValue) }"))
    assertTrue(
      "Ctrl+L در AppRoot به TillUser.lock و AppLockKey نمی‌رسد",
      Regex(
        """LaunchedEffect\(lockAsk\) \{\s*if \(lockAsk == lockSeen\) return@LaunchedEffect\s*lockSeen = lockAsk\s*""" +
          """when \(AppLockKey\.after\(ir\.vil3ntec\.tohid\.data\.TillUser\.lock\(context, lockStore\.enabled\), lockStore\.enabled\)\) \{\s*""" +
          //  STF-04 (`claude/stf-2`): قفلِ صندوق نوبتِ سرورِ همان دستگاه را هم می‌بندد (`TillUser.serverEnd`)
          """AppLockKey\.Then\.TILL -> ir\.vil3ntec\.tohid\.data\.TillUser\.serverEnd\(context\)[^\n]*\s*""" +
          """AppLockKey\.Then\.APP -> unlocked = false\s*""" +
          """AppLockKey\.Then\.HINT -> toasts\.show\(ir\.vil3ntec\.tohid\.core\.i18n\.StrTill\.lockNeedsPassword"""
      ).containsMatchIn(v3)
    )
    assertTrue(v3.contains("val locked = lockStore.enabled && !unlocked"))
    assertTrue(v3.contains("if (locked) { AppLockScreen { unlocked = true }; return }"))
    assertTrue(v3.contains("if (tillLocked) { ir.vil3ntec.tohid.ui.components.staff.TillLockScreen(cartStore, store, lockStore.enabled); return }"))
    //  قاعدهٔ همان `when`: قفلِ صندوق بر قفلِ برنامه پیش است و بی هیچ‌کدام قفلی وانمود نمی‌شود
    assertEquals(AppLockKey.Then.TILL, AppLockKey.after(tillLocked = true, appLock = true))
    assertEquals(AppLockKey.Then.TILL, AppLockKey.after(tillLocked = true, appLock = false))
    assertEquals(AppLockKey.Then.APP, AppLockKey.after(tillLocked = false, appLock = true))
    assertEquals(AppLockKey.Then.HINT, AppLockKey.after(tillLocked = false, appLock = false))
  }

  @Test
  fun `S46 نقش، فروشنده و درخواست‌کننده از کاربرِ صندوق`() {
    val role = source("android-native/app/src/main/java/ir/vil3ntec/tohid/data/ShopRole.kt", "src/main/java/ir/vil3ntec/tohid/data/ShopRole.kt")!!.readText()
    assertTrue(role.contains("TillUser.role(context, prefs(context).getString(KEY, \"\").orEmpty().ifBlank { OWNER })"))
    //  RET-05 (`claude/e-rows-37e`): هر دو برگهٔ برگشت فاکتورِ «تعویض» می‌سازند ⇒ فروشنده (و نویسندهٔ برگشت) همان کاربرِ صندوق
    for (rel in listOf(
      "ui/screens/DeskSale.kt", "ui/screens/SaleDialogs.kt", "ui/v3/sales/checkout/CheckoutRoute.kt",
      "ui/v3/sales/returns/ReturnFlowSheet.kt", "ui/screens/ReturnsV2.kt",
    )) {
      val f = source("android-native/app/src/main/java/ir/vil3ntec/tohid/$rel", "src/main/java/ir/vil3ntec/tohid/$rel")!!.readText()
      assertTrue("$rel فروشنده را از TillUser نمی‌گیرد", f.contains("TillUser.me(context)"))
      assertFalse("$rel هنوز نامِ حساب را مستقیم می‌گیرد", f.contains("SyncStore(context).accountName"))
    }
  }
}
