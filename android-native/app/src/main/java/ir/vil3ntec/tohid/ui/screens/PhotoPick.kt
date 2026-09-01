package ir.vil3ntec.tohid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import java.io.File

/**
 *  «عکس را از کجا بگیریم؟» — دوربین یا گالری.
 *
 *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
 *  تا امروز عکسِ کالا فقط از گالری می‌آمد. یعنی فروشنده‌ای که جنسِ
 *  تازه را روی پیشخوان گذاشته و می‌خواهد ثبتش کند، باید اول از
 *  برنامه بیرون می‌رفت، در دوربینِ گوشی عکس می‌گرفت، برمی‌گشت و از
 *  گالری پیدایش می‌کرد. سه مرحله برای کاری که یک دکمه است.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  هر دو راه **آفلاین** است: عکس با `PhotoStore` کوچک و فشرده می‌شود و
 *  در پوشهٔ خودِ برنامه می‌نشیند. هیچ‌جا آپلود نمی‌شود و بی‌اینترنت هم
 *  همان‌قدر کار می‌کند.
 *
 *  ── چرا اجازهٔ دوربین اینجا پرسیده می‌شود ─────────────────────────
 *  عکس‌گرفتن با `ACTION_IMAGE_CAPTURE` کارِ برنامهٔ دوربینِ گوشی است و
 *  خودش اجازه‌ای نمی‌خواهد — **ولی** وقتی برنامه‌ای `CAMERA` را در
 *  فهرستِ اجازه‌هایش نوشته باشد (ما برای خواندنِ بارکد نوشته‌ایم)،
 *  اندروید همان اجازه را برای این کار هم لازم می‌داند. بدونِ این
 *  پرسش، دکمهٔ دوربین روی خیلی از گوشی‌ها بی‌صدا هیچ کاری نمی‌کرد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(
  open: Boolean,
  onDismiss: () -> Unit,
  /** نوشتهٔ راهنمای زیرِ عنوان */
  note: String = "عکس در خودِ گوشی می‌ماند و به اینترنت نیازی نیست",
  onPicked: (Uri) -> Unit,
) {
  val context = LocalContext.current

  /*
   *  راه‌اندازها بی‌قید و شرط ساخته می‌شوند.
   *
   *  `rememberLauncherForActivityResult` را نمی‌شود داخلِ `if` گذاشت:
   *  کامپوز ترتیبِ فراخوانی‌ها را می‌شمارد و راه‌اندازی که یک بار
   *  ساخته شود و بارِ بعد نه، ثبتش را از دست می‌دهد و نتیجهٔ عکس
   *  هیچ‌وقت برنمی‌گردد.
   */
  var shot by remember { mutableStateOf<Uri?>(null) }

  /*
   *  اول `onPicked` و بعد `onDismiss` — نه برعکس.
   *
   *  صدازننده معمولاً «عکس برای کدام کالاست» را در همان حالتی نگه
   *  می‌دارد که با بستنِ برگه خالی می‌شود. اگر اول می‌بستیم، عکس
   *  می‌آمد و برنامه نمی‌دانست مالِ کیست — و بی‌صدا دور ریخته می‌شد.
   */
  val fromGallery = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) onPicked(uri)
    onDismiss()
  }

  val fromCamera = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
  ) { taken ->
    val target = shot
    shot = null
    if (taken && target != null) onPicked(target)
    onDismiss()
  }

  fun openCamera() {
    val uri = cameraTarget(context) ?: return
    shot = uri
    runCatching { fromCamera.launch(uri) }
  }

  //  اجازه گرفته شد؟ همان لحظه دوربین باز می‌شود، نه اینکه کاربر
  //  دوباره دکمه را بزند
  val askCamera = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) openCamera() else onDismiss()
  }

  if (!open) return

  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Shop.colors.bg) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
      Text(
        "انتخاب تصویر",
        style = MaterialTheme.typography.titleMedium,
        color = Shop.colors.text,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(4.dp))
      Text(note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      Spacer(Modifier.height(14.dp))

      SourceRow(
        icon = Icons.Filled.PhotoCamera,
        title = "دوربین",
        detail = "گرفتنِ عکسِ تازه",
      ) {
        val granted = ContextCompat.checkSelfPermission(
          context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else askCamera.launch(Manifest.permission.CAMERA)
      }

      Spacer(Modifier.height(8.dp))

      SourceRow(
        icon = Icons.Filled.PhotoLibrary,
        title = "گالری",
        detail = "انتخاب از عکس‌های گوشی",
      ) {
        fromGallery.launch(
          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
      }
    }
  }
}

/** یک ردیفِ انتخاب — آیکن در کادرِ رنگی، نام و توضیحِ کوتاه */
@Composable
private fun SourceRow(
  icon: ImageVector,
  title: String,
  detail: String,
  onClick: () -> Unit,
) {
  val colors = Shop.colors
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(colors.surface)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryTint),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.Bold)
      Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
  }
}

/**
 *  فایلی که دوربین عکس را در آن می‌نویسد.
 *
 *  در پوشهٔ کَشِ خودِ برنامه است و با `FileProvider` به برنامهٔ دوربین
 *  داده می‌شود، نه با مسیرِ خام — از اندروید ۷ به بعد مسیرِ خام
 *  `FileUriExposedException` می‌دهد و برنامه می‌بندد.
 *
 *  پوشه پیش از هر عکس خالی می‌شود: عکسِ اصلی جای دیگری (`PhotoStore`)
 *  ذخیره می‌شود و این فقط یک واسطه است؛ نگه داشتنشان یعنی پوشه‌ای که
 *  هر بار بزرگ‌تر می‌شود.
 */
private fun cameraTarget(context: android.content.Context): Uri? = runCatching {
  val dir = File(context.cacheDir, "camera").apply {
    if (!exists()) mkdirs() else listFiles()?.forEach { it.delete() }
  }
  val file = File(dir, "shot-${System.currentTimeMillis()}.jpg")
  FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()
