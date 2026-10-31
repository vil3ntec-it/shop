package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.SyncEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  SEC-01 — «بازسازی از سرور» برای دفتری که این دستگاه باز نمی‌کند (بازبینیِ #331).
 *
 *  ── چه چیزی خراب بود ─────────────────────────────────────────────────
 *  گوشیِ تازه (بازیابیِ گوگل / انتقال) دفترِ رمزدار و سایهٔ پُر و صف و cursor را می‌گرفت ولی
 *  کلیدِ Keystore را نه. تنها راهِ پیشنهادی بازیابیِ یک پشتیبان بود ⇐ `record` پشتیبانِ کهنه را
 *  با سایهٔ تازه‌تر تفاضل می‌گرفت ⇐ برای هر ردیفی که پس از پشتیبان عوض شده بود DELETE یا
 *  برگرداندن به سرور می‌رفت — حتی ردیف‌های همگام‌شده و INSERTهای در صف.
 *
 *  ── راه ───────────────────────────────────────────────────────────────
 *   ۱) صف **اول** تا ته فرستاده می‌شود (صف رمزدار نیست — `AtRestScope`). صف خالی نشد ⇒ هیچ
 *      چیزی عوض نمی‌شود (`QueueLeft`)؛ وگرنه آن تغییرها پس از بازسازی گم می‌شدند.
 *   ۲) Snapshot: ردیف‌ها **فقط** از سرور؛ از `base` فقط بخش‌های بیرون از همگام‌سازی (تنظیمات)؛
 *      شمارندهٔ فاکتور هرگز پایین‌تر از بلندترین شمارهٔ مصرف‌شده (`SyncEngine.withInvoiceCursor`).
 *   ۳) دفترِ تازه در فایلِ کناری + نشانِ «بازسازیِ نیمه‌کاره» با اثرِ سایهٔ تازه (`stage`) —
 *      دفترِ قبلی هنوز دست‌نخورده.
 *   ۴) سایه و cursor با هم (`commitShadow`).
 *   ۵) جابه‌جایی (`swap`): دفترِ قبلی کنار، کناری جایش، نشان پاک — **بی هیچ op** (دفتر = سایه).
 *  اگر میانِ ۴ و ۵ بسته شود، هنگامِ باز شدن (`atLoad`) سایه با نشان می‌خواند ⇒ جابه‌جایی تمام
 *  می‌شود؛ هرگز دفترِ کهنه (که شاید با برگشتنِ کلید خواندنی شده باشد) با سایهٔ تازه تفاضل
 *  گرفته نمی‌شود. پیش از ۴ بسته شود ⇒ کناری و نشان دور ریخته می‌شوند و چیزی عوض نشده است.
 *
 *  پشتیبانی که در همین حال بازیابی شود پس از این فقط ردیف‌هایی را می‌افزاید که **هرگز در
 *  سایهٔ پیش از بازسازی نبودند** (`withBackupRows`): ردیفی که بود و حالا سرور ندارد، پس از
 *  پشتیبان حذف شده است و زنده نمی‌شود. سایهٔ پیشین نیست ⇒ هیچ ردیفی افزوده نمی‌شود.
 *
 *  ⛔ بی اندروید و بی شبکه تا روی JVM آزموده شود (`NewPhoneRebuildTest`)؛ موتور (`SyncV1Engine`)
 *  شبکه، صف و prefs را می‌دهد و `ShopStore` فایل‌ها را.
 */
internal class SyncRebuild(private val io: Io) {

  interface Io {
    /** چند op هنوز در صف است */
    fun queued(): Int

    /** فرستادنِ صف تا ته؛ شکست (شبکه، ۴۲۶) پرتاب می‌شود */
    suspend fun push()

    /** سایهٔ همین حالا (پیش از بازسازی) */
    fun shadow(): String

    /** پاسخِ `snapshot` ِ سرور: `{tables, cursor, rows}` */
    suspend fun snapshot(): JsonObject

    /** دفترِ تازه در فایلِ کناری + نشانی با `shadowSha`؛ دفترِ قبلی دست نمی‌خورد. ننشست ⇒ پرتاب */
    fun stage(next: ShopData, shadowSha: String)

    /** سایه و cursor با هم و هم‌زمان؛ ننشست ⇒ پرتاب */
    fun commitShadow(shadow: String, cursor: Long)

    /** کناری جای دفترِ خوانده‌نشده، بی op؛ ننشست ⇒ پرتاب (هنگامِ باز شدن تمام می‌شود) */
    fun swap()
  }

  sealed class Outcome {
    /**
     *  @param rows شمارِ ردیف‌های Snapshot
     *  @param before سایهٔ پیش از بازسازی — برای `withBackupRows`
     */
    data class Done(val rows: Int, val ledger: ShopData, val before: JsonObject?) : Outcome()

    /** صف خالی نشد؛ هیچ چیزی عوض نشد */
    data class QueueLeft(val count: Int) : Outcome()
  }

  enum class AtLoad { NONE, FINISH, DISCARD }

  /** فایل‌های دفتر (`ShopStore`): کناری + نشان، و جابه‌جایی — همان دو گامِ `Io` */
  interface Staging {
    fun stage(next: ShopData, shadowSha: String)
    fun swap()
  }

  suspend fun run(base: ShopData): Outcome {
    io.push()
    val left = io.queued()
    if (left > 0) return Outcome.QueueLeft(left)
    val before = parse(io.shadow())
    val body = io.snapshot()
    val tree = fromServer(body, base)
    val shadow = shadowOf(tree)
    val ledger = SyncEngine.withInvoiceCursor(LedgerDiff.fromJson(tree))
    io.stage(ledger, sha(shadow))
    io.commitShadow(shadow, cursorOf(body))
    io.swap()
    return Outcome.Done(rowsOf(body), ledger, before)
  }

