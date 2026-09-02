package ir.vil3ntec.tohid.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 *  خلاصهٔ دفتر — همان چند عددی که نگهبان‌ها لازم دارند، و نه بیشتر.
 *
 *  ── چه اشکالی را می‌بندد ────────────────────────────────────────────
 *  `Watchman` هر **ربع ساعت** و `Reminders` روزی یک بار، کارِ پس‌زمینه
 *  اجرا می‌کردند و هر بار `ShopStore.load()` می‌زدند — یعنی کلِ دفترِ
 *  دکان را از دیسک می‌خواندند و تجزیه می‌کردند. روی دکانی که یک سال کار
 *  کرده، آن فایل ده‌ها مگابایت است. نتیجه‌اش شکایتِ «برنامه باتری را
 *  می‌خورد» بود، و روی گوشی‌های ضعیف‌تر خطرِ بسته شدنِ کارِ پس‌زمینه
 *  به‌خاطرِ حافظه.
 *
 *  و همهٔ آن کار برای چهار چیز بود: کدام کالا تمام شده، کدام رو به
 *  اتمام است، چقدر از هرکس طلب داریم، و اسمشان چیست.
 *
 *  حالا همان چهار چیز، هر بار که دفتر ذخیره می‌شود، در یک فایلِ کوچکِ
 *  کنارِ آن نوشته می‌شود. نگهبان همان را می‌خواند: چند ده کیلوبایت
 *  به‌جای چند ده مگابایت.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  اگر این فایل نبود (نسخهٔ تازه روی دفترِ قدیمی)، نگهبان مثلِ قبل کلِ
 *  دفتر را می‌خواند و همان‌جا خلاصه را می‌سازد — یعنی هیچ‌وقت بی‌خبر
 *  نمی‌ماند، فقط آن یک بار گران است.
 */
@Serializable
data class LedgerSummary(
  val savedAt: Long = 0,
  val stock: List<StockLine> = emptyList(),
  val debts: List<DebtLine> = emptyList(),
) {

  @Serializable
  data class StockLine(
    val id: String = "",
    val name: String = "",
    /** out | low | ok — همان سه حالتِ `ShopStore.stockStatus` */
    val status: String = "ok",
    val left: Double = 0.0,
  )

  @Serializable
  data class DebtLine(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
  )

  val outOfStock: List<StockLine> get() = stock.filter { it.status == "out" }
  val lowStock: List<StockLine> get() = stock.filter { it.status == "low" }

  /** کسانی که به ما بدهکارند */
  val owing: List<DebtLine> get() = debts.filter { it.amount > 0 }

  companion object {

    private const val NAME = "ledger-summary.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    private fun file(context: Context) = File(context.filesDir, NAME)

    /** ساختنِ خلاصه از روی دفتر — یک گذر روی کالاها و یک گذر روی قرض‌ها */
    fun of(d: ShopData, now: Long = System.currentTimeMillis()): LedgerSummary {
      val index = ShopStore.index(d)
      //  یک گذر روی تراکنش‌ها، نه یک پیمایش به ازای هر قرض‌دار
      val byDebtor = d.transactions.groupBy { it.debtorId }
      return LedgerSummary(
        savedAt = now,
        stock = d.products.map {
          StockLine(
            id = it.id,
            name = it.name,
            status = index.status(it),
            left = index.stock(it.id),
          )
        },
        debts = d.debtors.map { debtor ->
          var amount = 0.0
          byDebtor[debtor.id]?.forEach {
            amount += if (it.type == "give") it.amount else -it.amount
          }
          DebtLine(id = debtor.id, name = debtor.name, amount = amount)
        },
      )
    }

    /** نوشتنِ خلاصه کنارِ دفتر. شکستنش نباید ذخیرهٔ خودِ دفتر را بشکند. */
    fun write(context: Context, d: ShopData) {
      runCatching { file(context).writeText(json.encodeToString(of(d))) }
    }

    /** خواندنِ خلاصه — `null` یعنی هنوز ساخته نشده */
    fun read(context: Context): LedgerSummary? {
      val f = file(context)
      if (!f.exists()) return null
      return runCatching { json.decodeFromString<LedgerSummary>(f.readText()) }.getOrNull()
    }

    /**
     *  خلاصه، به هر قیمتی.
     *
     *  اول از فایلِ کوچک؛ اگر نبود، یک بار دفتر خوانده می‌شود و خلاصه
     *  همان‌جا ساخته و ذخیره می‌شود تا دفعهٔ بعد ارزان باشد.
     */
    suspend fun require(context: Context): LedgerSummary {
      read(context)?.let { return it }
      val store = ShopStore(context)
      store.load()
      val fresh = of(store.data.value)
      runCatching { file(context).writeText(json.encodeToString(fresh)) }
      return fresh
    }

    fun clear(context: Context) {
      runCatching { file(context).delete() }
    }
  }
}
