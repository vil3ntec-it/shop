package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.money.MAX_EXACT

/*
 *  ابزارهای انبارگردانی — INV-06، INV-07، INV-08 (§۶.۵، S20).
 *
 *  • `CountScan` (INV-06): هر اسکن یک واحدِ پایه به شمرده‌شدهٔ همان کالا می‌افزاید. کالای وزنی (کیلو، لیتر…)
 *    با اسکن شمرده نمی‌شود — «یک اسکن = یک کیلو» دروغ است؛ وزنش نوشته می‌شود. بارکدِ ناشناس و کالای غیرفعال
 *    چیزی را عوض نمی‌کنند و دلیلشان برمی‌گردد.
 *  • `CountBlind` (INV-08): شمارشِ کور — شمارنده «موجودیِ برنامه»، اختلاف و ارزشش را نمی‌بیند.
 *  • `CountMerge` (INV-07): چند دستگاه هم‌زمان می‌شمارند، هر کدام یک «برگه»؛ برگه‌ها جمع می‌شوند (هر دستگاه
 *    جای دیگری از دکان را شمرده است) و کالایی که در بیش از یک برگه آمده نشان داده می‌شود تا دوباره‌شماری
 *    دیده شود. برگهٔ تکراری (همان `sheetId`) جایگزینِ قبلی می‌شود، نه دو بار جمع. برگه یک متنِ سادهٔ
 *    خط‌به‌خط است تا بی اینترنت (فایل، پیام‌رسان) میانِ دستگاه‌ها برود؛ ورودی نامعتبر کامل رد می‌شود.
 *  مقدار همه‌جا به هزارم (Long). منطقِ خالص (iOS هم)؛ آزمون: `CountToolsTest` (commonTest).
 */

object CountScan {

  /** یک واحدِ پایه به هزارم */
  const val UNIT_MILLI: Long = 1000

  sealed interface Result {
    data class Counted(val productId: String, val countedMilli: Long) : Result
    data class Unknown(val code: String) : Result
    data class Weighed(val productId: String) : Result
    data class Inactive(val productId: String) : Result
  }

  /**
   *  `ownerOf(code)` ⇒ شناسهٔ کالای آن بارکد؛ `counted` شمرده‌شدهٔ فعلی (نبود ⇒ صفر).
   *  کد تمیز می‌شود: فاصلهٔ دو سر و رقمِ فارسی/عربی.
   */
  fun scan(
    code: String,
    ownerOf: (String) -> String?,
    counted: (String) -> Long?,
    weighed: (String) -> Boolean,
    active: (String) -> Boolean,
  ): Result {
    val clean = latin(code.trim())
    val id = ownerOf(clean) ?: return Result.Unknown(clean)
    if (!active(id)) return Result.Inactive(id)
    if (weighed(id)) return Result.Weighed(id)
    val now = (counted(id) ?: 0L).coerceAtLeast(0L)
    return Result.Counted(id, (now + UNIT_MILLI).coerceAtMost(MAX_EXACT))
  }

  internal fun latin(s: String): String = buildString(s.length) {
    for (c in s) append(
      when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
      },
    )
  }
}

object CountBlind {

  /** آنچه کنارِ هر ردیف دیده می‌شود؛ `null` = پنهان */
  data class Hint(val systemMilli: Long?, val diffMilli: Long?)

  fun hint(systemMilli: Long, countedMilli: Long?, blind: Boolean): Hint = when {
    blind -> Hint(null, null)
    countedMilli == null -> Hint(systemMilli, null)
    else -> Hint(systemMilli, countedMilli - systemMilli)
  }
}

object CountMerge {

  const val HEADER = "tohid-count v1"
  /** سقفِ ردیف‌های یک برگه — فایلِ اشتباه حافظه را پر نکند */
  const val MAX_LINES = 20_000

  data class Sheet(val sheetId: String, val device: String, val at: Long, val counts: Map<String, Long>)

  data class Result(
    /** جمعِ همهٔ برگه‌ها برای هر کالا */
    val counts: Map<String, Long>,
    /** کالاهایی که در بیش از یک برگه شمرده شده‌اند */
    val shared: Set<String>,
    /** برگه‌های به‌کاررفته پس از کنار گذاشتنِ تکراری‌ها، به ترتیبِ ورود */
    val sheets: List<Sheet>,
  )

  fun merge(sheets: List<Sheet>): Result {
    //  همان برگه دو بار ⇒ تازه‌ترین (`at` بزرگ‌تر؛ برابر ⇒ آخری) جای قبلی
    val byId = LinkedHashMap<String, Sheet>()
    for (s in sheets) {
      val old = byId[s.sheetId]
      if (old == null || s.at >= old.at) byId[s.sheetId] = s
    }
    val used = byId.values.toList()
    val sum = LinkedHashMap<String, Long>()
    val seen = HashMap<String, Int>()
    for (s in used) for ((id, q) in s.counts) {
      if (q < 0) continue
      sum[id] = ((sum[id] ?: 0L) + q).coerceAtMost(MAX_EXACT)
      seen[id] = (seen[id] ?: 0) + 1
    }
    return Result(sum, seen.filterValues { it > 1 }.keys, used)
  }

  fun encode(sheet: Sheet): String = buildString {
    append(HEADER).append('\n')
    append("sheet ").append(oneLine(sheet.sheetId)).append('\n')
    append("device ").append(oneLine(sheet.device)).append('\n')
    append("at ").append(sheet.at).append('\n')
    for ((id, q) in sheet.counts) append(oneLine(id)).append('\t').append(q).append('\n')
  }

  /** متنِ برگه ⇒ `Sheet`؛ هر نادرستی (سرآیند، شناسه، عدد، اندازه) ⇒ `null`، بی نیمه‌خواندن */
  fun decode(text: String): Sheet? {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.trim('﻿', ' ') }.filter { it.isNotEmpty() }
    if (lines.size < 4 || lines.size > MAX_LINES + 4 || lines[0] != HEADER) return null
    val sheetId = lines[1].removePrefix("sheet ").takeIf { lines[1].startsWith("sheet ") && it.isNotBlank() } ?: return null
    val device = lines[2].takeIf { it.startsWith("device ") }?.removePrefix("device ") ?: return null
    val at = lines[3].takeIf { it.startsWith("at ") }?.removePrefix("at ")?.toLongOrNull() ?: return null
    val counts = LinkedHashMap<String, Long>()
    for (l in lines.drop(4)) {
      val tab = l.indexOf('\t')
      if (tab <= 0) return null
      val id = l.substring(0, tab).trim()
      val q = l.substring(tab + 1).trim().toLongOrNull() ?: return null
      if (id.isEmpty() || q < 0 || q > MAX_EXACT || counts.containsKey(id)) return null
      counts[id] = q
    }
    return Sheet(sheetId.trim(), device.trim(), at, counts)
  }

  private fun oneLine(s: String): String = s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim()
}
