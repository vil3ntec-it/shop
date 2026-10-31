package ir.vil3ntec.tohid.hw

import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.ScaleProtocol
import ir.vil3ntec.tohid.core.hw.ScaleReading
import ir.vil3ntec.tohid.core.hw.SerialParams
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData

/**
 *  آنچه فقط بعضی دستگاه‌ها دارند — کامپیوتر پرش می‌کند، گوشی خالی می‌گذارد.
 *
 *  هیچ دستگاهِ ساختگی‌ای این‌جا ساخته نمی‌شود: درگاهِ سریال یا هست
 *  (کامپیوتر با jSerialComm) یا `null` است و صفحه همین را می‌گوید.
 */
object HwPlatform {
  /** درگاه‌های سریال (ترازو) — کامپیوتر: `DeskSerial`؛ گوشی: `null` */
  @Volatile var serial: SerialPorts? = null
}

interface SerialPorts {
  data class Port(val name: String, val title: String)

  fun list(): List<Port>

  /** فرستادنِ پرسش و خواندن تا پاسخِ کامل یا پایانِ مهلت */
  fun exchange(port: String, params: SerialParams, request: ByteArray, complete: (ByteArray) -> Boolean, timeoutMs: Int): ByteArray
}

/** ترازوی سریال (HW-09) — یک بار خواندنِ وزن با پروتکلِ انتخابی */
object ScaleReader {
  sealed interface Result {
    data class Ok(val reading: ScaleReading) : Result
    data class Failed(val message: String) : Result
  }

  fun read(port: String, protocol: ScaleProtocol): Result {
    val serial = HwPlatform.serial ?: return Result.Failed("ترازوی سریال فقط روی برنامهٔ کامپیوتر است")
    val bytes = try {
      serial.exchange(port, protocol.serial, protocol.request(), protocol::complete, 1_500)
    } catch (e: Exception) {
      return Result.Failed(e.message ?: "درگاه باز نشد")
    }
    if (bytes.isEmpty()) return Result.Failed("ترازو پاسخ نداد — درگاه، سرعت و پروتکل را ببینید")
    val r = protocol.parse(bytes) ?: return Result.Failed("پاسخِ ترازو خوانده نشد (${bytes.size} بایت)")
    return Result.Ok(r)
  }
}

/**
 *  نمایشگرِ مشتری (HW-10، POS-30) — پنجرهٔ دوم روی کامپیوتر.
 *
 *  صندوق سبد را این‌جا می‌گذارد و پنجرهٔ دوم همین را نشان می‌دهد؛ فقط
 *  نام، تعداد و مبلغ — نه سود، نه موجودی، نه نامِ مشتری.
 */
object CustomerFacing {
  /** این دستگاه پنجرهٔ دوم دارد (کامپیوتر) */
  @Volatile var supported: Boolean = false

  val open = mutableStateOf(false)

  data class Line(val name: String, val quantity: Double, val unit: String, val total: Double)
  data class View(val lines: List<Line> = emptyList(), val total: Double = 0.0, val paid: Double? = null)

  val view = mutableStateOf(View())

  fun showCart(d: ShopData, cart: List<SalesEngine.CartLine>) {
    //  HW-10: نمایشگرِ قطبی (اگر تنظیم شده) مستقل از پنجرهٔ دوم
    Pole.cart(cart.size, Math.round(SalesEngine.cartTotal(d, cart) * 100))
    if (!open.value) return
    //  سبدِ خالیِ پس از فروش «سپاس» را پاک نکند؛ نخستین کالای سبدِ بعدی پاکش می‌کند
    if (cart.isEmpty() && view.value.paid != null) return
    val lines = cart.map { line ->
      val p = d.products.firstOrNull { it.id == line.productId }
      val price = SalesEngine.linePrice(d, line)
      Line(p?.name ?: line.label.ifBlank { "—" }, line.quantity, p?.unit.orEmpty(), SalesEngine.lineTotal(price, line.quantity))
    }
    view.value = View(lines, SalesEngine.cartTotal(d, cart))
  }

  /** پس از ثبتِ فروش: «سپاس» و مبلغ، تا سبدِ بعدی */
  fun sold(total: Double) {
    Pole.paid(Math.round(total * 100))
    if (!open.value) return
    view.value = View(emptyList(), total, paid = total)
  }
}

/**
 *  HW-10 — نمایشگرِ قطبیِ مشتری (VFD دوخطی) روی درگاهِ سریالِ کامپیوتر؛ بایت‌ها از `core/hw/PoleDisplay`
 *  (آزمون: `ScalePoleTest`). فقط با «ابزارهای تازهٔ دستگاه» (`HwTools`) و درگاهِ انتخاب‌شده؛ هر فرستادن
 *  بیرون از نخِ صفحه و «آخرین برنده» است (صفحهٔ کهنه پشتِ هم صف نمی‌کشد). روی نمایشگرِ واقعی آزموده نشده.
 */
object Pole {
  /** ۹۶۰۰ و 8N1 — پیش‌فرضِ رایجِ نمایشگرهای قطبی */
  val PARAMS = SerialParams(baud = 9600, dataBits = 8, parity = "none", stopBits = 1)

  /** درگاهِ انتخاب‌شده (`pole_port` در `tohid`)؛ خالی ⇒ خاموش */
  @Volatile var port: String = ""
  private val pending = java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)
  private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "pole-display").apply { isDaemon = true } }

  fun load(context: android.content.Context) {
    port = runCatching { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE).getString("pole_port", "") }.getOrNull().orEmpty()
  }

  fun save(context: android.content.Context, name: String) {
    port = name
    runCatching { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE).edit().putString("pole_port", name).apply() }
  }

  private fun on(): Boolean = port.isNotBlank() && HwPlatform.serial != null && ir.vil3ntec.tohid.print.HwTools.on.value

  fun cart(items: Int, totalMinor: Long) { if (on()) send(ir.vil3ntec.tohid.core.hw.PoleDisplay.cart(items, totalMinor)) }
  fun paid(totalMinor: Long) { if (on()) send(ir.vil3ntec.tohid.core.hw.PoleDisplay.paid(totalMinor)) }

  /** فرستادنِ همین حالا (آزمونِ «دستگاه‌ها») — خطا را برمی‌گرداند */
  fun test(name: String): String? {
    val serial = HwPlatform.serial ?: return "درگاهِ سریال فقط روی برنامهٔ کامپیوتر است"
    return runCatching {
      serial.exchange(name, PARAMS, ir.vil3ntec.tohid.core.hw.PoleDisplay.frame("VILL3N  TEST", "0123456789 ABC xyz"), { true }, 50)
      null
    }.getOrElse { it.message ?: "به نمایشگر نرسید" }
  }

  private fun send(bytes: ByteArray) {
    if (pending.getAndSet(bytes) != null) return  // کارگر هنوز آخرین را برمی‌دارد
    worker.execute {
      val b = pending.getAndSet(null) ?: return@execute
      runCatching { HwPlatform.serial?.exchange(port, PARAMS, b, { true }, 50) }
    }
  }
}
