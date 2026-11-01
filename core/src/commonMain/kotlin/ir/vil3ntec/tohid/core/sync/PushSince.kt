package ir.vil3ntec.tohid.core.sync

/**
 *  `since` ِ بدنهٔ push ِ Sync v1 — «تا کجا را دیده بودم وقتی این opها ساخته شد».
 *
 *  سرور با پرچمِ دکانِ `sync.cursor_from_since` (#481) همین را ملاکِ «دیده بود؟» ِ تعارض می‌گیرد.
 *  cursor ِ همین حالا کافی نیست: opی که **وسطِ یک pull** ثبت شد (یا صفی که پیش از pull خالی نشد)
 *  پیش از دیدنِ opهای همان pull ساخته شده؛ اگر cursor ِ پس از pull برود، سرور خیال می‌کند دستگاه
 *  دیده بود و تعارض **ثبت نمی‌شود**. پس:
 *    • یادداشت = cursor ِ لحظه‌ای که صفِ **خالی** نخستین op را گرفت؛ تا صف خالی نشده فقط پایین می‌آید.
 *    • push کم‌ترینِ یادداشت و cursor را می‌فرستد. بی یادداشت (منفی) ⇒ همان cursor.
 *  محتاط است: شاید تعارضِ اضافه (op ِ تازه‌تری که پشتِ op ِ کهنه در همان صف ماند)، هرگز تعارضِ گم‌شده.
 *
 *  قرینهٔ `pushSinceNote` / `pushSince` در `license/sync-core.js`؛ هر دو با
 *  `core/test-vectors/push-since-v1.json` سنجیده می‌شوند. Kotlinِ خالص (commonMain)، بی java.*.
 */
object PushSince {
  /** یادداشتی که نیست */
  const val NONE: Long = -1L

  /** پیش از رفتنِ op به صف: صفِ خالی (یا بی یادداشت) ⇒ cursor ِ همین حالا؛ وگرنه کم‌ترینِ آن دو */
  fun note(previous: Long, queueWasEmpty: Boolean, cursor: Long): Long {
    val c = if (cursor < 0) 0L else cursor
    return if (queueWasEmpty || previous < 0) c else minOf(previous, c)
  }

  /** آنچه در بدنهٔ push می‌رود */
  fun since(noted: Long, cursor: Long): Long {
    val c = if (cursor < 0) 0L else cursor
    return if (noted < 0) c else minOf(noted, c)
  }
}
