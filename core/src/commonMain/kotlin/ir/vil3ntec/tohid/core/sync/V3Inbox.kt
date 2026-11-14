package ir.vil3ntec.tohid.core.sync

import ir.vil3ntec.tohid.core.sync.v3.Hlc
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  SYN-07 / S28 — صندوقِ تعارضِ Sync v3 در برنامه: قراردادِ کلاینت با همان دو درِ امروزِ سرور
 *  (`GET /api/sync/v3/conflicts`، `POST /api/sync/v3/conflicts/:id/resolve` ِ `server/src/routes/sync-v3.js`؛ نیمهٔ تازهٔ
 *  سرور `lib/sync-v3-inbox.js`). این‌جا فقط خواندن و شکل دادن است — Kotlin ِ خالص، بی `java.*`، تا روی آیفون هم بماند.
 *
 *  ⛔ پرچمِ دکانِ `shop.sync_v3_inbox` (پیش‌فرض خاموش): برنامه فقط وقتی پاسخِ فهرست کلیدِ `inbox` دارد چیزی نشان می‌دهد
 *     ([Page.on])؛ «ادغام» فقط با `inbox.merge` ([Page.merge]).
 *  ⛔ سرور تنها داور است: «فعلی» = برنده (مقدارِ امروزِ سرور)، «دیگری» = بازنده؛ نگه داشتنِ فعلی `keep`، گرفتنِ دیگری
 *     `revert`، ادغام `merge` با فیلدهای گرفته‌شده از دیگری. «مالِ من / مالِ دیگری» فقط برچسب است ([owner]).
 *  ⛔ گونه‌های صندوقِ سند (`perm_denied`، `approval_*`) جای خودشان را دارند (`/shop/conflict-inbox`، [ConflictInbox]) و
 *     این‌جا نمی‌آیند ([DOC_KINDS]).
 */
object V3Inbox {
  const val ROOT = "/sync/v3/conflicts"
  const val OPEN = "open"
  const val KEPT = "kept"
  const val REVERTED = "reverted"
  const val DISMISSED = "dismissed"

  const val KEEP = "keep"
  const val REVERT = "revert"
  const val DISMISS = "dismiss"
  const val MERGE = "merge"

  /** همهٔ ردیف (`clock_skew`، `stale_after_delete`، `append_only_edit`) و حذف */
  const val WHOLE = "*"
  const val DELETE = "*delete*"
  const val SERVER_DEVICE = "server"
  const val MAX_FIELDS = 64

  val DOC_KINDS: Set<String> = setOf("approval_required", "approval_rejected", "perm_denied")
  private val ID_RE = Regex("^[A-Za-z0-9_.-]{1,80}$")
  private val FIELD_RE = Regex("^[A-Za-z0-9_]{1,64}$")

  data class Conflict(
    val id: String,
    val kind: String,
    val task: Boolean,
    val revertible: Boolean,
    val table: String,
    val rowId: String,
    val field: String,
    val winner: JsonElement?,
    val loser: JsonElement?,
    val winnerHlc: String,
    val loserHlc: String,
    val winnerDevice: String,
    val loserDevice: String,
    val status: String,
    val at: Long,
    val resolvedAt: Long?,
    val resolvedBy: String,
    val merged: List<String>,
  ) {
    val open: Boolean get() = status == OPEN
  }

  /** [on]: سرور پرچم را روشن گفت (`inbox`)؛ بی آن هیچ چیزی دیده نمی‌شود */
  data class Page(val items: List<Conflict>, val on: Boolean, val merge: Boolean)

  // ---------- خواندن ----------

  private fun str(o: JsonObject?, k: String): String = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
  private fun long(o: JsonObject?, k: String): Long? = (o?.get(k) as? JsonPrimitive)?.let { p -> p.longOrNull ?: p.contentOrNull?.toLongOrNull() }
  private fun bool(o: JsonObject?, k: String): Boolean = (o?.get(k) as? JsonPrimitive)?.booleanOrNull == true
  private fun value(o: JsonObject?, k: String): JsonElement? = o?.get(k)?.takeUnless { it is JsonNull }

