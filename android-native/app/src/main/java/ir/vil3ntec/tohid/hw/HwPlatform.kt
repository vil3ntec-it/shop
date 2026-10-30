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
    if (!open.value) return
    view.value = View(emptyList(), total, paid = total)
  }
}
