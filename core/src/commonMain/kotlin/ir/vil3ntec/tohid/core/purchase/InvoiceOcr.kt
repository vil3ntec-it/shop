package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.text.TextNormalizer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 *  PUR-12 / AI-10 — «از عکسِ فاکتور»: خط‌های متنِ عکسِ فاکتورِ کاغذیِ تأمین‌کننده (Tesseract ِ سرورِ خودِ صاحب،
 *  `server/src/routes/purchase-ocr.js`) ⇒ ردیف‌های نامزد {نام، مقدار، فی، جمعِ ردیف} ⇒ تطبیق با کالاهای دفتر ⇒ بازبینی و
 *  تأییدِ **ردیف‌به‌ردیف**. منطقِ خالص (commonMain): گوشی، کامپیوتر و آیفون همین را می‌خوانند.
 *
 *  ⛔ هیچ مقداری ساخته نمی‌شود: مقدار و فی و جمع فقط وقتی پر می‌شوند که سه عددِ همان خط با هم بخوانند
 *     (مقدار × فی ≈ جمع، [tolerance])؛ وگرنه خالی و با نشان ([Flag]) — کاربر از متنِ خام ([Row.raw]) خودش می‌نویسد.
 *  ⛔ هیچ کالایی ساخته نمی‌شود: تطبیق فقط پیشنهاد است ([Match])؛ ردیفِ بی کالا تأییدشدنی نیست.
 *  ⛔ هیچ ردیفی خودبه‌خود تأیید نمی‌شود ([Decision.PENDING])؛ فقط ردیف‌های [Decision.CONFIRMED] به پیش‌نویسِ فاکتور می‌روند
 *     ([accepted]) و ثبت همان راهِ همیشگیِ فرم است (`PurchaseBook.record`) — نه انبار، نه بدهی پیش از آن.
 */
object InvoiceOcr {

  const val ROOT = "/shop/purchase-ocr"
  const val CONFIG = "$ROOT/config"

  /** سقفِ ردیف‌های بازبینی — همان سقفِ ردیف‌های فرمِ خریدِ گوشی */
  const val MAX_ROWS = 50

  /** زیرِ این اطمینان (۰ تا ۱۰۰) خط «کم‌رنگ خوانده شد» */
  const val LOW_CONF = 60

  /** آستانه‌های تطبیقِ نام */
  const val STRONG = 0.8
  const val WEAK = 0.5
  const val SHOWN = 0.35

  /* ══════════════════════════ پاسخِ سرور ══════════════════════════ */

  /** `GET /config` — هیچ چیز «روشن» فرض نمی‌شود */
  data class Config(
    val enabled: Boolean = false,
    /** نقش اجازهٔ نوشتنِ خرید دارد (`stock.adjust`) */
    val permitted: Boolean = false,
    /** اشتراکِ فعال یا آزمایشی */
    val paid: Boolean = false,
    /** Tesseract با دادهٔ فارسی روی سرور هست */
    val available: Boolean = false,
    /** بسته‌هایی که صاحبِ سرور باید نصب کند */
    val missing: List<String> = emptyList(),
  ) {
    /** دکمهٔ «از عکسِ فاکتور» دیده شود؟ (اشتراک و نصب نبودن را خودِ برگه می‌گوید، دکمه پنهان نمی‌شود) */
    val visible: Boolean get() = enabled && permitted
  }

