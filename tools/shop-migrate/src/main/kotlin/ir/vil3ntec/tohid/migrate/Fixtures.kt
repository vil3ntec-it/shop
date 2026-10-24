package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.PriceChange
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.SupplierPayment
import ir.vil3ntec.tohid.data.WarehouseEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Random

/**
 *  سازندهٔ دفترِ ساختگی — قطعی (همان seed ⇐ همان فایل)، بی هیچ دادهٔ واقعی.
 *
 *  دفتر را همان‌طور می‌سازد که برنامهٔ امروز می‌نویسد: مبلغِ ردیف همان لحظه
 *  گرد (`SalesEngine.lineTotal`)، تخفیفِ گردشده، فروشِ نسیه با ردیفِ `give`،
 *  مرجوعی با `returnedQty` و تسویهٔ قرض، لغو با دلیل، خرید با ورودِ انبار،
 *  تغییرِ قیمت، صندوقِ روزانه در سابقهٔ عملیات، کالای وزنی با سه رقمِ اعشار،
 *  قلمِ آزاد، و شناسه‌های قدیمیِ `Math.random` در کنارِ ULID.
 *
 *  `anomalies = true` چند خرابیِ شناخته‌شده هم می‌افزاید (فهرست در
 *  `injectAnomalies`) تا گزارشِ تطبیق «اختلافِ با دلیل» را هم نشان دهد.
 */
object Fixtures {

  enum class Size(val products: Int, val debtors: Int, val suppliers: Int, val days: Int, val maxSalesPerDay: Int) {
    SMALL(10, 4, 3, 50, 3),
    MEDIUM(60, 25, 8, 200, 8),
    LARGE(400, 150, 30, 730, 25),
  }

  val START: LocalDate = LocalDate.of(2024, 4, 10)

  fun generate(seed: Long, size: Size = Size.SMALL, anomalies: Boolean = false): ShopData {
    val g = Gen(Random(seed), size)
    g.run()
    val d = g.data()
    return if (anomalies) injectAnomalies(d) else d
  }

  /** همان شکلِ `ShopStore.exportBackup()`: دفتر + `exportedAt` + `settings` */
  fun backupJson(d: ShopData): String {
    val tree = BackupReader.json.encodeToJsonElement(ShopData.serializer(), d).jsonObject.toMutableMap()
    tree["exportedAt"] = JsonPrimitive("2024-08-01T00:00:00Z")
    tree["settings"] = buildJsonObject { put("storeName", JsonPrimitive("دکانِ نمونه")) }
    return JsonObject(tree).toString()
  }

  private class Gen(val rnd: Random, val size: Size) {
    var nextInvoice = 1
    var ulidClock = 0
    val products = ArrayList<Product>()
    val debtors = ArrayList<Debtor>()
    val suppliers = ArrayList<Supplier>()
    val entries = ArrayList<WarehouseEntry>()
    val sales = ArrayList<Sale>()
    val items = ArrayList<SaleItem>()
    val returns = ArrayList<SaleReturn>()
    val transactions = ArrayList<DebtTransaction>()
    val expenses = ArrayList<Expense>()
    val purchases = ArrayList<Purchase>()
    val supplierPayments = ArrayList<SupplierPayment>()
    val movements = ArrayList<StockMovement>()
    val priceHistory = ArrayList<PriceChange>()
    val audit = ArrayList<AuditEntry>()
    val stock = HashMap<String, Double>()

    /** نیمی شناسهٔ کهنهٔ `Math.random().toString(36)`، نیمی ULID */
    fun id(at: Long): String {
      if (rnd.nextBoolean()) {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
        return (1..9).map { chars[rnd.nextInt(36)] }.joinToString("")
      }
      val bytes = ByteArray(10).also { rnd.nextBytes(it) }
      return Ids.encode(at + (ulidClock++ % 1000), bytes)
    }

