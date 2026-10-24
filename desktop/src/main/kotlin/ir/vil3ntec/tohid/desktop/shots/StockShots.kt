package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.data.ProductImport
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.stock.AdjustChoice
import ir.vil3ntec.tohid.ui.v3.stock.AdjustForm
import ir.vil3ntec.tohid.ui.v3.stock.AdjustSheet
import ir.vil3ntec.tohid.ui.v3.stock.BulkEntryScreen
import ir.vil3ntec.tohid.ui.v3.stock.BulkEntryState
import ir.vil3ntec.tohid.ui.v3.stock.BulkRow
import ir.vil3ntec.tohid.ui.v3.stock.CountState
import ir.vil3ntec.tohid.ui.v3.stock.EntryForm
import ir.vil3ntec.tohid.ui.v3.stock.ImportModel
import ir.vil3ntec.tohid.ui.v3.stock.ImportScreen
import ir.vil3ntec.tohid.ui.v3.stock.MovementsScreen
import ir.vil3ntec.tohid.ui.v3.stock.StockCountScreen
import ir.vil3ntec.tohid.ui.v3.stock.StockEntrySheet
import ir.vil3ntec.tohid.ui.v3.stock.StockFilter
import ir.vil3ntec.tohid.ui.v3.stock.StockItemActions
import ir.vil3ntec.tohid.ui.v3.stock.StockItemScreen
import ir.vil3ntec.tohid.ui.v3.stock.StockListActions
import ir.vil3ntec.tohid.ui.v3.stock.StockListScreen

/**
 *  عکس‌های گروهِ G3 انبار (`stock.md`) — روی دفترِ نمایشیِ همین فایل (نه دادهٔ دکانِ
 *  واقعی): ۴۰ کالا با نام‌های بلند و ارزشِ ۱۲٬۳۴۵٬۶۷۸ ؋، کم/تمام‌شده/نزدیکِ انقضا/منقضی،
 *  ورودها و حرکت‌ها. صفحه‌ها مستقیم با همین دفتر کشیده می‌شوند؛ کارها بی‌اثرند.
 */
object StockShots : GroupShots {
  override val group = "stock"
  override val shots = listOf(
    Shot("list-full", content = { ListShot(StockDemo.full()) }),
    Shot("list-empty", content = { ListShot(ShopData()) }),
    Shot("list-out", content = { ListShot(StockDemo.full(), filter = StockFilter.OUT) }),
    Shot("list-expiring", content = { ListShot(StockDemo.full(), filter = StockFilter.EXPIRING) }),
    Shot("list-nomatch", content = { ListShot(StockDemo.full(), query = "کالای ناموجود") }),
    Shot("list-wide", content = { ListShot(StockDemo.full(), wide = true) }),
    Shot("item", content = { ItemShot() }),
    Shot("entry", content = { EntryShot() }),
    Shot("entry-pick", content = { EntryShot(pick = true) }),
    //  صفحه‌ای که دکمهٔ شناورِ «ورود کالا» با container transform به آن باز می‌شود
    Shot("entry-fab", content = { EntryPageShot() }),
    Shot("bulk", content = { BulkShot() }),
    Shot("adjust-up", content = { AdjustShot(AdjustChoice.UP) }),
    Shot("adjust-down", content = { AdjustShot(AdjustChoice.DOWN) }),
    Shot("adjust-return", content = { AdjustShot(AdjustChoice.RETURN) }),
    Shot("count", content = { CountShot() }),
    Shot("movements", content = { MovementsScreen(StockDemo.full(), StockDemo.RICE, todayIso(), onOpen = {}, onBack = {}) }),
    Shot("import", content = { ImportShot() }),
  )
}

