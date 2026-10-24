package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 *  commitِ ژورنالیِ حالِ همگام‌سازی — F0-05، تا فاز ۱ (تراکنشِ SQLite).
 *
 *  ── مشکل ─────────────────────────────────────────────────────────────
 *  حالِ همگام‌سازی سه تکهٔ جدا روی دیسک است: دفتر (`shop-data.json`)، صفِ
 *  opها (`oplog-*.json`) و سایه + cursor (`SharedPreferences`، با `apply()`
 *  ِ ناهمگام). اگر برنامه وسطِ این نوشتن‌ها بسته شود:
 *   • ثبتِ محلی: opها در صف هستند ولی سایه کهنه مانده ⇒ ذخیرهٔ بعدی همان
 *     تغییرها را با شناسهٔ **تازه** دوباره op می‌کند ⇒ سرور `$inc`ها
 *     (پرداخت، باقی، مرجوعی…) را دو بار جمع می‌زند.
 *   • نشاندنِ opهای رسیده: دفتر نوشته شده ولی سایه و cursor نه ⇒ همان opها
 *     دوباره گرفته و روی دفتر جمع می‌شوند (دو برابرِ محلی) و تفاضلِ بعدی
 *     آن‌ها را «تغییرِ من» به سرور پس می‌فرستد.
 *
 *  ── راه ──────────────────────────────────────────────────────────────
 *  پیش از چند نوشتن، یک **ژورنال** (نشانگرِ مرحله) با fsync روی دیسک
 *  می‌نشیند؛ نوشتن‌ها به ترتیب انجام می‌شوند و سایه/cursor **هم‌زمان**
 *  (`commit`) و در یک نوشتن؛ آخرِ کار ژورنال پاک می‌شود. اگر برنامه وسطِ
 *  کار بسته شود، هنگامِ شروع (`ShopStore.load`) `recover` از روی ژورنال و
 *  آنچه واقعاً روی دیسک است تصمیم می‌گیرد: یا کار را تمام می‌کند یا به
 *  حالِ پیش از آن برمی‌گرداند — هیچ‌وقت نیمه.
 *
 *  ژورنال کوچک است: اثرِ انگشت (SHA-256) سایه و دفتر، cursor و همان opها —
 *  نه کلِ دفتر.
 *
 *  ⚠️ opی که دوباره به صف برگردد همان `op_id` را دارد و سرور آن را
 *  «تکراری» می‌شمارد، نه دوباره جمع. خطر فقط در opِ **تازه** برای همان
 *  تغییر بود، و ژورنال جلوی همان را می‌گیرد.
 */
internal class SyncCommit(private val journal: Journal, private val parts: Parts) {

  /** سه تکه‌ای که با هم عوض می‌شوند (جز دفتر) — در آزمون با نمونهٔ حافظه‌ای */
  interface Parts {
    /** سایهٔ نشسته (متنِ JSON)؛ خالی یعنی هنوز هیچ */
    fun shadow(): String

    /** سایه و cursor با هم و **همزمان** — شکست یعنی پرتاب */
    fun commit(shadow: String, cursor: Long?)

    fun queued(): Set<String>
    fun push(ops: List<SyncOp>)
  }

  /**
   *  نتیجهٔ بازیابی. `STALE` یعنی ژورنالی که دیگر به سایهٔ امروز نمی‌خورد
   *  (پاک نشده بود): کنار می‌رود و به هیچ چیزی دست نمی‌زند.
   */
  enum class Outcome { NONE, ALREADY, FORWARD, BACK, STALE, MISMATCH }

