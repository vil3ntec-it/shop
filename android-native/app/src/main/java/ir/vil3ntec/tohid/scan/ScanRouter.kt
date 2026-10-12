package ir.vil3ntec.tohid.scan

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.channels.Channel

/**
 *  هر بارکد به همان صفحه‌ای می‌رود که کاربر رویش است.
 *
 *  گزارشِ صاحب مخزن: «وقتی در خرید و انبار کالا را اسکن می‌کنم، برنامه
 *  خودش می‌رود توی فروش». دلیل: هر بارکدی که از گوشیِ جفت‌شده می‌رسید
 *  صفحهٔ فروش را باز می‌کرد، و بارکدخوانِ USB/بلوتوث هم فقط وقتی کار
 *  می‌کرد که صفحهٔ فروش باز بود.
 *
 *  حالا هر صفحه‌ای که با بارکد کاری دارد، تا وقتی باز است «گیرنده» ثبت
 *  می‌کند (`register`، با `DisposableEffect` در خودِ صفحه):
 *
 *  | صفحه | اسکن ⇐ |
 *  |---|---|
 *  | صندوق | کالا به سبد |
 *  | کالاها | کالای ثبت‌شده ⇐ پنلِ ویرایشش؛ ناشناس ⇐ ردیفِ «کالای تازه» با همین بارکد |
 *  | انبار / خرید | کالای ثبت‌شده ⇐ ورودِ انبارِ همان؛ ناشناس ⇐ فرمِ کالای تازه با همین بارکد |
 *  | جاهای دیگر (داشبورد، گزارش، …) | صندوق باز و کالا به سبد |
 *
 *  ورودی از دو راه می‌آید و هر دو این‌جا یکی می‌شوند: بارکدخوانی که مثلِ
 *  صفحه‌کلید تایپ می‌کند (پنجرهٔ کامپیوتر وقتی هیچ کادرِ نوشتنی فوکوس
 *  ندارد، با `HidScan`) و گوشیِ جفت‌شده (`PhoneScanner.inbox`).
 */
object ScanRouter {

  /** گیرندهٔ صفحهٔ باز — `true` یعنی بارکد را برداشت */
  private val handlers = ArrayDeque<Pair<Any, (String) -> Boolean>>()

  /** بارکدی که هیچ صفحه‌ای برنداشت ⇒ صندوق باز می‌شود و همین را از این‌جا برمی‌دارد */
  val toSale = Channel<String>(Channel.UNLIMITED)

  /** آخرین بارکد — برای نوارِ وضعیت و آزمون */
  val last = mutableStateOf<String?>(null)

  fun register(owner: Any, handler: (String) -> Boolean) {
    handlers.removeAll { it.first === owner }
    handlers.addLast(owner to handler)
  }

  fun unregister(owner: Any) {
    handlers.removeAll { it.first === owner }
  }

  /**
   *  @param openSale وقتی هیچ گیرنده‌ای نبود، صندوق را باز می‌کند
   *  @return نامِ مقصد برای پیام: «sale» یا «page»
   */
  fun dispatch(code: String, openSale: () -> Unit): String {
    val clean = code.trim()
    if (clean.isEmpty()) return "none"
    last.value = clean
    //  تازه‌ترین گیرنده اول — صفحه‌ای که همین حالا باز شده
    for ((_, h) in handlers.reversed()) if (h(clean)) return "page"
    toSale.trySend(clean)
    openSale()
    return "sale"
  }
}
