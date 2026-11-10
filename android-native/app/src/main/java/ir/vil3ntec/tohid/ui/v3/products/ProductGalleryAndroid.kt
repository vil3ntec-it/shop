package ir.vil3ntec.tohid.ui.v3.products

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrProductGallery
import ir.vil3ntec.tohid.core.image.BulkJob
import ir.vil3ntec.tohid.core.image.ProductGallery
import ir.vil3ntec.tohid.data.ImageBank
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.ProductGalleryRepository
import ir.vil3ntec.tohid.data.repo.ProductGallerySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/*
 *  PRD-17/PRD-27 — مرزِ پلتفرمِ «عکس‌های بیشتر و ویترین»: درِ سرور، نقشِ «نوشتنِ کالا»، خواندنِ فایلِ انتخاب‌شده با سقف،
 *  آماده‌سازی با همان `ImageBank.prepare` (نمایش و کوچک، EXIF پاک) و «همان عکسِ کالا در ویترین» با همان
 *  `ImageBank.uploadOwn` — تا `ProductGalleryPage`/`ProductGalleryState` بی وابستگیِ پلتفرم بمانند.
 */

internal class GalleryPort(
  val src: ProductGallerySource,
  /** همان `canWrite(role, 'products')` ِ سرور */
  val canWrite: Boolean,
  /** کالا روی دستگاه عکس دارد ⇒ «همان عکسِ کالا در ویترین» ممکن است */
  val hasDevicePhoto: Boolean,
  private val app: Context,
  private val productId: String,
) {
  /** بایت‌های فایلِ کاربر ⇒ (نمایش، کوچک)؛ نه عکس یا بیش از اندازه ⇒ null */
  suspend fun prepare(raw: ByteArray): Pair<ByteArray, ByteArray?>? =
    try {
      val p = ImageBank.prepare(raw)
      p.display to p.thumb
    } catch (c: CancellationException) {
      throw c
    } catch (_: Exception) {
      null
    }

  /** عکسِ روی دستگاه دوباره عکسِ ویترین می‌شود (`force`: سنجهٔ عکسِ جدای ویترین کنار) */
  suspend fun useDevicePhoto(): Boolean {
    return try {
      val bytes = withContext(Dispatchers.IO) { PhotoStore.bytes(app, productId) } ?: return false
      ImageBank.uploadOwn(app, productId, ImageBank.prepare(bytes), BulkJob.Upload(), force = true)
      true
    } catch (c: CancellationException) {
      throw c
    } catch (_: Exception) {
      false
    }
  }
}

/** بیرون از حساب ⇒ null (صفحه «روشن نیست») */
@Composable
internal fun rememberGalleryPort(productId: String, hasDevicePhoto: Boolean): GalleryPort? {
  val context = LocalContext.current
  val ready = Backend.isReady(context)
  val canWrite = remember(context) { ShopRole.canWrite(context, "products") }
  return remember(context, ready, canWrite, productId, hasDevicePhoto) {
    if (!ready) null else GalleryPort(ProductGalleryRepository(Backend.api(context)), canWrite, hasDevicePhoto, context.applicationContext, productId)
  }
}

/** خواندن با سقف (همان `ImageBulk`) — فایلِ بزرگ‌تر تا ته خوانده نمی‌شود */
private suspend fun readCapped(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
  try {
    context.contentResolver.openInputStream(uri)?.use { input ->
      val out = ByteArrayOutputStream()
      val buf = ByteArray(64 * 1024)
      var total = 0L
      while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > ProductGallery.MAX_INPUT_BYTES) return@withContext null
        out.write(buf, 0, n)
      }
      out.toByteArray()
    }
  } catch (c: CancellationException) {
    throw c
  } catch (_: Exception) {
    null
  }
}

/** دوربین یا گالری (همان برگهٔ عکسِ کالا) ⇒ بایت‌های فایل یا null (خوانده نشد/بزرگ) */
@Composable
internal fun GalleryPickSheet(open: Boolean, onDismiss: () -> Unit, onBytes: (ByteArray?) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  DraftPhotoSourceSheet(
    open = open,
    onDismiss = onDismiss,
    title = StrProductGallery.add,
    onPicked = { uri -> scope.launch { onBytes(readCapped(context, uri)) } },
  )
}
