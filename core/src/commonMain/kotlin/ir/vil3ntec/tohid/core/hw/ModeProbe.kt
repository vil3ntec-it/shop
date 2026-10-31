package ir.vil3ntec.tohid.core.hw

/**
 *  HW-01 — «اگر چاپگر `GS v 0` را نشناخت، از `ESC *` استفاده شود».
 *
 *  چاپگرِ حرارتی به ما نمی‌گوید کدام فرمانِ تصویر را می‌فهمد: `GS I` (شناسهٔ مدل) در چاپگرهای
 *  ارزان یا پاسخ ندارد یا نامِ ساختگی می‌دهد، و فرمانِ ناشناخته هیچ پاسخِ خطایی برنمی‌گرداند —
 *  فقط نویسهٔ بی‌معنا چاپ می‌شود. پس تشخیص با یک **برگهٔ دوروشی** است: یک نمونه با `ESC *`
 *  (ستونی، که همهٔ چاپگرهای ESC/POS می‌شناسند) و یک نمونه با `GS v 0` (رستری)، و کاربر می‌گوید
 *  کدام درست درآمد (`choose`). روشِ انتخاب‌شده در پروفایلِ همان چاپگر می‌ماند (`PrinterProfile.mode`).
 *
 *  ترتیب عمدی است: **اول ستونی**، بعد `ESC @` و بعد رستری. چاپگری که `GS v 0` را نشناسد بایت‌های
 *  تصویر را نویسه می‌خواند و ممکن است حالتش به‌هم بریزد؛ وقتی رستری آخر است، نمونهٔ ستونی پیش‌تر
 *  سالم چاپ شده و `ESC @` ِ میانی هم هر حالتِ پیشین را پاک کرده است.
 *
 *  خالص است؛ چاپِ برگه روی چاپگرِ واقعی آزموده نشده.
 */
object ModeProbe {

  /**
   *  بایت‌های برگهٔ دوروشی: `ESC @` ← نمونهٔ ۱ با `ESC *` ← سطرِ خالی ← `ESC @` ←
   *  نمونهٔ ۲ با `GS v 0` ← سطرهای خالیِ پروفایل ← برش. کشو هرگز.
   */
  fun page(columnSample: MonoImage, rasterSample: MonoImage, profile: PrinterProfile): ByteArray {
    require(columnSample.width <= profile.paperDots && rasterSample.width <= profile.paperDots) {
      "پهنای نمونه از پهنای کاغذ (${profile.paperDots} نقطه) بیشتر است"
    }
    val out = EscPos.Bytes()
    out.add(EscPos.INIT)
    out.add(EscPos.column(columnSample))
    out.add(EscPos.feed(2))
    out.add(EscPos.INIT)
    out.add(EscPos.raster(rasterSample))
    out.add(EscPos.feed(profile.feedLines))
    out.add(EscPos.cut(profile.cut))
    return out.toByteArray()
  }

  /**
   *  پاسخِ کاربر ⇒ روش. رستری هر جا درست باشد می‌ماند (سریع‌تر و رفتارِ همیشگی)؛ فقط وقتی
   *  تنها نمونهٔ ستونی درست بود ستونی؛ هیچ‌کدام ⇒ `null` (چاپگر یا عرضِ کاغذ مشکلِ دیگری دارد
   *  و تنظیم عوض نمی‌شود).
   */
  fun choose(columnOk: Boolean, rasterOk: Boolean): ImageMode? = when {
    rasterOk -> ImageMode.RASTER
    columnOk -> ImageMode.COLUMN
    else -> null
  }
}
