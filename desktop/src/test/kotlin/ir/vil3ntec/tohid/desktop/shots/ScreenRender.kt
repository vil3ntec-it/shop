package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.File
import java.util.Collections

/**
 *  کشیدنِ یک صفحه بی پنجره و بی Xvfb (رَسترِ Skia ِ همان Compose ِ کامپیوتر) — همان پوشش‌های پیش‌نمایشِ گوشی
 *  (`PhonePreview`: `LocalContext`، `LocalConfiguration`، تم، جهتِ نوشتن و زبانِ `I18n`)، به‌علاوهٔ درختِ معنایی تا
 *  نوشتهٔ بریده، چیزِ بیرون از قاب، کلیدِ ترجمه‌نشده و هدفِ لمسیِ کوچک را بی نگاه به عکس بشمارد.
 *  `TOHID_RENDER_OUT=<پوشه>` ⇒ عکسِ هر حالت و `render-report.json` در همان پوشه. فقط کارِ «(گزارش)» ِ
 *  `desktop-shots.yml` این را می‌دهد؛ بی آن، یا با ران‌تایمِ ناجورِ Compose، آزمون کنار می‌رود ([RenderReport.skipReason]) و هرگز سد نیست.
 */
@OptIn(InternalComposeUiApi::class)
internal object ScreenRender {

  /** یک یافته: نوع، متنِ گره (یا نقشش) و جا */
  data class Finding(val kind: String, val text: String, val where: String) {
    override fun toString() = "$kind «$text» $where"
  }

  class Result(val name: String, val widthDp: Int, val en: Boolean, val findings: List<Finding>, val texts: List<String>)

  private val rawKey = Regex("^[a-z][a-zA-Z0-9]*([._][a-zA-Z0-9]+){1,}$")

  /**
   *  [content] در قابِ [widthDp]×[heightDp] با چگالیِ [density]؛ [frames] بار کشیده می‌شود (هر بار نیم ثانیه جلو) تا
   *  جلوه‌ها و خواندن‌ها بنشینند. [between] پس از هر قاب (حالتی که خواندنِ بی سرورِ صفحه پاکش می‌کند دوباره می‌نشیند).
   */
  fun render(
    name: String,
    widthDp: Int,
    heightDp: Int,
    en: Boolean,
    dark: Boolean = false,
    density: Float = 2f,
    frames: Int = 10,
    phone: Boolean = widthDp < 600,
    between: () -> Unit = {},
    content: @Composable () -> Unit,
  ): Result {
    val w = (widthDp * density).toInt()
    val h = (heightDp * density).toInt()
    val owners = Collections.synchronizedSet(LinkedHashSet<SemanticsOwner>())
    val info = object : WindowInfo {
      override val isWindowFocused: Boolean get() = true
      override val containerSize: IntSize get() = IntSize(w, h)
    }
    val platform = object : PlatformContext by PlatformContext.Empty {
      override val windowInfo: WindowInfo get() = info
      override val semanticsOwnerListener = object : PlatformContext.SemanticsOwnerListener {
        override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) { owners += semanticsOwner }
        override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) { owners -= semanticsOwner }
        override fun onSemanticsChange(semanticsOwner: SemanticsOwner) = Unit
        override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) = Unit
      }
    }
    val dir = if (en) LayoutDirection.Ltr else LayoutDirection.Rtl
    val before = I18n.source
    I18n.source = { if (en) Lang.EN else Lang.FA }
    val scene = CanvasLayersComposeScene(Density(density), dir, IntSize(w, h), Dispatchers.Unconfined, platform)
    val surface = Surface.makeRasterN32Premul(w, h)
    try {
      scene.setContent {
        CompositionLocalProvider(
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(widthDp, heightDp),
          ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout provides !phone,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
              Box(Modifier.fillMaxSize().background(Shop.colors.bg)) { content() }
            }
          }
        }
      }
      var t = 0L
      repeat(frames.coerceAtLeast(2)) {
        surface.canvas.clear(0)
        scene.render(surface.canvas.asComposeCanvas(), t)
        between()
        t += 500_000_000L
        Thread.sleep(40)
      }
      surface.canvas.clear(0)
      scene.render(surface.canvas.asComposeCanvas(), t)
      RenderReport.out?.let { o ->
        o.mkdirs()
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(o, "$name.png").writeBytes(it) }
      }
      val texts = ArrayList<String>()
      val found = ArrayList<Finding>()
      synchronized(owners) { owners.toList() }.forEach { walk(it.unmergedRootSemanticsNode, false, w, density, phone, found, texts) }
      return Result(name, widthDp, en, found.distinct(), texts)
    } finally {
      scene.close()
      surface.close()
      I18n.source = before
    }
  }

  private fun layout(n: SemanticsNode): TextLayoutResult? {
    val get = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
    val list = ArrayList<TextLayoutResult>()
    return if (runCatching { get(list) }.getOrDefault(false)) list.firstOrNull() else null
  }

  private fun walk(n: SemanticsNode, hScroll: Boolean, w: Int, density: Float, phone: Boolean, found: MutableList<Finding>, texts: MutableList<String>) {
    val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
      ?: n.config.getOrNull(SemanticsProperties.EditableText)?.text
    val label = text ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""
    val b = n.boundsInRoot
    val where = "[${(b.left / density).toInt()},${(b.top / density).toInt()} ${(b.width / density).toInt()}×${(b.height / density).toInt()}dp]"
    if (!text.isNullOrBlank()) {
      texts += text
      if (rawKey.matches(text.trim())) found += Finding("raw-key", text, where)
      layout(n)?.let { r ->
        val clip = r.layoutInput.overflow == TextOverflow.Clip || r.layoutInput.overflow == TextOverflow.Visible
        if (r.didOverflowWidth || (r.didOverflowHeight && clip)) found += Finding("clipped", text, where)
        else if (r.lineCount > 0 && r.isLineEllipsized(r.lineCount - 1)) found += Finding("ellipsis", text, where)
      }
    }
    //  پیمایشِ افقی (ردیفِ تراشه‌ها) چیزِ بیرون از قاب را به‌حق دارد
    val inH = hScroll || n.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
    if (!inH && b.width > 0 && b.height > 0 && (b.right > w + 1.5f || b.left < -1.5f)) found += Finding("offscreen", label.ifBlank { "#${n.id}" }, where)
    if (phone && n.config.getOrNull(SemanticsActions.OnClick) != null && n.config.getOrNull(SemanticsProperties.Disabled) == null) {
      val min = minOf(n.size.width, n.size.height) / density
      if (min in 1f..47.5f) found += Finding("small-touch", label.ifBlank { "#${n.id}" }, where)
    }
    n.children.forEach { walk(it, inH, w, density, phone, found, texts) }
  }
}