    fun at(day: Int, minute: Int): Long =
      START.plusDays(day.toLong()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + minute * 60_000L

    fun date(day: Int): String = START.plusDays(day.toLong()).toString()

    fun lineTotal(price: Double, qty: Double): Double = Math.round(price * qty).toDouble()

    fun log(type: String, day: Int, minute: Int, refId: String?, notes: String, amount: Double = 0.0) {
      val t = at(day, minute)
      audit += AuditEntry(id(t), type, date(day), refId, notes, t, amount)
    }

    fun run() {
      repeat(size.suppliers) { i ->
        suppliers += Supplier(id(at(0, i)), "تأمین‌کننده ${i + 1}", "07${rnd.nextInt(100_000_000)}", "", "", at(0, i))
      }
      repeat(size.debtors) { i ->
        debtors += Debtor(id(at(0, 10 + i)), "مشتری ${i + 1}", "07${rnd.nextInt(100_000_000)}", "", at(0, 10 + i))
      }
      repeat(size.products) { i -> addProduct(i) }
      for (day in 0 until size.days) day(day)
    }

    fun addProduct(i: Int) {
      val weighted = i % 4 == 1
      val cost = if (i % 3 == 0) 10.0 + rnd.nextInt(200) + 0.5 else 10.0 + rnd.nextInt(200)
      val price = Math.round(cost * 1.25).toDouble()
      val t = at(0, 30 + i)
      val p = Product(
        id = id(t), name = "کالا ${i + 1}", category = listOf("خوراکی", "پاک‌کننده", "نوشیدنی")[i % 3],
        unit = if (weighted) "کیلوگرم" else "عدد", purchasePrice = cost, salePrice = price,
        wholesalePrice = if (i % 5 == 0) price - 1 else 0.0, minStock = 5.0,
        barcodes = (0 until i % 3).map { "47${(100_000_000_00L + rnd.nextInt(1_000_000_000)).toString().padStart(11, '0')}" },
        createdAt = t, supplierId = suppliers[i % suppliers.size].id,
        saleUnit = if (weighted && i % 8 == 1) "گرم" else "", unitFactor = if (weighted && i % 8 == 1) 0.001 else 1.0,
      )
      products += p
      // موجودیِ اول: خرید از تأمین‌کننده + ورودِ انبار
      val qty = if (weighted) (20_000 + rnd.nextInt(80_000)) / 1000.0 else (20 + rnd.nextInt(80)).toDouble()
      purchase(p, qty, 0, 40 + i)
      if (i % 4 == 0) {
        val e = WarehouseEntry(id(t), p.id, 0.0, 0.0, -1.0, p.unit, cost, date(0), "شمارش", true, null, null, t + 1)
        entries += e
        stock.merge(p.id, -1.0, Double::plus)
        movements += StockMovement(id(t), p.id, "adjustment", -1.0, date(0), "شمارش", e.id, t + 1)
      }
    }

    fun purchase(p: Product, qty: Double, day: Int, minute: Int) {
      val t = at(day, minute)
      val supplier = suppliers.first { it.id == p.supplierId }
      val total = Math.round(qty * p.purchasePrice * 100) / 100.0
      val paid = if (rnd.nextInt(3) == 0) total else Math.floor(total / 2)
      val purchaseId = id(t)
      val entry = WarehouseEntry(id(t), p.id, 0.0, 0.0, qty, p.unit, p.purchasePrice, date(day), "", false, purchaseId, null, t)
      entries += entry
      purchases += Purchase(purchaseId, p.id, supplier.id, qty, p.unit, p.purchasePrice, total, date(day), "",
        paid, total - paid, entry.id, t, 0)
      movements += StockMovement(id(t), p.id, "purchase_in", qty, date(day), "", purchaseId, t)
      stock.merge(p.id, qty, Double::plus)
    }

    fun day(day: Int) {
      log("shift_open", day, 8 * 60, null, "باز کردنِ صندوق", 500.0)
      val shiftId = audit.last().id
      val count = 1 + rnd.nextInt(size.maxSalesPerDay)
      repeat(count) { k -> sale(day, 9 * 60 + k * 17) }
      if (rnd.nextInt(4) == 0) returnSomething(day)
      if (rnd.nextInt(9) == 0) cancelSomething(day)
      if (rnd.nextInt(3) == 0) receiveDebt(day)
      if (rnd.nextInt(3) == 0) {
        val t = at(day, 15 * 60)
        expenses += Expense(id(t), "هزینه", listOf("کرایه", "برق", "ترانسپورت")[rnd.nextInt(3)],
          (50 + rnd.nextInt(500)).toDouble(), date(day), "", t)
      }
      if (rnd.nextInt(6) == 0) {
        val s = suppliers[rnd.nextInt(suppliers.size)]
        val t = at(day, 16 * 60)
        supplierPayments += SupplierPayment(id(t), s.id, (100 + rnd.nextInt(900)).toDouble(), date(day), "", t)
      }
      if (rnd.nextInt(7) == 0) changePrice(day)
      if (rnd.nextInt(5) == 0) {
        val p = products[rnd.nextInt(products.size)]
        purchase(p, if (p.unit == "کیلوگرم") 10.5 else 12.0, day, 17 * 60)
      }
      log("shift_close", day, 20 * 60, shiftId, "بستنِ صندوق", (500 + rnd.nextInt(5000)).toDouble())
    }

    fun sale(day: Int, minute: Int) {
      val t = at(day, minute)
      val saleId = id(t)
      val lines = ArrayList<SaleItem>()
      repeat(1 + rnd.nextInt(4)) {
        val p = products[rnd.nextInt(products.size)]
        val weighted = p.unit == "کیلوگرم"
        val qty = if (weighted) (250 + rnd.nextInt(2750)) / 1000.0 else (1 + rnd.nextInt(3)).toDouble()
        if ((stock[p.id] ?: 0.0) < qty || lines.any { it.productId == p.id }) return@repeat
        stock.merge(p.id, -qty, Double::plus)
        lines += SaleItem(id(t), saleId, p.id, "", qty, p.salePrice, p.purchasePrice, lineTotal(p.salePrice, qty), 0.0)
      }
      if (rnd.nextInt(10) == 0) lines += SaleItem(id(t), saleId, "", "خدمتِ بسته‌بندی", 1.0, 50.0, 0.0, 50.0, 0.0)
      if (lines.isEmpty()) return
      val subtotal = lines.sumOf { it.totalPrice }
      val (type, value, discount) = when (rnd.nextInt(8)) {
        0 -> Triple("amount", 5.0, 5.0)
        1 -> Triple("percent", 10.0, Math.round(subtotal * 0.1).toDouble())
        else -> Triple("amount", 0.0, 0.0)
      }
      val finalTotal = maxOf(0.0, subtotal - discount)
      val credit = rnd.nextInt(4) == 0
      val debtor = if (credit) debtors[rnd.nextInt(debtors.size)] else null
      val paid = if (credit) Math.floor(finalTotal * rnd.nextInt(3) / 4) else finalTotal
      val remaining = finalTotal - paid
      val invoice = nextInvoice++
      sales += Sale(saleId, invoice, subtotal, type, value, discount, finalTotal, if (credit) "credit" else "cash",
        debtor?.id, paid, remaining, "completed", if (credit) remaining else 0.0, 0.0, t, date(day), "synced", "",
        listOf("", "احمد", "محمود")[rnd.nextInt(3)])
      items += lines
      lines.filter { it.productId.isNotEmpty() }.forEach {
        movements += StockMovement(id(t), it.productId, "sale", -it.quantity, date(day), "", saleId, t)
      }
      if (credit && remaining > 0) {
        transactions += DebtTransaction(id(t), debtor!!.id, "give", remaining, date(day), "فروش نسیه — فاکتور #$invoice", t)
      }
      log("sale", day, minute, saleId, "فروش — فاکتور #$invoice", finalTotal)
    }

    fun returnSomething(day: Int) {
      val status = sales.associate { it.id to it.status }
      val candidates = items.withIndex().filter { (_, it) ->
        it.productId.isNotEmpty() && it.quantity - it.returnedQty > 0 &&
          status[it.saleId] == "completed"
      }
      if (candidates.isEmpty()) return
      val (index, item) = candidates[rnd.nextInt(candidates.size)]
      val left = item.quantity - item.returnedQty
      val qty = if (left >= 1.0) 1.0 else left
      val amount = lineTotal(item.unitPrice, qty)
      val t = at(day, 14 * 60)
      val r = SaleReturn(id(t), item.saleId, item.id, item.productId, qty, amount, "خراب بود", date(day), t)
      returns += r
      items[index] = item.copy(returnedQty = item.returnedQty + qty)
      stock.merge(item.productId, qty, Double::plus)
      movements += StockMovement(id(t), item.productId, "customer_return", qty, date(day), "", r.id, t)
      val si = sales.indexOfFirst { it.id == item.saleId }
      val sale = sales[si]
      if (sale.debtorId != null) {
        val settle = minOf(amount, maxOf(0.0, sale.debtGiven - sale.debtSettled))
        if (settle > 0) {
          transactions += DebtTransaction(id(t), sale.debtorId!!, "receive", settle, date(day), "مرجوعی کالا", t)
          sales[si] = sale.copy(debtSettled = sale.debtSettled + settle)
        }
      }
      log("return", day, 14 * 60, sale.id, "مرجوعی", amount)
    }

    fun cancelSomething(day: Int) {
      val open = sales.withIndex().filter { it.value.status == "completed" && it.value.date == date(day) }
      if (open.isEmpty()) return
      val (si, sale) = open[rnd.nextInt(open.size)]
      val t = at(day, 19 * 60)
      var updated = sale.copy(status = "cancelled", cancelReason = "اشتباه در ثبت")
      if (sale.debtorId != null) {
        val outstanding = sale.debtGiven - sale.debtSettled
        if (outstanding > 0) {
          transactions += DebtTransaction(id(t), sale.debtorId!!, "receive", outstanding, date(day), "لغو فروش", t)
          updated = updated.copy(debtSettled = sale.debtSettled + outstanding)
        }
      }
      sales[si] = updated
      items.filter { it.saleId == sale.id && it.productId.isNotEmpty() }.forEach {
        val back = it.quantity - it.returnedQty
        stock.merge(it.productId, back, Double::plus)
        movements += StockMovement(id(t), it.productId, "sale_cancel", back, date(day), "", sale.id, t)
      }
      log("sale_cancel", day, 19 * 60, sale.id, "لغو فروش")
    }

    fun receiveDebt(day: Int) {
      val d = debtors[rnd.nextInt(debtors.size)]
      val owed = transactions.filter { it.debtorId == d.id }.sumOf { if (it.type == "give") it.amount else -it.amount }
      if (owed <= 0) return
      val t = at(day, 13 * 60)
      transactions += DebtTransaction(id(t), d.id, "receive", minOf(owed, (50 + rnd.nextInt(300)).toDouble()), date(day), "", t)
    }

    fun changePrice(day: Int) {
      val i = rnd.nextInt(products.size)
      val p = products[i]
      val next = p.salePrice + 1 + rnd.nextInt(10)
      val t = at(day, 7 * 60)
      priceHistory += PriceChange(id(t), p.id, p.salePrice, next, date(day), t)
      products[i] = p.copy(salePrice = next)
    }

    fun data() = ShopData(
      debtors = debtors, transactions = transactions, expenses = expenses, products = products,
      warehouseEntries = entries, sales = sales, saleItems = items, saleReturns = returns,
      nextInvoiceNo = nextInvoice, suppliers = suppliers, purchases = purchases, supplierPayments = supplierPayments,
      stockMovements = movements, priceHistory = priceHistory, auditLog = audit,
    )
  }

