package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.core.marketing.CampaignLinks

/**
 *  MKT-10 — «QR ِ ویترین زیرِ هر رسید»: تنظیمِ **همین دستگاه** (`SharedPreferences` ِ «tohid»، همگام نمی‌شود،
 *  پیش‌فرض خاموش = خالی). مقدار نشانیِ پیوندِ کمپینِ «QR ِ رسید» است که صاحب/مدیر در «پیوند و QR» ِ ویترین برگزیده
 *  (پشتِ پرچمِ خاموشِ `shop.storefront.campaigns`)؛ فقط نشانیِ درست (`CampaignLinks.receiptUrlOk`) برگردانده می‌شود.
 *  فاکتورِ فروش آن را زیرِ QR ِ خودش چاپ می‌کند (`ReceiptLayout`)؛ رسیدِ هدیه و برگشت نه.
 */
object ReceiptStoreQr {

  private const val KEY = "receipt_store_qr"

  private fun prefs(context: Context) = context.getSharedPreferences("tohid", Context.MODE_PRIVATE)

  /** نشانیِ برگزیده، یا null (خاموش یا نشانیِ نادرست) */
  fun url(context: Context): String? =
    runCatching { prefs(context).getString(KEY, "").orEmpty() }.getOrDefault("").takeIf { it.isNotBlank() && CampaignLinks.receiptUrlOk(it) }

  /** null ⇒ خاموش */
  fun set(context: Context, url: String?) {
    val v = url?.takeIf { CampaignLinks.receiptUrlOk(it) }.orEmpty()
    prefs(context).edit().putString(KEY, v).apply()
  }
}