  /**
   *  ثبتِ محلی: تفاضلِ دفترِ تازه با سایه ⇐ opها در صف و سایهٔ تازه، با هم.
   *  دفتر پیش از این روی دیسک نشسته است (`ShopStore.writeInternal`).
   *
   *  @return چند op ساخته شد
   */
  fun record(data: ShopData): Int {
    //  ژورنالی که هنگامِ شروع بازیابی نشد (نباید پیش بیاید) — دفترِ آن لحظه
    //  دیگر روی دیسک نیست؛ `recover` بی دفتر هم تصمیمِ امن می‌گیرد
    if (journal.exists()) recover(null)
    val before = parts.shadow()
    val tree = LedgerDiff.toJson(data)
    val shadow = parse(before)
    if (shadow == null) {
      /*
       *  نخستین بار روی این دستگاه: دفترِ امروز «تغییر» نیست، حالتِ پایه
       *  است. فرستادنِ دفترِ موجود کارِ `baseline()` است و فقط یک بار.
       */
      parts.commit(LedgerDiff.shadowOf(tree).toString(), null)
      return 0
    }
    val ops = LedgerDiff.diff(shadow, tree)
    if (ops.isEmpty()) return 0
    commitOps(ops, before, LedgerDiff.shadowOf(tree).toString())
    return ops.size
  }

  /** کلِ دفتر یک‌جا — فقط دستگاهِ نخست (`SyncV1Engine.baseline`). */
  fun baseline(data: ShopData): Int {
    if (journal.exists()) recover(null)
    val before = parts.shadow()
    val tree = LedgerDiff.toJson(data)
    val ops = LedgerDiff.diff(null, tree)
    commitOps(ops, before, LedgerDiff.shadowOf(tree).toString())
    return ops.size
  }

  /**
   *  بایگانیِ دفتر (`LedgerArchive.compact`): ردیف‌هایی که از دفتر به بایگانی
   *  می‌روند از سایه هم بیرون می‌آیند — بی هیچ op.
   *
   *  ⛔ بی این، ذخیرهٔ دفترِ کوتاه‌شده هر ردیفِ بایگانی‌شده را «حذف‌شده»
   *  می‌دید و DELETE به سرور و همهٔ دستگاه‌های دیگر می‌رفت (بازبینیِ F0-01:
   *  پیش‌تر فقط سایهٔ همگام‌سازیِ قدیمی هرس می‌شد).
   *
   *  پیش از ذخیرهٔ دفترِ کوتاه صدا زده می‌شود. اگر برنامه میانِ این دو بسته
   *  شود، تفاضلِ بعدی همان ردیف‌ها را **درج** می‌فرستد (سرور دارد، همان
   *  مقدار) — هرگز حذف. شکستِ نوشتنِ سایه پرتاب می‌شود تا دفتر کوتاه نشود.
   *
   *  @param gone نامِ مجموعه ⇐ شناسهٔ ردیف‌ها
   *  @return چند ردیف از سایه بیرون آمد؛ `0` اگر هنوز سایه‌ای نیست
   */
  fun forget(gone: Map<String, Collection<String>>): Int {
    if (journal.exists()) recover(null)
    val shadow = parse(parts.shadow()) ?: return 0
    var removed = 0
    val next = JsonObject(
      shadow.mapValues { (table, rows) ->
        val ids = gone[table]?.toSet()
        if (ids.isNullOrEmpty() || table !in LedgerDiff.COLLECTIONS || rows !is JsonArray) return@mapValues rows
        val left = rows.filterNot { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.content in ids }
        removed += rows.size - left.size
        JsonArray(left)
      },
    )
    if (removed == 0) return 0
    parts.commit(next.toString(), null)
    return removed
  }

  /** ژورنال ⇐ صف ⇐ سایه (همزمان) ⇐ پاک کردنِ ژورنال */
  private fun commitOps(ops: List<SyncOp>, before: String, shadow: String) {
    journal.write(Journal.Entry(Journal.RECORD, before = sha(before), shadow = sha(shadow), ops = ops))
    parts.push(ops)
    parts.commit(shadow, null)
    journal.clear()
  }

