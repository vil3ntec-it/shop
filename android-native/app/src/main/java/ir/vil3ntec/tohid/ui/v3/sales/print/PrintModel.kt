package ir.vil3ntec.tohid.ui.v3.sales.print

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.print.ThermalPrinter

/** راهِ وصلِ چاپگر — همان سه راهِ برگهٔ چاپِ پیشین */
enum class PrintKind { BLUETOOTH, NETWORK, USB }

/** نوشته‌های برگهٔ چاپ */
data class PrintForm(
  val width: Int = ThermalPrinter.WIDTH_58MM,
  val kind: PrintKind = PrintKind.BLUETOOTH,
  val bt: String? = null,
  val host: String = "",
  val port: String = ThermalPrinter.RAW_PORT.toString(),
  val usb: String? = null,
  /** چاپگرِ ثبت‌شده‌ای که برگزیده شد (`PrinterRegistry.Saved.link`) — `null` = چاپگرِ دیگر */
  val saved: String? = null,
)

/**
 *  برگهٔ چاپِ فاکتور و رسیدِ برگشت (`sales/{id}/print`) — خالص: چاپگرِ دفعهٔ قبل همان راه را
 *  باز می‌کند، نشانیِ چاپگر از راهِ برگزیده، و قاعدهٔ «چاپِ دوباره فقط با دلیل» (HW-02).
 */
object PrintModel {

  val WIDTHS = listOf(ThermalPrinter.WIDTH_58MM, ThermalPrinter.WIDTH_80MM)

  /** آغاز از چاپگرِ دفعهٔ قبل؛ اگر در فهرستِ ثبت‌شده‌ها هست همان برگزیده است */
  fun start(savedLink: String?, width: Int, known: List<String>): PrintForm {
    val link = ThermalPrinter.Link.parse(savedLink)
    val base = PrintForm(width = width.takeIf { it in WIDTHS } ?: ThermalPrinter.WIDTH_58MM)
    val manual = when (link) {
      is ThermalPrinter.Link.Network -> base.copy(kind = PrintKind.NETWORK, host = link.host, port = link.port.toString())
      is ThermalPrinter.Link.Usb -> base.copy(kind = PrintKind.USB, usb = link.deviceName)
      is ThermalPrinter.Link.Bluetooth -> base.copy(bt = link.address)
      else -> base
    }
    return if (savedLink != null && savedLink in known) manual.copy(saved = savedLink) else manual
  }

  /** نشانیِ چاپگرِ دستی، یا `null` تا چیزی برگزیده نشده */
  fun link(f: PrintForm): ThermalPrinter.Link? = when (f.kind) {
    PrintKind.BLUETOOTH -> f.bt?.let { ThermalPrinter.Link.Bluetooth(it) }
    PrintKind.NETWORK -> f.host.trim().takeIf { it.isNotBlank() }?.let {
      ThermalPrinter.Link.Network(it, f.port.trim().toIntOrNull() ?: ThermalPrinter.RAW_PORT)
    }
    PrintKind.USB -> f.usb?.let { ThermalPrinter.Link.Usb(it) }
  }

  /** آنچه به چاپ می‌رود: ثبت‌شده یا دستی */
  fun target(f: PrintForm): String? = f.saved ?: link(f)?.save()

  /** پورت فقط رقم و تا پنج رقم */
  fun port(raw: String): String = ir.vil3ntec.tohid.normalizeDigits(raw).filter { it in '0'..'9' }.take(5)

  /** فاکتور پیش‌تر چاپ شده (همین دستگاه، یا چاپِ دوباره‌ای در سابقهٔ دکان) ⇒ دلیل لازم است */
  fun needsReason(printedHere: Boolean, d: ShopData, saleId: String): Boolean =
    printedHere || d.auditLog.any { it.type == "reprint" && it.refId == saleId }

  /** دلیل‌های رایجِ چاپِ دوباره — همان سه دلیلِ پیشین */
  fun reasons(): List<String> = listOf(StrSale.reprintJam, StrSale.reprintFaint, StrSale.reprintCopy)

  fun reason(picked: String?, other: String): String = picked ?: other.trim()
}
