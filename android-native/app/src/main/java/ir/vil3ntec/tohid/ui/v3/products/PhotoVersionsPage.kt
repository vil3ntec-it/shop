package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrImageChanges
import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.PhotoVersionsRepository
import ir.vil3ntec.tohid.data.repo.PhotoVersionsSource
import ir.vil3ntec.tohid.ui.screens.onlineReportApi
import ir.vil3ntec.tohid.ui.screens.serverRefused
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  IMG-PRO-16 — «نسخه‌های عکس» (`products/{id}/photo/versions`): نسخهٔ کوچک و بزرگِ عکسِ بانکِ همین کالا (همان که
 *  ویترین و برنامه نشان می‌دهند) در اندازهٔ فهرست، در قابِ گوشی و صفحهٔ بزرگ، و ۱:۱؛ با سنجهٔ فشرده‌سازی و داوریِ
 *  `core/image/ImageQuality`. ⛔ هیچ نسخه‌ای بزرگ‌تر از پیکسلِ خودش کشیده نمی‌شود (`ImageQuality.fit`).
 *  ⛔ پرچمِ خاموشِ `shop.image_bank` ⇒ راه در «…» ِ عکسِ کالا پنهان (`rememberPhotoMore`) و صفحه «روشن نیست».
 */

/** یک نسخهٔ خوانده‌شده: بایت‌ها و سنجه */
class PhotoVersion(val bytes: ByteArray, val measure: ImageQuality.Measure)

/** حالِ صفحه — بی سرور و بی Compose ِ دیدنی آزموده می‌شود (`PhotoVersionsPageTest`) */
@Stable
class PhotoVersionsState(private val src: PhotoVersionsSource, val productId: String) {
  var shown by mutableStateOf<ImageQuality.Shown?>(null)
    private set
  var thumb by mutableStateOf<PhotoVersion?>(null)
    private set
  var display by mutableStateOf<PhotoVersion?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var loaded by mutableStateOf(false)
    private set

  /** داوریِ دو نسخه؛ پیش از رسیدن ⇒ خالی */
  val flags: List<ImageQuality.Flag> get() = if (!loaded || (thumb == null && display == null)) emptyList() else ImageQuality.verdict(thumb?.measure, display?.measure)

  private suspend fun fetch(url: String?): PhotoVersion? {
    url ?: return null
    return when (val r = src.bytes(url)) {
      is ApiResult.Success -> PhotoVersion(r.value, ImageQuality.measure(r.value))
      is ApiResult.Failure -> { failed = true; null }
    }
  }

  suspend fun load() {
    failed = false
    when (val r = src.shown(productId)) {
      is ApiResult.Success -> {
        shown = r.value
        off = false
        if (r.value.hasVersions) {
          thumb = fetch(r.value.thumbUrl)
          display = fetch(r.value.displayUrl)
        }
        loaded = !failed
      }
      is ApiResult.Failure -> if (serverRefused(r.error)) off = true else failed = true
    }
  }
}

/** سقفِ رمزگشاییِ پیش‌نمایش: نسخه‌های بانک ≤ ۲۴۰۰ پیکسل‌اند؛ هر چیزِ بزرگ‌تر از ۱۶ مگاپیکسل باز نمی‌شود */
private const val DECODE_MAX_PIXELS = 16_000_000L

private fun decode(v: PhotoVersion?): ImageBitmap? {
  val px = v?.measure?.pixels ?: return null
  if (px > DECODE_MAX_PIXELS) return null
  return decodeVersion(v.bytes)
}

private fun kb(bytes: Int): String = ((bytes + 512) / 1024).toString()

private fun gradeLabel(g: ImageQuality.Grade): String = when (g) {
  ImageQuality.Grade.SHARP -> StrImageChanges.gradeSharp
  ImageQuality.Grade.ACCEPTABLE -> StrImageChanges.gradeAcceptable
  ImageQuality.Grade.OVER_COMPRESSED -> StrImageChanges.gradeOver
  ImageQuality.Grade.LOSSLESS -> StrImageChanges.gradeLossless
  ImageQuality.Grade.UNKNOWN -> StrImageChanges.gradeUnknown
}

private fun flagLabel(f: ImageQuality.Flag): String = when (f) {
  ImageQuality.Flag.OVER_COMPRESSED -> StrImageChanges.flagOver
  ImageQuality.Flag.THUMB_MISMATCH -> StrImageChanges.flagThumb
  ImageQuality.Flag.SMALL_SOURCE -> StrImageChanges.flagSmall
  ImageQuality.Flag.UNREADABLE -> StrImageChanges.flagUnreadable
}

