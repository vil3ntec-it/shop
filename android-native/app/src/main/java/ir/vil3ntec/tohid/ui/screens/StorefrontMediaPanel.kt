package ir.vil3ntec.tohid.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.image.StorefrontMediaSpec.Kind
import ir.vil3ntec.tohid.data.StorefrontMediaPrep
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.StorefrontMediaDto
import ir.vil3ntec.tohid.data.repo.StorefrontMediaRepository
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  M3 — «لوگو و تصویرِ سرِ ویترین» در برگهٔ تنظیماتِ ویترین. جدا از `Storefront.kt` تا
 *  صفحهٔ «ویترین و بازار» (M1) بی برخورد ادغام شود؛ فقط وقتی سرور پرچمِ
 *  `shop.storefront.media` را روشن گفته صدا زده می‌شود.
 *
 *  انتخاب از گالری ⇒ بُرش و کوچک‌کردن روی دستگاه (`StorefrontMediaPrep`) ⇒ PUT. هر
 *  «ثبت شد» فقط پس از پاسخِ سرور؛ خطای سرور (نوع، حجم، اشتراک) همان پیامِ سرور است.
 */
@Composable
fun StorefrontMediaPanel(snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { StorefrontMediaRepository(Backend.api(context)) }
  var media by remember { mutableStateOf<StorefrontMediaDto?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf<Kind?>(null) }
  val previews = remember { mutableStateMapOf<String, ImageBitmap>() }
  var picking by remember { mutableStateOf(Kind.LOGO) }

  suspend fun loadPreviews(m: StorefrontMediaDto) {
    for (k in Kind.values()) {
      val item = m.of(k) ?: continue
      if (previews.containsKey(item.url)) continue
      repo.bytes(item.url).onSuccess { b ->
        withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(b, 0, b.size) }?.let { previews[item.url] = it.asImageBitmap() }
      }
    }
  }

  suspend fun load() {
    repo.current()
      .onSuccess { media = it; error = null; loadPreviews(it) }
      .onFailure { error = it.userMessage }
  }
  LaunchedEffect(Unit) { load() }

  fun upload(uri: Uri, kind: Kind) {
    busy = kind
    scope.launch {
      val prepared = runCatching {
        val raw = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { s ->
            val buf = s.readNBytesCompat(StorefrontMediaPrep.MAX_INPUT_BYTES + 1)
            require(buf.size <= StorefrontMediaPrep.MAX_INPUT_BYTES) { "فایل بیش از اندازه بزرگ است" }
            buf
          } ?: throw IllegalArgumentException("فایل خوانده نشد")
        }
        StorefrontMediaPrep.prepare(raw, kind)
      }
      prepared.onFailure { snackbar.showSnackbar(it.message ?: "عکس آماده نشد") }
      prepared.onSuccess { jpeg ->
        repo.upload(kind, jpeg)
          .onSuccess { media = it; loadPreviews(it); snackbar.showSnackbar("${kind.label} ثبت شد") }
          .onFailure { snackbar.showSnackbar(it.userMessage) }
      }
      busy = null
    }
  }

  val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) upload(uri, picking)
  }

  Panel {
    Text("لوگو و تصویرِ سرِ ویترین", style = MaterialTheme.typography.titleSmall)
    Text("لوگو مربع و تصویرِ سر سه به یک بریده می‌شود (از وسطِ عکس). همین‌ها در ویترین و بازار دیده می‌شوند.",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    val m = media
    when {
      m == null && error != null -> TohidErrorState("لوگو و تصویر باز نشد", error.orEmpty(), onRetry = { scope.launch { load() } })
      m == null -> TohidLoadingState(rows = 2)
      else -> for (kind in Kind.values()) {
        val item = m.of(kind)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          val box = if (kind == Kind.LOGO) Modifier.size(64.dp) else Modifier.width(144.dp).height(48.dp)
          Box(box.clip(Shape.card).background(Shop.colors.surface2), contentAlignment = Alignment.Center) {
            val bmp = item?.let { previews[it.url] }
            if (bmp != null) Image(bmp, contentDescription = kind.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(if (item == null) "ندارد" else "…", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(kind.label, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TohidSecondaryButton(if (busy == kind) "در حالِ فرستادن…" else if (item == null) "انتخاب" else "عوض کردن", onClick = {
                picking = kind
                runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
              }, enabled = busy == null)
              if (item != null) TohidDangerButton("برداشتن", onClick = {
                busy = kind
                scope.launch {
                  repo.remove(kind)
                    .onSuccess { media = it; snackbar.showSnackbar("${kind.label} برداشته شد") }
                    .onFailure { snackbar.showSnackbar(it.userMessage) }
                  busy = null
                }
              }, enabled = busy == null)
            }
          }
        }
      }
    }
  }
}

/** خواندنِ حداکثر `limit` بایت (readNBytes در API ِ قدیمیِ اندروید نیست) */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
  val out = java.io.ByteArrayOutputStream()
  val buf = ByteArray(64 * 1024)
  while (out.size() < limit) {
    val n = read(buf, 0, minOf(buf.size, limit - out.size()))
    if (n < 0) break
    out.write(buf, 0, n)
  }
  return out.toByteArray()
}