  /** یک ردیفِ `shapeConflict` ِ سرور (یا `{ conflict }`)؛ شکلِ نادرست ⇒ null */
  fun parse(raw: JsonObject?): Conflict? {
    val o = (raw?.get("conflict") as? JsonObject) ?: raw ?: return null
    val id = str(o, "id").takeIf { ID_RE.matches(it) } ?: return null
    val kind = str(o, "kind").ifEmpty { return null }
    val merged = ((o["detail"] as? JsonObject)?.get("merged") as? JsonArray).orEmpty()
      .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(FIELD_RE::matches) }
    return Conflict(
      id = id, kind = kind, task = bool(o, "task"), revertible = bool(o, "revertible"),
      table = str(o, "table"), rowId = str(o, "row_id"), field = str(o, "field"),
      winner = value(o, "winner_value"), loser = value(o, "loser_value"),
      winnerHlc = str(o, "winner_hlc"), loserHlc = str(o, "loser_hlc"),
      winnerDevice = str(o, "winner_device"), loserDevice = str(o, "loser_device"),
      status = str(o, "status").ifEmpty { OPEN }, at = long(o, "at") ?: 0L,
      resolvedAt = long(o, "resolved_at"), resolvedBy = str(o, "resolved_by"), merged = merged,
    )
  }

  /** پاسخِ فهرست ⇒ [Page]؛ گونه‌های صندوقِ سند بیرون */
  fun parsePage(o: JsonObject?): Page {
    val inbox = o?.get("inbox") as? JsonObject
    val items = (o?.get("conflicts") as? JsonArray).orEmpty().mapNotNull { parse(it as? JsonObject) }.filter { it.kind !in DOC_KINDS }
    return Page(items, on = inbox != null, merge = inbox != null && bool(inbox, "merge"))
  }

  // ---------- دو سو ----------

  enum class Who { THIS_DEVICE, OTHER_DEVICE, SERVER, UNKNOWN }

  /** یک سو: کدام دستگاه، کِی (دیواریِ HLC)، فعلی یا نه، حذف یا مقدارهای فیلدها */
  data class Side(
    val who: Who,
    val device: String,
    val wall: Long?,
    val current: Boolean,
    val deleted: Boolean,
    val values: Map<String, JsonElement?>,
  )

  fun who(device: String, me: String): Who = when {
    device.isEmpty() -> Who.UNKNOWN
    device == SERVER_DEVICE -> Who.SERVER
    me.isNotEmpty() && device == me -> Who.THIS_DEVICE
    else -> Who.OTHER_DEVICE
  }

  private fun fieldsOf(v: JsonElement?): Map<String, JsonElement?> =
    (v as? JsonObject)?.entries?.take(MAX_FIELDS)?.associate { (k, x) -> k to x.takeUnless { it is JsonNull } }.orEmpty()

  /**
   *  «فعلی» (برنده) و «دیگری» (بازنده) — هر کدام با فیلدهای همان سو. تک‌فیلدی ⇒ همان یک فیلد؛ `*` ⇒ فیلدهای بازنده
   *  (برنده فقط همان کلیدها)؛ حذف ⇒ سوی حذف‌کننده `deleted`. `stale_after_delete`: فعلی همان «حذف‌شده» است.
   */
  fun sides(c: Conflict, me: String): Pair<Side, Side> {
    val loserDeleted = c.field == DELETE
    val winnerDeleted = c.kind == "stale_after_delete"
    val (winVals, loseVals) = when (c.field) {
      WHOLE -> fieldsOf(c.winner).let { w -> fieldsOf(c.loser).keys.associateWith { w[it] } } to fieldsOf(c.loser)
      DELETE -> emptyMap<String, JsonElement?>() to emptyMap()
      else -> mapOf(c.field to c.winner) to mapOf(c.field to c.loser)
    }
    val win = Side(who(c.winnerDevice, me), c.winnerDevice, Hlc.parse(c.winnerHlc)?.wall, true, winnerDeleted, if (winnerDeleted) emptyMap() else winVals)
    val lose = Side(who(c.loserDevice, me), c.loserDevice, Hlc.parse(c.loserHlc)?.wall, false, loserDeleted, loseVals)
    return win to lose
  }

  // ---------- کارها ----------

  /** «نگه داشتنِ مالِ من/مالِ دیگری» — فقط وقتی یک سو مالِ همین دستگاه است؛ وگرنه (دستگاهِ سوم) «فعلی/دیگری» */
  enum class Owner { MINE, THEIRS, NEUTRAL }

  /** برچسبِ دو سو با هم: یکی «من» ⇒ دیگری «دیگری»؛ هیچ‌کدام (یا هر دو) ⇒ هر دو [Owner.NEUTRAL] */
  fun owners(current: Side, other: Side): Pair<Owner, Owner> {
    val a = current.who == Who.THIS_DEVICE
    val b = other.who == Who.THIS_DEVICE
    return when {
      a == b -> Owner.NEUTRAL to Owner.NEUTRAL
      a -> Owner.MINE to Owner.THEIRS
      else -> Owner.THEIRS to Owner.MINE
    }
  }

  enum class Pick { KEEP_CURRENT, TAKE_OTHER, MERGE, REVIEWED }

  /** ادغام فقط روی ردیفِ چندفیلدیِ برگشت‌پذیر که هر دو سو مقدار دارند — همان `mergeable` ِ سرور — و فقط با `inbox.merge` */
  fun mergeable(c: Conflict, serverMerge: Boolean): Boolean =
    serverMerge && c.open && c.revertible && c.field == WHOLE && c.winner is JsonObject && fieldsOf(c.loser).size >= 2

  /** کارهای ممکن (بسته ⇒ هیچ): وظیفه ⇒ «دیدم»؛ برگشت‌پذیر ⇒ دو سو (+ ادغام)؛ بقیه ⇒ فقط نگه داشتنِ فعلی */
  fun picks(c: Conflict, serverMerge: Boolean): List<Pick> = when {
    !c.open -> emptyList()
    c.task -> listOf(Pick.REVIEWED)
    c.revertible -> listOfNotNull(Pick.KEEP_CURRENT, Pick.TAKE_OTHER, Pick.MERGE.takeIf { mergeable(c, serverMerge) })
    else -> listOf(Pick.KEEP_CURRENT)
  }

  /** یک تصمیم برای سرور: کار و (فقط ادغام) فیلدهای گرفته‌شده از سوی دیگر */
  data class Decision(val conflictId: String, val action: String, val fields: List<String> = emptyList())

  /**
   *  تصمیم ⇒ بدنهٔ سرور. ادغام: [takeOther] زیرمجموعهٔ فیلدهای بازنده؛ همه ⇒ همان `revert`، هیچ ⇒ همان `keep`؛ فیلدِ
   *  ناشناخته یا کارِ نشدنی ⇒ null.
   */
  fun decide(c: Conflict, pick: Pick, serverMerge: Boolean, takeOther: Set<String> = emptySet()): Decision? {
    if (pick !in picks(c, serverMerge)) return null
    return when (pick) {
      Pick.REVIEWED -> Decision(c.id, DISMISS)
      Pick.KEEP_CURRENT -> Decision(c.id, KEEP)
      Pick.TAKE_OTHER -> Decision(c.id, REVERT)
      Pick.MERGE -> {
        val keys = fieldsOf(c.loser).keys.toList()
        if (!keys.containsAll(takeOther)) return null
        when {
          takeOther.isEmpty() -> Decision(c.id, KEEP)
          takeOther.size == keys.size -> Decision(c.id, REVERT)
          else -> Decision(c.id, MERGE, keys.filter { it in takeOther })
        }
      }
    }
  }

  /** حالِ پایانیِ یک تصمیم روی همین ردیف — همان `finalOf` ِ سرور (برای نشانِ «در صف») */
  fun finalOf(d: Decision, task: Boolean): String = when (d.action) {
    REVERT, MERGE -> REVERTED
    DISMISS -> DISMISSED
    else -> if (task) DISMISSED else KEPT
  }

  // ---------- نمایشِ مقدار ----------

  enum class Kind { MONEY, QTY, TEXT, NUMBER, BOOL, LIST, EMPTY, OTHER }

  /** یک مقدار برای صفحه: مبلغ (یک‌صدمِ افغانی) و مقدار (هزارم) عددِ صحیح، متن، فهرست؛ برچسبِ فیلد [base] */
  data class Shown(val kind: Kind, val text: String = "", val number: Long? = null, val size: Int = 0)

  /** `salePriceMinor` ⇒ `salePrice`، `minStockMilli` ⇒ `minStock` (واحدِ v3: `<نام>Minor`/`<نام>Milli`) */
  fun base(field: String): String = field.removeSuffix("Minor").removeSuffix("Milli").ifEmpty { field }

  fun shown(field: String, v: JsonElement?): Shown {
    val p = v as? JsonPrimitive
    return when {
      v == null || v is JsonNull -> Shown(Kind.EMPTY)
      p != null && p.isString -> if (p.content.isBlank()) Shown(Kind.EMPTY) else Shown(Kind.TEXT, p.content)
      p?.booleanOrNull != null -> Shown(Kind.BOOL, number = if (p.booleanOrNull == true) 1 else 0)
      p?.longOrNull != null && field.endsWith("Minor") -> Shown(Kind.MONEY, number = p.longOrNull)
      p?.longOrNull != null && field.endsWith("Milli") -> Shown(Kind.QTY, number = p.longOrNull)
      p != null -> Shown(Kind.NUMBER, p.content)
      v is JsonArray -> Shown(Kind.LIST, v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("، "), size = v.size)
      else -> Shown(Kind.OTHER, size = (v as? JsonObject)?.size ?: 0)
    }
  }
}