/** `products/{id}/photo/versions` */
@Composable
internal fun PhotoVersionsPage(productId: String) {
  val api = onlineReportApi()
  val state = remember(api, productId) { api?.let { PhotoVersionsState(PhotoVersionsRepository(it), productId) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(StrImageChanges.versionsNote, style = V3Type.footnote, color = c.inkMuted)
    val shown = state?.shown
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.PhotoSizeSelectLarge, StrImageChanges.off)
      state.failed && !state.loaded -> V3EmptyState(Icons.Outlined.CloudOff, StrImageChanges.failed, action = StrImageChanges.retry, onAction = { tick++ })
      shown == null -> Text(StrImageChanges.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
      !shown.hasVersions -> V3EmptyState(Icons.Outlined.PhotoSizeSelectLarge, StrImageChanges.noBankPhoto)
      !state.loaded -> Text(StrImageChanges.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
      else -> VersionsBody(state)
    }
  }
}

@Composable
private fun MeasureLine(m: ImageQuality.Measure) {
  val c = PhoneV3.colors
  val w = m.width
  val h = m.height
  val parts = listOfNotNull(
    if (w != null && h != null) StrImageChanges.measureLine(w, h, kb(m.bytes)) else null,
    m.quality?.let { StrImageChanges.quality(it) },
    gradeLabel(ImageQuality.grade(m)),
  )
  Text(
    parts.joinToString(" · "), style = V3Type.footnote,
    color = if (ImageQuality.grade(m) == ImageQuality.Grade.OVER_COMPRESSED) PhoneV3Roles.errorText(c) else c.inkMuted,
    modifier = Modifier.padding(vertical = 6.dp),
  )
}

@Composable
private fun VersionsBody(state: PhotoVersionsState) {
  val c = PhoneV3.colors
  val flags = state.flags
  Text(
    if (flags.isEmpty()) StrImageChanges.allGood else flags.joinToString("\n") { flagLabel(it) },
    style = V3Type.callout, color = if (flags.isEmpty()) c.ink else PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 12.dp),
  )
  val thumb = state.thumb
  val thumbBmp = remember(thumb) { decode(thumb) }
  if (thumb != null) {
    V3Section(StrImageChanges.thumbVersion, footer = StrImageChanges.thumbNote) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        MeasureLine(thumb.measure)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
          //  همان اندازه‌های فهرستِ گوشی، کارتِ ویترین و شبکهٔ کامپیوتر
          listOf(48.dp, 64.dp, 96.dp, 160.dp).forEach { s -> Framed(thumbBmp, thumb.measure, s, s) }
        }
      }
    }
  }
  val display = state.display
  val displayBmp = remember(display) { decode(display) }
  if (display != null) {
    var large by remember { mutableStateOf(false) }
    V3Section(StrImageChanges.displayVersion) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        MeasureLine(display.measure)
        V3ChipRow(contentPadding = PaddingValues(0.dp)) {
          V3Chip(StrImageChanges.phoneScreen, !large, { large = false })
          V3Chip(StrImageChanges.largeScreen, large, { large = true })
        }
        //  قابِ صفحهٔ کالا: گوشیِ ۳۶۰، صفحهٔ بزرگِ ۸۴۰ (دو ستونِ کامپیوتر)؛ بیرون از پهنا با لغزاندن
        val frame = if (large) 840.dp else 360.dp
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().horizontalScroll(rememberScrollState())) {
          Framed(displayBmp, display.measure, frame, frame * 3 / 4, note = true)
        }
      }
    }
    V3Section(StrImageChanges.actualPixels, footer = StrImageChanges.actualPixelsNote) {
      val d = LocalDensity.current
      val w = display.measure.width ?: 0
      val h = display.measure.height ?: 0
      Box(Modifier.padding(16.dp).fillMaxWidth().height(320.dp).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        if (displayBmp != null && w > 0 && h > 0) {
          Image(displayBmp, null, Modifier.size(with(d) { w.toDp() }, with(d) { h.toDp() }), contentScale = ContentScale.FillBounds)
        }
      }
    }
  }
}

/** یک نسخه در قابِ [boxW]×[boxH]: کوچک‌نمایی آری، بزرگ‌نمایی هرگز (`ImageQuality.fit`) */
@Composable
private fun Framed(bmp: ImageBitmap?, m: ImageQuality.Measure, boxW: Dp, boxH: Dp, note: Boolean = false) {
  val c = PhoneV3.colors
  val d = LocalDensity.current
  val w = m.width ?: 0
  val h = m.height ?: 0
  val bw = with(d) { boxW.roundToPx() }
  val bh = with(d) { boxH.roundToPx() }
  val (fw, fh) = ImageQuality.fit(w, h, bw, bh)
  Column {
    Box(Modifier.size(boxW, boxH).background(c.card).border(1.dp, c.hairline), contentAlignment = Alignment.Center) {
      if (bmp != null && fw > 0) Image(bmp, null, Modifier.size(with(d) { fw.toDp() }, with(d) { fh.toDp() }), contentScale = ContentScale.FillBounds)
    }
    if (note && w > 0) {
      Text(
        if (ImageQuality.nativeIn(w, h, bw, bh)) StrImageChanges.nativeSize else StrImageChanges.scaledDown,
        style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.width(boxW).padding(top = 4.dp),
      )
    }
  }
}
