package ir.vil3ntec.tohid.ios.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
  fun serverRows(table: String): List<JsonObject> {
    val c = java.net.URL("$probe/rows?table=$table").openConnection() as java.net.HttpURLConnection
    c.connectTimeout = 5_000
    c.readTimeout = 5_000
    val body = c.inputStream.readBytes().decodeToString()
    return (Json.parseToJsonElement(body).jsonObject["rows"] as? JsonArray ?: JsonArray(emptyList())).map { it.jsonObject }
  }
}

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

  fun text(value: String, substring: Boolean = false) = hasText(value, substring = substring)

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
  }

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

  fun waitFor(m: SemanticsMatcher, what: String, timeoutMs: Long = 20_000) = until("«$what» روی صفحه", timeoutMs) { shown(m) }

  fun tap(m: SemanticsMatcher, what: String, timeoutMs: Long = 20_000) {
    waitFor(m, what, timeoutMs)
    val node = ui.onAllNodes(m).onFirst()
    runCatching { node.performScrollTo() }
    pump(250)
    node.performClick()
    note("زدن: $what")
    pump(600)
  }

  fun type(label: String, value: String) {
    val m = Find.field(label)
    waitFor(m, label)
    val node = ui.onAllNodes(m).onFirst()
    runCatching { node.performScrollTo() }
    node.performClick()
    pump(200)
    node.performTextReplacement(value)
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

  /** جدولِ گام‌ها (`summary.md`) و لاگ (`e2e.log`) کنارِ عکس‌ها؛ هر گامِ سرخ یا اجرانشده ⇐ آزمون سرخ */
  fun finish() {
    val table = buildString {
      append("| گام | شرح | نتیجه | زمان | خطا |\n|---|---|---|---:|---|\n")
      steps.forEach { s ->
        val mark = when (s.ok) { true -> "✅"; false -> "❌"; null -> "⏭ اجرا نشد" }
        append("| ${s.id} | ${s.title} | $mark | ${s.ms / 1000.0}s | ${s.error.orEmpty().replace('|', '/').replace('\n', ' ').take(400)} |\n")
      }
    }
    val env = if (facts.isEmpty()) "" else facts.joinToString("\n", postfix = "\n\n") { "- $it" }
    runCatching { File("$out/summary.md").writeText("# آزمونِ سرتاسریِ آیفون — جریان‌های اصلی\n\n$env$table") }
    runCatching { File("$out/e2e.log").writeText(log.toString()) }
    println(table)
    val bad = steps.filter { it.ok != true }
    if (bad.isNotEmpty()) throw AssertionError("آزمونِ سرتاسری: ${bad.size} از ${steps.size} گام سبز نشد\n$table")
  }

  private companion object {
    const val FRAME_MS = 16L
  }
}

/**
 *  چرخهٔ عمرِ صفحه — همان کاری که `ComposeUIViewController` ِ برنامه می‌کند: صفحه جلوی چشم است (RESUMED)، پس
 *  `LifecycleEventEffect(ON_START)` ِ `AppRoot` (همگام‌سازی هنگامِ آمدنِ برنامه) همان‌طور اجرا می‌شود.
 */
@Composable
internal fun E2eHost(content: @Composable () -> Unit) {
  val owner = remember { ResumedOwner() }
  CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
}

private class ResumedOwner : LifecycleOwner {
  private val registry = LifecycleRegistry(this)
  override val lifecycle: Lifecycle get() = registry

  init {
    registry.currentState = Lifecycle.State.RESUMED
  }
}
