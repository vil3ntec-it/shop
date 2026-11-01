package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.sync.SyncEngine
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 *  فشرده کردنِ دفتر — بردنِ سابقهٔ کهنه به بایگانی.
 *
 *  ── چه اشکالی را می‌بندد ────────────────────────────────────────────
 *  `stockMovements`، `auditLog` و `priceHistory` فقط اضافه می‌شوند و
 *  هیچ‌وقت پاک نمی‌شوند. حرکاتِ انبار تقریباً همه‌شان از روی فروش و
 *  ورودی قابلِ محاسبه‌اند — داده‌ای که دو بار نگهداری می‌شود — و صفحهٔ
 *  گردشِ موجودی هم فقط ۲۰۰ ردیفِ آخر را نشان می‌دهد. یعنی بقیه فقط
 *  وزن‌اند.
 *
 *  و وزنِ دفتر مستقیم روی گران‌ترین کارِ برنامه می‌نشیند: هر ثبتِ فروش،
 *  **کلِ** فایل را از نو می‌نویسد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── چرا خودکار نیست ────────────────────────────────────────────────
 *  چون یک طرفه است، و چون با همگام‌سازی درگیر می‌شود.
 *
 *  «سایه»ی همگام‌سازی عکسِ آخرین وضعیتی است که با سرور یکی بوده. اگر
 *  ردیفی از دفتر برداشته شود ولی در سایه بماند، `SyncEngine.collect`
 *  آن را **حذف‌شده** می‌بیند و دستورِ حذفش را به سرور و به همهٔ گوشی‌های
 *  دیگر می‌فرستد. یعنی بایگانی کردن روی یک گوشی، پاک کردنِ سابقه روی
 *  همه‌ی گوشی‌ها می‌شد.
 *
 *  پس این کار سایه را هم در همان لحظه هرس می‌کند — **هر دو** سایه: قدیمی
 *  (`SyncStore`) و Sync v1 (`SyncV1Engine.forgetArchived`؛ پیش از بازبینیِ
 *  F0-01 این یکی جا مانده بود و حذف‌ها واقعاً می‌رفتند): ردیفی که به بایگانی
 *  می‌رود از سایه هم بیرون می‌آید، و آن‌وقت هیچ دستورِ حذفی ساخته
 *  نمی‌شود. همین دو کار باید با هم انجام شوند، وگرنه یکی بدونِ دیگری
 *  خطرناک است.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ردیف‌های بایگانی‌شده پاک **نمی‌شوند**؛ در فایلِ کنارِ دفتر می‌مانند و
 *  با پشتیبانِ کامل هم بیرون می‌روند.
 */
object LedgerArchive {

  private const val NAME = "ledger-archive.json"

  /** ردیف‌های تازه‌تر از این تعداد روز، سرِ جایشان می‌مانند */
  const val KEEP_DAYS = 400

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  @Serializable
  data class Archive(
    val stockMovements: List<StockMovement> = emptyList(),
    val priceHistory: List<PriceChange> = emptyList(),
    val auditLog: List<AuditEntry> = emptyList(),
  )

  /** چند ردیف قابلِ بایگانی است — برای نشان دادن پیش از انجام */
  data class Plan(val movements: Int, val prices: Int, val audits: Int) {
    val total: Int get() = movements + prices + audits
  }

  private fun file(context: Context) = File(context.filesDir, NAME)

  /** مرزِ زمانی: هر چه قدیمی‌تر از این، کهنه است */
  fun cutoff(now: Long, keepDays: Int = KEEP_DAYS): Long =
    now - keepDays.toLong() * 24L * 60L * 60L * 1000L

  /**
   *  ردیف‌هایی که هرگز بایگانی نمی‌شوند، هر چه کهنه: دفترِ موجودی و بها (`StockLedger`) **از
   *  خودِ آن‌ها** حساب می‌شود و از فروش و ورودی دوباره ساخته نمی‌شوند —
   *   • تنظیمِ دکان (`stock_mode`): بی آن موتور پس از ۴۰۰ روز خودبه‌خود خاموش می‌شد؛
   *   • «بررسی شد» (`stock_review_ack`): بی آن وظیفه‌های بسته دوباره باز می‌شدند؛
   *   • انتقال بین محل‌ها: بی آن موجودیِ هر محل غلط می‌شد.
   *  شمارشان کم است و وزنی ندارند.
   */
  fun pinned(m: StockMovement): Boolean = m.type in StockLedger.TRANSFER_TYPES
  fun pinned(a: AuditEntry): Boolean = a.type == StockLedger.MODE_TYPE || a.type == StockLedger.ACK_TYPE

