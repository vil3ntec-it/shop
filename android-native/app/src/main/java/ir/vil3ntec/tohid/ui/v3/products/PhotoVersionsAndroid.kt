package ir.vil3ntec.tohid.ui.v3.products

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 *  IMG-PRO-16 — مرزِ پلتفرمِ «نسخه‌های عکس»: رمزگشاییِ بایت‌های یک نسخه برای پیش‌نمایش (همان `BitmapFactory` ِ
 *  `ImageBankPicker`؛ کامپیوتر و آیفون از shim). سقفِ پیکسل پیش‌تر از سرآیند سنجیده شده (`PhotoVersionsPage.decode`).
 */
internal fun decodeVersion(bytes: ByteArray): ImageBitmap? =
  runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
