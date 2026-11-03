package ir.vil3ntec.tohid.ios.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import java.io.File
import kotlin.time.TimeSource
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate

/*
 *  ابزارِ آزمونِ سرتاسریِ شبیه‌ساز (گامِ ۸، docs/IOS-PLAN-fa.md §۰.۱۳): سرورِ آزمون، رانندهٔ صفحه، عکس.
 *  رابطِ کاربری همان ریشهٔ برنامهٔ آیفون (`IosRoot`) است که `MainViewController` به SwiftUI می‌دهد — Compose ِ همان
 *  چارچوب، با همان shimها، روی شبیه‌سازِ iOS؛ فقط به‌جای پنجرهٔ UIKit روی سطحِ Skia ِ بی‌صفحه (`runSkikoComposeUiTest`)
 *  کشیده می‌شود تا بی Xcode و بی نمایشگر (headless) زده، نوشته و عکس گرفته شود.
 */

/** سرورِ آزمون (`ios/e2e/backend.mjs`) و پوشهٔ عکس‌ها — از محیط (`SIMCTL_CHILD_*` ِ `ios/shared/build.gradle.kts`) */
internal class E2eEnv(
  val api: String,
  val probe: String,
  val email: String,
  val password: String,
  val product: String,
  val price: Double,
  val shots: String,
) {
  companion object {
    fun load(): E2eEnv {
      val path = env("TOHID_E2E_BACKEND")
        ?: throw AssertionError(
          "TOHID_E2E_BACKEND خالی است: آزمونِ سرتاسری سرورِ آزمون می‌خواهد — `node ios/e2e/backend.mjs <json>` و همان مسیر " +
            "در TOHID_E2E_BACKEND (کارِ ios-app ِ .github/workflows/ios-spike.yml)",
        )
      val j = Json.parseToJsonElement(File(path).readText()).jsonObject
      fun s(k: String) = j[k]?.jsonPrimitive?.content ?: throw AssertionError("$path: «$k» نیست")
      val shots = env("TOHID_E2E_SHOTS") ?: (NSTemporaryDirectory() + "tohid-e2e")
      File(shots).mkdirs()
      return E2eEnv(s("api"), s("probe"), s("email"), s("password"), s("product"), s("price").toDouble(), shots)
    }

    private fun env(k: String): String? = platform.posix.getenv(k)?.toKString()?.trim()?.takeIf { it.isNotEmpty() }
  }

  /** ردیف‌های همگام‌شدهٔ همین دکان روی سرور (`sync_rows`) — گواهِ این‌که نوشتنِ آیفون به سرور رسید */
  fun serverRows(table: String): List<JsonObject> =
    (probeGet("/rows?table=$table")["rows"] as? JsonArray ?: JsonArray(emptyList())).map { it.jsonObject }

  /**
   *  سفارشی که مشتری از ویترینِ همین دکان می‌گذارد (`GET /order/new` ِ backend.mjs: بارِ اول بخشِ سفارش و ویترین برای
   *  همین دکان روشن، سپس همان `POST /store/:slug/checkout` ِ صفحهٔ ویترین) — نه از راهِ برنامه
   */
  fun placeOrder(): PlacedOrder {
    val j = probeGet("/order/new", 60_000)
    fun s(k: String) = j[k]?.jsonPrimitive?.content ?: throw AssertionError("/order/new: «$k» نیست — $j")
    return PlacedOrder(s("id"), s("orderNo").toLong(), s("totalMinor").toLong(), s("product"))
  }

  /** حالِ سفارش روی سرور: وضعیت و شمارِ «فاکتور شد» (رویدادِ `invoice` ِ `lib/order-flow.js`) */
  fun orderState(id: String): Pair<String, Int> {
    val j = probeGet("/order?id=$id")
    return (j["status"]?.jsonPrimitive?.content.orEmpty()) to (j["invoices"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1)
  }

  private fun probeGet(path: String, timeoutMs: Int = 5_000): JsonObject {
    val c = java.net.URL("$probe$path").openConnection() as java.net.HttpURLConnection
    c.connectTimeout = 5_000
    c.readTimeout = timeoutMs
    val body = c.inputStream.readBytes().decodeToString()
    return Json.parseToJsonElement(body).jsonObject
  }
}

/** سفارشِ ویترین (`E2eEnv.placeOrder`) */
internal class PlacedOrder(val id: String, val orderNo: Long, val totalMinor: Long, val product: String)

/** یافتنِ چیزها روی صفحه — با همان متن‌های `Str*` که برنامه نشان می‌دهد (برچسبِ دکمه، کادر، زبانه) */
@OptIn(ExperimentalTestApi::class)
internal object Find {
  /** `onClickLabel` ِ کلیک (`v3Pressable`، `rowClick`، کاشیِ صندوق…) */
  fun clickLabel(label: String) = SemanticsMatcher("onClickLabel = «$label»") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
  }

  /** دکمه/ردیفِ زدنی با این نوشته (برچسبِ کلیک، متن یا توضیحِ دسترس‌پذیری) */
  fun button(label: String) = clickLabel(label) or (hasClickAction() and (hasText(label) or hasContentDescription(label)))

  /** کادرِ نوشتنیِ `V3TextField`/`DraftSecretField` (برچسبش `contentDescription` ِ کادر است) */
  fun field(label: String) = hasSetTextAction() and hasContentDescription(label)

  /** زبانهٔ نوارِ پایینِ گوشی (`PhoneNavBar`: نقشِ Tab، توضیح = نامِ مورد) */
  fun tab(id: String) =
    hasContentDescription(NavCatalog.of(id)!!.label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasClickAction()

  /** کاشی/ردیفِ کالای صندوق: «یکی به سبد» و نامِ کالا */
  fun tile(name: String, addOne: String) =
    clickLabel(addOne) and (hasContentDescription(name, substring = true) or hasText(name, substring = true))

  /** برچسبِ کلیک با این آغاز (کاشی‌ای که شمارش را هم در نامِ دسترس‌پذیرش می‌گوید) */
  fun clickLabelStarting(prefix: String) = SemanticsMatcher("onClickLabel از «$prefix»") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith(prefix) == true
  }

  fun text(value: String, substring: Boolean = false) = hasText(value, substring = substring)

  /** دکمهٔ گردِ نماد‌دار (توضیح = برچسب، نقشِ Button) — نه زبانهٔ هم‌نامِ نوارِ پایین */
  fun iconButton(label: String) =
    hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and hasClickAction()

  val back: SemanticsMatcher get() = button(StrKit.back)
}