  /** بدونِ دست زدن به چیزی، فقط می‌شمارد */
  fun plan(d: ShopData, now: Long = System.currentTimeMillis(), keepDays: Int = KEEP_DAYS): Plan {
    val edge = cutoff(now, keepDays)
    return Plan(
      movements = d.stockMovements.count { it.createdAt in 1 until edge && !pinned(it) },
      prices = d.priceHistory.count { it.createdAt in 1 until edge },
      audits = d.auditLog.count { it.createdAt in 1 until edge && !pinned(it) },
    )
  }

  data class Done(val data: ShopData, val moved: Plan)

  /**
   *  دفترِ فشرده‌شده، و ردیف‌هایی که کنار گذاشته شدند.
   *
   *  ردیفی که `createdAt` ندارد (صفر است) دست نمی‌خورد: نمی‌دانیم کِی
   *  ساخته شده و حدس زدن یعنی بایگانی کردنِ چیزی که شاید امروز ساخته
   *  شده باشد.
   */
  fun split(d: ShopData, now: Long = System.currentTimeMillis(), keepDays: Int = KEEP_DAYS): Pair<ShopData, Archive> {
    val edge = cutoff(now, keepDays)
    fun old(at: Long) = at in 1 until edge

    fun goMove(m: StockMovement) = old(m.createdAt) && !pinned(m)
    fun goAudit(a: AuditEntry) = old(a.createdAt) && !pinned(a)

    val oldMoves = d.stockMovements.filter(::goMove)
    val oldPrices = d.priceHistory.filter { old(it.createdAt) }
    val oldAudits = d.auditLog.filter(::goAudit)

    val kept = d.copy(
      stockMovements = d.stockMovements.filterNot(::goMove),
      priceHistory = d.priceHistory.filterNot { old(it.createdAt) },
      auditLog = d.auditLog.filterNot(::goAudit),
    )
    return kept to Archive(oldMoves, oldPrices, oldAudits)
  }

  /**
   *  انجامِ کار: بایگانی روی دیسک، سایه هرس، دفترِ سبک برگردانده می‌شود.
   *
   *  ذخیرهٔ خودِ دفتر کارِ صداکننده است، نه اینجا — تا اگر جایی از این
   *  زنجیره بشکند، دفترِ روی دیسک دست‌نخورده بماند.
   */
  fun compact(
    context: Context,
    d: ShopData,
    now: Long = System.currentTimeMillis(),
    keepDays: Int = KEEP_DAYS,
  ): Result<Done> = runCatching {
    compactWith(
      d, now, keepDays,
      keep = { fresh ->
        //  بایگانیِ قبلی خوانده و با تازه‌ها یکی می‌شود؛ هیچ ردیفی از بایگانی
        //  بیرون نمی‌رود. ردیفی که از تلاشِ نیمه‌کارهٔ پیشین آن‌جا نشسته،
        //  دوباره اضافه نمی‌شود.
        //  زیرِ قفلِ کوچِ رمز (SEC-01): کوچ میانِ خواندن و نوشتن نمی‌نشیند
        synchronized(AtRest.lock) {
          val existing = readArchive(context)
          val merged = Archive(
            stockMovements = union(existing.stockMovements, fresh.stockMovements) { it.id },
            priceHistory = union(existing.priceHistory, fresh.priceHistory) { it.id },
            auditLog = union(existing.auditLog, fresh.auditLog) { it.id },
          )
          //  ⛔ اگر بایگانی به دیسک نرسد همین‌جا شکست می‌خورد و دفتر کوتاه نمی‌شود
          //  — پیش‌تر نتیجهٔ `renameTo` دیده نمی‌شد و روی ویندوز ردیف‌ها از دفتر
          //  می‌رفتند بی آنکه در بایگانی نشسته باشند (F0-01)
          AtRest.writeText(context, file(context), json.encodeToString(merged))
        }
      },
      forget = { gone ->
        //  ⛔ سایهٔ Sync v1 — شکستش پرتاب می‌شود و دفتر کوتاه نمی‌شود. پیش‌تر
        //  فقط سایهٔ قدیمی هرس می‌شد و ذخیرهٔ دفترِ کوتاه برای هر ردیفِ
        //  بایگانی‌شده DELETE به سرور می‌فرستاد (بازبینیِ F0-01)
        SyncV1Engine.of(context).forgetArchived(gone)
        forgetInShadow(context, gone)
      },
    )
  }

