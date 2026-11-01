package ir.vil3ntec.tohid.core.pick

import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.core.receipt.ReceiptRaster

/**
 *  چاپِ برچسبِ کیسه‌ها روی چاپگرِ رسید (PCK-09) — منطقِ خالص، commonMain (بی `java.*`).
 *
 *  هر کیسه: QR ِ همان متنِ سرور (`VLNB-<سفارش>-<کیسه>-<همه>`، همان که قفسهٔ انتظار و پیک اسکن می‌کنند) در وسطِ
 *  کاغذ، و زیرش نوشتهٔ «#سفارش · کیسهٔ i از n» که قلمِ برنامه می‌کشد ([caption]، سمتِ پلتفرم). کیسه‌ها با فاصله
 *  زیرِ هم؛ چاپگر پس از همه می‌بُرد (`EscPos.document`). ⛔ متنِ QR فقط از سرور است؛ این‌جا ساخته نمی‌شود.
 */
object BagLabelPrint {

  data class Bag(val qr: String, val caption: String)

  /** فاصلهٔ میانِ دو کیسه (نقطه) — جای بریدن با قیچی */
  const val GAP = 40

  /** اندازهٔ هر خانهٔ QR: تا نیمِ کاغذ، و هر خانه دست‌کم ۳ نقطه تا بارکدخوانِ دستی بخواند */
  fun moduleDots(modules: Int, widthDots: Int): Int =
    ((widthDots / 2) / (modules + 2 * QrCode.QUIET_ZONE)).coerceIn(3, 8)

  /** همهٔ کیسه‌ها در یک تصویر به پهنای [widthDots]؛ [caption] نوشتهٔ زیرِ هر QR را می‌کشد */
  fun image(bags: List<Bag>, widthDots: Int, caption: (String) -> MonoImage): MonoImage {
    require(bags.isNotEmpty()) { "کیسه‌ای نیست" }
    require(widthDots > 0) { "پهنای کاغذ" }
    val parts = bags.map { b ->
      val modules = QrCode.encode(b.qr)
      ReceiptRaster.qr(modules, moduleDots(modules.size, widthDots)) to caption(b.caption)
    }
    val height = parts.sumOf { (q, c) -> q.height + c.height } + GAP * (parts.size - 1)
    val out = MonoImage(widthDots, height)
    var y = 0
    parts.forEachIndexed { i, (q, c) ->
      out.blit(q, ((widthDots - q.width) / 2).coerceAtLeast(0), y)
      y += q.height
      out.blit(c, ((widthDots - c.width) / 2).coerceAtLeast(0), y)
      y += c.height
      if (i < parts.size - 1) y += GAP
    }
    return out
  }
}
