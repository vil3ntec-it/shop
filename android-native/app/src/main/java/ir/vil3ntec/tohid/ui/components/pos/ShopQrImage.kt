package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import ir.vil3ntec.tohid.core.gateway.SimpleAccount
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv

/**
 *  عکسِ QR ِ خودِ دکان (صاحب‌کار ۱۴۰۵/۰۸/۲۶ — «عکسِ QR ِ حساب‌پیم را بار می‌کنم و همان را بزرگ نشان بده»): فقط عکسی که
 *  دوباره خوانده شد و همان متنِ QR ِ حسابِ فعال بود (`HesabPayEnv.qrPicture`)؛ تا رسیدن یا بی آن `null` ⇒ همان QR ِ کشیده.
 *  هر دو کنارِ هم: برگهٔ حساب‌پیِ گوشی، برگهٔ «پرداخت به حسابِ دکان» ِ کامپیوتر و نمایشگرِ رو به مشتری.
 */
@Composable
fun rememberShopQr(env: HesabPayEnv, simple: SimpleAccount?): ImageBitmap? {
  val img by produceState<ImageBitmap?>(null, env, simple?.qrImage, simple?.qrPayload) { value = env.qrPicture(simple) }
  return img
}

/** خودِ عکس در کادرِ روشنِ QR (نامِ دسترسیِ جدا از QR ِ کشیده) */
@Composable
fun ShopQrPicture(img: ImageBitmap, modifier: Modifier = Modifier) {
  Image(img, StrTillPay.shopQrImage, modifier, contentScale = ContentScale.Fit)
}
