package ir.vil3ntec.tohid.core.product

/*
 *  چند بارکد برای یک کالا — PRD-04 (§۶.۳).
 *
 *  • `parse`: کادرِ «بارکدها» (چند تا با «،» یا «,» یا «؛» یا فاصله یا خطِ تازه) ⇒ فهرستِ تمیز؛
 *    رقمِ فارسی/عربی لاتین می‌شود (همان قاعدهٔ F0-06)، تهی بیرون، تکراری یکی، ترتیب می‌ماند.
 *  • `mergePrimary`: فرمِ قدیمی فقط بارکدِ نخست را نشان می‌دهد؛ ویرایشش فقط همان را عوض می‌کند و
 *    **بقیهٔ بارکدهای کالا نمی‌افتند** (پیش از این ویرایش همه را به یکی کم می‌کرد).
 *  • `conflict`: نخستین بارکدی که مالِ کالای دیگری است — بارکد بین کالاها تکراری نمی‌شود.
 *  منطقِ خالص؛ آزمون: `ProductBarcodesTest` (commonTest).
 */
object ProductBarcodes {

  /** سقفِ بارکد برای یک کالا — فهرستِ بی‌حد اسکنر را کند و فرم را ناخوانا می‌کند */
  const val MAX = 20

  private fun latin(c: Char): Char = when (c) {
    in '۰'..'۹' -> '0' + (c - '۰')
    in '٠'..'٩' -> '0' + (c - '٠')
    else -> c
  }

  fun parse(text: String): List<String> =
    text.split(',', '،', ';', '؛', '\n', '\r', '\t', ' ')
      .map { part -> part.trim().map(::latin).joinToString("") }
      .filter { it.isNotEmpty() }
      .distinct()

  /** فرمِ تک‌بارکدی: بارکدِ نخست عوض/پاک می‌شود، بقیه همان */
  fun mergePrimary(primary: String, existing: List<String>): List<String> {
    val p = primary.trim()
    val rest = existing.drop(1)
    return (if (p.isEmpty()) rest else listOf(p) + rest).filter { it.isNotBlank() }.distinct()
  }

  /** نخستین بارکدی که `ownerOf` آن را مالِ کالای دیگری (نه `selfId`) می‌داند؛ null = بی تعارض */
  fun conflict(codes: List<String>, selfId: String?, ownerOf: (String) -> String?): String? =
    codes.firstOrNull { code -> ownerOf(code).let { it != null && it != selfId } }
}
