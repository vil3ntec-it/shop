package ir.vil3ntec.tohid.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  پشتیبانِ سرورِ فاکتورهای نگه‌داشته — POS-12، S09 (`/api/shop/held-invoices`، مهاجرتِ ۴۲۵).
 *
 *  تا امروز نگه‌داشته فقط در فایلِ همین دستگاه بود (`HeldInvoiceStore`): پاک کردنِ برنامه یا دستگاهِ تازه همه را
 *  می‌برد. حالا هر نگه‌داشته با نگه‌داشتن یا ویرایش فرستاده می‌شود و با بازگرداندن، فروش یا دور انداختن برداشته؛
 *  دستگاهِ تازه یا دیگرِ همان دکان همه را در فهرستِ نگه‌داشته‌ها پس می‌گیرد.
 *
 *  ⛔ **پیش‌فرض خاموش**: پرچمِ سرورِ `shop.held_sync`. خاموش (یا سرورِ کهنه، یا بی سرور) ⇒ هیچ چیز نوشته یا فرستاده
 *     نمی‌شود: نه فایلِ نگه‌داشته‌ها، نه وضعِ همگام‌سازی — فقط یک `GET` که `{enabled:false}` می‌گیرد.
 *  ⛔ قاعدهٔ تعارض (همان سرور): هر فاکتور جدا با نسخهٔ دستگاه (`version` = ساعتِ ویرایش، همیشه بیش از نسخه‌ای که
 *     همین دستگاه دیده بود)؛ کهنه‌تر هرگز روی تازه‌تر نمی‌نشیند. کارِ محلیِ نرسیده جلوتر از ردیفِ سرور است تا سرور
 *     داوری کند؛ پاسخِ «کهنه» (`held_stale`) ردیفِ تازه‌ترِ سرور را می‌نشاند.
 *  ⛔ هیچ نگه‌داشته‌ای این‌جا گم نمی‌شود: نوشتنِ ناموفق ⇒ کار در صف می‌ماند؛ ردِ همیشگی (شکل، اندازه) ⇒ نسخهٔ دستگاه
 *     سرِ جایش؛ نگه‌داشتهٔ سرور که جا نشد (`MAX_OPEN`) روی سرور می‌ماند و دورِ بعد از نو خوانده می‌شود.
 *  ⛔ فقط نگه‌داشته‌هایی که همین دستگاه زیرِ همین حساب ساخت (سریِ سند) خودبه‌خود فرستاده می‌شوند — نگه‌داشتهٔ ماندهٔ
 *     حسابِ دیگر هرگز به دکانِ دیگری نمی‌رود.
 *
 *  ── «در دستِ دستگاهِ X» (ادعای نرم، همان پرچم) ──────────────────────────────────────────────────────
 *  پیش از بازگرداندن [HeldSyncEngine.claim] از سرور می‌پرسد (`DELETE …&claim=1&base=<نسخهٔ دیده‌شده>`، شرح: سرِ
 *  `server/src/lib/shop-held-sync.js`): داد ⇒ همان بازگرداندن؛ در دستِ دستگاهِ دیگر ⇒ [HeldClaim.Held] تا صفحه تأییدِ صریح
 *  بگیرد («در دستِ دستگاهِ A1»)؛ با تأیید (`force`) یا بی شبکه (نگه‌داشته‌ای که سرور می‌شناخت) ⇒ [HeldClaim.Rekey]: همان
 *  نگه‌داشته با **کلیدِ تازهٔ فروش** باز می‌شود، تا اگر دستگاهِ دیگر هم فروخت دو فروشِ جدا باشند و Sync v1 دو فروشِ یک شناسه
 *  را بی‌صدا در یک ردیف نیامیزد (ردیف‌های هر دو زیرِ یک فروش، جمعِ یکی). بازگرداندنِ بی شبکه در صف با `base` می‌رود؛ سرور
 *  اگر دیگری پیش‌تر برش گرداند/فروخت/عوضش کرد ⇒ `held_conflict` ⇒ [HeldConflict] تا کاربر ببیند («یک فاکتور در دو دستگاه»)؛
 *  دستگاهی که فاکتورِ صندوقش را دیگری با تأیید گرفت هم با دورِ بعد همان را می‌بیند. هیچ‌گاه «دو فروشِ یک نگه‌داشته» بی دیدنِ کاربر.
 */
