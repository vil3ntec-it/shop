package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.SetAside
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max

/**
 *  بازفرستِ opهایی که سرور **فقط** به خاطرِ شکلِ نوشتنِ عدد رد کرده بود — DATA-SAFETY §۷.
 *
 *  ── چرا ──────────────────────────────────────────────────────────────
 *  ساخت‌های پیش از `JsNumber` عددِ زیرِ ۰٫۰۰۱ یا بالای ده میلیون را نماییِ جاوا می‌نوشتند (`4.0E-4`)؛ سرور اثرِ انگشت را
 *  روی شکلِ جاوااسکریپت (`0.0004`) می‌سازد ⇒ `hash_mismatch` ⇒ op به دفترِ ردشده (`oplog-*-dropped.json`) رفت و هرگز
 *  دوباره نرفت: ردیف فقط روی همان دستگاه ماند. علتِ دوم همان پایان را داشت: `Double.toString` ِ جاوا ۱۷ و اندروید (ART)
 *  گاه رقمِ اضافه می‌نوشت (`5.6843418860808015E-14`، `0.0020`). رفعِ `JsNumber` (و `JsNumber.shortest` برای رقم‌ها) فقط
 *  opهای **تازه** را درست می‌کند؛ این‌جا همان opهای کهنه، با همان `JsNumber` (هر دو شکل).
 *
 *  ── نامزد (همه با هم) ─────────────────────────────────────────────
 *   • دلیلِ رد `hash_mismatch`، op ِ درج یا ویرایش با `op_id` ِ درست؛
 *   • اثرِ انگشتِ نوشته‌شده همان است که کاتلین روی همان بایت‌ها می‌سازد (دفترِ سالم: تنها فرقِ دو سو شکلِ عدد است)؛
 *   • نوشتنِ دوبارهٔ فیلدها با `JsNumber` شکلِ مرتب (`OpHash.canonical`) را عوض می‌کند؛
 *   • هر عددِ تازه همان متنی است که `Number#toString` ِ جاوااسکریپت برای همان مقدار می‌نویسد ([jsStable]) ⇒ اثرِ انگشتِ
 *     تازه همان است که سرور می‌سازد (سنجه: `DroppedResendTest` با آینهٔ مستقلِ `canonical` ِ سرور، و
 *     `server/test/sync-resend-dropped.test.js` با خودِ `hashOf` و push ِ واقعی).
 *  ⛔ هر دلیلِ دیگر (اجازه، کهنه، تعارض، اندازه، …) دست نمی‌خورد: نه بازفرست، نه پاک.
 *
 *  ── چه می‌رود (پس از Pull، با Snapshot ِ همان دم) ──────────────────
 *   • ردیف روی سرور حذف شده ⇒ هیچ (زنده کردنِ ردیفِ حذف‌شده نه).
 *   • سندی که صاحب در «سندهای ردشده» کنار گذاشت یا اصلاح کرد ([Settled]، `SetAside` ∪ `SetAsideMarks`) ⇒ هیچ opی روی
 *     همان ردیف‌ها، حتی بایگانی‌شده (وگرنه فاکتورِ اصلاح‌شده دو بار، یا سندِ کنارگذاشته زنده می‌شد).
 *   • سرور هیچ ردیفی ندارد ⇒ هیچ opی از هیچ دستگاهی روی آن پذیرفته نشده ⇒ همان opها **همان‌طور** و به همان ترتیب (از
 *     نخستین درج؛ ویرایشِ پیش از آن ردیفِ نیمه می‌ساخت ⇒ نه). ردیفِ پاک‌شده در همین دفتر (و نه بایگانی) ⇒ هیچ.
 *   • سرور ردیف را دارد (ویرایشِ بعدیِ پذیرفته، درجِ دوباره، دستگاهِ دیگر) ⇒ فقط فیلدی که هنوز درست است و سرور ندارد:
 *     مقدارِ همین op = مقدارِ امروزِ دفتر ≠ مقدارِ سرور، و opِ ردشدهٔ دیگری پس از آن همان فیلد را نزده؛ `null` هرگز.
 *     شمارنده (`LedgerDiff.INC_FIELDS`) فقط وقتی که «سرور + همهٔ دلتاهای نرسیده = دفتر» درست درآید، و همان سهمِ همین op
 *     به شکلِ `$inc` (درج ⇒ ویرایش). هیچ فیلدی نماند ⇒ هیچ.
 *  ⇒ مقدارِ کهنه هرگز روی تازه نمی‌نشیند و ویرایشِ دیدهٔ دستگاهِ دیگر بازنویسی نمی‌شود.
 *
 *  ── تکرار ناممکن ────────────────────────────────────────────────────
 *  همان `op_id` (سرور در `hash_mismatch` هیچ چیزی در `oplog` ننوشته بود ⇒ آزاد است) و همان `ts`: فرستادنِ دوباره
 *  «duplicate» است. دفترچهٔ `oplog-*-resend.json` ([OpQueue.resend]) هر op را یک بار تصمیم می‌گیرد؛ «queued» پیش از صف
 *  نوشته می‌شود و اگر برنامه میانِ این دو بسته شد همان op دوباره جلوی صف می‌رود. ردیفِ دفترِ ردشده فقط پس از پذیرشِ سرور
 *  پاک می‌شود؛ ردِ دوباره ⇒ «rejected» در دفترچه و همان ردیفِ کهنه سرِ جایش.
 *  ⛔ v1 فقط؛ v3 اثرِ انگشت ندارد (شرح در DATA-SAFETY §۷).
 */
