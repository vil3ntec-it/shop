package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 *  چند چاپگر، نه یکی — فهرستِ چاپگرهای ثبت‌شدهٔ همین دستگاه.
 *
 *  هر چاپگر: نام، راهِ وصل (بلوتوث، شبکه، سیم/چاپگرِ سیستم) و عرضِ کاغذ.
 *  یکی «پیش‌فرض» است و فاکتورِ فروش به همان می‌رود؛ همان کلیدهای
 *  قدیمیِ `printer_address`/`printer_width` را می‌نویسد، پس صفحهٔ فروش
 *  و چاپِ فاکتور هیچ تغییری لازم ندارند. چاپگری که پیش از این فهرست
 *  تنظیم شده بود، بارِ اول خودش به فهرست می‌آید.
 *
 *  همهٔ مدل‌ها: دستورِ چاپ ESC/POSِ تصویری است (GS v 0) که تقریباً همهٔ
 *  چاپگرهای حرارتیِ ۵۸ و ۸۰ میلی‌متری (Xprinter، Epson TM، Rongta، Sunmi،
 *  HPRT، Gprinter، Bixolon، چاپگرهای بی‌نامِ بلوتوثی…) می‌فهمند؛ فارسی
 *  هم چون تصویر است، به جدولِ نویسهٔ چاپگر نیازی ندارد.
 */
object PrinterRegistry {

  data class Saved(val id: String, val name: String, val link: String, val width: Int)

  private fun prefs(context: Context) = context.getSharedPreferences("tohid", Context.MODE_PRIVATE)

  fun list(context: Context): List<Saved> {
    val p = prefs(context)
    val raw = p.getString("printers", null)
    val out = runCatching {
      val arr = JSONArray(raw ?: "[]")
      (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Saved(o.getString("id"), o.optString("name"), o.getString("link"), o.optInt("width", ThermalPrinter.WIDTH_58MM))
      }
    }.getOrDefault(emptyList())
    //  چاپگرِ نسخه‌های قبلی
    val legacy = p.getString("printer_address", null)
    if (raw == null && !legacy.isNullOrBlank()) {
      val first = listOf(Saved(newId(), "چاپگرِ فاکتور", legacy, p.getInt("printer_width", ThermalPrinter.WIDTH_58MM)))
      write(context, first)
      return first
    }
    return out
  }

  fun defaultLink(context: Context): String? = prefs(context).getString("printer_address", null)

  fun add(context: Context, name: String, link: String, width: Int): Saved {
    val all = list(context).filterNot { it.link == link }
    val s = Saved(newId(), name.trim().ifBlank { label(link) }, link, width)
    write(context, all + s)
    if (defaultLink(context).isNullOrBlank() || all.isEmpty()) makeDefault(context, s)
    return s
  }

  fun remove(context: Context, id: String) {
    val all = list(context)
    val gone = all.firstOrNull { it.id == id } ?: return
    val rest = all.filterNot { it.id == id }
    write(context, rest)
    if (defaultLink(context) == gone.link) {
      val next = rest.firstOrNull()
      if (next != null) makeDefault(context, next)
      else prefs(context).edit().remove("printer_address").apply()
    }
  }

  fun makeDefault(context: Context, s: Saved) {
    prefs(context).edit().putString("printer_address", s.link).putInt("printer_width", s.width).apply()
  }

  fun kind(link: String): String = when (ThermalPrinter.Link.parse(link)) {
    is ThermalPrinter.Link.Network -> "شبکه / وای‌فای"
    is ThermalPrinter.Link.Usb -> if (link.startsWith("usb:sys:")) "چاپگرِ نصب‌شده در سیستم" else "سیم (USB)"
    else -> "بلوتوث"
  }

  fun label(link: String): String = ThermalPrinter.Link.parse(link)?.label() ?: link

  /** برگهٔ آزمون — یک فاکتورِ نمونه با همان کشنده‌ای که فاکتورِ واقعی را می‌کشد */
  suspend fun testPrint(context: Context, s: Saved): String? = withContext(Dispatchers.IO) {
    val sale = Sale(id = "test", invoiceNumber = 0, total = 1000.0, finalTotal = 1000.0, paidAmount = 1000.0,
      date = ir.vil3ntec.tohid.todayIso(), createdAt = System.currentTimeMillis())
    val item = SaleItem(id = "test-1", saleId = "test", name = "آزمونِ چاپگر — ${s.name}", quantity = 1.0,
      unitPrice = 1000.0, totalPrice = 1000.0)
    val d = ShopData(sales = listOf(sale), saleItems = listOf(item))
    val bitmap = runCatching { Receipt.render(context, d, sale, "آزمونِ چاپگر", s.width) }
      .getOrElse { return@withContext "برگهٔ آزمون کشیده نشد: ${it.message}" }
    try { ThermalPrinter.print(context, s.link, bitmap, s.width) } finally { bitmap.recycle() }
  }

  private fun write(context: Context, all: List<Saved>) {
    val arr = JSONArray()
    all.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("link", it.link).put("width", it.width)) }
    prefs(context).edit().putString("printers", arr.toString()).apply()
  }

  private fun newId() = "pr" + System.currentTimeMillis().toString(36) + (1000..9999).random()
}
