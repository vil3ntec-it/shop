package ir.vil3ntec.tohid.ui.v3.products

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.PhotoStudio
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  استودیوی عکسِ کالا (`products/{id}/photo/studio`، و پنجرهٔ `PhotoStudioDialog`
 *  برای پیوندِ گوشی) — پیش از ذخیره عکس آماده می‌شود: زمینهٔ سفید، برشِ مربع،
 *  بهبودِ کیفیت، چرخش. همان کارِ پیشین (`PhotoStudio`): پیش‌نمایشِ ۴۰۰ پیکسلی با
 *  ۱۵۰ میلی‌ثانیه درنگ پس از آخرین تغییر، خروجیِ نهایی روی نخِ پس‌زمینه.
 *  «ذخیرهٔ بی ویرایش» فقط کوچک و فشرده می‌کند. صفحهٔ پهن: پیش‌نمایش و کلیدها کنارِ هم.
 */
@Composable
internal fun PhotoStudioScreen(source: Uri, onBack: () -> Unit, onSave: (Bitmap) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var original by remember(source) { mutableStateOf<PhotoStudio.Image?>(null) }
  var failed by remember(source) { mutableStateOf(false) }
  var opts by remember { mutableStateOf(PhotoStudio.Options()) }
  var preview by remember { mutableStateOf<ImageBitmap?>(null) }
  var before by remember { mutableStateOf<ImageBitmap?>(null) }
  var busy by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var side by remember { mutableIntStateOf(1) }

  LaunchedEffect(source) {
    val img = withContext(Dispatchers.IO) { PhotoStore.decode(context, source)?.let { PhotoStore.toStudio(it) } }
    if (img == null) { failed = true; return@LaunchedEffect }
    original = withContext(Dispatchers.Default) { PhotoStudio.fitWithin(img, PhotoStudio.WORK_EDGE) }
    before = withContext(Dispatchers.Default) { PhotoStore.fromStudio(PhotoStudio.fitWithin(img, PREVIEW)).asImageBitmap() }
  }
  LaunchedEffect(original, opts) {
    val img = original ?: return@LaunchedEffect
    delay(DEBOUNCE_MS)
    busy = true
    preview = withContext(Dispatchers.Default) {
      PhotoStore.fromStudio(PhotoStudio.process(PhotoStudio.fitWithin(img, 600), opts.copy(outEdge = PREVIEW))).asImageBitmap()
    }
    busy = false
  }

  val ready = original != null && !saving
  V3Scaffold(
    title = StrProducts.studioTitle,
    onBack = onBack,
    largeTitle = false,
    notice = null,
    bottomBar = if (failed) null else ({
      V3FormBar(
        primary = StrKit.save,
        onPrimary = {
          val img = original ?: return@V3FormBar
          saving = true
          scope.launch {
            val out = withContext(Dispatchers.Default) { PhotoStore.fromStudio(PhotoStudio.process(img, opts)) }
            saving = false
            onSave(out)
          }
        },
        primaryEnabled = ready,
        loading = saving,
        secondary = StrProducts.saveRaw,
        onSecondary = { original?.takeIf { !saving }?.let { onSave(PhotoStore.fromStudio(it)) } },
      )
    }),
  ) { pad ->
    if (failed) {
      Column(Modifier.fillMaxSize().padding(16.dp)) {
        V3Message(V3Tone.ERROR, StrProducts.unreadable)
        Spacer(Modifier.height(16.dp))
        V3Button(StrKit.back, onBack, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
      }
      return@V3Scaffold
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val wide = maxWidth >= 720.dp
      val previewPane: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
          PreviewCard(if (side == 0) before else preview, busy && side == 1)
          Spacer(Modifier.height(12.dp))
          V3Segmented(listOf(StrProducts.before, StrProducts.after), side, { side = it })
          if (side == 1) Text(StrProducts.afterCaption, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        }
      }
      val controls: @Composable (Modifier) -> Unit = { m ->
        Column(m) { StudioControls(opts) { opts = it } }
      }
      val scroll = rememberScrollState()
      if (wide) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = pad.calculateBottomPadding()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          previewPane(Modifier.weight(1.2f))
          controls(Modifier.weight(1f).verticalScroll(scroll))
        }
      } else {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = pad.calculateBottomPadding())) {
          previewPane(Modifier.fillMaxWidth())
          controls(Modifier.fillMaxWidth())
        }
      }
    }
  }
}