object DroppedResend {

  const val REASON = "hash_mismatch"

  /** وضعِ هر op در دفترچه */
  const val QUEUED = "queued"
  const val ACCEPTED = "accepted"
  const val SKIPPED = "skipped"
  const val REJECTED = "rejected"

  private val OP_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

  /** ردیفِ [at] ِ دفترِ ردشده با op ِ عدددرست‌شده */
  data class Candidate(val at: Int, val op: SyncOp)

  /** [send] null ⇒ نمی‌رود، با [why] */
  data class Decision(val opId: String, val send: SyncOp?, val why: String)

  /** تصمیمِ نشستهٔ «کنار بگذار»/«اصلاح و ثبت»: شناسهٔ opها و ردیف‌هایشان (`SetAside.settledOps` ∪ `SetAsideMarks`) */
  data class Settled(val ops: Set<String> = emptySet(), val rows: Set<RestoreMerge.Row> = emptySet())

  /** [Settled] از دفتر (`ShopData.setAside`، opها و ردیف‌های اصلی) و نشانِ بیرون از دفتر ([SetAsideMarks]، فقط شناسه) */
  fun settled(d: ShopData, marks: SetAsideMarks?): Settled = Settled(
    SetAside.settledOps(d) + marks?.read()?.ops.orEmpty(),
    d.setAside.flatMapTo(HashSet()) { doc ->
      (doc.ops.map { RestoreMerge.Row(str(it, "table"), str(it, "row_id")) } + RestoreMerge.Row(doc.table, doc.rowId))
        .filter { it.table.isNotEmpty() && it.id.isNotEmpty() }
    },
  )

  /** شبکه، بایگانی و صندوقِ [run] — آزمون‌ها ساختگی می‌دهند */
  class Io(
    val snapshot: suspend () -> JsonObject,
    /** ۴۰۴ یا هر شکست ⇒ پرتاب (نبودنِ پاسخ «حذف نشده» نیست) */
    val deleted: suspend (List<RestoreMerge.Row>) -> Set<RestoreMerge.Row>,
    val archived: () -> Map<String, Set<String>>,
    val settled: () -> Settled = { Settled() },
  )