  /**
   *  خرابی‌های شناخته‌شده، هرکدام با کدِ ناهنجاریِ مورد انتظار:
   *   ‌• قلمِ فروشِ بی فاکتور ⇐ ORPHAN_SALE_ITEM (موجودیِ کالای اول)
   *   ‌• `returnedQty`ِ بی ردیفِ مرجوعی ⇐ RETURNED_QTY_MISMATCH
   *   ‌• مرجوعیِ بی قلم ⇐ ORPHAN_RETURN (سود)
   *   ‌• اعشارِ زیرِ دقت: دو `give`ِ ۱۰٫۰۰۴ برای مشتریِ اول، بهای ۳٫۳۳۳۳ روی یک قلم،
   *     دو ورودِ ۱٫۰۰۰۴ برای کالای دوم ⇐ DOUBLE_DECIMALS
   *   ‌• یک بارکد روی دو کالا ⇐ DUPLICATE_BARCODE
   *   ‌• فاکتورِ لغوشدهٔ بی دلیل ⇐ CANCEL_WITHOUT_REASON
   *   ‌• `give`ِ منفی برای مشتریِ دوم ⇐ NEGATIVE_LEDGER_AMOUNT
   *   ‌• ورودِ انبار برای کالای حذف‌شده ⇐ PLACEHOLDER_PRODUCT
   */
  fun injectAnomalies(d: ShopData): ShopData {
    val p0 = d.products[0]
    val p1 = d.products[1]
    val day = d.sales.first().date
    val at = d.sales.first().createdAt
    val activeSale = d.sales.first { it.status == "completed" }
    val itemsOfActive = d.saleItems.filter { it.saleId == activeSale.id && it.productId.isNotEmpty() }
    val mismatch = d.saleItems.first { item ->
      item.productId.isNotEmpty() && item.returnedQty == 0.0 &&
        d.sales.first { it.id == item.saleId }.status == "completed" && item.id != itemsOfActive.first().id
    }
    val costly = itemsOfActive.first()
    val cancelled = d.sales.indexOfFirst { it.status == "cancelled" }
    val barcode = (d.products.firstOrNull { it.barcodes.isNotEmpty() }?.barcodes?.first()) ?: "4700000000001"
    return d.copy(
      products = d.products.mapIndexed { i, p ->
        when (i) {
          0 -> if (p.barcodes.contains(barcode)) p else p.copy(barcodes = p.barcodes + barcode)
          1 -> if (p.barcodes.contains(barcode)) p else p.copy(barcodes = p.barcodes + barcode)
          else -> p
        }
      },
      saleItems = d.saleItems.map {
        when (it.id) {
          mismatch.id -> it.copy(returnedQty = minOf(1.0, it.quantity))
          costly.id -> it.copy(purchasePrice = 3.3333, quantity = 3.0, totalPrice = it.unitPrice * 3)
          else -> it
        }
      } + SaleItem("orphan-item", "gone-sale", p0.id, "", 2.0, p0.salePrice, p0.purchasePrice, p0.salePrice * 2, 0.0),
      saleReturns = d.saleReturns + SaleReturn("orphan-return", activeSale.id, "gone-item", p0.id, 1.0, 30.0, "", day, at),
      transactions = d.transactions +
        DebtTransaction("tiny-1", d.debtors[0].id, "give", 10.004, day, "", at) +
        DebtTransaction("tiny-2", d.debtors[0].id, "give", 10.004, day, "", at) +
        DebtTransaction("negative-give", d.debtors[1].id, "give", -20.0, day, "", at),
      warehouseEntries = d.warehouseEntries +
        WarehouseEntry("tiny-entry-1", p1.id, 0.0, 0.0, 1.0004, p1.unit, p1.purchasePrice, day, "", false, null, null, at) +
        WarehouseEntry("tiny-entry-2", p1.id, 0.0, 0.0, 1.0004, p1.unit, p1.purchasePrice, day, "", false, null, null, at) +
        WarehouseEntry("ghost-entry", "gone-product", 0.0, 0.0, 5.0, "عدد", 10.0, day, "", false, null, null, at),
      sales = if (cancelled < 0) d.sales else d.sales.mapIndexed { i, s -> if (i == cancelled) s.copy(cancelReason = "") else s },
    )
  }
}