  companion object {

    /** ردیف‌ها فقط از سرور؛ از `base` فقط آنچه همگام نمی‌شود — از درِ مدلِ همین ساخت (A00) */
    fun fromServer(snapshot: JsonObject, base: ShopData): JsonObject {
      val bare = LedgerDiff.toJson(base).filterKeys { it !in LedgerDiff.COLLECTIONS && it !in LedgerDiff.LIST_COLLECTIONS }
      return LedgerDiff.modelTree(LedgerDiff.fromSnapshot(snapshot, JsonObject(bare)))
    }

    /** همان سایه‌ای که `SyncV1Engine.writeShadow` می‌نویسد */
    fun shadowOf(tree: JsonObject): String = LedgerDiff.shadowOf(LedgerDiff.modelTree(tree)).toString()

    /** بی سرور (هیچ‌وقت همگام نشده): دفترِ بازیابی‌شده خودش سایه است ⇐ هیچ op */
    fun shadowFor(ledger: ShopData): String = shadowOf(LedgerDiff.toJson(ledger))

    /**
     *  دفترِ سرور به‌اضافهٔ ردیف‌هایی از پشتیبان که سرور ندارد **و هرگز در سایهٔ پیشین نبودند**
     *  (هیچ‌وقت همگام نشده بودند). ردیفی که در سایه بود و سرور ندارد پس از پشتیبان حذف شده است
     *  و زنده نمی‌شود (سرور ردیفِ حذف‌شده را با INSERT زنده می‌کرد). ردیفِ مشترک همان نسخهٔ سرور.
     *  `before == null` ⇒ نمی‌دانیم کدام همگام شده بود ⇒ هیچ ردیفی افزوده نمی‌شود.
     */
    fun withBackupRows(server: ShopData, backup: ShopData, before: JsonObject?): ShopData {
      if (before == null) return server
      val tree = LedgerDiff.toJson(backup)
      val fresh = LinkedHashMap<String, JsonElement>(tree)
      for (key in LedgerDiff.COLLECTIONS) {
        val seen = (before[key] as? JsonArray).orEmpty().mapNotNullTo(HashSet()) { idOf(it) }
        val rows = tree[key] as? JsonArray ?: continue
        fresh[key] = JsonArray(rows.filter { idOf(it) !in seen })
      }
      for (key in LedgerDiff.LIST_COLLECTIONS) {
        //  سایهٔ بی این کلید یعنی «هنوز هیچ‌وقت نرفته» (F0-04) ⇒ همهٔ نام‌ها تازه‌اند
        val seen = (before[key] as? JsonArray ?: continue).mapNotNullTo(HashSet()) { (it as? JsonPrimitive)?.content }
        val names = tree[key] as? JsonArray ?: continue
        fresh[key] = JsonArray(names.filter { (it as? JsonPrimitive)?.content !in seen })
      }
      //  فروشی که از پشتیبان آمد شاید شمارهٔ فاکتورِ بلندتری داشته باشد ⇒ کف دوباره (پیگیریِ #331)
      return SyncEngine.withInvoiceCursor(LedgerDiff.fromJson(LedgerDiff.unionRows(LedgerDiff.toJson(server), JsonObject(fresh))))
    }

    /** مجموعه ⇐ شناسهٔ ردیف‌ها — برای `RestoreRoute.notOnServer` (پیامِ بازیابیِ دستگاهِ همگام، S143) */
    fun rowIds(d: ShopData): Map<String, List<String>> {
      val tree = LedgerDiff.toJson(d)
      return LedgerDiff.COLLECTIONS.associateWith { key -> (tree[key] as? JsonArray).orEmpty().mapNotNull { idOf(it) } }
    }

    /**
     *  جابه‌جایی را کسِ دیگری (کارگرِ پس‌زمینه با نمونهٔ دیگرِ `ShopStore`) زودتر تمام کرده؟
     *  نشان و کناری رفته‌اند و دفترِ روی دیسک همان است که این بازسازی گذاشت.
     */
    fun swappedElsewhere(sidecar: Boolean, marker: Boolean, onDisk: ShopData?, staged: ShopData?): Boolean =
      !sidecar && !marker && staged != null && onDisk == staged

    /**
     *  هنگامِ باز شدنِ برنامه، اگر بازسازی نیمه‌کاره ماند.
     *  @param marker اثرِ سایه‌ای که در نشان نوشته شد؛ `null` یعنی نشانی نیست
     *  @param shadowSha اثرِ سایهٔ همین حالا (`sha`)
     */
    fun atLoad(marker: String?, sidecar: Boolean, shadowSha: String): AtLoad = when {
      marker == null -> if (sidecar) AtLoad.DISCARD else AtLoad.NONE
      marker == shadowSha && sidecar -> AtLoad.FINISH
      else -> AtLoad.DISCARD
    }

    fun sha(s: String): String =
      java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun cursorOf(snapshot: JsonObject): Long = (snapshot["cursor"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

    private fun rowsOf(snapshot: JsonObject): Int = (snapshot["rows"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

    private fun idOf(e: JsonElement): String? = ((e as? JsonObject)?.get("id") as? JsonPrimitive)?.content

    private fun parse(raw: String): JsonObject? =
      if (raw.isBlank()) null else runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
  }
}