  /**
   *  یک دور: «queued» ِ بیرون از صف دوباره، نامزدهای تازه با Snapshot ⇒ تصمیم ⇒ دفترچه ⇒ جلوی صف. صدازننده قفلِ موتور را
   *  دارد و پس از Pull صدا می‌زند. شکستِ شبکه ⇒ هیچ تصمیمی (دورِ بعدی که دفترِ ردشده عوض شد، یا باز شدنِ بعدیِ برنامه).
   *  @param local دفترِ همین حالا (`LedgerDiff.toJson`)
   *  @return چند op جلوی صف آمد
   */
  suspend fun run(queue: OpQueue, io: Io, local: () -> JsonObject, now: Long): Int {
    if (!queue.resendDue()) return 0
    val again = queue.resendRestore()
    val done = queue.resendRecord().mapNotNullTo(HashSet()) { str(it, "op_id").ifEmpty { null } }
    val book = queue.dropped()
    val cands = candidates(book, done)
    if (cands.isEmpty()) { queue.resendChecked(); return again }
    val decisions = try {
      val snapshot = io.snapshot()
      val ask = cands.map { RestoreMerge.Row(it.op.table, it.op.rowId) }.distinct().filter { serverRow(snapshot, it) == null }
      val gone = if (ask.isEmpty()) emptySet() else io.deleted(ask)
      plan(cands, book, queue.all(), local(), io.archived(), snapshot, gone, io.settled())
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Throwable) {
      queue.resendChecked()
      return again
    }
    val sent = queue.resend(decisions, now)
    queue.resendChecked()
    return again + sent
  }

  /* ---------------- نامزد ---------------- */

  /** نامزدهای [book] به ترتیبِ دفتر — هر `op_id` یک بار، جز آنچه دفترچه پیش‌تر تصمیم گرفته ([done]) */
  fun candidates(book: List<JsonObject>, done: Set<String>): List<Candidate> {
    val seen = HashSet<String>()
    val out = ArrayList<Candidate>()
    book.forEachIndexed { i, e ->
      val op = candidate(e) ?: return@forEachIndexed
      if (op.opId !in done && seen.add(op.opId)) out += Candidate(i, op)
    }
    return out
  }

  /** ردیفِ دفترِ ردشده ⇒ همان op با عددِ جاوااسکریپتی، یا null اگر نامزد نیست (شرح سرِ پرونده) */
  fun candidate(entry: JsonObject): SyncOp? {
    if (str(entry, "reason") != REASON) return null
    val raw = entry["op"] as? JsonObject ?: return null
    val op = SyncOp.fromJson(raw) ?: return null
    if (op.type == SyncOp.DELETE || !OP_ID.matches(op.opId)) return null
    val fields = op.fields ?: return null
    if (str(raw, "hash") != op.hash) return null
    val fixed = LedgerDiff.jsNumbers(fields) as? JsonObject ?: return null
    if (OpHash.canonical(fixed) == OpHash.canonical(fields)) return null
    if (!numbers(fixed).all(::jsStable)) return null
    return op.copy(fields = fixed, row = null)
  }

  /**
   *  متنِ عدد همان است که جاوااسکریپت برای همان مقدار می‌نویسد؟ قالب از `JsNumber.text` و رقم‌ها **کوتاه‌ترینِ** رفت‌وبرگشتی
   *  (هیچ گردکردنِ کوتاه‌تری همان `Double` را نمی‌دهد) — `Double.toString` ِ زمان‌اجرای کهنه گاه رقمِ زیادی می‌نوشت.
   */
  fun jsStable(text: String): Boolean {
    val d = text.toDoubleOrNull() ?: return false
    if (d.isNaN() || d.isInfinite() || JsNumber.text(text) != text) return false
    val t = text.removePrefix("-")
    val at = t.indexOfFirst { it == 'e' || it == 'E' }
    val mant = if (at >= 0) t.substring(0, at) else t
    val exp = if (at >= 0) t.substring(at + 1).removePrefix("+").toIntOrNull() ?: return false else 0
    val dot = mant.indexOf('.')
    val whole = if (dot >= 0) mant.substring(0, dot) else mant
    val all = whole + (if (dot >= 0) mant.substring(dot + 1) else "")
    val lead = all.indexOfFirst { it != '0' }
    if (lead < 0) return true
    val digits = all.substring(lead).trimEnd('0')
    val n = whole.length + exp - lead  //  مقدار = 0.<digits> × 10^n
    //  هر عددِ p رقمیِ درونِ بازهٔ رفت‌وبرگشت یا بریدهٔ رو به پایینِ رقم‌هاست یا یکی بالاتر از آن (کنارِ توانِ ۲ بازه نامتقارن است)
    for (p in 1 until digits.length) {
      val down = digits.substring(0, p)
      if ("0.${down}e$n".toDouble() == abs(d)) return false
      val (up, carry) = bump(down)
      if ("0.${up}e${n + carry}".toDouble() == abs(d)) return false
    }
    return true
  }

