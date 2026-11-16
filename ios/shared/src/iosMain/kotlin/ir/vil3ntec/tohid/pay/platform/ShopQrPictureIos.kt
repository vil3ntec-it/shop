package ir.vil3ntec.tohid.pay.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 *  آیفون: همان API ِ `pay/platform/ShopQrPicture.kt` ِ اندروید (ios-exclude.txt، دستهٔ «ب») — عکسِ QR ِ حساب‌پیِ دکان
 *  (صاحب‌کار ۱۴۰۵/۰۸/۲۶). خواندنِ QR از عکس (Vision/CoreImage) هنوز ساخته نشده، پس صادقانه هیچ: `CAN_READ = false` دکمهٔ
 *  «انتخابِ عکسِ QR» را پنهان می‌کند و `read` ِ تهی یعنی صندوقِ آیفون عکسِ سنجیده‌نشده را هرگز نشان نمی‌دهد (همان QR ِ کشیده از
 *  متنِ حساب). عکس را صاحب از گوشیِ اندروید یا کامپیوتر بار می‌کند.
 */
object ShopQrPicture {
  const val MAX_INPUT_BYTES = 25 * 1024 * 1024
  const val MAX_SIDE = 1024
  const val CAN_READ = false

  sealed interface Prep {
    class Ok(val jpeg: ByteArray, val payload: String) : Prep
    data object NotImage : Prep
    data object TooBig : Prep
    data object NoQr : Prep
  }

  suspend fun prepare(bytes: ByteArray): Prep = Prep.NoQr

  suspend fun read(bytes: ByteArray): String? = null

  fun bitmap(bytes: ByteArray): ImageBitmap? = null

  @Composable
  fun picker(onPicked: (ByteArray?) -> Unit): () -> Unit = {}
}
