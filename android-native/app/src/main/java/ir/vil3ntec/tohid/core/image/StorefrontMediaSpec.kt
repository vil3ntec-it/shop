package ir.vil3ntec.tohid.core.image

import kotlin.math.min
import kotlin.math.roundToInt

/**
 *  لوگو و تصویرِ سرِ ویترین (M3، قراردادِ بازار §۱.۱) — اندازه‌های ثابت، منطقِ خالص بی `android.*`.
 *
 *  ── همان سرور (`server/src/lib/storefront-media.js`، `SPECS`) ────────────
 *    logo    مربع (نسبتِ ۱)، عرضِ ۱۲۸..۵۱۲، ≤۱۵۰KB
 *    header  ۳ به ۱، عرضِ ۶۰۰..۱۵۰۰، ≤۴۰۰KB
 *  سرور نسبتِ بیرون از ۲٪، عرضِ بیرون از بازه و EXIF orientation > ۱ را رد می‌کند؛
 *  پس برنامه از وسط می‌بُرد (`crop`)، به عرضِ هدف کوچک می‌کند (`target`) و چرخش را
 *  روی پیکسل‌ها می‌نشاند.
 *  ──────────────────────────────────────────────────────────────────
 */
object StorefrontMediaSpec {
  enum class Kind(val key: String, val ratio: Int, val minW: Int, val maxW: Int, val maxBytes: Int, val label: String) {
    LOGO("logo", 1, 128, 512, 150 * 1024, "لوگو"),
    HEADER("header", 3, 600, 1500, 400 * 1024, "تصویرِ سر"),
  }

  /** بُرشِ وسطِ تصویر به نسبتِ نوع: (x, y, w, h) در مختصاتِ همان تصویر */
  data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

  fun crop(w: Int, h: Int, kind: Kind): Rect {
    require(w > 0 && h > 0) { "ابعادِ عکس صفر است" }
    //  بزرگ‌ترین مستطیلِ با نسبتِ `ratio`:1 که در عکس جا شود
    val cw = min(w, h * kind.ratio)
    val ch = min(h, cw / kind.ratio)
    val fw = ch * kind.ratio
    return Rect((w - fw) / 2, (h - ch) / 2, fw, ch)
  }

  /** اندازهٔ نهایی پس از بُرش: هرگز بزرگ‌تر از بُرش و از سقف؛ کوچک‌تر از کمینه ⇒ خطای روشن */
  fun target(cropW: Int, kind: Kind): ImagePrep.Size {
    val w = min(cropW, kind.maxW)
    require(w >= kind.minW) { "${kind.label} خیلی کوچک است؛ عکسی با عرضِ دست‌کم ${kind.minW} پیکسل (پس از بُرش) انتخاب کنید" }
    return ImagePrep.Size(w, (w.toDouble() / kind.ratio).roundToInt().coerceAtLeast(1))
  }

  /** همان مرزِ سرور: نسبت در ۲٪ و عرض در بازه */
  fun accepts(size: ImagePrep.Size, kind: Kind): Boolean =
    size.w in kind.minW..kind.maxW &&
      kotlin.math.abs(size.ratio - kind.ratio) / kind.ratio <= 0.02
}
