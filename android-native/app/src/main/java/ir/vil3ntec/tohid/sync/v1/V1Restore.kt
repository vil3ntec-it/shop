package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.core.sync.RestoreReplay
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  §۹.۵ برای Sync v1 — «سرور از پشتیبان برگشت» (شرح سرِ `core/sync/RestoreReplay.kt`)،
 *  جدا از اندروید تا روی JVM سنجیده شود. `SyncV1Engine.runOnce` فقط ترتیبِ شبکه را دارد:
 *
 *      دوران (هر دور، فقط اگر `asks`) ⇒ `check` ⇒ Push تا خالی ⇒ اگر «در انتظار» و صف خالی:
 *      Snapshot ⇒ `localOnly` ⇒ (سرور: کدام حذف‌شده) ⇒ `rebuild` ⇒ Push ⇒ Pull
 *      (در انتظار ⇒ هیچ Pullی روی دفترِ کهنه)
 *
 *  ── دوباره فرستادن (`check`) ─────────────────────────────────────────
 *  opهای خودِ دستگاه **و** opهای دیگران که این دستگاه گرفته بود (`ReplayBook.seen`) با
 *  `server_seq > base`، **به ترتیبِ همان شماره** و با همان op_id جلوی صف. دستگاهی که زودتر برسد
 *  پنجرهٔ گم‌شده را به ترتیبِ پیش از بازیابی می‌نشاند و بقیه «duplicate» می‌گیرند:
 *   • فروشی که دستگاهِ دیگر ساخت و این‌جا رسید، حتی اگر سازنده‌اش هرگز برنگردد؛
 *   • `$inc`ِ مرجوعیِ این دستگاه روی قلمِ فروشِ دیگری **پس از** درجِ همان قلم — وگرنه درجِ
 *     دیرتر (با مقدارِ سادهٔ ۰) دلتای پیشین را می‌پوشاند و همان کالا دو بار پس داده می‌شد.
 *  ردِ opِ دوباره‌فرستاده (مثلاً شاگردی که opِ تخفیف‌دارِ صاحب را دوباره می‌فرستد) بی‌صداست:
 *  دستگاهِ سازنده خودش دوباره می‌فرستد. (`sync-v1.js` دست نمی‌خورد، پس سرور دلیلی برای
 *  «پذیرفتنِ دوبارهٔ opِ دیگری» ندارد — و نباید داشته باشد: درِ جعلِ op برای شاگرد می‌شد.)
 *
 *  ── ساختنِ دوباره (`rebuild`) — «نگه‌دارِ محلی» ──────────────────────
 *  دفتر = Snapshot + ردیف‌هایی که **فقط این‌جا** هستند (نه در Snapshot، نه در بایگانیِ دفتر، و نه
 *  حذف‌شده روی سرور)؛ سایه = همان Snapshot ⇒ ردیف‌های تنها با `record` ِ بلافاصله «درج» می‌روند.
 *  پس فروشی که دفترچه نداشت (برنامه میانِ پشتیبان و بازیابی به‌روز شد، سقفِ دفترچه، نوشتنِ
 *  ناموفق) یا دستگاهِ دیگری که هرگز برنگشت، از دفتر پاک نمی‌شود و به سرور می‌رسد.
 *  ⛔ فقط وقتی صف خالی است **و** تفاضلِ سایه با دفتر هیچ (پس از `record` ِ همان لحظه): تغییرِ
 *     نرفته‌ای زیرِ Snapshot گم نمی‌شود — اول می‌رود، دورِ بعد ساخته می‌شود.
 *  ⛔ ردیفِ بایگانی‌شده (`LedgerArchive`، بالای ۴۰۰ روز) از Snapshot برنمی‌گردد — دفتر و
 *     بایگانی هر دو آن را داشتند و «خروجیِ صاحب» دو بار می‌شمرد.
 *
 *  ⛔ صف، سایه و cursor فقط از راه‌های F0-05: opِ دوباره همان op_id را دارد
 *     (`Queue.pushFront`) و دفتر + سایه + cursor با ژورنالِ `SyncCommit.rebuild`.
 *  ── پرچمِ خاموشِ سرور (`SYNC_EPOCH_ENABLED`) ─────────────────────────
 *  درِ `GET /api/shop/sync-epoch` فقط وقتی زده می‌شود (`asks`) که pull ِ پیشین دوران آورده بود
 *  (`pulled`) — سرور آن را فقط با پرچمِ روشن کنارِ pull می‌گذارد. خاموش ⇒ هیچ درخواستی که هر دور ۴۰۴
 *  بگیرد؛ ۴۰۴ ِ پس از روشن بودن (`closed`) ⇒ تا pull ِ بعدیِ دوران‌دار دیگر نه.
 *
 *  ⛔ دوران و «در انتظار» با هم و همزمان (`State.commitRestore`): بسته شدن پس از آن ⇒ دورِ
 *     بعد کار را از همان‌جا ادامه می‌دهد؛ پیش از آن ⇒ دورِ بعد بازیابی را دوباره می‌بیند و
 *     `pushFront` opِ در صف را دوباره نمی‌آورد.
 */