/** گام‌ها، زمان، کلیک، نوشتن و عکس روی صحنهٔ آزمون — هر شکست با عکس و درختِ دسترس‌پذیریِ همان لحظه */
@OptIn(ExperimentalTestApi::class)
internal class E2eDriver(private val ui: SkikoComposeUiTest, private val out: String) {
  private class Step(val id: String, val title: String, val ok: Boolean?, val ms: Long, val error: String?)

  private val steps = ArrayList<Step>()
  private val failed = HashSet<String>()
  private val facts = ArrayList<String>()
  private val log = StringBuilder()
  private val began = TimeSource.Monotonic.markNow()

  /** حالِ محیطِ آزمون (نه گام): در سرِ `summary.md` */
  fun fact(s: String) {
    facts += s
    note("محیط: $s")
  }

  fun note(s: String) {
    val line = "[${began.elapsedNow().inWholeMilliseconds / 1000.0}s] $s"
    println("e2e: $line")
    log.append(line).append('\n')
    //  هر سطر همان دم روی دیسک: اگر فرآیند گیر کرد یا افتاد، تا همین‌جا در CI دیده می‌شود
    runCatching { File("$out/e2e.log").writeText(log.toString()) }
  }

  /** گامِ در حالِ اجرا — برای نگهبانِ زمان (`armWatchdog`) */
  @kotlin.concurrent.Volatile
  private var current: String = "آغاز"

