package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.ShopData
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
 *   ۲) Snapshot: ردیف‌ها **فقط** از سرور؛ از `base` فقط بخش‌های بیرون از همگام‌سازی (تنظیمات).
 *   ۳) سایه و cursor **با هم** و **پیش از** دفتر (`commitShadow`). تا وقتی دفترِ کهنه هنوز
 *      خوانده‌نشده است هیچ نوشتنی به آن نمی‌رسد؛ پس اگر میانِ ۳ و ۴ بسته شود، دفتر هنوز
 *      خوانده‌نشده است و همین کار دوباره انجام می‌شود — هیچ تفاضلی با سایهٔ کهنه گرفته نمی‌شود.
 *   ۴) دفتر جای دفترِ خوانده‌نشده می‌نشیند، **بی هیچ op** (دفتر = سایه).
 *
 *  پشتیبانی که در همین حال بازیابی شود (`ShopStore.applyRestore` و …) پس از این فقط
 *  ردیف‌هایی را می‌افزاید که سرور ندارد (`withBackupRows`) ⇐ فقط INSERT، هرگز DELETE.
 *
 *  ⛔ بی اندروید و بی شبکه تا روی JVM آزموده شود (`NewPhoneRebuildTest`)؛ موتور (`SyncV1Engine`)
 *  شبکه، صف و prefs را می‌دهد.
 */
internal class SyncRebuild(private val io: Io) {

  interface Io {
    /** چند op هنوز در صف است */
    fun queued(): Int

    /** فرستادنِ صف تا ته؛ شکست (شبکه، ۴۲۶) پرتاب می‌شود */
    suspend fun push()

    /** پاسخِ `snapshot` ِ سرور: `{tables, cursor, rows}` */
    suspend fun snapshot(): JsonObject

    /** سایه و cursor با هم و هم‌زمان؛ ننشست ⇒ پرتاب */
    fun commitShadow(shadow: String, cursor: Long)

    /** دفترِ تازه جای دفترِ خوانده‌نشده، بی op؛ ننشست ⇒ پرتاب */
    fun writeLedger(next: ShopData)
  }

  sealed class Outcome {
    /** @param rows شمارِ ردیف‌های Snapshot */
    data class Done(val rows: Int, val ledger: ShopData) : Outcome()

    /** صف خالی نشد؛ هیچ چیزی عوض نشد */
    data class QueueLeft(val count: Int) : Outcome()
  }

  suspend fun run(base: ShopData): Outcome {
    io.push()
    val left = io.queued()
    if (left > 0) return Outcome.QueueLeft(left)
    val body = io.snapshot()
    val tree = fromServer(body, base)
    io.commitShadow(shadowOf(tree), cursorOf(body))
    val ledger = LedgerDiff.fromJson(tree)
    io.writeLedger(ledger)
    return Outcome.Done(rowsOf(body), ledger)
  }

  companion object {

    /** ردیف‌ها فقط از سرور؛ از `base` فقط آنچه همگام نمی‌شود — از درِ مدلِ همین ساخت (A00) */
    fun fromServer(snapshot: JsonObject, base: ShopData): JsonObject {
      val bare = LedgerDiff.toJson(base).filterKeys { it !in LedgerDiff.COLLECTIONS && it !in LedgerDiff.LIST_COLLECTIONS }
      return LedgerDiff.modelTree(LedgerDiff.fromSnapshot(snapshot, JsonObject(bare)))
    }

    /** همان سایه‌ای که `SyncV1Engine.writeShadow` می‌نویسد */
    fun shadowOf(tree: JsonObject): String = LedgerDiff.shadowOf(LedgerDiff.modelTree(tree)).toString()

    /** بی سرور (هنوز وارد نشده): دفترِ بازیابی‌شده خودش سایه است ⇐ هیچ op؛ پس از ورود `prepare` هم‌تراز می‌کند */
    fun shadowFor(ledger: ShopData): String = shadowOf(LedgerDiff.toJson(ledger))

    /** دفترِ سرور به‌اضافهٔ ردیف‌هایی از پشتیبان که سرور ندارد — ردیفِ مشترک همان نسخهٔ سرور می‌ماند */
    fun withBackupRows(server: ShopData, backup: ShopData): ShopData =
      LedgerDiff.fromJson(LedgerDiff.unionRows(LedgerDiff.toJson(server), LedgerDiff.toJson(backup)))

    fun cursorOf(snapshot: JsonObject): Long = (snapshot["cursor"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

    private fun rowsOf(snapshot: JsonObject): Int = (snapshot["rows"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
  }
}
