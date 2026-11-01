package ir.vil3ntec.tohid.ui.v3.sales.scan

/** حالِ دوربین در برگهٔ اسکن */
enum class CameraAccess {
  /** اجازه هست ⇒ تصویرِ زنده */
  GRANTED,

  /** هنوز پرسیده نشده ⇒ «اجازهٔ دوربین» */
  ASK,

  /** رد شد ⇒ فقط از تنظیماتِ گوشی برمی‌گردد */
  DENIED,
}

/**
 *  قاعده‌های خالصِ برگهٔ اسکنِ بارکد (`scan?for=`) — بی Compose تا آزمون‌پذیر باشند.
 *  کار با دوربین و اجازه‌اش در `ScanScreen` است؛ این‌جا فقط «حالا چه نشان داده شود» و
 *  «چه چیزی نتیجه است».
 */
object ScanModel {

  /** آغاز: اجازه هست ⇒ دوربین، نه ⇒ پرسیدن */
  fun start(granted: Boolean): CameraAccess = if (granted) CameraAccess.GRANTED else CameraAccess.ASK

  /** پاسخِ پنجرهٔ اجازهٔ دستگاه */
  fun afterAsk(ok: Boolean): CameraAccess = if (ok) CameraAccess.GRANTED else CameraAccess.DENIED

  /**
   *  پس از برگشت از تنظیماتِ گوشی: اگر اجازه داده شده، دوربین؛ وگرنه همان حالِ پیشین
   *  (`ASK` دوباره پرسیده نمی‌شود — تنها راهِ بازگشتِ «رد» تنظیماتِ دستگاه است).
   */
  fun recheck(current: CameraAccess, granted: Boolean): CameraAccess = if (granted) CameraAccess.GRANTED else current

  /** کدِ خوانده‌شده: فاصلهٔ دو سر برداشته؛ خالی ⇒ null (فریمِ خالی نتیجه نیست) */
  fun code(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }
}
