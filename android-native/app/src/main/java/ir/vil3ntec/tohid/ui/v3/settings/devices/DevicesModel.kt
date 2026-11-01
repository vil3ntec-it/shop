package ir.vil3ntec.tohid.ui.v3.settings.devices

import ir.vil3ntec.tohid.print.ThermalPrinter

/** بخش‌های صفحهٔ «دستگاه‌های جانبی» به ترتیب؛ `?tab=` یکی از همین‌هاست */
internal enum class DeviceTab(val id: String) { PRINTERS("printers"), SCANNERS("scanners"), PHONE("phone"), LIVE("live"), PHOTOS("photos") }

internal object DevicesModel {
  /** `devices?tab=printers` ⇐ همان کارت؛ ناشناس یا خالی ⇐ null (بالای صفحه) */
  fun tab(raw: String?): DeviceTab? = DeviceTab.entries.firstOrNull { it.id == raw?.trim()?.lowercase() }

  /** راهِ وصلِ چاپگرِ تازه در برگهٔ «افزودنِ چاپگر» */
  enum class Kind { BLUETOOTH, NETWORK, USB }

  /**
   *  چاپگرِ انتخاب‌شده ⇐ `ThermalPrinter.Link` (همان قاعدهٔ برگهٔ چاپِ فروش): بلوتوث
   *  با نشانیِ جفت‌شده، شبکه با IP (درگاهِ خالی یا نادرست ⇐ ۹۱۰۰)، سیم با نامِ دستگاه.
   */
  fun link(kind: Kind, bluetooth: String?, host: String, port: String, usb: String?): ThermalPrinter.Link? = when (kind) {
    Kind.BLUETOOTH -> bluetooth?.takeIf { it.isNotBlank() }?.let { ThermalPrinter.Link.Bluetooth(it) }
    Kind.NETWORK -> host.trim().takeIf { it.isNotBlank() }?.let { ThermalPrinter.Link.Network(it, port.trim().toIntOrNull() ?: ThermalPrinter.RAW_PORT) }
    Kind.USB -> usb?.takeIf { it.isNotBlank() }?.let { ThermalPrinter.Link.Usb(it) }
  }

  /** کدِ جفت‌شدن سه‌تا‌سه‌تا: «۱۲۳ ۴۵۶» */
  fun grouped(code: String): String = code.chunked(3).joinToString(" ")
}