  /**
   *  همان `compact`، بی `Context` — تا آزمون همان مسیر را برود.
   *
   *  ترتیب: بایگانی روی دیسک ⇐ سایه‌ها هرس ⇐ دفترِ سبک. هر گامی بشکند
   *  پرتاب می‌شود و دفترِ کوتاه هیچ‌وقت به صداکننده نمی‌رسد.
   *
   *  @param keep  ردیف‌های کهنه را روی دیسک می‌نشاند
   *  @param forget همان ردیف‌ها را از سایه‌های همگام‌سازی بیرون می‌آورد
   */
  internal fun compactWith(
    d: ShopData,
    now: Long,
    keepDays: Int,
    keep: (Archive) -> Unit,
    forget: (Map<String, List<String>>) -> Unit,
  ): Done {
    val (kept, fresh) = split(d, now, keepDays)
    val moved = Plan(fresh.stockMovements.size, fresh.priceHistory.size, fresh.auditLog.size)
    if (moved.total == 0) return Done(d, moved)

    keep(fresh)

    //  و سایه، در همان نفس. بدونِ این، همگام‌سازی این ردیف‌ها را
    //  «حذف‌شده» می‌فهمد و روی گوشی‌های دیگر هم پاکشان می‌کند.
    forget(
      mapOf(
        "stockMovements" to fresh.stockMovements.map { it.id },
        "priceHistory" to fresh.priceHistory.map { it.id },
        "auditLog" to fresh.auditLog.map { it.id },
      ),
    )

    return Done(kept, moved)
  }

  private fun <T> union(old: List<T>, fresh: List<T>, id: (T) -> String): List<T> {
    val have = old.mapTo(HashSet(), id)
    return old + fresh.filter { id(it) !in have }
  }

  /**
   *  ⛔ بایگانیِ **رمزداری** که باز نشد پرتاب می‌کند (`AtRestException`)، نه
   *  «بایگانیِ خالی»: وگرنه فشرده‌سازیِ بعدی بایگانیِ واقعی را با همان چند
   *  ردیفِ تازه رونویسی می‌کرد. بایگانیِ ساده همان رفتارِ همیشه را دارد.
   */
  fun readArchive(context: Context): Archive {
    val f = file(context)
    if (!f.exists()) return Archive()
    val text = try {
      AtRest.readText(context, f)
    } catch (e: ir.vil3ntec.tohid.core.atrest.AtRestException) {
      throw e
    } catch (e: Exception) {
      return Archive()
    }
    return runCatching { json.decodeFromString<Archive>(text) }.getOrDefault(Archive())
  }

  /**
   *  §۹.۵: شناسهٔ ردیف‌های بایگانی‌شده، هر مجموعه — ساختنِ دوبارهٔ دفتر از Snapshot پس از
   *  بازیابیِ سرور (`V1Restore`، `SyncV3Engine.rebuild`) آن‌ها را به دفتر برنمی‌گرداند؛ وگرنه
   *  هم در دفتر بودند هم در بایگانی و «خروجیِ صاحب» دو بار می‌شمرد. ورودی: `readArchive`.
   */
  fun archivedIds(a: Archive): Map<String, Set<String>> =
    mapOf(
      "stockMovements" to a.stockMovements.mapTo(HashSet()) { it.id },
      "priceHistory" to a.priceHistory.mapTo(HashSet()) { it.id },
      "auditLog" to a.auditLog.mapTo(HashSet()) { it.id },
    )

  /** اندازهٔ فایلِ بایگانی، برای نشان دادن در تنظیمات */
  fun archiveBytes(context: Context): Long = file(context).let { if (it.exists()) it.length() else 0L }

  private fun forgetInShadow(context: Context, gone: Map<String, List<String>>) {
    runCatching {
      val store = SyncStore(context)
      val shadow = store.shadow
      if (shadow.entries.isEmpty()) return@runCatching
      val next = shadow.entries.mapValues { (name, rows) ->
        val ids = gone[name] ?: return@mapValues rows
        if (ids.isEmpty()) rows else rows - ids.toSet()
      }
      store.shadow = SyncEngine.Shadow(next)
    }
  }
}