enum class HeldChange(val reason: String) {
  HOLD(""), PARK(""), RENAME(""),
  RESUME("resumed"), RECONCILE("resumed"),
  PRUNE("sold"),
  DISCARD("deleted"), STALE("deleted"),
}

/** «در دستِ دستگاهِ X» — پاسخِ پرسشِ پیش از بازگرداندن ([HeldSyncEngine.claim]) */
sealed interface HeldClaim {
  /** همان بازگرداندنِ پیشین: پرچم خاموش، نگه‌داشتهٔ فقط-همین-دستگاه، یا سرور به همین دستگاه داد */
  data object Free : HeldClaim
  /** همان نگه‌داشته با شناسهٔ تازه ([id] = کلیدِ تازهٔ فروش) باز شود: بی شبکه، یا گرفته از دستِ دستگاهِ دیگر با تأیید */
  data class Rekey(val id: String) : HeldClaim
  /** در دستِ دستگاهِ دیگر ([series] سریِ سندِ آن، خالی = نامعلوم)؛ [fresh] = همین حالا باز است. تأییدِ صریح ⇒ `force` */
  data class Held(val series: String, val reason: String, val fresh: Boolean) : HeldClaim
}

/** «یک فاکتور در دو دستگاه» — تا کاربر ببیند می‌ماند ([HeldSyncState.conflicts]، [HeldSyncEngine.ack]) */
@Serializable
data class HeldConflict(val title: String = "", val series: String = "", val reason: String = "", val at: Long = 0)

/** وضعِ همگام‌سازیِ نگه‌داشته‌های یک دکان روی این دستگاه — فقط وقتی پرچم روشن است نوشته می‌شود */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HeldSyncState(
  val on: Boolean = false,
  val cursor: Long = 0,
  /** کارهای نرسیده، هر فاکتور یکی (آخرین)، به ترتیبِ رخ دادن */
  val out: Map<String, Op> = emptyMap(),
  /** نسخهٔ شناخته‌شدهٔ هر فاکتور: ویرایشِ همین دستگاه یا آنچه از سرور رسید */
  val known: Map<String, Long> = emptyMap(),
  /** نگه‌داشته‌هایی که نگه‌دارنده‌شان کسِ دیگری است (به گفتهٔ سرور) */
  val others: Set<String> = emptySet(),
  /** «لغوِ فاکتور» (`sales.cancel`) به گفتهٔ سرور */
  val cancel: Boolean = false,
  /** نگه‌داشته‌ای از سرور جا نشد (`MAX_OPEN`) ⇒ دورِ بعد زنده‌ها از نو خوانده می‌شوند (روی سرور می‌ماند، گم نمی‌شود) */
  val refill: Boolean = false,
  /** «یک فاکتور در دو دستگاه»ِ دیده‌نشده (شناسهٔ نگه‌داشته ⇒ شرح)؛ نبود ⇒ نوشته نمی‌شود (وضعِ پیشین بایت‌به‌بایت همان) */
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val conflicts: Map<String, HeldConflict> = emptyMap(),
) {
  /** [base] و [title] فقط برای بازگرداندن (نسخه‌ای که دیده بود؛ نامِ برگه برای پیامِ «دوتایی»)؛ ۰/خالی نوشته نمی‌شوند */
  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  data class Op(
    val del: Boolean,
    val v: Long,
    val why: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val base: Long = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val title: String = "",
  )
}

/** قواعدِ خالص — بی شبکه و بی فایل */
object HeldSyncPlan {