private val noList = StockListActions({}, {}, {}, {}, {}, {}, {}, {}, { null })
private val noItem = StockItemActions({}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun ListShot(d: ShopData, filter: StockFilter = StockFilter.ALL, query: String = "", wide: Boolean = false) {
  if (!wide) StockListScreen(d, todayIso(), noList, initialFilter = filter, initialQuery = query)
  else StockListScreen(d, todayIso(), noList, wide = true, selectedId = StockDemo.RICE)
}

@Composable
private fun ItemShot() {
  StockItemScreen(StockDemo.full(), todayIso(), StockDemo.RICE, noItem, onBack = {})
}

@Composable
private fun EntryShot(pick: Boolean = false) {
  val d = StockDemo.full()
  Box(Modifier.fillMaxSize()) {
    StockListScreen(d, todayIso(), noList)
    if (pick) StockEntrySheet(d, null, null, onSave = { null }, onNewProduct = {}, onClose = {})
    else StockEntrySheet(
      d, StockDemo.RICE, null, onSave = { null }, onNewProduct = {}, onClose = {},
      start = EntryForm(
        productId = StockDemo.RICE, cartonMode = true, cartons = "12", perCarton = "12", price = "20", unit = "کیلو",
        expiry = "1406-01-30", batch = "B-1405-17",
      ),
    )
  }
}

@Composable
private fun EntryPageShot() {
  val d = StockDemo.full()
  val flow = ir.vil3ntec.tohid.ui.v3.stock.rememberEntryFlow(
    d, null, null,
    start = EntryForm(productId = "p3", units = "24", price = "180", unit = "عدد"),
  )
  ir.vil3ntec.tohid.ui.v3.stock.StockEntryPage(d, flow, onSave = { null }, onNewProduct = {}, onClose = {})
}

@Composable
private fun BulkShot() {
  val d = StockDemo.full()
  val rows = listOf(
    BulkRow(key = 0, productId = StockDemo.RICE, qty = "20", price = "95", unit = "کیلو"),
    BulkRow(key = 1, productId = "p3", qty = "12", price = "180", unit = "عدد"),
    BulkRow(key = 2, newProduct = true, newName = "عدسِ درشت", newBarcode = "6260000077", qty = "10", price = "60", unit = "کیلو", scanMiss = true),
  )
  BulkEntryScreen(d, todayIso(), onScan = {}, onSave = { _, _ -> null }, onBack = {}, start = BulkEntryState(rows, todayIso(), seq = 3))
}

@Composable
private fun AdjustShot(choice: AdjustChoice) {
  val d = StockDemo.full()
  val p = d.products.first { it.id == StockDemo.RICE }
  val form = when (choice) {
    AdjustChoice.UP -> AdjustForm(choice, "6", "کارتنِ اضافه در انبارِ پشتی پیدا شد")
    AdjustChoice.DOWN -> AdjustForm(choice, "14", "کیسه پاره شد")
    AdjustChoice.RETURN -> AdjustForm(choice, "10", "جنسِ نم‌دار به عمده‌فروش برگشت")
  }
  Box(Modifier.fillMaxSize()) {
    StockItemScreen(d, todayIso(), StockDemo.RICE, noItem, onBack = {})
    AdjustSheet(p, ir.vil3ntec.tohid.data.ShopStore.stock(d, p.id), onSave = { null }, onClose = {}, start = form)
  }
}

@Composable
private fun CountShot() {
  val d = StockDemo.full()
  val counts = d.products.filter { it.active }.sortedBy { it.name }.take(7).mapIndexed { i, p ->
    val sys = ir.vil3ntec.tohid.data.ShopStore.stock(d, p.id)
    p.id to when (i) {
      1 -> (sys + 2).toLong().toString()
      3 -> (sys - 3).coerceAtLeast(0.0).toLong().toString()
      5 -> "۱۲x"
      else -> sys.toLong().toString()
    }
  }.toMap()
  StockCountScreen(d, todayIso(), onExport = {}, onSave = { _, _ -> null }, onBack = {}, start = CountState(counts, "قفسهٔ برنج و حبوبات"))
}

@Composable
private fun ImportShot() {
  val csv = buildString {
    appendLine("نام محصول,دسته,واحد,قیمت خرید,قیمت فروش,بارکد")
    listOf("چای سیاهِ هندی", "قهوهٔ فوری", "بیسکویتِ کرم‌دار", "آبِ معدنی", "دستمال کاغذی", "شامپوی بچه").forEachIndexed { i, n ->
      appendLine("$n,خوراکی,عدد,${40 + i * 5},${60 + i * 7},62699${i}0$i")
    }
    appendLine("برنجِ هندیِ سفیدِ دانه‌بلندِ ممتاز,خوراکی,کیلو,90,120,")
    appendLine(",خوراکی,عدد,10,12,")
    appendLine("نمکِ یُددار,خوراکی,عدد,ده,15,")
  }
  val plan = (ImportModel.read(StockDemo.full(), csv) as? ir.vil3ntec.tohid.ui.v3.stock.ImportState.Ready)?.plan
    ?: ProductImport.Plan(emptyList(), emptyList(), missingName = false)
  ImportScreen(ir.vil3ntec.tohid.ui.v3.stock.ImportState.Ready(plan), onPick = {}, onSave = { null }, onBack = {})
}

/** دفترِ نمایشیِ انبار — ساختگی، برای عکس‌ها */
internal object StockDemo {
  const val RICE = "p1"

  private val names = listOf(
    "برنجِ هندیِ سفیدِ دانه‌بلندِ ممتاز کیسهٔ بیست‌وپنج کیلویی", "روغن آفتاب‌گردانِ خالص بطریِ پنج لیتری", "شکرِ سفید",
    "چای سبزِ کیسه‌ای", "آردِ گندمِ سفید", "عدسِ قرمز", "لوبیا چیتی", "نخودِ درشت", "ماکارونیِ فرمی", "رب گوجه‌فرنگی",
    "نمکِ یُددار", "زعفرانِ هراتی بستهٔ یک مثقالی", "پنیرِ سفید", "شیرِ پاکتی", "ماست", "تخم‌مرغ شانه‌ای", "کرهٔ حیوانی",
    "صابونِ دست", "شامپو", "خمیردندان", "مایعِ ظرف‌شویی", "پودرِ لباس‌شویی", "دستمالِ کاغذی", "باتریِ قلمی",
    "کبریت", "شمع", "بیسکویت", "شکلاتِ تلخ", "آبِ معدنی", "نوشابه", "آب‌میوهٔ انگور", "قهوهٔ فوری", "زیرهٔ سبز",
    "زردچوبه", "فلفلِ سیاه", "کشمشِ سبز", "بادامِ زمینی", "گردو", "خرمای خشک", "عسلِ طبیعیِ کوهستان",
  )

  fun full(): ShopData {
    val today = todayIso()
    val products = names.mapIndexed { i, n ->
      Product(
        id = "p${i + 1}", name = n, unit = if (i % 3 == 0) "کیلو" else "عدد",
        purchasePrice = 40.0 + i * 7, salePrice = 55.0 + i * 9, minStock = 5.0,
        category = if (i < 12) "خوراکی" else if (i < 17) "لبنیات" else "بهداشتی",
        barcodes = listOf("62600${1000 + i}"),
      )
    }
    val entries = ArrayList<WarehouseEntry>()
    products.forEachIndexed { i, p ->
      val units = when {
        i % 9 == 4 -> 3.0
        else -> 24.0 + i
      }
      entries += WarehouseEntry(
        id = "e${i + 1}", productId = p.id, cartons = if (i % 2 == 0) 2.0 else 0.0, perCarton = if (i % 2 == 0) units / 2 else 0.0,
        units = units, unit = p.unit, price = p.purchasePrice, date = shift(today, -i), createdAt = 1_000L + i,
        expiryDate = when (i) { 0 -> shift(today, 5); 13 -> shift(today, -2); 14 -> shift(today, 12); else -> "" },
        batch = if (i == 0) "B-1405-17" else "",
      )
    }
    entries += WarehouseEntry(id = "e-old", productId = RICE, cartons = 4.0, perCarton = 12.0, units = 48.0, unit = "کیلو", price = 88.0, date = shift(today, -40), createdAt = 10)
    entries += WarehouseEntry(id = "e-adj", productId = RICE, units = -2.0, unit = "کیلو", date = shift(today, -1), notes = "کیسه پاره شد", isAdjustment = true, createdAt = 5_000)
    val sum = entries.sumOf { it.units * it.price }
    entries += WarehouseEntry(id = "e-big", productId = "p12", units = 1.0, unit = "عدد", price = (12_345_678 - sum).coerceAtLeast(0.0), date = today, createdAt = 2_000)
    val sold = listOf("p5" to 3.0, "p14" to 2.0, "p23" to 3.0, "p8" to 29.0, RICE to 20.0)
    val sales = sold.mapIndexed { i, _ -> Sale(id = "s$i", invoiceNumber = 1200 + i, date = today, createdAt = 3_000L + i) }
    val items = sold.mapIndexed { i, (id, q) -> SaleItem(id = "i$i", saleId = "s$i", productId = id, quantity = q) }
    val moves = listOf(
      StockMovement(id = "m1", productId = RICE, type = "purchase_in", qty = 24.0, date = today, notes = "ثبت ورود کالا", refId = "e1", createdAt = 9),
      StockMovement(id = "m2", productId = RICE, type = "sale", qty = -20.0, date = today, notes = "فاکتور #1204", refId = "s4", createdAt = 8),
      StockMovement(id = "m3", productId = RICE, type = "adjustment", qty = -2.0, date = shift(today, -1), notes = "کیسه پاره شد", refId = "e-adj", createdAt = 7),
      StockMovement(id = "m4", productId = RICE, type = "customer_return", qty = 1.0, date = shift(today, -1), notes = "مرجوعی فاکتور #1190", createdAt = 6),
      StockMovement(id = "m5", productId = RICE, type = "supplier_return", qty = -5.0, date = shift(today, -6), notes = "جنسِ نم‌دار", createdAt = 5),
      StockMovement(id = "m6", productId = RICE, type = "sale_cancel", qty = 3.0, date = shift(today, -6), notes = "لغو فروش — فاکتور #1101", refId = "s4", createdAt = 4),
      StockMovement(id = "m7", productId = RICE, type = "purchase_in", qty = 48.0, date = shift(today, -40), notes = "ثبت ورود کالا", createdAt = 3),
    )
    return ShopData(
      products = products, productCategories = listOf("خوراکی", "لبنیات", "بهداشتی"), productUnits = listOf("عدد", "کیلو", "لیتر", "بسته"),
      warehouseEntries = entries, sales = sales, saleItems = items, stockMovements = moves,
    )
  }

  private fun shift(iso: String, days: Int): String {
    val (y, m, d) = iso.split('-').map { it.toInt() }
    val c = java.util.GregorianCalendar(y, m - 1, d)
    c.add(java.util.Calendar.DAY_OF_MONTH, days)
    return String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
  }
}
