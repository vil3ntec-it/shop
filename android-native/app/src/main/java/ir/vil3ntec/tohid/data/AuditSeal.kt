package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.audit.AuditChain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 *  STF-09 — مُهر و زنجیرهٔ هشِ سابقهٔ عملیات (`auditLog`) **هنگامِ نوشتنِ دفتر**، بی `Context` تا آزمون‌پذیر باشد.
 *  `ShopStore` پیش از هر نوشتنِ صفحه/پس‌زمینه و **زیرِ همان قفلِ نوشتن** صدایش می‌زند (پس افزودن‌های هم‌زمانِ یک
 *  دستگاه پشتِ هم‌اند)؛ مرزِ پلتفرم (نشست، پرچم، پروندهٔ لنگر) در `AuditSealAndroid.kt`. منطقِ زنجیره در
 *  `core/audit/AuditChain` (همان SHA-256 و canonicalJson ِ زنجیرهٔ سرور و اسناد).
 *
 *  - ردیفِ تازه = در دفترِ نوشتنی هست، در پایهٔ صفحه نیست و هنوز مُهر و حلقه ندارد ⇐ کاربر، نقش و دستگاهِ همین لحظه
 *    (همیشه — پاسبانی است، نه پول یا اجازه) و، با پرچمِ `shop.doc_chain`، حلقهٔ بعدیِ زنجیرهٔ همین دستگاه.
 *  - ⛔ ردیفِ موجود مُهر و حلقه‌اش را از دفترِ همین حالای دیسک نگه می‌دارد: صفحه نه می‌تواند آن‌ها را عوض کند،
 *    نه به ردیفِ کهنه مُهر بزند. بازیابی و «پاک کردنِ همه» (`writeInternal`) و دفترِ سرور هرگز مُهر نمی‌خورند.
 *  - همان ذخیره دوباره (پاسخِ گم‌شده، تلاشِ دوباره) ردیف را دوباره زنجیره نمی‌کند: ردیف در دیسک هست ⇐ همان حلقه.
 *  - لنگر ([AnchorStore]) فقط **پس از** نشستنِ دفتر نوشته می‌شود ([Plan.commit])؛ عقب ماندنش را `AuditChain.resume` می‌پذیرد.
 *  - هیچ شکستی نوشتنِ دفتر را نمی‌گیرد (`ShopStore` در شکست همان دفتر را بی مُهر می‌نویسد).
 */
object AuditSeal {
  /**
   *  سطحِ مدلِ `AuditEntry` (`chainV`): خواننده‌ای که سطحِ پایین‌تری دارد فیلدی را نمی‌شناسد و محتوای ردیف را
   *  نمی‌سنجد (فقط پیوند). ⛔ هر فیلدِ تازه در `AuditEntry` یا سندهای درونش ⇐ این +۱ و فهرستِ `AuditSealTest.SCHEMA`.
   */
  const val LEVEL = 1L

  /** نشستِ همان لحظه؛ [chainOn] = پرچمِ `shop.doc_chain` ِ همین دکان */
  class Session(val stamp: AuditChain.Stamp, val chainOn: Boolean)

  /** لنگرِ محلیِ زنجیره (پرونده‌ای بیرون از پشتیبان) */
  interface AnchorStore {
    fun read(): AuditChain.Anchors?
    fun write(anchors: AuditChain.Anchors)
  }

  /** دفترِ مُهرخورده و لنگرِ تازه — [commit] فقط پس از نشستنِ [data] روی دیسک */
  class Plan(val data: ShopData, private val anchors: AuditChain.Anchors? = null, private val store: AnchorStore? = null) {
    fun commit() {
      val a = anchors ?: return
      runCatching { store?.write(a) }
    }
  }

  /** همان پیکربندیِ دفتر و `LedgerDiff`، تا درختِ هش‌شده همان باشد که به سرور می‌رود */
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  fun tree(e: AuditEntry): JsonObject = json.encodeToJsonElement(AuditEntry.serializer(), e).jsonObject
  private fun entry(o: JsonObject): AuditEntry = json.decodeFromJsonElement(AuditEntry.serializer(), o)

  fun linkOf(e: AuditEntry): AuditChain.Link? {
    val chain = e.chain ?: return null
    val seq = e.chainSeq ?: return null
    return AuditChain.Link(chain, seq, e.prev ?: return null, e.hash ?: return null, e.chainV ?: 1)
  }

  private fun sealOf(e: AuditEntry) = listOf(e.userId, e.userRole, e.deviceId, e.chain, e.chainSeq, e.prev, e.hash, e.chainV)

  private fun AuditEntry.withSealOf(p: AuditEntry) = copy(
    userId = p.userId, userRole = p.userRole, deviceId = p.deviceId,
    chain = p.chain, chainSeq = p.chainSeq, prev = p.prev, hash = p.hash, chainV = p.chainV,
  )

  private fun fresh(e: AuditEntry) = e.deviceId == null && e.userId == null && e.chain == null && e.hash == null

  /**
   *  @param prior دفترِ همین حالای دیسک (منبعِ مُهر و حلقهٔ ردیف‌های موجود)
   *  @param base  دفتری که [next] از آن ساخته شد
   *  @param next  دفتری که نوشته می‌شود (ذخیرهٔ صفحه، یا ادغامِ آن روی [prior])
   *  @param session فقط وقتی ردیفِ تازه‌ای هست خوانده می‌شود؛ `null` ⇐ بی مُهر
   */
  fun plan(prior: ShopData, base: ShopData, next: ShopData, session: () -> Session?, anchors: AnchorStore?): Plan {
    val list = next.auditLog
    if (list === prior.auditLog || list.isEmpty()) return Plan(next)
    val before = prior.auditLog.associateBy { it.id }
    val baseIds = if (base === prior) before.keys else base.auditLog.mapTo(HashSet()) { it.id }
    val out = ArrayList<AuditEntry>(list.size)
    val added = ArrayList<Int>()
    var changed = false
    for (r in list) {
      val p = before[r.id]
      when {
        p != null -> if (sealOf(p) != sealOf(r)) { out += r.withSealOf(p); changed = true } else out += r
        r.id !in baseIds && fresh(r) -> { added += out.size; out += r }
        else -> out += r
      }
    }
    val s = if (added.isEmpty()) null else session()
    if (s == null) return if (changed) Plan(next.copy(auditLog = out)) else Plan(next)
    val stamped = added.associateWith { AuditChain.stamped(tree(out[it]), s.stamp) }
    if (!s.chainOn || anchors == null) {
      stamped.forEach { (i, t) -> out[i] = entry(t) }
      return Plan(next.copy(auditLog = out))
    }
    val was = anchors.read()
    var head = AuditChain.resume(s.stamp.deviceId, out.mapNotNull(::linkOf), was)
    for (i in added) {
      val (row, h) = AuditChain.linked(stamped.getValue(i), head, LEVEL)
      out[i] = entry(row)
      head = h
    }
    return Plan(next.copy(auditLog = out), (was ?: AuditChain.Anchors()).with(head), anchors)
  }

  /** سنجشِ سابقهٔ همین دفتر روی دستگاه (صفحهٔ «سابقهٔ عملیات») */
  fun verify(log: List<AuditEntry>, anchors: AuditChain.Anchors?): AuditChain.Report =
    AuditChain.verify(log.map(::tree), anchors, strict = false, known = LEVEL)
}