  private fun bool(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true
  private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

  fun config(o: JsonObject): Config = Config(
    enabled = bool(o, "enabled"),
    permitted = bool(o, "permitted"),
    paid = bool(o, "paid"),
    available = bool(o, "available"),
    missing = (o["missing"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }
      .filter { Regex("^[a-z0-9.+-]{1,40}$").matches(it) }.take(4),
  )

  /** یک خطِ خوانده‌شده؛ [conf] اطمینانِ میانگینِ واژه‌ها (۰ تا ۱۰۰) */
  data class Line(val text: String, val conf: Int = 100)

  /** `POST` ⇒ خط‌ها (بیش از ۴۰۰ یا بلندتر از ۳۰۰ نویسه کنار) */
  fun lines(o: JsonObject): List<Line> = (o["lines"] as? JsonArray).orEmpty().mapNotNull { e ->
    val x = e as? JsonObject ?: return@mapNotNull null
    val text = str(x, "text")?.trim()?.take(300)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    Line(text, ((x["conf"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, 100))
  }.take(400)

  /* ══════════════════════════ عدد ══════════════════════════ */

  /** رقم‌های فارسی (۰–۹) و عربی (٠–٩) ⇒ لاتین؛ بقیه همان */
  fun latinDigits(s: String): String {
    val b = StringBuilder(s.length)
    for (c in s) b.append(
      when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
      },
    )
    return b.toString()
  }

  private const val THOUSANDS = ",٬،'"
  private const val SEPS = ",٬،'.٫/"

  /**
   *  یک تکهٔ عددی (رقم‌های لاتین و جداکننده‌ها) ⇒ مقدارهای ممکن. با تحمل ولی بی حدس:
   *  «۱٬۲۵۰»، «1,250»، «1.250.000» ⇒ هزارگان؛ «۲٫۵»، «2,5»، «۲/۵» ⇒ اعشار؛ «1,250.50» و «1.250,50» ⇒ هر دو؛
   *  «1.250» دو معنا دارد (هزار و دویست و پنجاه یا یک و ربع) ⇒ هر دو، تا سنجهٔ جمع یکی را برگزیند؛ شکلِ نادرست ⇒ تهی.
   */
  fun numberValues(raw: String): List<Double> {
    val t = latinDigits(raw).trim()
    if (t.isEmpty() || !t[0].isAsciiDigit() || !t.last().isAsciiDigit()) return emptyList()
    val groups = ArrayList<String>()
    val seps = ArrayList<Char>()
    val cur = StringBuilder()
    for (c in t) {
      when {
        c.isAsciiDigit() -> cur.append(c)
        c in SEPS -> { if (cur.isEmpty()) return emptyList(); groups += cur.toString(); cur.clear(); seps += c }
        else -> return emptyList()
      }
    }
    groups += cur.toString()
    if (groups.any { it.isEmpty() }) return emptyList()
    fun whole(gs: List<String>) = gs.joinToString("").toDoubleOrNull()
    fun dec(i: String, f: String) = "$i.$f".toDoubleOrNull()
    fun thousandsOk(gs: List<String>) = gs.first().length in 1..3 && gs.drop(1).all { it.length == 3 }
    val out: List<Double?> = when {
      seps.isEmpty() -> listOf(whole(groups))
      seps.distinct().size == 1 -> {
        val s = seps[0]
        when {
          s == '٫' || s == '/' -> if (groups.size == 2) listOf(dec(groups[0], groups[1])) else emptyList()
          s == '.' -> when {
            groups.size == 2 && groups[1].length == 3 && groups[0].length in 1..3 -> listOf(whole(groups), dec(groups[0], groups[1]))
            groups.size == 2 -> listOf(dec(groups[0], groups[1]))
            thousandsOk(groups) -> listOf(whole(groups))
            else -> emptyList()
          }
          //  , ٬ ، ' — هزارگان؛ «,» با یک یا دو رقمِ پایانی (2,5) اعشارِ اروپایی است
          s == ',' && groups.size == 2 && groups[1].length in 1..2 -> listOf(dec(groups[0], groups[1]))
          thousandsOk(groups) -> listOf(whole(groups))
          else -> emptyList()
        }
      }
      else -> {
        //  دو گونه: آخرین جداکننده (یک بار، در پایان) اعشار، بقیه هزارگان
        val last = seps.last()
        val rest = seps.dropLast(1)
        val restOk = rest.all { it == rest[0] } && rest[0] != last && (rest[0] in THOUSANDS || rest[0] == '.')
        if (!restOk || last !in ".٫,") emptyList()
        else {
          val ints = groups.dropLast(1)
          if (thousandsOk(ints)) listOf(dec(ints.joinToString(""), groups.last())) else emptyList()
        }
      }
    }
    return out.filterNotNull().filter { !it.isNaN() && it >= 0 && it < 1e12 }.distinct()
  }

  private fun Char.isAsciiDigit() = this in '0'..'9'

  /** متنِ کادر برای یک مقدار: «1250»، «2.5»، «0.125» (حداکثر سه رقمِ اعشار، بی نمادِ علمی) */
  fun text(v: Double): String {
    val r = round(v * 1000) / 1000
    val whole = floor(r).toLong()
    val frac = round((r - whole) * 1000).toLong()
    if (frac == 0L) return whole.toString()
    return "$whole." + frac.toString().padStart(3, '0').trimEnd('0')
  }

  private val PLAIN = Regex("^\\d+(\\.\\d+)?$")

  /**
   *  نوشتهٔ کاربر در کادرِ بازبینی ⇒ عدد؛ نادرست یا دومعنا ⇒ null. این‌جا «.» و «٫» همیشه اعشارند (صفحه‌کلیدِ عددی)؛
   *  شکل‌های دیگر همان [numberValues] با یک معنای تنها.
   */
  fun parseTyped(s: String): Double? {
    val t = latinDigits(s.trim()).replace('٫', '.')
    if (PLAIN.matches(t)) return t.toDoubleOrNull()?.takeIf { it < 1e12 }
    return numberValues(t).singleOrNull()
  }

  /** مرزِ «می‌خواند»: نیم‌افغانیِ گِرد کردن، یا یک‌هزارمِ جمع (گِرد کردن به ده/صدِ فاکتورهای بزرگ) */
  fun tolerance(total: Double): Double = max(0.5, total * 0.001)

  /* ══════════════════════════ خط ⇒ تکه ══════════════════════════ */

  internal enum class Kind { NUM, WORD, PUNCT }

  internal class Tok(
    val text: String, val kind: Kind, val values: List<Double> = emptyList(), val percent: Boolean = false, val date: Boolean = false,
    /** جای تکه در خط — دو تکهٔ چسبیده («3in1») در نام هم چسبیده می‌مانند */
    val start: Int = 0, val end: Int = 0,
  )

  private fun isLetter(c: Char) = c.isLetter() || c == '‌' || c in 'ً'..'ٟ' || c == 'ٰ' || c == 'ـ'
  private fun isDigit(c: Char) = c in '0'..'9' || c in '۰'..'۹' || c in '٠'..'٩'
  private val INVISIBLE = Regex("[‎‏‪-‮⁦-⁩﻿]")
  private val DATE = Regex("^(\\d{2,4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,4})$")

  /** خط ⇒ تکه‌ها به ترتیبِ منطقی: عدد (با جداکننده‌های میانش)، واژه، و نشانه */
  internal fun tokens(line: String): List<Tok> {
    val s = line.replace(INVISIBLE, "")
    val out = ArrayList<Tok>()
    var i = 0
    while (i < s.length) {
      val c = s[i]
      when {
        isDigit(c) -> {
          var j = i + 1
          while (j < s.length && (isDigit(s[j]) || (s[j] in SEPS && j + 1 < s.length && isDigit(s[j + 1])))) j++
          val raw = s.substring(i, j)
          var k = j
          while (k < s.length && s[k] == ' ') k++
          val pct = k < s.length && (s[k] == '%' || s[k] == '٪')
          val lat = latinDigits(raw)
          val date = DATE.matches(lat) && lat.count { it == '/' || it == '-' || it == '.' } == 2 && lat.count { it == '.' } != 2
          out += Tok(raw, Kind.NUM, if (date) emptyList() else numberValues(raw), pct, date, i, j)
          i = j
        }
        isLetter(c) -> {
          var j = i + 1
          while (j < s.length && isLetter(s[j])) j++
          out += Tok(s.substring(i, j), Kind.WORD, start = i, end = j)
          i = j
        }
        c.isWhitespace() -> i++
        else -> { out += Tok(c.toString(), Kind.PUNCT, start = i, end = i + 1); i++ }
      }
    }
    return out
  }

  private fun key(s: String) = TextNormalizer.searchKey(s)

  /** واژه‌های راهنما با همان کلیدِ نام‌ها («آدرس» = «ادرس»، «ي» = «ی») */
  private fun keys(vararg w: String): Set<String> = w.mapTo(HashSet()) { key(it) }

  private val QTY_WORDS = keys("تعداد", "مقدار", "qty", "quantity")
  private val PRICE_WORDS = keys("فی", "قیمت", "نرخ", "price", "rate")
  private val HEAD_WORDS = QTY_WORDS + PRICE_WORDS + keys("ردیف", "شرح", "کالا", "جنس", "نام", "مبلغ", "جمع", "مجموعی", "واحد", "item", "description", "amount", "total", "no")
  private val TOTAL_WORDS = keys("جمع", "مجموع", "مجموعه", "کل", "total", "subtotal", "sum", "پرداخت", "قابل", "مانده", "باقی")
  private val SKIP_WORDS = keys("تخفیف", "مالیات", "discount", "tax", "vat", "پیش", "بیعانه")
  private val META_WORDS = keys(
    "تاریخ", "شماره", "فاکتور", "صورتحساب", "تلفن", "تماس", "موبایل", "آدرس", "نشانی", "امضا", "مهر", "خریدار", "فروشنده",
    "date", "invoice", "tel", "phone", "address", "mobile", "no.",
  )
  private val UNIT_WORDS = keys("عدد", "دانه", "بسته", "کارتن", "جعبه", "قوطی", "پاکت", "pcs", "pc", "ctn", "box")
  private val CURRENCY_WORDS = keys("افغانی", "afn", "af", "؋", "ریال", "تومان")

  private fun words(t: List<Tok>) = t.filter { it.kind == Kind.WORD }.map { key(it.text) }

  /** سرخطِ جدول؟ ⇒ ترتیبِ ستون: «مقدار» پیش از «فی»؟ (null = نامعلوم) */
  private fun header(t: List<Tok>): Boolean? {
    val ws = words(t)
    if (ws.count { it in HEAD_WORDS } < 2 || t.count { it.kind == Kind.NUM && it.values.isNotEmpty() } > 1) return null
    val q = ws.indexOfFirst { it in QTY_WORDS }
    val p = ws.indexOfFirst { it in PRICE_WORDS }
    return if (q >= 0 && p >= 0) q < p else null
  }

  private fun isHeader(t: List<Tok>): Boolean {
    val ws = words(t)
    return ws.count { it in HEAD_WORDS } >= 2 && t.count { it.kind == Kind.NUM && it.values.isNotEmpty() } <= 1
  }

  /* ══════════════════════════ ردیف ══════════════════════════ */

  enum class Flag {
    /** سه عددِ خط با هم نمی‌خوانند (یا عددی نیست) ⇒ مقدار/فی/جمع خالی؛ از متنِ خام خودتان بنویسید */
    MISMATCH,
    /** بیش از یک جوابِ درست با مقدارهای متفاوت ⇒ خالی */
    AMBIGUOUS,
    /** جای «مقدار» و «فی» از روی ستونِ سرخط معلوم نبود */
    ORDER_GUESSED,
    /** خط کم‌رنگ خوانده شد */
    LOW_CONFIDENCE,
    /** کالایی با نامِ نزدیک پیدا نشد */
    NO_MATCH,
    /** نام فقط شبیه است */
    WEAK_MATCH,
  }

  /** یک ردیفِ نامزد از متن — هر مقدار یا خوانده‌شدهٔ مطمئن است یا null */
  data class Candidate(
    val raw: String,
    val name: String,
    val qty: Double?,
    val price: Double?,
    val total: Double?,
    val flags: Set<Flag>,
    val conf: Int,
    /** بارکدِ چاپ‌شده روی همان خط (۸ تا ۱۴ رقم)، اگر بود */
    val barcode: String? = null,
  )

  data class Parsed(
    val rows: List<Candidate>,
    /** «جمعِ کل» ِ چاپ‌شده — فقط وقتی خطِ جمع یک عدد داشت */
    val printedTotal: Double?,
    /** ردیف‌هایی که از [MAX_ROWS] بیرون ماندند */
    val dropped: Int = 0,
  )

  private class Triple3(val q: Int, val p: Int, val t: Int, val qv: Double, val pv: Double, val tv: Double, val score: Int)

  /** بهترین سه‌تاییِ «مقدار × فی ≈ جمع» در عددهای [nums] (اندیس در فهرستِ عددها)؛ null با دلیل */
  private fun triple(nums: List<Tok>, qtyFirst: Boolean?): Pair<Triple3?, Flag?> {
    //  ستون‌های عددیِ یک خط کم‌اند؛ خطِ پرعدد (شمارهٔ تلفن، بارکدهای پیاپی) فقط دوازده‌تای پایانی
    val idx = nums.indices.filter { nums[it].values.isNotEmpty() && !nums[it].percent }.takeLast(12)
    if (idx.size < 3) return null to Flag.MISMATCH
    val found = ArrayList<Triple3>()
    val last = idx.last()
    for (t in idx) for (a in idx) for (b in idx) {
      if (a >= b || a == t || b == t) continue
      for (tv in nums[t].values) for (av in nums[a].values) for (bv in nums[b].values) {
        if (tv <= 0 || av <= 0 || bv <= 0) continue
        if (abs(av * bv - tv) > tolerance(tv)) continue
        //  ترتیب: جمع پس از هر دو (ستونِ آخر در ترتیبِ منطقی)، سه ستونِ پایانیِ خط، و نزدیکی
        var score = 0
        if (t == last) score += 4
        if (t > a && t > b) score += 2
        val tail = idx.takeLast(3)
        if (a in tail && b in tail && t in tail) score += 2
        if (b == a + 1) score += 1
        if (tv >= av && tv >= bv) score += 1
        //  مقدار/فی: ستونِ سرخط؛ وگرنه ترتیبِ منطقیِ خط (اولی مقدار)
        val qFirst = qtyFirst ?: true
        found += if (qFirst) Triple3(a, b, t, av, bv, tv, score) else Triple3(b, a, t, bv, av, tv, score)
      }
    }
    if (found.isEmpty()) return null to Flag.MISMATCH
    val best = found.maxOf { it.score }
    val top = found.filter { it.score == best }
    val distinct = top.map { Triple(it.qv, it.pv, it.tv) }.distinct()
    if (distinct.size > 1) return null to Flag.AMBIGUOUS
    //  همان مقدارها از تکه‌های دیگر (دو «۱») ⇒ آن‌که دیرتر است (ستونِ مقدار، نه شمارهٔ ردیف)
    return top.maxBy { it.q + it.p + it.t } to null
  }

  private fun lineKind(t: List<Tok>): String {
    val ws = words(t).toSet()
    val joined = words(t).joinToString("")
    return when {
      ws.any { it in SKIP_WORDS } -> "skip"
      ws.any { it in TOTAL_WORDS } || joined.contains("جمعکل") || joined.contains("مبلغکل") -> "total"
      t.any { it.date } || ws.any { it in META_WORDS } -> "meta"
      else -> "row"
    }
  }

  /**
   *  خط‌ها ⇒ ردیف‌های نامزد. سرخط (برای ترتیبِ ستون)، جمعِ چاپی، تخفیف/مالیات و خط‌های تاریخ/شماره/نشانی کنار می‌روند؛
   *  خطِ فقط‌نام که خطِ بعدش فقط عدد است یکی می‌شود (شکستنِ خط در عکس). هر خطِ دیگری با دست‌کم یک واژه و یک عدد ردیف است.
   */
  fun parse(lines: List<Line>): Parsed {
    var qtyFirst: Boolean? = null
    var printed: Double? = null
    val rows = ArrayList<Candidate>()
    var dropped = 0
    var pending: Line? = null
    for (raw in lines) {
      val toks0 = tokens(raw.text)
      //  خطِ فقط‌نام + خطِ فقط‌عدد ⇒ یکی
      val joined = pending?.let { p ->
        if (toks0.none { it.kind == Kind.WORD } && toks0.any { it.kind == Kind.NUM }) Line("${p.text} ${raw.text}", min(p.conf, raw.conf)) else null
      }
      val line = joined ?: raw
      val toks = if (joined != null) tokens(joined.text) else toks0
      pending = null
      if (isHeader(toks)) { header(toks)?.let { qtyFirst = it }; continue }
      val hasWord = toks.any { it.kind == Kind.WORD }
      val nums = toks.filter { it.kind == Kind.NUM }
      when (lineKind(toks)) {
        "skip" -> continue
        "total" -> {
          val vals = nums.filter { it.values.size == 1 && !it.percent }
          if (vals.size == 1 && printed == null) printed = vals[0].values[0]
          continue
        }
        "meta" -> if (triple(nums, qtyFirst).first == null) continue
      }
      if (!hasWord) continue
      if (nums.none { it.values.isNotEmpty() }) { pending = line; continue }
      val c = candidate(line, toks, qtyFirst) ?: continue
      if (rows.size >= MAX_ROWS) { dropped++; continue }
      rows += c
    }
    return Parsed(rows, printed, dropped)
  }

  private fun candidate(line: Line, toks: List<Tok>, qtyFirst: Boolean?): Candidate? {
    val nums = toks.filter { it.kind == Kind.NUM }
    val (tri, why) = triple(nums, qtyFirst)
    val used = if (tri == null) emptySet() else setOf(nums[tri.q], nums[tri.p], nums[tri.t])
    //  نام: از نخستین تا آخرین واژه (عددِ میانش مثلِ «۵ لیتری» می‌ماند)، بی عددهای سه‌تایی، بی واحد/پول در دو سر
    val first = toks.indexOfFirst { it.kind == Kind.WORD }
    var lastW = toks.indexOfLast { it.kind == Kind.WORD }
    if (first < 0) return null
    //  عددِ چسبیده به آخرین واژه («3in1») هنوز نام است
    while (lastW + 1 < toks.size && toks[lastW + 1].kind == Kind.NUM && toks[lastW + 1].start == toks[lastW].end && toks[lastW + 1] !in used) lastW++
    val span = toks.subList(first, lastW + 1).filter { it !in used && it.kind != Kind.PUNCT }.toMutableList()
    while (span.size > 1 && key(span.last().text) in (UNIT_WORDS + CURRENCY_WORDS)) span.removeAt(span.lastIndex)
    while (span.size > 1 && key(span.first().text) in CURRENCY_WORDS) span.removeAt(0)
    val name = buildString {
      span.forEachIndexed { k, t -> if (k > 0 && span[k - 1].end != t.start) append(' '); append(t.text) }
    }.trim()
    if (TextNormalizer.compareKey(name).count { it.isLetter() } < 2) return null
    val barcode = nums.firstOrNull { it !in used && latinDigits(it.text).let { d -> d.length in 8..14 && d.all { ch -> ch in '0'..'9' } } }
      ?.let { latinDigits(it.text) }
    val flags = LinkedHashSet<Flag>()
    if (why != null) flags += why
    if (tri != null && qtyFirst == null) flags += Flag.ORDER_GUESSED
    if (line.conf < LOW_CONF) flags += Flag.LOW_CONFIDENCE
    return Candidate(
      raw = line.text.replace(INVISIBLE, "").trim(), name = name,
      qty = tri?.qv, price = tri?.pv, total = tri?.tv, flags = flags, conf = line.conf, barcode = barcode,
    )
  }

  /* ══════════════════════════ تطبیقِ کالا ══════════════════════════ */

  /** یک کالای دفتر برای تطبیق */
  data class CatalogItem(val id: String, val name: String, val barcodes: List<String> = emptyList(), val purchasePrice: Double = 0.0)

  data class Match(val id: String, val name: String, val score: Double)

  /** نمایهٔ کالاها — یک بار برای هر دفتر؛ کلیدِ نام همان `TextNormalizer` ِ جست‌وجو (ي/ی، ك/ک، نیم‌فاصله، رقم، فاصله) */
  class Index(items: List<CatalogItem>) {
    internal class Entry(val item: CatalogItem, val key: String, val grams: Set<String>, val words: Set<String>)
    internal val entries: List<Entry> = items.map { entry(it) }
    internal val byGram: Map<String, List<Int>> = HashMap<String, MutableList<Int>>().also { m ->
      entries.forEachIndexed { i, e -> for (g in e.grams) m.getOrPut(g) { ArrayList() }.add(i) }
    }
    internal val byCode: Map<String, CatalogItem> = items.flatMap { it -> it.barcodes.map { b -> latinDigits(b.trim()) to it } }.toMap()
    private val byId: Map<String, CatalogItem> = items.associateBy { it.id }
    val size: Int get() = entries.size
    fun item(id: String): CatalogItem? = byId[id]
  }

  private fun grams(k: String): Set<String> =
    if (k.length < 2) setOf(k) else (0 until k.length - 1).mapTo(HashSet()) { k.substring(it, it + 2) }

  private fun entry(it: CatalogItem): Index.Entry {
    val sk = key(it.name)
    val ck = sk.replace(" ", "")
    return Index.Entry(it, ck, grams(ck), sk.split(' ').filter { w -> w.isNotEmpty() }.toSet())
  }

  /** شباهتِ دو نام (۰ تا ۱): بیشینهٔ Dice ِ دوحرفی و هم‌پوشانیِ واژه‌ها؛ یکی درونِ دیگری (≥ ۳ حرف) هم بالا می‌برد */
  fun similarity(a: String, b: String): Double = score(entry(CatalogItem("a", a)), entry(CatalogItem("b", b)))

  private fun score(q: Index.Entry, e: Index.Entry): Double {
    if (q.key.isEmpty() || e.key.isEmpty()) return 0.0
    if (q.key == e.key) return 1.0
    val shared = q.grams.count { it in e.grams }
    val dice = 2.0 * shared / (q.grams.size + e.grams.size)
    val common = q.words.count { it in e.words }
    val words = common.toDouble() / max(q.words.size, e.words.size).coerceAtLeast(1)
    var s = max(dice, words)
    val (short, long) = if (q.key.length <= e.key.length) q.key to e.key else e.key to q.key
    if (short.length >= 3 && long.contains(short)) s = max(s, 0.6 + 0.4 * short.length / long.length)
    return min(1.0, s)
  }

  /** بهترین کالاها برای [name] (و بارکدِ چاپی، که اگر بود قطعی است) — بیشینه [limit]، از [SHOWN] به بالا */
  fun matches(name: String, index: Index, barcode: String? = null, limit: Int = 5): List<Match> {
    barcode?.let { b -> index.byCode[b]?.let { return listOf(Match(it.id, it.name, 1.0)) } }
    val q = entry(CatalogItem("?", name))
    if (q.key.isEmpty()) return emptyList()
    val hits = HashMap<Int, Int>()
    for (g in q.grams) index.byGram[g]?.forEach { hits[it] = (hits[it] ?: 0) + 1 }
    return hits.keys.asSequence()
      .map { i -> index.entries[i] }
      .map { e -> Match(e.item.id, e.item.name, score(q, e)) }
      .filter { it.score >= SHOWN }
      .sortedWith(compareByDescending<Match> { it.score }.thenBy { TextNormalizer.compareKey(it.name) }.thenBy { it.id })
      .take(limit)
      .toList()
  }

  /* ══════════════════════════ بازبینی ══════════════════════════ */

  enum class Decision { PENDING, CONFIRMED, SKIPPED }

  /** یک ردیفِ برگهٔ بازبینی — [qty] و [price] متنِ کادرند تا کاربر عوضشان کند */
  data class Row(
    val raw: String,
    val name: String,
    val flags: Set<Flag>,
    val choices: List<Match>,
    val productId: String?,
    val qty: String,
    val price: String,
    val total: Double?,
    val decision: Decision = Decision.PENDING,
  )

  data class Review(val rows: List<Row>, val printedTotal: Double?, val dropped: Int = 0) {
    val confirmed: Int get() = rows.count { it.decision == Decision.CONFIRMED }
    val pending: Int get() = rows.count { it.decision == Decision.PENDING }
  }

  /**
   *  ردیف‌های نامزد + نمایهٔ کالاها ⇒ برگهٔ بازبینی. کالا فقط پیشنهاد می‌شود (بهترین با شباهتِ ≥ [WEAK])؛ جای مقدار و
   *  فیِ حدسی فقط وقتی جابه‌جا می‌شود که قیمتِ خریدِ همان کالا آن را نشان دهد (و نشانِ حدس می‌رود).
   */
  fun review(parsed: Parsed, index: Index): Review = Review(
    rows = parsed.rows.map { c ->
      val choices = matches(c.name, index, c.barcode)
      val best = choices.firstOrNull()?.takeIf { it.score >= WEAK }
      val flags = LinkedHashSet(c.flags)
      when {
        best == null -> flags += Flag.NO_MATCH
        best.score < STRONG -> flags += Flag.WEAK_MATCH
      }
      var q = c.qty
      var p = c.price
      if (Flag.ORDER_GUESSED in flags && best != null && q != null && p != null) {
        val known = index.item(best.id)?.purchasePrice?.takeIf { it > 0 }
        if (known != null) {
          val keep = abs(p - known) / known
          val swap = abs(q - known) / known
          if (swap < keep && swap < 0.5) { val x = q; q = p; p = x }
          if (min(keep, swap) < 0.5) flags -= Flag.ORDER_GUESSED
        }
      }
      Row(c.raw, c.name, flags, choices, best?.id, q?.let(::text).orEmpty(), p?.let(::text).orEmpty(), c.total)
    },
    printedTotal = parsed.printedTotal,
    dropped = parsed.dropped,
  )

  /** تأییدشدنی: کالا انتخاب شده، مقدار > ۰، فی ≥ ۰ */
  fun canConfirm(r: Row): Boolean =
    r.productId != null && (parseTyped(r.qty) ?: 0.0) > 0 && (parseTyped(r.price) ?: -1.0) >= 0

  private fun put(v: Review, i: Int, f: (Row) -> Row): Review =
    if (i !in v.rows.indices) v else v.copy(rows = v.rows.mapIndexed { j, r -> if (j == i) f(r) else r })

  /** هر ویرایش تأییدِ پیشین را برمی‌دارد — آنچه تأیید شد همان است که دیده شد */
  fun pick(v: Review, i: Int, productId: String?): Review = put(v, i) { it.copy(productId = productId, decision = Decision.PENDING) }
  fun setQty(v: Review, i: Int, qty: String): Review = put(v, i) { it.copy(qty = qty, decision = Decision.PENDING) }
  fun setPrice(v: Review, i: Int, price: String): Review = put(v, i) { it.copy(price = price, decision = Decision.PENDING) }
  fun confirm(v: Review, i: Int): Review = put(v, i) { if (canConfirm(it)) it.copy(decision = Decision.CONFIRMED) else it }
  fun skip(v: Review, i: Int): Review = put(v, i) { it.copy(decision = Decision.SKIPPED) }
  fun undo(v: Review, i: Int): Review = put(v, i) { it.copy(decision = Decision.PENDING) }

  /** ردیفِ تأییدشده برای پیش‌نویسِ فاکتور — مقدار به واحدِ پایهٔ کالا، فی به افغانی */
  data class Accepted(val productId: String, val qty: Double, val price: Double)

  /** فقط ردیف‌های تأییدشده، به همان ترتیب */
  fun accepted(v: Review): List<Accepted> = v.rows.filter { it.decision == Decision.CONFIRMED && canConfirm(it) }.map {
    Accepted(it.productId!!, parseTyped(it.qty)!!, parseTyped(it.price)!!)
  }

  /** جمعِ ردیف‌های تأییدشده — همان گِرد کردنِ هر ردیفِ فرمِ خرید */
  fun confirmedTotal(v: Review): Double = accepted(v).sumOf { round(it.qty * it.price) }
}