  /**
   *  ردیفِ سرور: `body = null` ⇒ سنگِ قبر (برگشت، فروش یا دور انداخته شد) با [device]/[series] ِ دستگاهی که کرد و [reason].
   */
  data class Remote(
    val id: String, val version: Long, val body: HeldInvoice?, val mine: Boolean = true,
    val device: String = "", val series: String = "", val reason: String = "",
  )

  /** نتیجهٔ نشاندن: دفترِ تازه، نسخه‌های تازه (بیشینه با پیشین)، و «کسِ دیگر»ها */
  data class Absorbed(
    val book: HeldBook,
    val known: Map<String, Long>,
    val mine: Set<String>,
    val others: Set<String>,
    /** ردیفی که جا نشد ⇒ دورِ بعد از نو */
    val full: Boolean,
    /** فاکتورِ همین حالای صندوق که دستگاهِ دیگری (با تأیید) گرفت */
    val taken: List<Remote> = emptyList(),
  )

  fun nextVersion(known: Map<String, Long>, id: String, now: Long): Long = maxOf(now, (known[id] ?: 0L) + 1)

  /** نامِ برگه در پیامِ «یک فاکتور در دو دستگاه» */
  fun titleOf(h: HeldInvoice): String = h.label.ifBlank { "#${h.number}" }

  /** پس از تغییرِ محلیِ دفتر: هر فاکتورِ تازه یا عوض‌شده یک `PUT`، هر رفته یک `DELETE` با دلیلِ همان کار */
  fun record(s: HeldSyncState, before: HeldBook, after: HeldBook, why: HeldChange, now: Long): HeldSyncState {
    val was = before.items.associateBy { it.id }
    val next = after.items.associateBy { it.id }
    val out = LinkedHashMap(s.out)
    val known = LinkedHashMap(s.known)
    for ((id, h) in next) {
      if (was[id] == h) continue
      val v = nextVersion(known, id, now)
      out.remove(id); out[id] = HeldSyncState.Op(del = false, v = v); known[id] = v
    }
    for (id in was.keys) {
      if (id in next) continue
      //  بازگرداندن: نسخه‌ای که این دستگاه دیده بود — سرور با آن «دیگری پیش‌تر برش گرداند/فروخت» را می‌فهمد (`held_conflict`)
      val resumed = why.reason == HeldChange.RESUME.reason
      val base = if (resumed) known[id] ?: 0L else 0L
      val v = nextVersion(known, id, now)
      out.remove(id)
      out[id] = HeldSyncState.Op(
        del = true, v = v, why = why.reason.ifBlank { HeldChange.DISCARD.reason },
        base = base, title = if (resumed) titleOf(was.getValue(id)) else "",
      )
      known[id] = v
    }
    return if (out == s.out && known == s.known) s else s.copy(out = out, known = known)
  }

  /**
   *  نگه‌داشته‌های همین دستگاه که هرگز فرستاده نشده‌اند (پیش از روشن شدنِ پرچم، یا پیش از نخستین پاسخِ سرور) ⇒ `PUT`.
   *  فقط آن‌که همین دستگاه زیرِ همین حساب ساخت ([series]، سریِ سند): نگه‌داشتهٔ ماندهٔ حسابِ دیگر نه.
   */
  fun unsent(book: HeldBook, s: HeldSyncState, series: String, now: Long): HeldSyncState {
    if (series.isBlank()) return s
    val out = LinkedHashMap(s.out)
    val known = LinkedHashMap(s.known)
    for (h in book.items) {
      if (h.id in known || h.id in out || h.device != series) continue
      val v = nextVersion(known, h.id, now)
      out[h.id] = HeldSyncState.Op(del = false, v = v); known[h.id] = v
    }
    return if (out.size == s.out.size) s else s.copy(out = out, known = known)
  }