  /** [digits] + ۱ در رقمِ آخر ⇒ رقم‌ها (همان طول) و یک شدنِ نما ([carry]) */
  private fun bump(digits: String): Pair<String, Int> {
    val head = digits.toCharArray()
    var i = head.size - 1
    while (i >= 0 && head[i] == '9') { head[i] = '0'; i-- }
    if (i < 0) return ("1" + head.concatToString()).substring(0, head.size) to 1
    head[i] = head[i] + 1
    return head.concatToString() to 0
  }

  private fun numbers(e: JsonElement): List<String> = when (e) {
    is JsonObject -> e.values.flatMap { numbers(it) }
    is JsonArray -> e.flatMap { numbers(it) }
    is JsonNull -> emptyList()
    is JsonPrimitive -> if (e.isString || e.content == "true" || e.content == "false") emptyList() else listOf(e.content)
  }

  /* ---------------- تصمیم ---------------- */

  /**
   *  @param book همان دفترِ ردشده (opهای دیگرِ همان ردیف)
   *  @param queued صفِ همین حالا (دلتاهای نرفته)
   *  @param local دفترِ همین حالا (`LedgerDiff.toJson`، پس از Pull)
   *  @param snapshot Snapshot ِ v1 ِ همان دم
   *  @param gone از ردیف‌های نبودِ Snapshot، آنچه سرور حذف‌شده می‌داند
   *  @param settled سندهای کنارگذاشته/اصلاح‌شدهٔ صندوق — ردیفِ هر op ِ آن (از خودش یا از [book]) دیگر نمی‌رود
   */
  fun plan(
    cands: List<Candidate>,
    book: List<JsonObject>,
    queued: List<SyncOp>,
    local: JsonObject,
    archived: Map<String, Set<String>>,
    snapshot: JsonObject,
    gone: Set<RestoreMerge.Row>,
    settled: Settled = Settled(),
  ): List<Decision> {
    val mine = cands.mapTo(HashSet()) { it.op.opId }
    val setAside = settled.rows + book.mapNotNull { e ->
      (e["op"] as? JsonObject)?.let { SyncOp.fromJson(it) }?.takeIf { it.opId in settled.ops }?.let { RestoreMerge.Row(it.table, it.rowId) }
    }
    val out = ArrayList<Pair<Int, Decision>>()
    for ((row, group) in cands.groupBy { RestoreMerge.Row(it.op.table, it.op.rowId) }) {
      val decided = when {
        row.table !in LedgerDiff.COLLECTIONS -> group.map { skip(it, "unknown_table") }
        row in setAside -> group.map { skip(it, "set_aside") }
        row in gone -> group.map { skip(it, "deleted_on_server") }
        else -> {
          val server = serverRow(snapshot, row)
          val here = localRow(local, row)?.let { LedgerDiff.jsNumbers(it) as? JsonObject }
          when {
            server == null && here == null && row.id !in archived[row.table].orEmpty() -> group.map { skip(it, "deleted_here") }
            server == null -> replay(group)
            here == null -> group.map { skip(it, "not_in_ledger") }
            else -> {
              val waiting = queued.filter { it.table == row.table && it.rowId == row.id }
              merge(group, row, server, here, others(book, row, mine + waiting.map { it.opId }), waiting)
            }
          }
        }
      }
      out += group.map { it.at }.zip(decided)
    }
    return out.sortedBy { it.first }.map { it.second }
  }

  /** سرور ردیف را ندارد: همان opها از نخستین درج، همان‌طور */
  private fun replay(group: List<Candidate>): List<Decision> {
    val first = group.indexOfFirst { it.op.type == SyncOp.INSERT }
    return group.mapIndexed { i, c ->
      if (first < 0 || i < first) skip(c, "no_row_on_server") else Decision(c.op.opId, c.op, "replay")
    }
  }

  /** opهای دیگرِ دفترِ ردشده روی همان ردیف (نه نامزدها و نه آنچه در صف است) ⇒ (جای دفتر، op) */
  private fun others(book: List<JsonObject>, row: RestoreMerge.Row, mine: Set<String>): List<Pair<Int, SyncOp>> =
    book.mapIndexedNotNull { i, e ->
      val op = (e["op"] as? JsonObject)?.let { SyncOp.fromJson(it) } ?: return@mapIndexedNotNull null
      if (op.table != row.table || op.rowId != row.id || op.opId in mine) null else i to op
    }

