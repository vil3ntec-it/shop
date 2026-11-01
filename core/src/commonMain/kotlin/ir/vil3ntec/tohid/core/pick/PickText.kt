package ir.vil3ntec.tohid.core.pick

import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.orders.OrderText

/**
 *  متن و تصمیم‌های کوچکِ برگهٔ جمع‌آوری (PCK) برای هر رابط (برنامهٔ مدیریتِ امروز، میزِ v3 ِ #400 و
 *  آیفون) — خالص، commonMain. تصمیمِ نهایی همیشه سرور است؛ این‌ها فقط نمایش و پیش‌سنجش‌اند.
 */
object PickText {

  /** نامِ وضعیتِ قلم */
  fun lineStatus(status: String): String = when (status) {
    "pending" -> "مانده"
    "picked" -> "برداشته شد"
    "short" -> "کمتر از سفارش"
    "not_found" -> "پیدا نشد"
    "substituted" -> "جایگزین"
    "removed" -> "حذف شد"
    else -> status
  }

  /** «راهرو ۲ · قفسه ۱ · طبقه بالا»؛ بی‌جا ⇒ «جا ثبت نشده» */
  fun location(aisle: String, shelf: String, level: String): String {
    val parts = buildList {
      if (aisle.isNotBlank()) add("راهرو ${aisle.trim()}")
      if (shelf.isNotBlank()) add("قفسه ${shelf.trim()}")
      if (level.isNotBlank()) add("طبقه ${level.trim()}")
    }
    return if (parts.isEmpty()) "جا ثبت نشده" else parts.joinToString(" · ")
  }

  /** «۱٫۵ کیلو» — مقدار با واحد */
  fun qty(milli: Long, unit: String): String = if (unit.isBlank()) OrderText.qty(milli) else "${OrderText.qty(milli)} ${unit.trim()}"

  /**
   *  قلمی که اسکنِ بعدی رویش می‌نشیند: همان که کاربر برگزیده اگر هنوز باز است، وگرنه نخستین قلمِ باز
   *  به ترتیبِ برگه (`lines` از پیش به ترتیبِ قفسه‌اند). هیچ قلمِ باز ⇒ null.
   */
  fun <T> current(lines: List<T>, selected: Int?, lineNo: (T) -> Int, pending: (T) -> Boolean): Int? {
    val open = lines.filter(pending)
    if (selected != null && open.any { lineNo(it) == selected }) return selected
    return open.firstOrNull()?.let(lineNo)
  }

  /** وزنِ تایپ‌شده («۱٫۶»، «1.6») به هزارم؛ نامعتبر یا صفر ⇒ null */
  fun weightMilli(text: String): Long? = OrderDraft.scaled(text.trim(), 3)?.takeIf { it > 0 }

  /**
   *  وزنِ ترازو (PCK-04) ⇒ متنِ کادرِ «وزنِ واقعی»: مقدار به واحدِ کالا با حداکثر سه رقمِ اعشار و بی صفرِ ته
   *  (۱٫۲۵۰ کیلو ⇒ «1.25»، ۷۵۰ گرم ⇒ «750»)؛ همان را [weightMilli] می‌خواند. نامعتبر ⇒ «».
   */
  fun scaleText(quantity: Double): String {
    if (!quantity.isFinite() || quantity <= 0.0) return ""
    val milli = kotlin.math.round(quantity * 1000).toLong()
    val whole = milli / 1000
    val frac = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (frac.isEmpty()) "$whole" else "$whole.$frac"
  }

  /**
   *  پیش‌سنجشِ وزن پیش از فرستادن: بیش از حد ⇒ پیامِ «کمتر بردارید» با سقف (سرور هم همین را رد می‌کند)؛
   *  کمتر از حد ⇒ هشدار که قلم «کم» ثبت می‌شود؛ در حد ⇒ null.
   */
  fun weighHint(orderedMilli: Long, actualMilli: Long, unit: String, toleranceBp: Int = PickCore.DEFAULT_TOLERANCE_BP): String? =
    when (PickCore.weighVerdict(orderedMilli, actualMilli, toleranceBp)) {
      PickCore.Weigh.OVER -> "بیش از حدِ مجاز؛ حداکثر ${qty(PickCore.maxWeighMilli(orderedMilli, toleranceBp), unit)}"
      PickCore.Weigh.SHORT -> "کمتر از سفارش؛ «کم» ثبت و مبلغ کم می‌شود"
      PickCore.Weigh.OK -> null
    }

  /** گذارهایی که با پرچمِ جمع‌آوری فقط از برگه می‌روند (تا کسرِ خودکار و برچسب دور زده نشوند) */
  val SHEET_ONLY: Set<String> = setOf("picking", "packed")

  /** دکمه‌های گذارِ کارتِ سفارش؛ پرچمِ جمع‌آوری روشن ⇒ بی «جمع‌آوری» و «بسته‌بندی» ِ ساده */
  fun orderActions(actions: List<String>, picking: Boolean): List<String> =
    if (picking) actions.filter { it !in SHEET_ONLY } else actions

  /** دکمهٔ برگه: فقط «قبول‌شده» یا «در حال جمع‌آوری» و فقط با `orders.manage` */
  fun canPick(status: String, canManage: Boolean): Boolean = canManage && (status == "accepted" || status == "picking")

  fun pickAction(status: String): String = if (status == "picking") "ادامهٔ جمع‌آوری" else "جمع‌آوری"

  /** پیامِ کدِ ردِ سرور (اگر سرور پیامی نفرستاد) */
  fun reject(code: String): String = when (code) {
    "wrong_item" -> "کالای اشتباه — این بارکد مالِ این قلم نیست"
    "scan_required" -> "این کالا بارکد دارد؛ اسکن کنید"
    "over_qty" -> "بیش از مقدارِ سفارش"
    "over_tolerance" -> "وزن از حدِ مجاز بیشتر است؛ کمتر بردارید"
    "line_done" -> "تکلیفِ این قلم روشن شده؛ برای دوباره «از نو» بزنید"
    "pick_closed" -> "برگهٔ این سفارش بسته است"
    "not_weighed" -> "این قلم وزنی نیست"
    else -> "پذیرفته نشد ($code)"
  }
}
