package ir.vil3ntec.tohid.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 *  PERF-LEDGER — جدول‌های ردیفیِ دفتر (همان `LedgerDiff.COLLECTIONS`، با نامِ JSON، فهرست و رمزگذارِ هر کدام) برای
 *  میان‌بُرهای دفترِ بزرگ: [LedgerText] (نوشتنِ فایل) و `sync/v1/LedgerTrees` (درختِ همگام‌سازی). شرح در
 *  `docs/PERF-LEDGER-fa.md`.
 *
 *  هر چیزی جز ردیف‌ها (کلیدها، ترتیبشان، فهرست‌های نام، شمارهٔ فاکتورِ بعدی، و هر فیلدِ تازه‌ای که روزی به `ShopData`
 *  بیاید) همیشه از رمزگذارِ خودِ `ShopData` روی [bare] می‌آید؛ جدولی که این‌جا نیامده فقط میان‌بُر ندارد، غلط نمی‌شود.
 */
internal object LedgerTables {

  class Table<T>(val key: String, val rows: (ShopData) -> List<T>, val serializer: KSerializer<T>)

  val ALL: List<Table<*>> = listOf(
    Table("products", { it.products }, Product.serializer()),
    Table("warehouseEntries", { it.warehouseEntries }, WarehouseEntry.serializer()),
    Table("sales", { it.sales }, Sale.serializer()),
    Table("saleItems", { it.saleItems }, SaleItem.serializer()),
    Table("returns", { it.saleReturns }, SaleReturn.serializer()),
    Table("debtors", { it.debtors }, Debtor.serializer()),
    Table("transactions", { it.transactions }, DebtTransaction.serializer()),
    Table("expenses", { it.expenses }, Expense.serializer()),
    Table("suppliers", { it.suppliers }, Supplier.serializer()),
    Table("purchases", { it.purchases }, Purchase.serializer()),
    Table("supplierPayments", { it.supplierPayments }, SupplierPayment.serializer()),
    Table("stockMovements", { it.stockMovements }, StockMovement.serializer()),
    Table("priceHistory", { it.priceHistory }, PriceChange.serializer()),
    Table("auditLog", { it.auditLog }, AuditEntry.serializer()),
    Table("safeEntries", { it.safeEntries }, SafeEntry.serializer()),
  )

  /** دفترِ بی ردیف — همان کلیدها و هر فیلدِ دیگر، با فهرست‌های ردیفیِ خالی */
  fun bare(d: ShopData): ShopData = d.copy(
    debtors = emptyList(), transactions = emptyList(), expenses = emptyList(), products = emptyList(),
    warehouseEntries = emptyList(), sales = emptyList(), saleItems = emptyList(), saleReturns = emptyList(),
    suppliers = emptyList(), purchases = emptyList(), supplierPayments = emptyList(), stockMovements = emptyList(),
    priceHistory = emptyList(), auditLog = emptyList(), safeEntries = emptyList(),
  )
}

/**
 *  PERF-LEDGER — **همان بایت‌های** `json.encodeToString(ShopData.serializer(), d).toByteArray(UTF_8)`، تکه‌تکه در یک
 *  جریان (`ShopStore.writeToDisk` ⇐ `DiskWrite.stream`؛ «رمزگذاریِ داده» روشن ⇒ همان `AtRest.writeText` ِ همیشه).
 *
 *  ── چرا ──────────────────────────────────────────────────────────────
 *  هر ذخیره کلِ دفتر را یک رشته می‌کرد و بعد یک آرایهٔ بایت: دفترِ ۹۰ مگابایتی (۵۰هزار فاکتور) یعنی رشتهٔ UTF-16 ِ
 *  ۱۸۰ مگابایتی با بزرگ‌شدنِ پیاپیِ `StringBuilder` و ۹۰ مگابایتِ دیگر — روی گوشیِ ضعیف (heap ِ ۲۵۶–۵۱۲ مگ) همین
 *  به‌تنهایی OutOfMemory است. حالا بیشترین حافظهٔ گذرا یک تکه ([CHUNK] ردیف) است.
 *
 *  ── چرا همان بایت‌ها ─────────────────────────────────────────────────
 *  رمزگذارِ فشرده (بی فاصله) هر فهرست را `[` + ردیف‌ها با `,` + `]` می‌نویسد و هر ردیف را با همان رمزگذارِ ردیف؛ پس
 *  متنِ فهرست = تکه‌های `encodeToString(ListSerializer(…), تکه)` بی کروشه، پشتِ هم با `,`. بقیهٔ متن همان رمزگذاری ِ
 *  [LedgerTables.bare] است و هر `"<جدول>":[]` ِ آن دقیقاً یک بار می‌آید (`"` درونِ رشته همیشه `\"` نوشته می‌شود، پس
 *  الگو درونِ هیچ مقداری پیدا نمی‌شود). هر ناهنجاری ⇒ همان رشتهٔ کامل، پیش از نوشتنِ هیچ بایتی. مرزِ تکه‌ها همیشه
 *  نویسهٔ ساختاریِ ASCII است، پس هیچ جفتِ UTF-16 ای دو نیم نمی‌شود. `LedgerPerfEquivalenceTest` بایت‌به‌بایت قفلش کرده.
 */
internal object LedgerText {

  /** ردیف در هر تکه — چند صد کیلوبایت */
  private const val CHUNK = 1_000

  /** @param out نوشتنِ تکه‌ای از بایت‌ها: (آرایه، آغاز، اندازه) — همان `OutputStream.write` */
  fun write(json: Json, d: ShopData, out: (ByteArray, Int, Int) -> Unit) {
    val skeleton = json.encodeToString(ShopData.serializer(), LedgerTables.bare(d))
    val cuts = ArrayList<Pair<Int, LedgerTables.Table<*>>>()
    for (t in LedgerTables.ALL) {
      if (t.rows(d).isEmpty()) continue
      val needle = "\"${t.key}\":[]"
      val at = skeleton.indexOf(needle)
      if (at < 0 || skeleton.indexOf(needle, at + 1) >= 0) {
        out.all(json.encodeToString(ShopData.serializer(), d).toByteArray(Charsets.UTF_8))
        return
      }
      //  جای `]` ِ همان فهرست
      cuts += (at + needle.length - 1) to t
    }
    cuts.sortBy { it.first }
    var pos = 0
    for ((at, t) in cuts) {
      out.all(skeleton.substring(pos, at).toByteArray(Charsets.UTF_8))
      rows(json, t, d, out)
      pos = at
    }
    out.all(skeleton.substring(pos).toByteArray(Charsets.UTF_8))
  }

  private fun ((ByteArray, Int, Int) -> Unit).all(b: ByteArray) = this(b, 0, b.size)

  private fun <T> rows(json: Json, t: LedgerTables.Table<T>, d: ShopData, out: (ByteArray, Int, Int) -> Unit) {
    val all = t.rows(d)
    val list = ListSerializer(t.serializer)
    var i = 0
    while (i < all.size) {
      val end = minOf(all.size, i + CHUNK)
      val bytes = json.encodeToString(list, all.subList(i, end)).toByteArray(Charsets.UTF_8)
      //  بی `]` ِ پایان؛ `[` ِ آغاز فقط برای تکهٔ نخست بیرون، بقیه همان جا `,` (هر کدام یک بایت)
      if (i == 0) {
        out(bytes, 1, bytes.size - 2)
      } else {
        bytes[0] = ','.code.toByte()
        out(bytes, 0, bytes.size - 1)
      }
      i = end
    }
  }
}
