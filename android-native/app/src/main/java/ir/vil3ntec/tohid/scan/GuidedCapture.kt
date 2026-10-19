package ir.vil3ntec.tohid.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.vil3ntec.tohid.core.image.CaptureQuality
import ir.vil3ntec.tohid.core.image.ImagePrep
import ir.vil3ntec.tohid.core.image.JpegMeta
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 *  عکس‌برداریِ راهنمایی‌شده با گوشی (v3 ۳۶.۶ — بخش‌های بی هوشِ مصنوعی).
 *
 *  ── چه می‌کند ──────────────────────────────────────────────────────
 *  • دوربینِ داخلِ برنامه با کادرِ راهنمای کالا و خطِ افق
 *  • پس از هر عکس، سنجشِ ساده روی خودِ گوشی (`CaptureQuality`): تار، تاریک،
 *    نورِ تند، خیلی کوچک — با پیشنهادِ «نور بیشتر» / «ثابت نگه دار»
 *  • «دوباره بگیر» یا «همین را بردار»؛ از چند تلاش، بهترین نشان داده
 *    می‌شود ولی انتخاب با کاربر است
 *  • عکسِ پذیرفته ایستاده (چرخشِ EXIF روی پیکسل) و بی EXIF/GPS به
 *    استودیوی همیشگی می‌رود؛ هیچ عکسی بی تأییدِ کاربر جای عکسِ کالا نمی‌نشیند
 *  ──────────────────────────────────────────────────────────────────
 *  کامپیوتر دوربینِ کالا ندارد: `GuidedCaptureDesk.kt` همین نام‌ها را دارد و
 *  `AVAILABLE = false`، پس دکمه‌اش آن‌جا نیست.
 */
object GuidedCapture {
  const val AVAILABLE = true
}

private class Shot(val file: File, val image: ImageBitmap, val verdict: CaptureQuality.Verdict)

/** بزرگ‌ترین ضلعِ عکسی که به استودیو می‌رود — استودیو خودش ۱۲۰۰ و ۸۰۰ را می‌سازد */
private const val SHOT_SIDE = 1600

@Composable
fun GuidedCaptureDialog(onDismiss: () -> Unit, onAccepted: (Uri) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  var granted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    granted = ok
    if (!ok) onDismiss()
  }
  LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

  val dir = remember { File(context.cacheDir, "guided").apply { if (!exists()) mkdirs() else listFiles()?.forEach { it.delete() } } }
  val shots = remember { mutableStateListOf<Shot>() }
  var review by remember { mutableStateOf<Int?>(null) }
  var busy by remember { mutableStateOf(false) }
  var problem by remember { mutableStateOf<String?>(null) }
  val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
  val previewView = remember {
    PreviewView(context).apply {
      scaleType = PreviewView.ScaleType.FILL_CENTER
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
  }

  /** عکسِ گرفته‌شده ⇒ ایستاده، بی EXIF، ≤۱۶۰۰ پیکسل + سنجش */
  suspend fun process(raw: File): Shot = withContext(Dispatchers.Default) {
    val bytes = raw.readBytes()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val opts = BitmapFactory.Options().apply { inSampleSize = ImagePrep.sampleSize(bounds.outWidth, bounds.outHeight, SHOT_SIDE) }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: throw IllegalStateException("عکس خوانده نشد")
    val fit = ImagePrep.fit(decoded.width, decoded.height, SHOT_SIDE)
    val scaled = if (fit.w != decoded.width || fit.h != decoded.height) Bitmap.createScaledBitmap(decoded, fit.w, fit.h, true) else decoded
    if (scaled !== decoded) decoded.recycle()
    val px = IntArray(scaled.width * scaled.height)
    scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    val (opx, size) = ImagePrep.orient(px, scaled.width, scaled.height, JpegMeta.orientation(bytes))
    scaled.recycle()
    val verdict = CaptureQuality.judge(CaptureQuality.measure(opx, size.w, size.h))
    val upright = Bitmap.createBitmap(opx, size.w, size.h, Bitmap.Config.ARGB_8888)
    val out = File(dir, "shot-${System.currentTimeMillis()}.jpg")
    val buf = java.io.ByteArrayOutputStream()
    upright.compress(Bitmap.CompressFormat.JPEG, 92, buf)
    out.writeBytes(JpegMeta.strip(buf.toByteArray()))
    raw.delete()
    Shot(out, upright.asImageBitmap(), verdict)
  }

  fun take() {
    if (busy) return
    busy = true
    problem = null
    val raw = File(dir, "raw-${System.currentTimeMillis()}.jpg")
    capture.takePicture(
      ImageCapture.OutputFileOptions.Builder(raw).build(),
      ContextCompat.getMainExecutor(context),
      object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
          scope.launch {
            runCatching { process(raw) }
              .onSuccess { shots += it; review = shots.lastIndex }
              .onFailure { problem = "عکس آماده نشد — دوباره بگیرید" }
            busy = false
          }
        }
        override fun onError(exception: ImageCaptureException) {
          problem = "عکس گرفته نشد — دوباره بگیرید"
          busy = false
        }
      },
    )
  }

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
      if (granted) {
        DisposableEffect(Unit) {
          var provider: ProcessCameraProvider? = null
          val future = ProcessCameraProvider.getInstance(context)
          future.addListener({
            val p = runCatching { future.get() }.getOrNull()
            if (p == null) { problem = "دوربین در دسترس نیست"; return@addListener }
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            runCatching {
              p.unbindAll()
              p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }.onFailure { problem = "دوربین باز نشد" }
          }, ContextCompat.getMainExecutor(context))
          onDispose { runCatching { provider?.unbindAll() } }
        }
      }
      Box(Modifier.fillMaxSize()) {
        if (granted) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        FrameGuide(Modifier.fillMaxSize())
        Column(
          Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp),
        ) {
          Text("عکسِ کالا با راهنما", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(4.dp))
          Text(
            "کالا را داخلِ کادر و روبه‌رو بگذارید؛ زمینهٔ ساده و نورِ یکنواخت (کنارِ پنجره) کافی است. خطِ وسط را با لبهٔ قفسه یکی کنید.",
            color = Color.White, style = MaterialTheme.typography.bodySmall,
          )
          problem?.let { Spacer(Modifier.height(6.dp)); StatusPill(it, PillTone.DANGER) }
        }
        Row(
          Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.55f)).padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          TextButton(onClick = onDismiss) { Text("بستن", color = Color.White) }
          Box(
            Modifier.size(68.dp).clip(CircleShape).background(if (busy) Color.Gray else Color.White)
              .border(BorderStroke(4.dp, Color.White.copy(alpha = 0.6f)), CircleShape)
              .clickable(enabled = granted && !busy) { take() },
            contentAlignment = Alignment.Center,
          ) { Text(if (busy) "…" else "عکس", color = Color.Black, fontWeight = FontWeight.Bold) }
          Text("${shots.size} عکس", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
      }
      review?.let { idx -> Review(shots, idx, onPick = { review = it }, onRetake = { review = null }) { s ->
        onAccepted(Uri.fromFile(s.file))
      } }
    }
  }
}