  /**
   *  نگهبانِ زمان روی رشتهٔ دیگر (صفِ سراسریِ GCD): اگر آزمون تا `seconds` تمام نشد (رشتهٔ اصلی گیر کرده)، همان گام و جدولِ
   *  تا همین‌جا را می‌نویسد و فرآیندِ آزمون را با کدِ ۳ می‌بندد — سرخِ روشن به‌جای کارِ CI ِ بی‌پایان.
   */
  fun armWatchdog(seconds: Long) {
    val deadline = platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, seconds * 1_000_000_000L)
    platform.darwin.dispatch_after(deadline, platform.darwin.dispatch_get_global_queue(platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
      if (done) return@dispatch_after
      runCatching { File("$out/e2e.log").writeText(log.toString() + "⏱ نگهبان: پس از $seconds ثانیه در «$current» گیر کرد\n") }
      runCatching { File("$out/summary.md").writeText("# آزمونِ سرتاسریِ آیفون — جریان‌های اصلی\n\n⏱ نگهبان: پس از $seconds ثانیه در «$current» گیر کرد (رشتهٔ اصلی پاسخ نداد)\n\n" + table()) }
      println("e2e: ⏱ watchdog — stuck in «$current» after ${seconds}s")
      platform.posix.exit(3)
    }
  }

  @kotlin.concurrent.Volatile
  private var done = false