  /**
   *  نشاندنِ ردیف‌های سرور. کارِ محلیِ نرسیده ([pending]) جلوتر است؛ نسخهٔ کهنه‌تر یا برابر با آنچه دیده شده نادیده؛
   *  فاکتورِ همین حالای صندوق ([activeKey]) دوباره نگه‌داشته نمی‌شود. سنگِ قبر فقط آن را برمی‌دارد که همین دستگاه
   *  پیش‌تر با نسخه‌ای نه تازه‌تر دیده بود.
   */
  fun absorb(
    book: HeldBook, known: Map<String, Long>, pending: Set<String>, rows: List<Remote>, activeKey: String?, me: String = "",
  ): Absorbed {
    var b = book
    val seen = LinkedHashMap<String, Long>()
    val mine = HashSet<String>()
    val others = HashSet<String>()
    val taken = ArrayList<Remote>()
    var full = false
    fun k(id: String): Long? = seen[id] ?: known[id]
    for (r in rows) {
      //  فاکتورِ همین حالای صندوق را دستگاهِ دیگری برگرداند (فقط با تأییدِ صریح، «در دستِ دستگاهِ X») ⇒ کاربرِ این‌جا هم ببیند
      if (r.body == null && r.id == activeKey && r.reason == HeldChange.RESUME.reason && me.isNotBlank() && r.device.isNotBlank() && r.device != me) {
        taken += r
      }
      if (r.id in pending) continue
      val have = k(r.id)
      val local = b.items.firstOrNull { it.id == r.id }
      if (r.body == null) {
        if (have != null && have <= r.version) {
          if (local != null) b = HeldRules.discard(b, r.id)
          seen[r.id] = r.version
        }
        continue
      }
      if (have != null && have >= r.version) continue
      if (r.id == activeKey || r.id == b.current?.id) continue
      val item = r.body.copy(id = r.id)
      if (item.lines.isEmpty()) continue
      if (local != null) {
        b = b.copy(items = b.items.map { if (it.id == r.id) item.copy(number = it.number) else it })
      } else {
        when (val p = HeldRules.park(b, item)) {
          is HeldRules.Result.Ok -> b = p.book
          is HeldRules.Result.Refused -> { full = true; continue }
        }
      }
      seen[r.id] = r.version
      if (r.mine) mine += r.id else others += r.id
    }
    return Absorbed(b, seen, mine, others, full, taken)
  }

  /** نسخه‌ها و «کسِ دیگر»ها فقط برای فاکتورهای زنده یا در صف — بقیه کنار */
  fun tidy(s: HeldSyncState, book: HeldBook): HeldSyncState {
    val live = book.items.mapTo(HashSet()) { it.id }.apply { book.current?.id?.let { add(it) } }
    val known = s.known.filterKeys { it in live || it in s.out }
    val others = s.others.filterTo(HashSet()) { it in live }
    return if (known.size == s.known.size && others.size == s.others.size) s else s.copy(known = known, others = others)
  }
}

/**
 *  یک دکان روی این دستگاه: صف، نسخه‌ها و دورِ Pull ⇐ نشاندن ⇐ فرستادن. بی Android — آزمون‌ها سرورِ ساختگی می‌دهند.
 */