  /** سرور ردیف را دارد: فقط فیلدِ هنوز درست که سرور ندارد (شرح سرِ پرونده) */
  private fun merge(
    group: List<Candidate>,
    row: RestoreMerge.Row,
    server: JsonObject,
    here: JsonObject,
    others: List<Pair<Int, SyncOp>>,
    queued: List<SyncOp>,
  ): List<Decision> {
    //  شمارنده: «سرور + همهٔ دلتاهای نرسیده (همین نامزدها، ردشده‌های دیگر، صف) = دفتر» ⇒ سهمِ هر نامزد
    val incOk = HashSet<String>()
    val incFields = group.flatMap { c -> c.op.fields.orEmpty().keys.filter { LedgerDiff.isInc(row.table, it) } }.toSet()
    for (f in incFields) {
      val parts = group.map { share(it.op, f, base = true) } + others.map { share(it.second, f) } + queued.map { share(it, f) }
      if (parts.any { it == null }) continue
      val now = num(here[f]) ?: continue
      val was = if (f in server) num(server[f]) ?: continue else 0.0
      val sum = was + parts.sumOf { it!! }
      if (abs(sum - now) <= 1e-9 * max(1.0, max(abs(sum), abs(now)))) incOk += f
    }
    return group.map { c ->
      val later = others.filter { it.first > c.at }.flatMap { it.second.fields.orEmpty().keys }.toSet()
      val keep = LinkedHashMap<String, JsonElement>()
      var inc = false
      for ((f, v) in c.op.fields.orEmpty()) {
        val d = if (f in incFields) share(c.op, f, base = true) else null
        if (d != null) {
          if (f in incOk && (d != 0.0 || f !in server)) { keep[f] = buildJsonObject { put("\$inc", JsNumber.of(d)) }; inc = true }
          continue
        }
        if (v is JsonNull || f in later) continue
        val want = OpHash.canonical(v)
        if (server[f]?.let { OpHash.canonical(it) } == want) continue
        if (here[f]?.let { OpHash.canonical(it) } != want) continue
        keep[f] = v
      }
      if (keep.isEmpty()) skip(c, "nothing_left")
      else Decision(c.op.opId, c.op.copy(type = if (inc) SyncOp.UPDATE else c.op.type, fields = JsonObject(keep)), "merge")
    }
  }

  /**
   *  سهمِ [op] در شمارندهٔ [f]: ویرایش ⇒ `$inc`؛ نزده ⇒ ۰؛ درجِ همین نامزد ([base]) ⇒ مقدارش (پایهٔ ردیفِ نیمهٔ سرور ۰ بود)؛
   *  هر مقدارِ مطلقِ دیگر ⇒ null (نامعلوم ⇒ آن شمارنده نمی‌رود)
   */
  private fun share(op: SyncOp, f: String, base: Boolean = false): Double? {
    val v = op.fields?.get(f) ?: return 0.0
    if (op.type == SyncOp.INSERT) return if (base) num(v) else null
    return num((v as? JsonObject)?.get("\$inc"))
  }

  private fun num(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

  private fun skip(c: Candidate, why: String) = Decision(c.op.opId, null, why)

  private fun str(o: JsonObject, k: String): String = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

  private fun serverRow(snapshot: JsonObject, row: RestoreMerge.Row): JsonObject? =
    ((snapshot["tables"] as? JsonObject)?.get(row.table) as? JsonArray)?.firstNotNullOfOrNull { r ->
      val o = r as? JsonObject
      if ((o?.get("id") as? JsonPrimitive)?.content == row.id) o?.get("data") as? JsonObject ?: JsonObject(emptyMap()) else null
    }

  private fun localRow(local: JsonObject, row: RestoreMerge.Row): JsonObject? =
    (local[row.table] as? JsonArray)?.firstNotNullOfOrNull { r ->
      val o = r as? JsonObject
      if ((o?.get("id") as? JsonPrimitive)?.content == row.id) o else null
    }

  private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
}
