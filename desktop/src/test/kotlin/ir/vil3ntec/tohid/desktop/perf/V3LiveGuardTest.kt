package ir.vil3ntec.tohid.desktop.perf

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PERF-SCREEN — نگهبانِ متن: هر چیزِ زندهٔ تازه از `V3Live` می‌گذرد (`docs/RULES-fa.md`).
 *   • `rememberInfiniteTransition(` فقط در `ui/v3/V3Live.kt` (و صحنه‌های هنرِ `ui/v3/art/`، که خودشان بیرون از قاب می‌ایستند)؛
 *     `infiniteRepeatable(` فقط به‌عنوانِ مشخصهٔ `v3LiveFloat(` (همان سطر).
 *   • `LaunchedEffect(…) { while (true|isActive) …` (تیک، «… پیش»، نظرسنجیِ صفحه) فقط در کارِ داده‌ایِ سراسریِ فهرستِ [TICKERS]؛
 *     صفحه‌ها `V3LiveEffect` یا `rememberV3LiveClock`.
 *   • اسپینرِ بی‌پایان (`CircularProgressIndicator`/`LinearProgressIndicator` بی `progress`) فقط در فهرستِ ماندهٔ [SPINNERS]
 *     (فقط کم می‌شود)؛ تازه‌ها `V3LiveSpinner`.
 */
class V3LiveGuardTest {

  private val roots = listOf(File("../android-native/app/src/main/java"), File("src/main/kotlin")).filter { it.isDirectory }

  private fun files(): Map<String, String> = roots.flatMap { r ->
    r.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.relativeTo(r).invariantSeparatorsPath to it.readText() }.toList()
  }.toMap()

  /** کارِ داده‌ایِ سراسری (همگام‌سازی، زنگِ سفارش، مترادف، روزِ کاری، ردگیریِ پیک، پشتیبانِ نگه‌داشته، انتظارِ تأیید) — صفحه نیست */
  private val TICKERS = setOf(
    "ir/vil3ntec/tohid/ui/AppRoot.kt",
    "ir/vil3ntec/tohid/ui/OrdersLive.kt",
    "ir/vil3ntec/tohid/ui/PromoLive.kt",
    "ir/vil3ntec/tohid/ui/components/pos/HeldInvoiceBar.kt",
    "ir/vil3ntec/tohid/ui/components/staff/RemoteAsk.kt",
    "ir/vil3ntec/tohid/ui/courier/CourierLiveTrackAndroid.kt",
    "ir/vil3ntec/tohid/TillSynonymsAndroid.kt",
    "ir/vil3ntec/tohid/ShopDayChoiceAndroid.kt",
    "ir/vil3ntec/tohid/desktop/OrderTrayAlarmDesktop.kt",
  )

  /** اسپینرهای بی‌پایانِ پیش از PERF-SCREEN — فقط کوچک می‌شود */
  private val SPINNERS = setOf(
    "ir/vil3ntec/tohid/ui/courier/CourierUi.kt",
    "ir/vil3ntec/tohid/ui/v3/sales/history/HistoryParts.kt",
    "ir/vil3ntec/tohid/ui/v3/account/profile/DeleteAccountSheet.kt",
    "ir/vil3ntec/tohid/ui/v3/account/profile/SignOutSheet.kt",
    "ir/vil3ntec/tohid/ui/v3/settings/sections/BackupScreen.kt",
    "ir/vil3ntec/tohid/ui/v3/onboarding/TermsStep.kt",
    "ir/vil3ntec/tohid/ui/screens/EasySetupAndroid.kt",
    "ir/vil3ntec/tohid/ui/screens/VisualSearchUi.kt",
    "ir/vil3ntec/tohid/ui/screens/Tohid.kt",
    "ir/vil3ntec/tohid/ui/screens/More.kt",
    "ir/vil3ntec/tohid/ui/screens/StockBook.kt",
    "ir/vil3ntec/tohid/ui/screens/OrderFeedbackPanel.kt",
  )

  private val helper = "ir/vil3ntec/tohid/ui/v3/V3Live.kt"
  private fun art(path: String) = path.startsWith("ir/vil3ntec/tohid/ui/v3/art/")

  @Test fun `انیمیشنِ بی‌پایان فقط از راهِ V3Live`() {
    val all = files()
    assertTrue("پوشهٔ کد پیدا نشد", all.size > 500)
    val bad = all.filter { (path, text) ->
      path != helper && !art(path) && text.lines().any { l ->
        val code = l.substringBefore("//")
        code.contains("rememberInfiniteTransition(") || (code.contains("infiniteRepeatable(") && !code.contains("v3LiveFloat("))
      }
    }.keys
    assertTrue("انیمیشنِ بی‌پایان بیرون از v3LiveFloat:\n" + bad.joinToString("\n"), bad.isEmpty())
  }

  private val rawTicker = Regex("""LaunchedEffect\([^)]*\)\s*\{\s*(?:[^\n]*\n\s*){0,2}while \((?:true|isActive)\)""")

  @Test fun `تیکِ صفحه فقط با V3LiveEffect`() {
    val bad = files().filter { (path, text) -> path != helper && path !in TICKERS && rawTicker.containsMatchIn(text) }.keys
    assertTrue("حلقهٔ «while» در LaunchedEffect ِ صفحه (V3LiveEffect یا rememberV3LiveClock):\n" + bad.joinToString("\n"), bad.isEmpty())
  }

  private val spinner = Regex("""\b(?:Circular|Linear)ProgressIndicator\(""")

  @Test fun `اسپینرِ بی‌پایانِ تازه فقط V3LiveSpinner`() {
    val found = files().filter { (path, text) ->
      path != helper && text.lines().any { l -> spinner.containsMatchIn(l.substringBefore("//")) && !l.contains("progress") && !l.trimStart().startsWith("import ") }
    }.keys
    val fresh = found - SPINNERS
    assertTrue("اسپینرِ بی‌پایانِ تازه (V3LiveSpinner):\n" + fresh.joinToString("\n"), fresh.isEmpty())
  }
}