class HeldSyncEngine(
  private val store: HeldInvoiceStore,
  private val port: Port,
  private val state: StateStore,
  private val clock: () -> Long,
  /** سریِ سندِ همین دستگاه زیرِ همین حساب (`DocSeries.current`) */
  private val series: () -> String = { "" },
  /** فاکتورِ همین حالای صندوق (`CartStore`) */
  private val activeKey: () -> String? = { null },
  private val deviceId: () -> String = { "" },
  /** کلیدِ تازهٔ فروش (`Ulid`) — بازگرداندنِ بی شبکه یا گرفته از دستِ دیگری ([HeldClaim.Rekey]) */
  private val newKey: () -> String = { ir.vil3ntec.tohid.sync.v1.Ulid.next() },
) {
  interface Port {
    /** `null` ⇒ نرسید */
    suspend fun pull(since: Long): JsonObject?
    suspend fun put(id: String, body: JsonObject): Reply
    suspend fun delete(id: String, version: Long, reason: String): Reply
    /** بازگرداندن با نسخه‌ای که این دستگاه دیده بود ([base] ۰ = نامعلوم ⇒ همان پیشین) */
    suspend fun delete(id: String, version: Long, reason: String, base: Long): Reply = delete(id, version, reason)
    /** «در دستِ دستگاهِ X» پیش از بازگرداندن ([force] = تأییدِ صریحِ گرفتن)؛ نرسید ⇒ `Reply(0)` */
    suspend fun claim(id: String, version: Long, base: Long, force: Boolean): Reply = Reply(0)
  }

  /** `status` ۰ ⇒ نرسید (شبکه)؛ [error] = شیءِ `error` ِ پاسخِ ناموفق */
  data class Reply(val status: Int, val body: JsonObject? = null, val error: JsonObject? = null) {
    val code: String get() = (error?.get("code") as? JsonPrimitive)?.contentOrNull.orEmpty()
  }

  /** وضع روی دستگاه. [update] خواندن و نوشتنِ اتمی است (قفلِ سکو در پیاده‌سازی: صف از رشتهٔ صفحه و دور از پس‌زمینه) */
  interface StateStore {
    fun read(): HeldSyncState
    fun update(f: (HeldSyncState) -> HeldSyncState): HeldSyncState
  }

  /** گزارشِ یک دور (آزمون و سابقه) */
  data class Report(val enabled: Boolean, val absorbed: Int = 0, val sent: Int = 0)

  private val mutex = Mutex()

  private fun update(f: (HeldSyncState) -> HeldSyncState): HeldSyncState = state.update(f)

  val enabled: Boolean get() = state.read().on

  /** «کسِ دیگر» ِ نگه‌داشته — دور انداختنش «لغوِ فاکتور» می‌خواهد */
  fun othersHeld(id: String): Boolean = state.read().let { it.on && id in it.others }

  /** «یک فاکتور در دو دستگاه» ِ دیده‌نشده */
  fun conflicts(): Map<String, HeldConflict> = state.read().conflicts

  /** کاربر دید ⇒ کنار */
  fun ack(id: String) { update { s -> if (id !in s.conflicts) s else s.copy(conflicts = s.conflicts - id) } }

  private fun conflict(id: String, c: HeldConflict) = update { s -> s.copy(conflicts = s.conflicts + (id to c)) }

  private fun holderOf(o: JsonObject?, title: String): HeldConflict = HeldConflict(
    title = title, series = str(o, "series"), reason = str(o, "reason"), at = clock(),
  )

  /**
   *  «در دستِ دستگاهِ X» — پیش از بازگرداندنِ نگه‌داشتهٔ [id] (`HeldInvoicesUi.resume`). خاموش یا نگه‌داشتهٔ نبوده ⇒
   *  [HeldClaim.Free] بی هیچ درخواستی. شرحِ پاسخ‌ها سرِ همین پرونده.
   */
  suspend fun claim(id: String, force: Boolean = false): HeldClaim {
    if (!state.read().on || !ID.matches(id)) return HeldClaim.Free
    store.load()
    val h = store.book.value.items.firstOrNull { it.id == id } ?: return HeldClaim.Free
    return ask(h, force, retry = true)
  }

  private suspend fun ask(h: HeldInvoice, force: Boolean, retry: Boolean): HeldClaim {
    val s = state.read()
    val base = s.known[h.id] ?: 0L
    val reply = port.claim(h.id, HeldSyncPlan.nextVersion(s.known, h.id, clock()), base, force)
    return when {
      reply.status in 200..299 -> {
        val v = (reply.body?.get("version") as? JsonPrimitive)?.longOrNull ?: 0L
        if (v > 0) update { st -> st.copy(known = LinkedHashMap(st.known).apply { put(h.id, maxOf(v, st.known[h.id] ?: 0L)) }) }
        //  گرفته از دستِ دستگاهِ دیگر ⇒ کلیدِ تازه (آن دستگاه شاید هنوز بفروشد؛ دو فروشِ یک شناسه هرگز)
        if ((reply.body?.get("claim") as? JsonObject)?.get("takeover") is JsonObject) rekey(h, queue = false) else HeldClaim.Free
      }
      reply.code == "held_claimed" -> {
        val o = reply.error?.get("holder") as? JsonObject
        HeldClaim.Held(str(o, "series"), str(o, "reason"), (o?.get("fresh") as? JsonPrimitive)?.booleanOrNull == true)
      }
      //  نسخهٔ دیده‌شده کهنه بود ⇒ اول همان تازه‌تر را بنشاند، بعد یک بار دیگر بپرسد
      reply.code == "held_stale" && retry -> {
        (reply.error?.get("current") as? JsonObject)?.let { cur ->
          remoteOf(cur, gone = (cur["deleted"] as? JsonPrimitive)?.booleanOrNull == true)?.let { absorb(listOf(it), null, state.read().cancel) }
        }
        val again = store.book.value.items.firstOrNull { it.id == h.id } ?: return HeldClaim.Free
        ask(again, force, retry = false)
      }
      reply.code == "held_sync_disabled" -> { update { HeldSyncState() }; HeldClaim.Free }
      //  بی شبکه: آزاد — نگه‌داشتهٔ در همگام‌سازی (`known`؛ شاید روی دستگاهِ دیگری هم هست) با کلیدِ تازه، و بازگرداندنش با نسخهٔ
      //  دیده‌شده در صف (دوتایی پس از وصل دیده شود). نگه‌داشته‌ای که هرگز به سرور نرسیده بود هم بی‌خطر: آن برداشتن بی‌اثر است
      reply.status == 0 || reply.status == 401 || reply.status == 429 || reply.status >= 500 ->
        if (h.id in s.known) rekey(h, queue = true) else HeldClaim.Free
      else -> HeldClaim.Free
    }
  }

  private suspend fun rekey(h: HeldInvoice, queue: Boolean): HeldClaim {
    val next = newKey()
    if (!store.rekey(h.id, next)) return HeldClaim.Free
    if (queue) update { st ->
      val known = LinkedHashMap(st.known)
      val out = LinkedHashMap(st.out)
      val base = known[h.id] ?: 0L
      val v = HeldSyncPlan.nextVersion(known, h.id, clock())
      out.remove(h.id)
      out[h.id] = HeldSyncState.Op(del = true, v = v, why = HeldChange.RESUME.reason, base = base, title = HeldSyncPlan.titleOf(h))
      known[h.id] = v
      st.copy(out = out, known = known)
    }
    return HeldClaim.Rekey(next)
  }

  /** تغییرِ محلی (`HeldInvoiceStore.onChange`، زیرِ قفلِ دفتر). خاموش ⇒ هیچ، حتی خواندنِ دوباره یا نوشتن */
  fun changed(before: HeldBook, after: HeldBook, why: HeldChange): Boolean {
    if (!state.read().on) return false
    update { if (!it.on) it else HeldSyncPlan.record(it, before, after, why, clock()) }
    return true
  }

  /**
   *  یک دور: Pull از cursor (تازه‌ها و سنگِ قبرها) ⇐ نشاندن ⇐ (جا نشده بود؟ زنده‌ها از نو) ⇐ فرستادنِ صف ⇐ Pull ِ
   *  دوباره اگر چیزی رفت، تا cursor از نوشته‌های خودمان بگذرد و سنگِ قبرِ بعدی دیده شود.
   */
  suspend fun cycle(): Report = mutex.withLock {
    store.load()
    val pulled = pull(state.read().cursor) ?: return@withLock Report(enabled = state.read().on)
    if (!pulled.enabled) return@withLock Report(false)
    var absorbed = absorb(pulled.rows, pulled.rev, pulled.cancel)
    if (state.read().refill) {
      val all = pull(0)
      if (all != null && all.enabled) absorbed += absorb(all.rows.filter { it.body != null }, null, all.cancel, refillPass = true)
    }
    update { HeldSyncPlan.unsent(store.book.value, it, series(), clock()) }
    val sent = flush()
    if (sent > 0) {
      val again = pull(state.read().cursor)
      if (again != null && again.enabled) absorbed += absorb(again.rows, again.rev, again.cancel)
    }
    update { HeldSyncPlan.tidy(it, store.book.value) }
    Report(true, absorbed, sent)
  }

  private class Pulled(val enabled: Boolean, val rows: List<HeldSyncPlan.Remote> = emptyList(), val rev: Long = 0, val cancel: Boolean = false)

  private suspend fun pull(from: Long): Pulled? {
    var since = from
    val rows = ArrayList<HeldSyncPlan.Remote>()
    var cancel = false
    var pages = 0
    while (pages++ < 5) {
      val body = port.pull(since) ?: return null
      if ((body["enabled"] as? JsonPrimitive)?.booleanOrNull != true) {
        //  پرچم خاموش شد ⇒ وضعِ همگام‌سازی فراموش (دفترِ دستگاه دست نمی‌خورد)؛ هرگز روشن نبود ⇒ هیچ نوشتنی
        if (state.read() != HeldSyncState()) update { HeldSyncState() }
        return Pulled(false)
      }
      cancel = ((body["permissions"] as? JsonObject)?.get("cancel") as? JsonPrimitive)?.booleanOrNull == true
      (body["items"] as? JsonArray)?.forEach { e -> remoteOf(e as? JsonObject, gone = false)?.let(rows::add) }
      (body["gone"] as? JsonArray)?.forEach { e -> remoteOf(e as? JsonObject, gone = true)?.let(rows::add) }
      since = maxOf(since, (body["rev"] as? JsonPrimitive)?.longOrNull ?: since)
      if ((body["more"] as? JsonPrimitive)?.booleanOrNull != true) break
    }
    return Pulled(true, rows, since, cancel)
  }

  /**
   *  نشاندن زیرِ قفلِ دفتر — هیچ تغییرِ محلی‌ای میانِ خواندنِ صف و نوشتنِ دفتر نمی‌رسد. [rev] `null` ⇒ cursor همان
   *  (خواندنِ دوبارهٔ زنده‌ها یا ردیفِ پاسخِ «کهنه»).
   */
  private suspend fun absorb(rows: List<HeldSyncPlan.Remote>, rev: Long?, cancel: Boolean, refillPass: Boolean = false): Int {
    var result: HeldSyncPlan.Absorbed? = null
    val me = runCatching(deviceId).getOrDefault("")
    val active = activeKey()
    val title = store.book.value.current?.takeIf { it.id == active }?.let { it.label.ifBlank { "#${it.number}" } }.orEmpty()
    val ok = store.absorb { book ->
      val s = state.read()
      HeldSyncPlan.absorb(book, s.known, s.out.keys, rows, active, me).also { result = it }.book
    }
    val a = result
    a?.taken?.forEach { r -> conflict(r.id, HeldConflict(title, r.series, r.reason, clock())) }
    update { s ->
      if (!ok || a == null) return@update s.copy(on = true, cancel = cancel, refill = s.refill || !ok)
      val known = LinkedHashMap(s.known)
      for ((id, v) in a.known) known[id] = maxOf(v, known[id] ?: 0L)
      s.copy(
        on = true, cancel = cancel,
        cursor = rev ?: s.cursor,
        //  ردیفی جا نشد ⇒ دورِ بعد زنده‌ها از نو (روی سرور می‌ماند، گم نمی‌شود)؛ خواندنِ زنده‌ها که همه را نشاند ⇒ تمام
        refill = if (refillPass) a.full else s.refill || a.full,
        known = known,
        others = (s.others - a.mine) + a.others,
      )
    }
    return if (ok && a != null) a.known.size else 0
  }

  private fun done(id: String, op: HeldSyncState.Op) =
    update { s -> if (s.out[id] == op) s.copy(out = LinkedHashMap(s.out).apply { remove(id) }) else s }

  private suspend fun flush(): Int {
    var sent = 0
    for ((id, op) in state.read().out.entries.toList()) {
      //  شناسه‌ای که سرور نمی‌پذیرد (همان `ID_RE` ِ سرور) ⇒ فقط روی همین دستگاه
      if (!ID.matches(id)) { done(id, op); continue }
      val reply: Reply = if (op.del) {
        if (op.base > 0) port.delete(id, op.v, op.why, op.base) else port.delete(id, op.v, op.why)
      } else {
        //  دیگر در دفتر نیست (کارِ بعدی برداشتنش را در صف گذاشته یا می‌گذارد)
        val h = store.book.value.items.firstOrNull { it.id == id }
        if (h == null) { done(id, op); continue }
        port.put(id, bodyOf(h, op.v))
      }
      when {
        reply.status == 0 || reply.status == 401 || reply.status >= 500 || reply.status == 429 -> return sent
        reply.status in 200..299 -> { done(id, op); sent++ }
        reply.code == "held_sync_disabled" -> { update { HeldSyncState() }; return sent }
        reply.code == "held_stale" -> {
          done(id, op)
          (reply.error?.get("current") as? JsonObject)?.let { cur ->
            val row = remoteOf(cur, gone = (cur["deleted"] as? JsonPrimitive)?.booleanOrNull == true)
            if (row != null) absorb(listOf(row), null, state.read().cancel)
          }
        }
        //  جا برای نگه‌داشتهٔ تازه نیست ⇒ در صف می‌ماند تا برداشتنی جا باز کند
        reply.code == "too_many_held" -> Unit
        //  همین نگه‌داشته را دستگاهِ دیگری پیش‌تر برگرداند، فروخت یا عوض کرد ⇒ کاربر ببیند؛ سرور دست نخورد
        reply.code == "held_conflict" -> {
          done(id, op)
          conflict(id, holderOf(reply.error?.get("holder") as? JsonObject, op.title))
        }
        //  ردِ همیشگی (شکل، اندازه، نگه‌داشتهٔ کسِ دیگر): نسخهٔ دستگاه سرِ جایش، کار کنار
        else -> done(id, op)
      }
    }
    return sent
  }

  private fun bodyOf(h: HeldInvoice, version: Long): JsonObject = buildJsonObject {
    put("version", JsonPrimitive(version))
    put("body", json.encodeToJsonElement(HeldInvoice.serializer(), h))
    put("device_id", JsonPrimitive(runCatching(deviceId).getOrDefault("")))
  }

  private fun str(o: JsonObject?, key: String): String = (o?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

  private fun remoteOf(o: JsonObject?, gone: Boolean): HeldSyncPlan.Remote? {
    o ?: return null
    val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return null
    val v = (o["version"] as? JsonPrimitive)?.longOrNull ?: return null
    if (gone) return HeldSyncPlan.Remote(id, v, null, device = str(o, "device_id"), series = str(o, "series"), reason = str(o, "reason"))
    val body = (o["body"] as? JsonObject)?.let { runCatching { json.decodeFromJsonElement(HeldInvoice.serializer(), it) }.getOrNull() } ?: return null
    return HeldSyncPlan.Remote(id, v, body, mine = (o["mine"] as? JsonPrimitive)?.booleanOrNull != false)
  }

  companion object {
    private val ID = Regex("^[0-9A-Za-z_-]{1,64}$")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    fun encodeState(s: HeldSyncState): String = json.encodeToString(HeldSyncState.serializer(), s)
    fun decodeState(raw: String?): HeldSyncState =
      raw?.let { runCatching { json.decodeFromString(HeldSyncState.serializer(), it) }.getOrNull() } ?: HeldSyncState()
  }
}
