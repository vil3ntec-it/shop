package ir.vil3ntec.tohid.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.PhotoStudio
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  استودیوی عکسِ کالا — پیش از ذخیره، عکس آماده می‌شود.
 *
 *  خودکار: زمینه سفید، کالا جدا و وسطِ کادرِ مربع، روشن و تیز. هر کدام
 *  کلیدِ خودش را دارد و چهار لغزنده (حساسیتِ زمینه، فاصله تا لبه،
 *  تیزی، روشنایی) و چرخش. «پیش» و «پس» کنارِ هم دیده می‌شوند.
 *
 *  پیش‌نمایش ۴۰۰ پیکسلی است و با ۱۵۰ میلی‌ثانیه درنگ پس از آخرین تغییر
 *  ساخته می‌شود — کشیدنِ لغزنده برنامه را کند نمی‌کند. خروجیِ نهایی
 *  مربعِ ۸۰۰ است. همهٔ کار روی نخِ پس‌زمینه (`Dispatchers.Default`).
 *
 *  روی گوشی و کامپیوتر همین یک صفحه است؛ روی کامپیوتر دوستونه.
 */
@Composable
fun PhotoStudioDialog(
  source: Uri,
  onDismiss: () -> Unit,
  /** خروجی آماده — صدازننده ذخیره می‌کند */
  onSave: (Bitmap) -> Unit,
) {
  val context = LocalContext.current
  var original by remember(source) { mutableStateOf<PhotoStudio.Image?>(null) }
  var failed by remember(source) { mutableStateOf(false) }
  var opts by remember { mutableStateOf(PhotoStudio.Options()) }
  var preview by remember { mutableStateOf<ImageBitmap?>(null) }
  var before by remember { mutableStateOf<ImageBitmap?>(null) }
  var busy by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(source) {
    val img = withContext(Dispatchers.IO) {
      PhotoStore.decode(context, source)?.let { PhotoStore.toStudio(it) }
    }
    if (img == null) { failed = true; return@LaunchedEffect }
    original = withContext(Dispatchers.Default) { PhotoStudio.fitWithin(img, PhotoStudio.WORK_EDGE) }
    before = withContext(Dispatchers.Default) {
      PhotoStore.fromStudio(PhotoStudio.fitWithin(img, 400)).asImageBitmap()
    }
  }

  //  پیش‌نمایش: درنگِ کوتاه تا کشیدنِ لغزنده ده‌ها بار کار نسازد
  LaunchedEffect(original, opts) {
    val img = original ?: return@LaunchedEffect
    delay(150)
    busy = true
    preview = withContext(Dispatchers.Default) {
      PhotoStore.fromStudio(PhotoStudio.process(PhotoStudio.fitWithin(img, 600), opts.copy(outEdge = 400))).asImageBitmap()
    }
    busy = false
  }

  val wide = screenSize() != Screen.PHONE
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = Shop.colors.surfaceSolid,
      modifier = Modifier.padding(16.dp).widthIn(max = if (wide) 980.dp else 560.dp).fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = Shop.colors.primary)
          Spacer(Modifier.width(8.dp))
          Text("استودیوی عکسِ کالا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = Shop.colors.text, modifier = Modifier.weight(1f))
          if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(12.dp))
        if (failed) {
          Text("این عکس خوانده نشد. عکسِ دیگری انتخاب کنید (JPG یا PNG).", color = Shop.colors.danger)
          Spacer(Modifier.height(12.dp))
          TextButton(onClick = onDismiss) { Text("بستن") }
          return@Column
        }
        val previews: @Composable (Modifier) -> Unit = { m ->
          Row(m, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PreviewBox("پیش", before, ContentScale.Fit, Modifier.weight(1f))
            PreviewBox("پس — همین ذخیره می‌شود", preview, ContentScale.Fit, Modifier.weight(1f))
          }
        }
        val controls: @Composable (Modifier) -> Unit = { m ->
          Column(m.verticalScroll(rememberScrollState())) {
            ToggleRow("زمینهٔ سفید و جدا کردنِ کالا", opts.removeBackground) { opts = opts.copy(removeBackground = it) }
            if (opts.removeBackground) {
              SliderRow("حساسیتِ زمینه", opts.tolerance, 5f..80f) { opts = opts.copy(tolerance = it) }
            }
            ToggleRow("برش و هم‌اندازه کردن (مربع)", opts.autoCrop) { opts = opts.copy(autoCrop = it) }
            SliderRow("فاصله تا لبهٔ کادر ٪", opts.margin, 0f..25f) { opts = opts.copy(margin = it) }
            ToggleRow("بهبودِ کیفیت (نور، رنگ، وضوح)", opts.enhance) { opts = opts.copy(enhance = it) }
            SliderRow("تیزی", opts.sharpen, 0f..100f) { opts = opts.copy(sharpen = it) }
            SliderRow("روشنایی", opts.brightness, -50f..50f) { opts = opts.copy(brightness = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { opts = opts.copy(rotate = (opts.rotate + 90) % 360) }) {
                Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("چرخش")
              }
              TextButton(onClick = { opts = PhotoStudio.Options() }) { Text("پیش‌فرض") }
            }
          }
        }
        if (wide) {
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            previews(Modifier.weight(1.4f))
            controls(Modifier.weight(1f).heightIn(max = 420.dp))
          }
        } else {
          previews(Modifier.fillMaxWidth())
          Spacer(Modifier.height(10.dp))
          controls(Modifier.fillMaxWidth().heightIn(max = 300.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Button(
            enabled = original != null && !saving,
            onClick = {
              val img = original ?: return@Button
              saving = true
              scope.launch {
                val out = withContext(Dispatchers.Default) { PhotoStore.fromStudio(PhotoStudio.process(img, opts)) }
                saving = false
                onSave(out)
              }
            },
          ) { Text(if (saving) "در حالِ ساختن…" else "ذخیره") }
          OutlinedButton(
            enabled = original != null && !saving,
            onClick = {
              //  بی ویرایش: فقط کوچک و فشرده، همان رفتارِ قبلی
              val img = original ?: return@OutlinedButton
              onSave(PhotoStore.fromStudio(img))
            },
          ) { Text("ذخیرهٔ بی ویرایش") }
          TextButton(onClick = onDismiss) { Text("انصراف") }
        }
      }
    }
  }
}

@Composable
private fun PreviewBox(label: String, image: ImageBitmap?, scale: ContentScale, modifier: Modifier) {
  Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
    Spacer(Modifier.height(6.dp))
    Box(
      Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
        .background(Color(0xFFEFF1F5)).border(1.dp, Shop.colors.border, RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center,
    ) {
      if (image == null) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
      else Image(bitmap = image, contentDescription = label, contentScale = scale, modifier = Modifier.fillMaxSize())
    }
  }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, modifier = Modifier.weight(1f))
    Switch(checked = value, onCheckedChange = onChange)
  }
}

@Composable
private fun SliderRow(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onChange: (Int) -> Unit) {
  Column(Modifier.fillMaxWidth()) {
    Row {
      Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted, modifier = Modifier.weight(1f))
      Text(value.toString(), style = MaterialTheme.typography.labelMedium, color = Shop.colors.text)
    }
    Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = range)
  }
}