private const val PREVIEW = 400
private const val DEBOUNCE_MS = 150L

/** کارتِ پیش‌نمایشِ مربع؛ عکس‌ها با محو جابه‌جا می‌شوند و هنگامِ ساختن پوششِ «در حالِ ساختن…» */
@Composable
private fun PreviewCard(image: ImageBitmap?, busy: Boolean) {
  val c = PhoneV3.colors
  V3Card(radius = V3Space.featuredRadius, padding = PaddingValues(0.dp)) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(c.tile), contentAlignment = Alignment.Center) {
      Crossfade(image, animationSpec = MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "studioImage") { img ->
        if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
      }
      DraftBusyVeil(busy || image == null, StrProducts.processing)
    }
  }
}

/** کلیدها و لغزنده‌ها در سه گروه + چرخش و «پیش‌فرض» */
@Composable
private fun StudioControls(opts: PhotoStudio.Options, onChange: (PhotoStudio.Options) -> Unit) {
  val stagger = rememberV3Stagger()
  V3Section(StrProducts.groupBackground, Modifier.v3Stagger(stagger, "bg", 0)) {
    V3ToggleRow(StrProducts.whiteBackground, opts.removeBackground, { onChange(opts.copy(removeBackground = it)) })
    DraftReveal(opts.removeBackground) {
      Column {
        V3Divider(start = 16.dp)
        SliderRow(StrProducts.tolerance, opts.tolerance, 5f..80f) { onChange(opts.copy(tolerance = it)) }
      }
    }
  }
  V3Section(StrProducts.groupCrop, Modifier.v3Stagger(stagger, "crop", 1)) {
    V3ToggleRow(StrProducts.autoCrop, opts.autoCrop, { onChange(opts.copy(autoCrop = it)) })
    V3Divider(start = 16.dp)
    SliderRow(StrProducts.margin, opts.margin, 0f..25f) { onChange(opts.copy(margin = it)) }
  }
  V3Section(StrProducts.groupQuality, Modifier.v3Stagger(stagger, "quality", 2)) {
    V3ToggleRow(StrProducts.enhance, opts.enhance, { onChange(opts.copy(enhance = it)) })
    V3Divider(start = 16.dp)
    SliderRow(StrProducts.sharpen, opts.sharpen, 0f..100f) { onChange(opts.copy(sharpen = it)) }
    V3Divider(start = 16.dp)
    SliderRow(StrProducts.brightness, opts.brightness, -50f..50f) { onChange(opts.copy(brightness = it)) }
  }
  Row(Modifier.padding(top = 16.dp, bottom = 8.dp).v3Stagger(stagger, "tools", 3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    V3Button(StrProducts.rotate, { onChange(opts.copy(rotate = (opts.rotate + 90) % 360)) }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.RotateRight)
    V3Button(StrProducts.reset, { onChange(PhotoStudio.Options()) }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.RestartAlt)
  }
}

@Composable
private fun SliderRow(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onChange: (Int) -> Unit) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row {
      Text(label, style = V3Type.callout, color = c.ink, modifier = Modifier.weight(1f))
      Text(value.toString(), style = V3Type.callout, color = c.inkMuted)
    }
    Slider(
      value = value.toFloat(),
      onValueChange = { onChange(it.toInt()) },
      valueRange = range,
      colors = SliderDefaults.colors(
        thumbColor = PhoneV3Roles.focus(c),
        activeTrackColor = PhoneV3Roles.focus(c),
        inactiveTrackColor = c.hairline,
      ),
    )
  }
}