  /**
   *  نشاندنِ opهای رسیده: ژورنال ⇐ دفتر ⇐ سایه + cursor ⇐ پاک کردنِ ژورنال.
   *
   *  سایه = سایهٔ پیشین + همین opها (نه کلِ دفترِ امروز)، تا تغییرِ محلی‌ای
   *  که هنوز op نشده گم نشود — همان قاعدهٔ پیشینِ `absorbRemote`.
   *
   *  @param cursor cursorِ پس از این opها؛ `null` یعنی دست نخورد
   */
  fun absorb(ops: List<SyncOp>, current: ShopData, cursor: Long?, writeLedger: (ShopData) -> Unit) {
    if (journal.exists()) recover(current)
    val ledger = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(current), ops))
    val tree = LedgerDiff.toJson(ledger)
    val before = parts.shadow()
    val shadow = absorbedShadow(before, ops, tree)
    journal.write(
      Journal.Entry(
        Journal.ABSORB, before = sha(before), shadow = sha(shadow),
        ledger = sha(tree.toString()), cursor = cursor, ops = ops,
      ),
    )
    writeLedger(ledger)
    parts.commit(shadow, cursor)
    journal.clear()
  }

  fun pending(): Boolean = journal.exists()

  /**
   *  SEC-01 — دفترِ رمزدارِ خوانده‌نشده: opهای ژورنالِ «ثبت» (همان `op_id`) به صف می‌روند تا
   *  فرستاده شوند، **بی** دست زدن به سایه یا ژورنال. `recover` ِ بعدی (با دفترِ خوانده‌شده) همان
   *  را تمام می‌کند و چون صف را می‌بیند opی دوباره نمی‌گذارد؛ ژورنالِ «نشاندن» دست نمی‌خورد، چون
   *  بی دفتر نمی‌شود دانست نشسته بود یا نه.
   *
   *  @return چند op به صف رفت
   */
  fun queueRecorded(): Int {
    val j = journal.read() ?: return 0
    if (j.stage != Journal.RECORD || sha(parts.shadow()) != j.before) return 0
    val have = parts.queued()
    val fresh = j.ops.filter { it.opId !in have }
    if (fresh.isNotEmpty()) parts.push(fresh)
    return fresh.size
  }

  /**
   *  A00 — درمانِ سایهٔ ساختِ پیش از A00، یک بار: هنگامِ شروع (`recover` ِ موتورها) با
   *  دفترِ روی دیسک و **پیش از نخستین تفاضل**. سایهٔ بی مهر (`LedgerDiff.SHADOW_STAMP`)
   *  یعنی ساختِ پیشین نوشته‌اش؛ سایهٔ Snapshot ِ آن ساخت‌ها ردیفِ خامِ سرور بود و کلیدهایی
   *  داشت که دفترِ آن ساخت نمی‌شناخت. این ساخت آن کلیدها را می‌شناسد ولی دفتر پیش‌فرضشان
   *  را دارد، پس بی درمان نخستین ذخیرهٔ بی‌ربط پیش‌فرض را روی مقدارِ سرور می‌نوشت
   *  (`LedgerDiff.healShadow`). سایهٔ درمان‌شده با مهر نوشته می‌شود؛ بارِ بعد کاری نمی‌کند.
   *
   *  @return true اگر سایه درمان و مهر شد
   */
  fun heal(ledger: ShopData, force: Boolean = false): Boolean {
    if (journal.exists()) recover(ledger)
    val shadow = parse(parts.shadow()) ?: return false
    if (!force && LedgerDiff.isStamped(shadow)) return false
    val healed = LedgerDiff.modelTree(LedgerDiff.healShadow(shadow, LedgerDiff.toJson(ledger)))
    parts.commit(LedgerDiff.shadowOf(healed).toString(), null)
    return true
  }

  /** نتیجهٔ `start`: بازیابیِ ژورنال، درمان، و نشانِ opهای ساختِ پیشین در صف ("" یعنی هیچ) */
  data class Start(val outcome: Outcome, val healed: Boolean, val legacyUpTo: String)

  /**
   *  شروعِ برنامه (`recover` ِ موتورهای v1 و v3) با دفترِ روی دیسک — A00:
   *   ۱) مهرِ سایه **پیش از** بازیابی خوانده می‌شود: بازیابیِ ژورنالِ «ثبت» ِ ساختِ پیشین
   *      سایه‌ای مهردار ولی درمان‌نشده می‌نویسد (شاخهٔ «دفترِ آن لحظه در دست نیست»)؛
   *   ۲) ژورنال بازیابی می‌شود (opهای ساختِ پیشین با همان `op_id` در صف برمی‌گردند)؛
   *   ۳) اگر سایه بی مهر بود: بزرگ‌ترین `op_id` ِ صف (ULID به ترتیبِ زمان است) **پیش از** مهر
   *      به [keepLegacy] سپرده می‌شود — دسته‌ای که op ِ کوچک‌تر یا برابر دارد بی `caps` می‌رود
   *      تا تورِ سرور `null` ِ کهنهٔ آن ساخت را بپالاید (`LedgerDiff.capsFor`) — و بعد درمان.
   *      اگر برنامه میانِ این دو بسته شود، سایه هنوز بی مهر است و بارِ بعد همین تکرار می‌شود.
   */
  fun start(ledger: ShopData, keepLegacy: (String) -> Unit = {}): Start {
    val wasStamped = parse(parts.shadow())?.let { LedgerDiff.isStamped(it) } ?: true
    val outcome = if (journal.exists()) recover(ledger) else Outcome.NONE
    if (wasStamped) return Start(outcome, false, "")
    val upTo = parts.queued().maxOrNull().orEmpty()
    if (upTo.isNotEmpty()) keepLegacy(upTo)
    return Start(outcome, heal(ledger, force = true), upTo)
  }

  /** همان سایه با یا بی مهرِ A00 — ژورنالِ ساختِ پیشین اثرِ انگشتِ سایهٔ بی مهر را دارد */
  private fun same(candidate: String, target: String): Boolean =
    sha(candidate) == target || sha(LedgerDiff.unstamped(candidate)) == target

  /**
   *  بازیابی هنگامِ شروع (`ShopStore.load`) یا پیش از کارِ بعدی.
   *
   *  | سایهٔ روی دیسک        | تصمیم                                              |
   *  |-----------------------|----------------------------------------------------|
   *  | همان «پس از»          | کار تمام شده بود (cursor هم در همان commit)        |
   *  | نه «پیش از» نه «پس از» | ژورنالِ کهنه — کنار می‌رود، به چیزی دست نمی‌خورد   |
   *  | همان «پیش از»، ثبت    | opها (همان `op_id`) در صف و سایه جلو — تمام می‌شود |
   *  | همان «پیش از»، نشاندن | دفتر نشسته؟ تمام می‌شود : برمی‌گردد (Pullِ دوباره)  |
   *
   *  @param ledger دفترِ همین حالا روی دیسک؛ `null` یعنی معلوم نیست.
   */
  fun recover(ledger: ShopData?): Outcome {
    val j = journal.read()
    if (j == null) {
      if (!journal.exists()) return Outcome.NONE
      //  ژورنالِ نصفه: پیش از نقطهٔ commit بسته شده؛ چیزی عوض نشده بود
      journal.clear()
      return Outcome.BACK
    }
    val current = parts.shadow()
    val now = sha(current)
    val outcome = when {
      now == j.shadow -> Outcome.ALREADY
      now != j.before -> Outcome.STALE

      j.stage == Journal.RECORD -> {
        //  opها با همان `op_id` — اگر پیش‌تر رفته باشند سرور «تکراری» می‌گوید
        val have = parts.queued()
        parts.push(j.ops.filter { it.opId !in have })
        val mine = ledger?.let { LedgerDiff.shadowOf(LedgerDiff.toJson(it)).toString() }
        val next = if (mine != null && same(mine, j.shadow)) {
          mine
        } else {
          //  دفترِ آن لحظه در دست نیست: سایهٔ پیشین + همین opها. بیشترین
          //  خطایش گردکردنِ اعشار در `$inc` است که تفاضلِ بعدی صافش می‌کند
          LedgerDiff.shadowOf(LedgerDiff.applyRemote(parse(current) ?: JsonObject(emptyMap()), j.ops)).toString()
        }
        parts.commit(next, null)
        Outcome.FORWARD
      }

      j.stage == Journal.ABSORB -> {
        //  ⚠️ (پیشین، پیگیریِ A00) اثرِ انگشتِ دفتر به مدلِ همین ساخت بسته است: ژورنالی که ساختِ
        //  پیشین نوشته و پس از آن مدل فیلدی گرفته، این‌جا «ننشسته» خوانده می‌شود (BACK) حتی اگر
        //  دفتر نشسته بود ⇐ همان opها دوباره گرفته می‌شوند. این‌جا درست نشده است.
        val tree = ledger?.let { LedgerDiff.toJson(it) }
        if (tree == null || sha(tree.toString()) != j.ledger) {
          //  دفتر ننشسته بود ⇒ هیچ چیزی عوض نشده؛ همان opها دوباره گرفته می‌شوند
          Outcome.BACK
        } else {
          val next = absorbedShadow(current, j.ops, tree)
          if (same(next, j.shadow)) {
            parts.commit(next, j.cursor)
            Outcome.FORWARD
          } else {
            Outcome.MISMATCH
          }
        }
      }

      else -> Outcome.BACK
    }
    journal.clear()
    return outcome
  }

  /** سایه پس از opهای رسیده — همان قاعدهٔ `absorbRemote` */
  private fun absorbedShadow(before: String, ops: List<SyncOp>, ledgerTree: JsonObject): String {
    val base = parse(before)
    return LedgerDiff.shadowOf(if (base != null) LedgerDiff.applyRemote(base, ops) else ledgerTree).toString()
  }

  private fun parse(raw: String): JsonObject? =
    if (raw.isBlank()) null else runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

  /**
   *  ژورنال روی دیسک — یک فایل، نوشته با fsync.
   *
   *  جابه‌جایی (rename) ندارد: فایلِ نصفه JSONِ درستی نیست و «نبود» خوانده
   *  می‌شود — یعنی بسته شدن پیش از نقطهٔ commit، که درست همان است.
   */
  class Journal(private val file: File) {

    /**
     *  @param before اثرِ انگشتِ سایه پیش از کار
     *  @param shadow اثرِ انگشتِ سایه پس از کار
     *  @param ledger اثرِ انگشتِ دفترِ پس از کار (فقط نشاندن)
     */
    data class Entry(
      val stage: String,
      val before: String,
      val shadow: String,
      val ledger: String = "",
      val cursor: Long? = null,
      val ops: List<SyncOp> = emptyList(),
    )

    fun exists(): Boolean = file.exists()

    fun write(e: Entry) {
      val body = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("stage", JsonPrimitive(e.stage))
        put("before", JsonPrimitive(e.before))
        put("shadow", JsonPrimitive(e.shadow))
        put("ledger", JsonPrimitive(e.ledger))
        e.cursor?.let { put("cursor", JsonPrimitive(it)) }
        put("ops", JsonArray(e.ops.map { it.toJson() }))
      }.toString().toByteArray(Charsets.UTF_8)
      file.parentFile?.mkdirs()
      if (file.exists() && !file.delete()) throw IOException("ژورنالِ کهنه پاک نشد")
      FileOutputStream(file).use { out ->
        out.write(body)
        out.flush()
        out.fd.sync()
      }
    }

    fun read(): Entry? {
      if (!file.exists()) return null
      return runCatching {
        val o = Json.parseToJsonElement(file.readText(Charsets.UTF_8)) as JsonObject
        Entry(
          stage = (o["stage"] as JsonPrimitive).content,
          before = (o["before"] as JsonPrimitive).content,
          shadow = (o["shadow"] as JsonPrimitive).content,
          ledger = (o["ledger"] as? JsonPrimitive)?.content.orEmpty(),
          cursor = (o["cursor"] as? JsonPrimitive)?.content?.toLongOrNull(),
          ops = (o["ops"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(SyncOp::fromJson) },
        )
      }.getOrNull()
    }

    /** پاک کردن؛ اگر نشد، خالی — فایلِ خالی «نبود» خوانده می‌شود */
    fun clear() {
      if (file.delete() || !file.exists()) return
      runCatching { FileOutputStream(file).use { it.fd.sync() } }
    }

    companion object {
      const val RECORD = "record"
      const val ABSORB = "absorb"
    }
  }

  companion object {
    fun sha(s: String): String =
      MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
  }
}