/** کادرِ کالا (مربع، ۷۸٪ِ ضلعِ کوچک)، گوشه‌های پررنگ و خطِ افق */
@Composable
private fun FrameGuide(modifier: Modifier) {
  Canvas(modifier) {
    val side = minOf(size.width, size.height) * 0.78f
    val left = (size.width - side) / 2
    val top = (size.height - side) / 2
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(left, top), Size(side, side), CornerRadius(24f), style = Stroke(2.dp.toPx()))
    val c = side * 0.12f
    val w = 5.dp.toPx()
    val corners = listOf(Offset(left, top) to Offset(1f, 1f), Offset(left + side, top) to Offset(-1f, 1f),
      Offset(left, top + side) to Offset(1f, -1f), Offset(left + side, top + side) to Offset(-1f, -1f))
    for ((p, d) in corners) {
      drawLine(Color.White, p, Offset(p.x + c * d.x, p.y), w)
      drawLine(Color.White, p, Offset(p.x, p.y + c * d.y), w)
    }
    drawLine(Color.White.copy(alpha = 0.45f), Offset(left, size.height / 2), Offset(left + side, size.height / 2), 1.5.dp.toPx())
  }
}

@Composable
private fun Review(shots: List<Shot>, index: Int, onPick: (Int) -> Unit, onRetake: () -> Unit, onAccept: (Shot) -> Unit) {
  val shot = shots[index]
  val best = CaptureQuality.best(shots.map { it.verdict })
  Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
    Image(shot.image, contentDescription = "عکسِ گرفته‌شده", contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    if (shot.verdict.ok) StatusPill("سنجشِ تقریبی: نور و وضوح خوب است", PillTone.SUCCESS)
    else shot.verdict.hints.forEach { Spacer(Modifier.height(4.dp)); StatusPill(it.text, PillTone.WARNING) }
    if (shots.size > 1) {
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shots.forEachIndexed { i, s ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
              s.image, contentDescription = "عکسِ ${i + 1}", contentScale = ContentScale.Crop,
              modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(if (i == index) 3.dp else 1.dp, if (i == index) Color.White else Color.Gray), RoundedCornerShape(8.dp))
                .clickable { onPick(i) },
            )
            if (i == best) Text("پیشنهادی", color = Color.White, style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("دوباره بگیر", color = Color.White) }
      Button(onClick = { onAccept(shot) }, modifier = Modifier.weight(1f)) { Text("همین را بردار") }
    }
    Spacer(Modifier.width(1.dp))
  }
}
