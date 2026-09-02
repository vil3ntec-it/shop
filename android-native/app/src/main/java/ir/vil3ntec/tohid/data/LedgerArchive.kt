package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.sync.SyncEngine
import ir.vil3ntec.tohid.sync.SyncStore
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
 *  پس این کار سایه را هم در همان لحظه هرس می‌کند: ردیفی که به بایگانی
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

  /** بدونِ دست زدن به چیزی، فقط می‌شمارد */
  fun plan(d: ShopData, now: Long = System.currentTimeMillis(), keepDays: Int = KEEP_DAYS): Plan {
    val edge = cutoff(now, keepDays)
    return Plan(
      movements = d.stockMovements.count { it.createdAt in 1 until edge },
      prices = d.priceHistory.count { it.createdAt in 1 until edge },
      audits = d.auditLog.count { it.createdAt in 1 until edge },
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

    val oldMoves = d.stockMovements.filter { old(it.createdAt) }
    val oldPrices = d.priceHistory.filter { old(it.createdAt) }
    val oldAudits = d.auditLog.filter { old(it.createdAt) }

    val kept = d.copy(
      stockMovements = d.stockMovements.filterNot { old(it.createdAt) },
      priceHistory = d.priceHistory.filterNot { old(it.createdAt) },
      auditLog = d.auditLog.filterNot { old(it.createdAt) },
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
    val (kept, fresh) = split(d, now, keepDays)
    val moved = Plan(fresh.stockMovements.size, fresh.priceHistory.size, fresh.auditLog.size)
    if (moved.total == 0) return@runCatching Done(d, moved)

    //  بایگانیِ قبلی خوانده و با تازه‌ها یکی می‌شود؛ هیچ ردیفی از بایگانی
    //  بیرون نمی‌رود
    val existing = readArchive(context)
    val merged = Archive(
      stockMovements = existing.stockMovements + fresh.stockMovements,
      priceHistory = existing.priceHistory + fresh.priceHistory,
      auditLog = existing.auditLog + fresh.auditLog,
    )
    val tmp = File(context.filesDir, "$NAME.tmp")
    tmp.writeText(json.encodeToString(merged))
    tmp.renameTo(file(context))

    //  و سایه، در همان نفس. بدونِ این، همگام‌سازی این ردیف‌ها را
    //  «حذف‌شده» می‌فهمد و روی گوشی‌های دیگر هم پاکشان می‌کند.
    forgetInShadow(
      context,
      mapOf(
        "stockMovements" to fresh.stockMovements.map { it.id },
        "priceHistory" to fresh.priceHistory.map { it.id },
        "auditLog" to fresh.auditLog.map { it.id },
      ),
    )

    Done(kept, moved)
  }

  fun readArchive(context: Context): Archive {
    val f = file(context)
    if (!f.exists()) return Archive()
    return runCatching { json.decodeFromString<Archive>(f.readText()) }.getOrDefault(Archive())
  }

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