  /** زمانِ واقعی می‌گذرد و ساعتِ Compose همراهش؛ کارهای رشتهٔ اصلی (Dispatchers.Main، صفِ اصلیِ GCD) هم می‌روند */
  fun pump(ms: Long) {
    val start = TimeSource.Monotonic.markNow()
    do {
      ui.mainClock.advanceTimeBy(FRAME_MS, ignoreFrameDuration = true)
      NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.008))
    } while (start.elapsedNow().inWholeMilliseconds < ms)
  }

  fun count(m: SemanticsMatcher): Int = ui.onAllNodes(m).fetchSemanticsNodes(atLeastOneRootRequired = false).size

  fun shown(m: SemanticsMatcher): Boolean = count(m) > 0

  fun until(what: String, timeoutMs: Long = 20_000, ok: () -> Boolean) {
    val start = TimeSource.Monotonic.markNow()
    while (!ok()) {
      if (start.elapsedNow().inWholeMilliseconds > timeoutMs) throw AssertionError("$what — پس از ${timeoutMs / 1000} ثانیه نشد")
      pump(150)
    }
  }

  fun waitFor(m: SemanticsMatcher, what: String, timeoutMs: Long = 20_000) {
    note("…«$what»")
    until("«$what» روی صفحه", timeoutMs) { shown(m) }
  }

  /** تا چیزی از صفحه برود (برگه‌ای که با حرکت بسته می‌شود: زدن روی پرده‌اش در همان لحظه «بستنِ دوباره» است) */
  fun gone(m: SemanticsMatcher, what: String, timeoutMs: Long = 20_000) {
    note("…رفتنِ «$what»")
    until("رفتنِ «$what» از صفحه", timeoutMs) { !shown(m) }
  }

  /**
   *  پیمایش تا چیز در قابِ پدرِ پیمایش‌پذیرش باشد — با کنشِ `ScrollBy` ِ همان پدر و ساعتی که راننده جلو می‌برد.
   *  (`performScrollTo` ِ ui-test پیمایشِ انیمیشن‌دار را با ساعتِ ایستاده تا ابد منتظر می‌ماند: گیرِ اجرای چهارم.)
   *  زیرِ نوارِ شناورِ پایین هم «بیرونِ قاب» است؛ پیمایشی که جا را عوض نکرد (تهِ فهرست، برگهٔ کوتاه) ⇐ بس — پیمایشِ
   *  بی‌جا به برگه می‌رسد و آن را می‌کشد (اجرای پنجم).
   */
  fun reveal(m: SemanticsMatcher, lastOne: Boolean = false) {
    val bar = if (shown(Find.tab(NavCatalog.HOME))) REVEAL_MARGIN else 0f
    var last: Float? = null
    repeat(8) {
      val node = pick(m, lastOne).fetchSemanticsNode()
      var parent = node.parent
      while (parent != null && !parent.config.contains(SemanticsActions.ScrollBy)) parent = parent.parent
      val box = parent ?: return
      val view = box.boundsInWindow
      val at = node.rect()
      val bottom = minOf(view.bottom, screen().bottom - bar)
      if (at.top >= view.top && at.bottom <= bottom) return
      if (last == at.top) return
      last = at.top
      val dy = at.center.y - (view.top + view.height / 3)
      box.config[SemanticsActions.ScrollBy].action?.invoke(0f, dy)
      pump(500)
    }
  }

  private fun screen() = ui.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInWindow

  /**
   *  جای واقعیِ گره، بی بریدن — `boundsInWindow` به قابِ پدرها بریده می‌شود و چیزِ پیمایش‌شده به بیرون «نقطه‌ای در لبه»
   *  می‌شود که «درونِ قاب» به نظر می‌آید (اجرای ششم: «تازه‌ها» و «ظاهر و زبان» ِ زیرِ صفحه هرگز پیمایش نشدند)
   */
  private fun androidx.compose.ui.semantics.SemanticsNode.rect() =
    androidx.compose.ui.geometry.Rect(positionInWindow, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))

  /**
   *  تا چیز کامل روی صفحه و ایستاده باشد (برگه‌ای که از پایین بالا می‌آید: کادری که پیش‌تر پیدا شد هنوز بیرونِ صفحه
   *  است و زدنش هیچ — اجرای پنجم). بیشینه شش ثانیه؛ پس از آن همان‌جا که هست.
   */
  fun settle(m: SemanticsMatcher, lastOne: Boolean = false) {
    val start = TimeSource.Monotonic.markNow()
    var last: androidx.compose.ui.geometry.Rect? = null
    while (start.elapsedNow().inWholeMilliseconds < 6_000) {
      val at = pick(m, lastOne).fetchSemanticsNode().rect()
      val s = screen()
      if (at == last && at.top >= s.top && at.bottom <= s.bottom && at.left >= s.left && at.right <= s.right) return
      last = at
      pump(200)
    }
  }

  /**
   *  زدن مثلِ دسترس‌پذیری (VoiceOver): کنشِ OnClick ِ همان چیز، پس از آوردن به دید و ایستادن. لمسِ ساختگی روی برگه‌ای که
   *  هنوز جا می‌افتد گاه به پردهٔ پشتش می‌خورد و برگه را می‌بست (اجراهای ۵ تا ۷)؛ آزمون جریانِ برنامه را می‌سنجد، نه
   *  تشخیصِ لمسِ Compose.
   */
  fun tap(m: SemanticsMatcher, what: String, timeoutMs: Long = 20_000, lastOne: Boolean = false) {
    waitFor(m, what, timeoutMs)
    reveal(m, lastOne)
    settle(m, lastOne)
    pick(m, lastOne).performSemanticsAction(SemanticsActions.OnClick)
    note("زدن: $what")
    pump(600)
  }

  /** نخستین (یا آخرین: برگه‌ای که روی صفحه با دکمهٔ هم‌نام باز شده، پس از آن در درخت می‌آید) */
  private fun pick(m: SemanticsMatcher, lastOne: Boolean) = ui.onAllNodes(m).let { if (lastOne) it.onLast() else it.onFirst() }

  /**
   *  زدنِ دکمهٔ خاموش، مثلِ دسترس‌پذیری که کنشِ کلیکِ چیزِ «غیرفعال» را باز هم می‌فرستد: اگر کنشی هست اجرا می‌شود
   *  (سدِ پشتِ دکمه — نه فقط ظاهرِ خاموشش — باید جلوی کار را بگیرد)؛ بی کنش ⇐ هیچ
   */
  fun pressDisabled(m: SemanticsMatcher, what: String) {
    waitFor(m, what)
    val action = pick(m, false).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.action
    note("زدنِ دکمهٔ خاموش: $what (${if (action == null) "بی کنش" else "کنشِ کلیک فرستاده شد"})")
    action?.invoke()
    pump(800)
  }

  /**
   *  کشیدنِ انگشت رو به پایین روی صفحه (لمسِ واقعی، نه کنشِ دسترس‌پذیری): از `dyBelow` پیکسل زیرِ [below] به اندازهٔ
   *  [distance] پیکسل در [ms] میلی‌ثانیه — مثلِ کشیدنِ کادرِ پنهانِ بالای خانه
   */
  fun pullDown(below: SemanticsMatcher, what: String, distance: Float, ms: Long, dyBelow: Float = 150f) {
    waitFor(below, what)
    val at = pick(below, false).fetchSemanticsNode().rect()
    val s = screen()
    val y0 = at.bottom + dyBelow
    val y1 = minOf(y0 + distance, s.bottom - 60f)
    ui.onAllNodes(isRoot()).onFirst().performTouchInput {
      swipe(Offset(s.center.x, y0), Offset(s.center.x, y1), ms)
    }
    note("کشیدن: $what (${y0.toInt()} ⇐ ${y1.toInt()} پیکسل، $ms میلی‌ثانیه)")
    pump(1_200)
  }

  fun type(label: String, value: String) {
    val m = Find.field(label)
    waitFor(m, label)
    reveal(m)
    settle(m)
    //  بی زدن (تمرکز): کنشِ SetText ِ همان کادر، مثلِ دسترس‌پذیری. تمرکز در برگه پیمایشِ «به دید آوردن» می‌سازد و برگهٔ
    //  «قلمِ آزاد» ِ صندوق پس از آن بسته شد (اجرای ششم)
    ui.onAllNodes(m).onFirst().performTextReplacement(value)
    note("نوشتن در «$label»")
    pump(300)
  }

  /** تا زبانهٔ نوارِ پایین: صفحهٔ درونی ⇐ «بازگشت» (نوار فقط روی ریشهٔ زبانه‌هاست، `ShellFrame`) */
  fun toTab(id: String) {
    val tab = Find.tab(id)
    repeat(6) {
      if (shown(tab)) return tap(tab, "زبانهٔ «${NavCatalog.of(id)!!.label}»")
      if (shown(Find.back)) tap(Find.back, "بازگشت") else pump(800)
    }
    tap(tab, "زبانهٔ «${NavCatalog.of(id)!!.label}»")
  }

  /** عکسِ کلِ صفحهٔ گوشی (همهٔ لایه‌ها: برگه‌ها و پنجره‌ها) و درختِ دسترس‌پذیریِ همان لحظه */
  fun shot(name: String) {
    note("…عکسِ $name")
    pump(500)
    runCatching {
      val bitmap = ui.captureToImage().asSkiaBitmap()
      val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG) ?: error("PNG ساخته نشد")
      File("$out/$name.png").writeBytes(png.bytes)
    }.onFailure { note("عکسِ $name: ${it.message}") }
    runCatching { File("$out/$name.txt").writeText(ui.onAllNodes(isRoot()).printToString(Int.MAX_VALUE)) }
      .onFailure { note("درختِ $name: ${it.message}") }
    note("عکس: $name")
  }

  /** یک گام؛ شکست ⇐ عکسِ `<id>-FAIL` و ادامهٔ گام‌هایی که به آن تکیه ندارند (گامِ وابسته «اجرا نشد») */
  fun step(id: String, title: String, needs: List<String> = emptyList(), body: () -> Unit) {
    val blocked = needs.filter { it in failed }
    if (blocked.isNotEmpty()) {
      failed += id
      steps += Step(id, title, null, 0, "پیش‌نیازِ ${blocked.joinToString("، ")} سرخ است")
      note("⏭ $id $title")
      return
    }
    note("▶ $id $title")
    current = "$id $title"
    val start = TimeSource.Monotonic.markNow()
    val error = runCatching(body).exceptionOrNull()
    val ms = start.elapsedNow().inWholeMilliseconds
    if (error != null) {
      failed += id
      note("❌ $id: ${error::class.simpleName}: ${error.message}")
      shot("$id-FAIL")
    } else note("✅ $id (${ms / 1000.0}s)")
    steps += Step(id, title, error == null, ms, error?.let { "${it::class.simpleName}: ${it.message}" })
  }

  /** جدولِ markdown ِ گام‌ها تا همین لحظه */
  private fun table(): String = buildString {
    append("| گام | شرح | نتیجه | زمان | خطا |\n|---|---|---|---:|---|\n")
    steps.forEach { s ->
      val mark = when (s.ok) { true -> "✅"; false -> "❌"; null -> "⏭ اجرا نشد" }
      append("| ${s.id} | ${s.title} | $mark | ${s.ms / 1000.0}s | ${s.error.orEmpty().replace('|', '/').replace('\n', ' ').take(400)} |\n")
    }
  }

  /** جدولِ گام‌ها (`summary.md`) و لاگ (`e2e.log`) کنارِ عکس‌ها؛ هر گامِ سرخ یا اجرانشده ⇐ آزمون سرخ */
  fun finish() {
    done = true
    val table = table()
    val env = if (facts.isEmpty()) "" else facts.joinToString("\n", postfix = "\n\n") { "- $it" }
    runCatching { File("$out/summary.md").writeText("# آزمونِ سرتاسریِ آیفون — جریان‌های اصلی\n\n$env$table") }
    runCatching { File("$out/e2e.log").writeText(log.toString()) }
    println(table)
    val bad = steps.filter { it.ok != true }
    if (bad.isNotEmpty()) throw AssertionError("آزمونِ سرتاسری: ${bad.size} از ${steps.size} گام سبز نشد\n$table")
  }

  private companion object {
    const val FRAME_MS = 16L
    /** پایینِ قاب زیرِ نوارِ شناورِ گوشی است (۲۴۰ پیکسل در ۳x) */
    const val REVEAL_MARGIN = 240f
  }
}