class V1Restore internal constructor(
  private val state: State,
  private val queue: Queue,
  private val book: ReplayBook,
  private val commit: SyncCommit,
  /** ساعت (`SyncV1Engine.clock`) — برای هشدارِ پوششِ دفترچه */
  private val now: () -> Long,
  /** شناسهٔ ردیف‌های بایگانی‌شدهٔ دفتر، هر مجموعه (`LedgerArchive.archivedIds`) */
  private val archived: () -> Map<String, Set<String>> = { emptyMap() },
) {

  interface State {
    val epoch: String
    val cursor: Long
    val restorePending: Boolean
    /** دوران و «در انتظارِ ساختنِ دوباره» با هم، با `commit()` */
    fun commitRestore(epoch: String, pending: Boolean): Boolean
    /** فقط دوران (بی بازیابی) */
    fun adoptEpoch(epoch: String)
    /** دفترچه همهٔ پنجرهٔ پس از پایه را ندارد — هشدارِ ماندگار به کاربر از این لحظه */
    fun noteGap(at: Long) {}
    /** آخرین pull دوران آورد (پرچمِ سرور روشن) ⇒ درِ دوران پرسیده می‌شود */
    var epochLive: Boolean
  }

  interface Queue {
    fun all(): List<SyncOp>
    fun pushFront(ops: List<SyncOp>): Int
  }

  /** درِ دورانِ سرور این دور پرسیده شود؟ فقط وقتی آخرین pull دوران آورد — خاموش ⇒ هیچ درخواستی */
  fun asks(): Boolean = state.epochLive

  /** پاسخِ pull (یا Snapshot): دوران دارد ⇔ پرچمِ سرور روشن */
  fun pulled(epoch: RestoreReplay.Epoch?) {
    val live = epoch != null
    if (state.epochLive != live) state.epochLive = live
  }

  /** درِ دوران ۴۰۴ داد (پرچم خاموش شد یا سرورِ کهنه) ⇒ دیگر نپرس تا pull دوباره دوران بیاورد */
  fun closed() { if (state.epochLive) state.epochLive = false }

  /**
   *  دورانِ سرور (null ⇒ سرورِ کهنه یا خاموش ⇒ هیچ). بازیابی ⇒ opهای خودِ دستگاه و
   *  گرفته‌شده‌ها بالای پایه، به ترتیبِ سرور، جلوی صف و «در انتظار».
   *  @return بازیابی آغاز شد
   */
  fun check(server: RestoreReplay.Epoch?): Boolean {
    //  پرچمِ خاموش (`SYNC_EPOCH_ENABLED`) یا سرورِ کهنه ⇒ دورانی نیست ⇒ همان رفتارِ پیشین، بی دفترچه
    if (server == null) return false
    //  opی که پیش از نخستین دوران پذیرفته شد (مثلاً `pushPending`) در دفترچه نیست ⇒ پوشش از سرِ
    //  دفترِ سرور در همان لحظه، تا پایه‌ای زیرِ آن هشدار بدهد، نه دوباره‌فرستادنِ ناقصِ بی‌صدا
    book.begin(maxOf(state.cursor, server.head))
    val plan = RestoreReplay.plan(state.epoch, state.cursor, server) { book.hasAbove(it) }
    if (!plan.restore) {
      if (plan.epoch != state.epoch) state.adoptEpoch(plan.epoch)
      return false
    }
    queue.pushFront(book.replayAbove(plan.base).mapNotNull { SyncOp.fromJson(it.body) })
    if (book.floor() > plan.base) state.noteGap(now())
    check(state.commitRestore(plan.epoch, true)) { "دورانِ همگام‌سازی روی دیسک ننشست" }
    return true
  }

  /** پذیرفته‌های یک دسته (applied/duplicate، با `server_seq`) — **پیش از** `ack`ِ صف */
  fun accepted(batch: List<SyncOp>, results: List<OpResult>) {
    if (!live()) return
    val seqOf = results.filter { it.accepted && it.seq > 0 }.associate { it.opId to it.seq }
    if (seqOf.isEmpty()) return
    book.sent(batch.filter { it.opId in seqOf }.map { ReplayBook.Sent(it.opId, seqOf.getValue(it.opId), it.toJson()) })
  }

  /** opهای دستگاه‌های دیگر که Pull آورد (با `server_seq` و دستگاه) — **پیش از** نشاندن روی دفتر */
  fun seen(pulled: List<ReplayBook.Sent>) { if (live()) book.seen(pulled) }

  /** دفترچه فقط پس از نخستین دورانی که سرور داد نوشته می‌شود (پرچمِ سرور روشن) */
  private fun live(): Boolean = state.epoch.isNotEmpty()

  /**
   *  ردِ opی که پیش‌تر پذیرفته یا گرفته شده بود (دوباره‌فرستاده پس از بازیابی) — «انجام‌شده»، نه
   *  ردشده‌ای که در دفترِ ردشده‌ها به کاربر نشان داده شود.
   */
  fun isReplay(opId: String): Boolean = book.wasSent(opId)

  /** ردیف‌هایی که فقط این‌جا هستند — پیش از `rebuild` از سرور پرسیده می‌شود کدام حذف‌شده است */
  fun localOnly(snapshot: JsonObject, current: ShopData): List<RestoreMerge.Row> =
    if (!state.restorePending) emptyList() else RestoreMerge.localOnly(snapshot, LedgerDiff.toJson(current), archived())

  /**
   *  پس از خالی شدنِ صف: دفتر = Snapshot + ردیف‌های تنهای این دستگاه (جز `gone`)؛ سایه = Snapshot؛
   *  cursor = سرِ Snapshot؛ بعد «در انتظار» برداشته می‌شود و ردیف‌های تنها «درج» به صف می‌روند.
   *  زیرِ قفلِ `record()` صدا زده می‌شود.
   *  @param gone ردیف‌هایی از `localOnly` که سرور حذف‌شده می‌داند — درجِ دوباره زنده‌شان می‌کرد
   *  @return ساخته شد (صفِ پر، تغییرِ ثبت‌نشده یا Snapshot ِ ناقص ⇒ نه؛ دورِ بعد دوباره)
   */
  fun rebuild(snapshot: JsonObject, current: ShopData, gone: Set<RestoreMerge.Row>, write: (ShopData) -> Unit): Boolean {
    if (!state.restorePending) return false
    val cursor = (snapshot["cursor"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return false
    if (queue.all().isNotEmpty()) return false
    //  تغییری که ثبت نشده بود (ذخیرهٔ ناموفق) ⇒ اول op شود و برود؛ ساختن دورِ بعد
    if (commit.record(current) > 0) return false
    val (ledger, shadow) = RestoreMerge.merge(snapshot, LedgerDiff.toJson(current), gone, archived())
    val written = commit.rebuild(ledger, shadow, current, cursor, write)
    check(state.commitRestore(state.epoch, false)) { "دورانِ همگام‌سازی روی دیسک ننشست" }
    //  ردیف‌های تنهای این دستگاه ⇒ «درج» (همان op_idِ تازه با ژورنالِ ثبت)
    commit.record(written)
    return true
  }
}

/**
 *  دفترِ «نگه‌دارِ محلی» پس از بازیابیِ سرور — همان `prepare()`/`keepLocalOnly` ِ v3، به‌اضافهٔ
 *  حذف‌شده‌های سرور و بایگانیِ دفتر. منطقِ خالص؛ `V1Restore` و `SyncV3Engine.rebuild` می‌خوانند.
 */
object RestoreMerge {

  /** یک ردیف: مجموعه و شناسه (فهرستِ نام: `LedgerDiff.listRowId(نام)`) */
  data class Row(val table: String, val id: String)

  private fun idOf(e: JsonElement?): String = ((e as? JsonObject)?.get("id") as? JsonPrimitive)?.content.orEmpty()
  private fun rowsOf(tree: JsonObject?, key: String): List<JsonObject> =
    (tree?.get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }?.filter { idOf(it).isNotEmpty() }.orEmpty()

  private fun tablesOf(snapshot: JsonObject): JsonObject = snapshot["tables"] as? JsonObject ?: JsonObject(emptyMap())

  /** ردیف‌های Snapshot ({id, data} ⇒ ردیفِ دفتر)؛ جدولِ نبود ⇒ هیچ */
  private fun serverRows(tables: JsonObject, key: String): List<JsonObject> =
    (tables[key] as? JsonArray).orEmpty().mapNotNull { r ->
      val o = r as? JsonObject ?: return@mapNotNull null
      val id = (o["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
      JsonObject(LinkedHashMap<String, JsonElement>((o["data"] as? JsonObject).orEmpty()).apply { put("id", JsonPrimitive(id)) })
    }

  /** نام‌های فهرستِ Snapshot؛ جدولِ نبود ⇒ null */
  private fun serverNames(tables: JsonObject, key: String): List<String>? {
    val rows = tables[key] as? JsonArray ?: return null
    val out = ArrayList<String>()
    for (r in rows) {
      val d = (r as? JsonObject)?.get("data") as? JsonObject
      val n = (d?.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
      if (n.isNotEmpty() && n !in out) out += n
    }
    return out
  }

  /** ردیف‌های دفترِ محلی که Snapshot ندارد (بایگانی‌شده‌ها نه) */
  fun localOnly(snapshot: JsonObject, local: JsonObject, archived: Map<String, Set<String>>): List<Row> {
    val tables = tablesOf(snapshot)
    val out = ArrayList<Row>()
    for (key in LedgerDiff.COLLECTIONS) {
      val server = serverRows(tables, key).mapTo(HashSet()) { idOf(it) }
      val skip = archived[key].orEmpty()
      for (row in rowsOf(local, key)) {
        val id = idOf(row)
        if (id !in server && id !in skip) out += Row(key, id)
      }
    }
    for (key in LedgerDiff.LIST_COLLECTIONS) {
      val server = serverNames(tables, key).orEmpty().toSet()
      for (name in LedgerDiff.listNames(local, key).orEmpty()) if (name !in server) out += Row(key, LedgerDiff.listRowId(name))
    }
    return out
  }

  /**
   *  @return (دفتر، سایه): دفتر = ردیف‌های Snapshot (جز بایگانی‌شده) + ردیف‌های تنهای محلی (جز
   *          `gone`)، از همان درِ مدل؛ سایه = همان دفتر بی ردیف‌های تنها ⇒ تفاضل = فقط «درج»ِ آن‌ها.
   */
  fun merge(snapshot: JsonObject, local: JsonObject, gone: Set<Row>, archived: Map<String, Set<String>>): Pair<JsonObject, JsonObject> {
    val tables = tablesOf(snapshot)
    val raw = LinkedHashMap<String, JsonElement>(local)
    val serverIds = HashMap<String, Set<String>>()
    val serverLists = HashMap<String, Set<String>>()
    for (key in LedgerDiff.COLLECTIONS) {
      val skip = archived[key].orEmpty()
      val server = serverRows(tables, key).filter { idOf(it) !in skip }
      val ids = server.mapTo(HashSet()) { idOf(it) }
      serverIds[key] = ids
      val mine = rowsOf(local, key).filter { r -> idOf(r).let { it !in ids && it !in skip && Row(key, it) !in gone } }
      raw[key] = JsonArray(server + mine)
    }
    for (key in LedgerDiff.LIST_COLLECTIONS) {
      val server = serverNames(tables, key)
      val mine = LedgerDiff.listNames(local, key)
      if (server == null && mine == null) { raw.remove(key); continue }
      val srv = server.orEmpty()
      //  نام‌های مشترک با ترتیبِ محلی (همان `fromSnapshot`)، بعد نام‌های تازهٔ سرور، بعد تنهاها
      val keep = mine.orEmpty().filter { it in srv }
      val names = keep + srv.filter { it !in keep } +
        mine.orEmpty().filter { it !in srv && Row(key, LedgerDiff.listRowId(it)) !in gone }
      raw[key] = JsonArray(names.map { JsonPrimitive(it) })
      serverLists[key] = srv.toSet()
    }
    val ledger = shape(JsonObject(raw))
    val shadow = LinkedHashMap<String, JsonElement>()
    for (key in LedgerDiff.COLLECTIONS) {
      val ids = serverIds[key].orEmpty()
      shadow[key] = JsonArray(rowsOf(ledger, key).filter { idOf(it) in ids })
    }
    for (key in LedgerDiff.LIST_COLLECTIONS) {
      val names = LedgerDiff.listNames(ledger, key) ?: continue
      val srv = serverLists[key].orEmpty()
      shadow[key] = JsonArray(names.filter { it in srv }.map { JsonPrimitive(it) })
    }
    return ledger to JsonObject(shadow)
  }

  /**
   *  ردیف‌ها از همان درِ مدل (پیش‌فرض‌ها پر می‌شوند — سایه و دفتر یک شکل، تفاضلِ بیهوده نه)؛
   *  فهرستی که در درخت نبود نبود می‌ماند (همان قاعدهٔ `LedgerDiff.applyRemote`).
   */
  private fun shape(raw: JsonObject): JsonObject {
    val shaped = try { LedgerDiff.toJson(LedgerDiff.fromJson(raw)) } catch (_: Exception) { return raw }
    val out = LinkedHashMap<String, JsonElement>(shaped)
    for (key in LedgerDiff.LIST_COLLECTIONS) {
      val v = raw[key]
      if (v is JsonArray) out[key] = v else out.remove(key)
    }
    return JsonObject(out)
  }

  private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
  private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
}