/**
 *  دروازه و گزارشِ [ScreenRender] — جدا و بی هیچ کلاسِ Compose، تا سنجیدنِ ران‌تایم و نوشتنِ «کنار رفت» پیش از بارگذاریِ
 *  کلاس‌های ناجورِ ui/runtime باشد (محلی، `androidx.collection` هم نیست).
 */
internal object RenderReport {
  /** نوع‌هایی که بی‌گمان عیب‌اند (هشدارِ خلاصهٔ CI) — بقیه (لمسِ کوچک، سه‌نقطه) فقط شمرده می‌شوند؛ `error` = کشیدن شکست */
  val DEFECTS = setOf("clipped", "offscreen", "raw-key", "error")

  /** همهٔ نوع‌ها به ترتیبِ گزارش */
  val KINDS = listOf("clipped", "offscreen", "raw-key", "small-touch", "ellipsis", "error")

  val out: File? = System.getenv("TOHID_RENDER_OUT")?.takeIf { it.isNotBlank() }?.let(::File)

  /** «اصلی.فرعی» از نامِ jar ِ کلاس (`runtime-desktop-1.7.3.jar` ⇒ `1.7`)؛ پوشه یا نامِ بی نسخه ⇒ null */
  private fun jarMinor(cls: String): String? = runCatching {
    Class.forName(cls, false, RenderReport::class.java.classLoader).protectionDomain?.codeSource?.location?.path
      ?.let { Regex("""-(\d+\.\d+)\.[^/]*\.jar$""").find(it)?.groupValues?.get(1) }
  }.getOrNull()

  /** `ComposeVersion.version` ِ ران‌تایم (1.7.3 ⇒ 12900) — فقط برای پیام و گزارش */
  val runtimeVersion: Int? get() = runCatching {
    Class.forName("androidx.compose.runtime.ComposeVersion", true, RenderReport::class.java.classLoader).getField("version").getInt(null)
  }.getOrNull()

  /**
   *  چرا کشیده نمی‌شود، یا null. (۱) ران‌تایمِ Compose باید همان نسخهٔ فرعیِ ui باشد: کشِ آفلاینِ محلی ران‌تایمِ 1.7.3 را
   *  جای 1.9 می‌گذارد (`tools/claude-ops/v3f/offline.init.gradle`) که با ui ِ 1.9.3 نمی‌سازد ⇒ کنار، نه شکست؛ CI وابستگیِ
   *  درست را دارد. (۲) بی `TOHID_RENDER_OUT` هم کنار: آزمونِ سدِ کامپیوتر (`tohid-desktop.yml`) این گزارش را نمی‌کشد.
   */
  fun skipReason(): String? {
    val rt = jarMinor("androidx.compose.runtime.Composer")
    val ui = jarMinor("androidx.compose.ui.Modifier")
    if (rt == null || rt != ui) {
      return "ران‌تایمِ Compose ${rt ?: "?"} (ComposeVersion=${runtimeVersion ?: "?"}) با ui ِ ${ui ?: "?"} یکی نیست — " +
        "کشیدنِ بی پنجره فقط با وابستگی‌های درست (CI)"
    }
    if (out == null) return "TOHID_RENDER_OUT نیست — این گزارش فقط در کارِ «(گزارش)» ِ desktop-shots.yml کشیده می‌شود"
    return null
  }

  /** `render-report.json` در پوشهٔ خروجی: شمارِ هر نوع در کل و در هر حالت، با یافته‌ها؛ [skipped] ⇒ فقط دلیلِ کنار رفتن */
  fun write(results: List<ScreenRender.Result>, skipped: String?) {
    val o = out ?: return
    o.mkdirs()
    val report = buildJsonObject {
      put("skipped", skipped)
      put("composeRuntime", runtimeVersion)
      putJsonArray("defects") { DEFECTS.forEach { add(JsonPrimitive(it)) } }
      putJsonObject("totals") { KINDS.forEach { k -> put(k, results.sumOf { r -> r.findings.count { it.kind == k } }) } }
      putJsonArray("scenes") {
        results.forEach { r ->
          addJsonObject {
            put("name", r.name)
            put("widthDp", r.widthDp)
            put("lang", if (r.en) "en" else "fa")
            put("png", "${r.name}.png".takeIf { File(o, it).isFile })
            putJsonObject("counts") { KINDS.forEach { k -> put(k, r.findings.count { it.kind == k }) } }
            putJsonArray("findings") {
              r.findings.forEach { f -> addJsonObject { put("kind", f.kind); put("text", f.text); put("where", f.where) } }
            }
          }
        }
      }
    }
    File(o, "render-report.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report))
  }
}
