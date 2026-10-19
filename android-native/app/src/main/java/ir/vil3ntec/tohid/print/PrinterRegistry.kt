package ir.vil3ntec.tohid.print

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ir.vil3ntec.tohid.core.hw.CutMode
import ir.vil3ntec.tohid.core.hw.ImageMode
import ir.vil3ntec.tohid.core.hw.PrinterProfile

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

  /**
   *  یک چاپگرِ ثبت‌شده و پروفایلش (HW-01، HW-03، HW-04). هر فیلدِ تازه
   *  پیش‌فرضی دارد که **رفتارِ امروز** است: چاپگری که پیش از این ثبت شده
   *  همان بایت‌ها را می‌گیرد، کشو باز نمی‌کند و برچسب‌چاپ نیست.
   */
  data class Saved(
    val id: String,
    val name: String,
    val link: String,
    val width: Int,
    /** `raster` (GS v 0) یا `column` (ESC * برای چاپگرهای قدیمی‌تر) */
    val mode: String = ImageMode.RASTER.key,
    /** `partial` | `full` | `none` */
    val cut: String = CutMode.PARTIAL.key,
    /** کشوی پول پس از فروشِ نقدی */
    val drawer: Boolean = false,
    val drawerPin: Int = 0,
    /** نشانِ دکان بالای رسید */
    val logo: Boolean = false,
    /** `receipt` (رسید) یا `label` (برچسبِ کالا) */
    val role: String = ROLE_RECEIPT,
    /** زبانِ چاپگرِ برچسب: `tspl` یا `escpos` */
    val labelLang: String = LANG_TSPL,
  ) {
    fun profile(): PrinterProfile = PrinterProfile(
      paperDots = PrinterProfile.paper(width),
      mode = ImageMode.of(mode),
      cut = CutMode.of(cut),
      drawer = drawer,
      drawerPin = if (drawerPin == 1) 1 else 0,
      logo = logo,
    )
    val isLabel: Boolean get() = role == ROLE_LABEL
  }

  const val ROLE_RECEIPT = "receipt"
  const val ROLE_LABEL = "label"
  const val LANG_TSPL = "tspl"
  const val LANG_ESCPOS = "escpos"

  private fun prefs(context: Context) = context.getSharedPreferences("tohid", Context.MODE_PRIVATE)

  fun list(context: Context): List<Saved> {
    val p = prefs(context)
    val raw = p.getString("printers", null)
    val out = runCatching {
      val arr = JSONArray(raw ?: "[]")
      (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Saved(
          o.getString("id"), o.optString("name"), o.getString("link"), o.optInt("width", ThermalPrinter.WIDTH_58MM),
          mode = o.optString("mode", ImageMode.RASTER.key),
          cut = o.optString("cut", CutMode.PARTIAL.key),
          drawer = o.optBoolean("drawer", false),
          drawerPin = o.optInt("drawerPin", 0),
          logo = o.optBoolean("logo", false),
          role = o.optString("role", ROLE_RECEIPT),
          labelLang = o.optString("labelLang", LANG_TSPL),
        )
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

  /** چاپگرِ رسیدِ پیش‌فرض با پروفایلش؛ چاپگری که فقط نشانی‌اش هست ⇒ پروفایلِ پیش‌فرض */
  fun defaultSaved(context: Context): Saved? {
    val link = defaultLink(context)?.takeIf { it.isNotBlank() } ?: return null
    return list(context).firstOrNull { it.link == link }
      ?: Saved("default", "چاپگرِ فاکتور", link, prefs(context).getInt("printer_width", ThermalPrinter.WIDTH_58MM))
  }

  /** پروفایلِ چاپگری که با این نشانی ثبت شده؛ نبود ⇒ همان عرض و بقیه پیش‌فرض */
  fun savedFor(context: Context, link: String, width: Int): Saved =
    list(context).firstOrNull { it.link == link }?.copy(width = width) ?: Saved("adhoc", label(link), link, width)

  /** نخستین چاپگرِ برچسب */
  fun labelPrinter(context: Context): Saved? = list(context).firstOrNull { it.isLabel }

  /** ذخیرهٔ پروفایلِ ویرایش‌شده؛ اگر پیش‌فرض است، عرضِ پیش‌فرض هم تازه می‌شود */
  fun update(context: Context, s: Saved) {
    val all = list(context).map { if (it.id == s.id) s else it }
    write(context, all)
    if (defaultLink(context) == s.link) prefs(context).edit().putInt("printer_width", s.width).apply()
  }

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
  /**
   *  برگهٔ آزمون (HW-11) — نه فاکتورِ ساختگی: عرضِ کاغذ با خط‌کش، نمونهٔ
   *  دری و پشتو، دو بارکد و پروفایلِ چاپگر، با همان بایت‌هایی که رسید
   *  می‌گیرد. بی صفِ چاپ، تا نتیجه همان لحظه دیده شود.
   */
  suspend fun testPrint(context: Context, s: Saved): String? = withContext(Dispatchers.IO) {
    val bytes = runCatching { HwPages.testBytes(context, s) }
      .getOrElse { return@withContext "برگهٔ آزمون کشیده نشد: ${it.message}" }
    val link = ThermalPrinter.Link.parse(s.link) ?: return@withContext "نشانیِ چاپگر خوانده نشد"
    ThermalPrinter.send(context, link, bytes)
  }

  private fun write(context: Context, all: List<Saved>) {
    val arr = JSONArray()
    all.forEach {
      arr.put(
        JSONObject().put("id", it.id).put("name", it.name).put("link", it.link).put("width", it.width)
          .put("mode", it.mode).put("cut", it.cut).put("drawer", it.drawer).put("drawerPin", it.drawerPin)
          .put("logo", it.logo).put("role", it.role).put("labelLang", it.labelLang)
      )
    }
    prefs(context).edit().putString("printers", arr.toString()).apply()
  }

  private fun newId() = "pr" + System.currentTimeMillis().toString(36) + (1000..9999).random()
}