/**
 *  چرخهٔ عمرِ صفحه — همان کاری که `ComposeUIViewController` ِ برنامه می‌کند: صفحه جلوی چشم است (RESUMED)، پس
 *  `LifecycleEventEffect(ON_START)` ِ `AppRoot` (همگام‌سازی هنگامِ آمدنِ برنامه) همان‌طور اجرا می‌شود.
 */
@Composable
internal fun E2eHost(content: @Composable () -> Unit) {
  val owner = remember { ResumedOwner().also { E2eApp.owner = it } }
  CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
}

/**
 *  رفتن به برنامهٔ دیگر و برگشتن (همان `ON_STOP`/`ON_START` که `ComposeUIViewController` هنگامِ پس‌زمینه و پیش‌زمینه
 *  می‌فرستد) — برنامه با آن، مثلِ گوشی، همگام‌سازی و «بخشِ سفارش روشن است؟» را از نو می‌پرسد (`AppRoot`، `OrdersLive.Host`)
 */
internal object E2eApp {
  internal var owner: ResumedOwner? = null

  fun background() = owner?.move(Lifecycle.State.CREATED)

  fun foreground() = owner?.move(Lifecycle.State.RESUMED)
}

internal class ResumedOwner : LifecycleOwner {
  private val registry = LifecycleRegistry(this)
  override val lifecycle: Lifecycle get() = registry

  init {
    registry.currentState = Lifecycle.State.RESUMED
  }

  fun move(to: Lifecycle.State) {
    registry.currentState = to
  }
}
