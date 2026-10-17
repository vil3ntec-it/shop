package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.db.AuditLog
import ir.vil3ntec.tohid.db.Barcode
import ir.vil3ntec.tohid.db.CashShift
import ir.vil3ntec.tohid.db.Currency
import ir.vil3ntec.tohid.db.Customer
import ir.vil3ntec.tohid.db.CustomerLedgerEntry
import ir.vil3ntec.tohid.db.Device
import ir.vil3ntec.tohid.db.LegacyId
import ir.vil3ntec.tohid.db.MeasureUnit
import ir.vil3ntec.tohid.db.Price
import ir.vil3ntec.tohid.db.PriceList
import ir.vil3ntec.tohid.db.SaleLine
import ir.vil3ntec.tohid.db.SalePayment
import ir.vil3ntec.tohid.db.Shop
import ir.vil3ntec.tohid.db.StockMove
import ir.vil3ntec.tohid.db.SupplierInvoice
import ir.vil3ntec.tohid.db.UnitConversion
import ir.vil3ntec.tohid.db.Warehouse
import ir.vil3ntec.tohid.db.Expense as DbExpense
import ir.vil3ntec.tohid.db.Product as DbProduct
import ir.vil3ntec.tohid.db.Sale as DbSale
import ir.vil3ntec.tohid.db.SaleReturn as DbSaleReturn
import ir.vil3ntec.tohid.db.Supplier as DbSupplier
import ir.vil3ntec.tohid.db.SupplierPayment as DbSupplierPayment

/**
 *  یک ناهنجاریِ داده که نگاشت دید.
 *
 *  `deltas`: اثرش روی عددهای گزارش، به مقیاسِ همان کلید و به شکلِ
 *  «تازه − قدیم». مقایسه‌گر فقط وقتی اختلافی را «با دلیل» می‌داند که دقیقاً
 *  برابرِ جمعِ همین اثرها باشد. ناهنجاریِ بی `deltas` فقط اطلاع است.
 */
data class Anomaly(val code: String, val ref: String, val deltas: Map<String, Long> = emptyMap())

/** فروشِ آمادهٔ نشاندن: پیش‌نویس، ردیف‌ها، پرداخت‌ها و حلقهٔ زنجیره */
data class SaleBundle(
  val draft: DbSale,
  val lines: List<SaleLine>,
  val payments: List<SalePayment>,
  val chainSeq: Long,
  val prevHash: String,
  val hash: String,
)

data class Cancel(val saleId: String, val reason: String, val at: Long)

class NewModel(
  val currency: Currency,
  val shop: Shop,
  val device: Device,
  val warehouse: Warehouse,
  val units: List<MeasureUnit>,
  val suppliers: List<DbSupplier>,
  val products: List<DbProduct>,
  val conversions: List<UnitConversion>,
  val barcodes: List<Barcode>,
  val priceLists: List<PriceList>,
  val prices: List<Price>,
  val customers: List<Customer>,
  val sales: List<SaleBundle>,
  val returns: List<DbSaleReturn>,
  val cancels: List<Cancel>,
  val ledger: List<CustomerLedgerEntry>,
  val stockMoves: List<StockMove>,
  val invoices: List<SupplierInvoice>,
  val supplierPayments: List<DbSupplierPayment>,
  val expenses: List<DbExpense>,
  val shifts: List<CashShift>,
  val audit: List<AuditLog>,
  val legacyIds: List<LegacyId>,
  val anomalies: List<Anomaly>,
  /** شناسهٔ تازه ⇐ شناسهٔ قدیم، فقط برای ردیف‌های واقعی (نه جانشین) */
  val productLegacy: Map<String, String>,
  val customerLegacy: Map<String, String>,
  val supplierLegacy: Map<String, String>,
)

/**
 *  دفترِ قدیم ⇐ ردیف‌های مدلِ تازه. هیچ ردیفی بی‌صدا نمی‌افتد: یا نگاشته
 *  می‌شود، یا یک `Anomaly` با دلیل می‌سازد.
 *
 *  قاعده‌ها:
 *   ‌• عددها فقط جابه‌جا می‌شوند، از نو حساب نمی‌شوند: مبلغِ ذخیره‌شدهٔ هر
 *     ردیف همان می‌ماند (فقط به واحدِ کوچک)، چون همان چیزی است که مشتری دیده.
 *   ‌• موجودی از منبعِ واقعیِ امروز ساخته می‌شود (ورودِ انبار − فروشِ لغونشده
 *     + مرجوعی)، نه از `stockMovements` که فقط دفترِ نمایشی بود.
 *   ‌• کالا/مشتری/تأمین‌کننده‌ای که حذف شده ولی سابقه به آن ارجاع دارد، یک
 *     ردیفِ «جانشین» (غیرفعال) می‌گیرد تا سابقه نیفتد.
 *   ‌• دادهٔ قدیم یک دستگاه است: سریِ `L`.
 */
object Mapper {

  const val CURRENCY = "AFN"
  const val SERIES = "L"
  const val NO_REASON = "(دلیل در دادهٔ قدیمی ثبت نشده)"
  const val REMOVED_PRODUCT = "(کالای حذف‌شده)"
  const val REMOVED_CUSTOMER = "(قرض‌دارِ حذف‌شده)"
  const val REMOVED_SUPPLIER = "(تأمین‌کنندهٔ حذف‌شده)"

  fun map(d: ShopData, storeName: String = ""): NewModel = Run(d, storeName).build()

  private class Run(val d: ShopData, val storeName: String) {
    val anomalies = ArrayList<Anomaly>()
    val legacyIds = ArrayList<LegacyId>()
    private val occurrences = HashMap<Pair<String, String>, Int>()
    /** هر شناسهٔ قدیم ⇐ شناسهٔ تازه، برای `refId`ِ سابقهٔ عملیات */
    val anyLegacy = HashMap<String, String>()

    fun occ(entity: String, legacy: String): Int = occurrences.merge(entity to legacy, 1, Int::plus)!!

    fun newId(entity: String, legacy: String, at: Long): Pair<String, Int> {
      val o = occ(entity, legacy)
      val id = Ids.derive(entity, legacy, o, at)
      legacyIds += LegacyId(entity, legacy, o.toLong(), id)
      if (o == 1) anyLegacy.putIfAbsent(legacy, id)
      return id to o
    }

    fun note(code: String, ref: String, vararg deltas: Pair<String, Long>) {
      anomalies += Anomaly(code, ref, deltas.filter { it.second != 0L }.toMap())
    }

    val shopId = Ids.derive("shop", "legacy", 1, 0)
    val deviceId = Ids.derive("device", "legacy", 1, 0)
    val warehouseId = Ids.derive("warehouse", "main", 1, 0)
    val firstAt = (d.sales.map { it.createdAt } + d.products.map { it.createdAt } + d.auditLog.map { it.createdAt })
      .filter { it > 0 }.minOrNull() ?: 0L

    val realProducts: Set<String> = d.products.map { it.id }.toSet()
    val productIds = LinkedHashMap<String, String>()
    val customerIds = LinkedHashMap<String, String>()
    val supplierIds = LinkedHashMap<String, String>()
    val placeholderProducts = ArrayList<DbProduct>()
    val placeholderCustomers = ArrayList<Customer>()
    val placeholderSuppliers = ArrayList<DbSupplier>()

    fun product(legacy: String): String? {
      if (legacy.isBlank()) return null
      productIds[legacy]?.let { return it }
      val (id, _) = newId("product", legacy, 0)
      productIds[legacy] = id
      placeholderProducts += DbProduct(
        id = id, shopId = shopId, name = REMOVED_PRODUCT, category = "", subcategory = "", unitId = null,
        minStockMilli = 0, refCostMinor = 0, supplierId = null, notes = "", hasPhoto = 0, active = 0, createdAt = 0,
      )
      note("PLACEHOLDER_PRODUCT", legacy, "rows/product" to 1)
      return id
    }

    fun customer(legacy: String?): String? {
      if (legacy.isNullOrBlank()) return null
      customerIds[legacy]?.let { return it }
      val (id, _) = newId("customer", legacy, 0)
      customerIds[legacy] = id
      placeholderCustomers += Customer(id, shopId, REMOVED_CUSTOMER, "", "", 0)
      note("PLACEHOLDER_CUSTOMER", legacy, "rows/customer" to 1)
      return id
    }

    fun supplier(legacy: String): String? {
      if (legacy.isBlank()) return null
      supplierIds[legacy]?.let { return it }
      val (id, _) = newId("supplier", legacy, 0)
      supplierIds[legacy] = id
      placeholderSuppliers += DbSupplier(id, shopId, REMOVED_SUPPLIER, "", "", "", 0)
      note("PLACEHOLDER_SUPPLIER", legacy, "rows/supplier" to 1)
      return id
    }

    fun build(): NewModel {
      val m = Scale::toMinor
      val q = Scale::toMilli

      /* ---------------- تأمین‌کننده ---------------- */
      val suppliers = ArrayList<DbSupplier>()
      d.suppliers.forEach { s ->
        if (s.id in supplierIds) { note("DUPLICATE_ID", "supplier:${s.id}", "rows/supplier" to -1); return@forEach }
        val (id, _) = newId("supplier", s.id, s.createdAt)
        supplierIds[s.id] = id
        suppliers += DbSupplier(id, shopId, s.name, s.phone, s.address, s.notes, s.createdAt)
      }
      val supplierLegacy = supplierIds.entries.associate { it.value to it.key }

      /* ---------------- واحد ---------------- */
      val unitIds = LinkedHashMap<String, String>()
      (d.productUnits + d.products.map { it.unit } + d.products.map { it.saleUnit })
        .filter { it.isNotBlank() }.distinct()
        .forEach { unitIds[it] = Ids.derive("unit", it, 1, 0) }
      val units = unitIds.map { (name, id) -> MeasureUnit(id, shopId, name) }

      /* ---------------- کالا ---------------- */
      val products = ArrayList<DbProduct>()
      val conversions = ArrayList<UnitConversion>()
      d.products.forEach { p ->
        if (p.id in productIds) { note("DUPLICATE_ID", "product:${p.id}", "rows/product" to -1); return@forEach }
        val (id, _) = newId("product", p.id, p.createdAt)
        productIds[p.id] = id
        products += DbProduct(
          id = id, shopId = shopId, name = p.name, category = p.category, subcategory = p.subcategory,
          unitId = unitIds[p.unit], minStockMilli = q(p.minStock), refCostMinor = m(p.purchasePrice),
          supplierId = supplier(p.supplierId), notes = p.notes, hasPhoto = if (p.photo) 1 else 0,
          active = if (p.active) 1 else 0, createdAt = p.createdAt,
        )
        if (p.saleUnit.isNotBlank() && p.unit.isNotBlank() && p.saleUnit != p.unit) {
          val f = fraction(p.unitFactor)
          if (f == null) note("UNIT_FACTOR_NOT_RATIONAL", p.id)
          else conversions += UnitConversion(id, unitIds.getValue(p.saleUnit), unitIds.getValue(p.unit), f.first, f.second)
        }
      }
      val productLegacy = productIds.entries.associate { it.value to it.key }

      /* ---------------- بارکد ---------------- */
      val owners = HashMap<String, MutableList<String>>()
      d.products.distinctBy { it.id }.forEach { p ->
        p.barcodes.filter { it.isNotBlank() }.distinct().forEach { owners.getOrPut(it) { ArrayList() } += p.id }
      }
      owners.filter { it.value.size > 1 }.keys.forEach { note("DUPLICATE_BARCODE", it) }
      val barcodes = d.products.distinctBy { it.id }.flatMap { p ->
        p.barcodes.filter { it.isNotBlank() }.distinct().map { code ->
          Barcode(
            id = Ids.derive("barcode", "${p.id}/$code", 1, 0), shopId = shopId, code = code,
            productId = productIds.getValue(p.id), duplicate = if (owners.getValue(code).size > 1) 1 else 0,
          )
        }
      }

      /* ---------------- قیمت ---------------- */
      val retail = PriceList(Ids.derive("priceList", "retail", 1, 0), shopId, "retail", "خرده", CURRENCY)
      val wholesale = PriceList(Ids.derive("priceList", "wholesale", 1, 0), shopId, "wholesale", "عمده", CURRENCY)
      val prices = ArrayList<Price>()
      val history = d.priceHistory.groupBy { it.productId }
      val firstById = d.products.distinctBy { it.id }.associateBy { it.id }
      (firstById.keys + history.keys.filter { it !in realProducts }).distinct().forEach { legacy ->
        val p = firstById[legacy]
        val pid = product(legacy) ?: run {
          history[legacy].orEmpty().forEach { note("ORPHAN_PRICE_CHANGE", it.id, "rows/priceChange" to -1) }
          return@forEach
        }
        val changes = history[legacy].orEmpty().sortedWith(compareBy({ it.createdAt }, { it.id }))
        val start = p?.createdAt ?: 0L
        var from = minOf(start, changes.firstOrNull()?.createdAt ?: start)
        changes.forEach { c ->
          val (id, _) = newId("priceChange", c.id, c.createdAt)
          val amount = m(c.oldPrice)
          if (amount < 0) note("NEGATIVE_PRICE", c.id, "rows/priceChange" to -1)
          else prices += Price(id, retail.id, pid, amount, from, c.createdAt)
          from = c.createdAt
        }
        if (p != null) {
          val amount = m(p.salePrice)
          if (amount < 0) note("NEGATIVE_PRICE", p.id)
          else prices += Price(Ids.derive("price", "retail/${p.id}", 1, from), retail.id, pid, amount, from, null)
          if (p.wholesalePrice > 0) {
            prices += Price(Ids.derive("price", "wholesale/${p.id}", 1, p.createdAt), wholesale.id, pid, m(p.wholesalePrice), p.createdAt, null)
          }
        }
      }

      /* ---------------- مشتری ---------------- */
      val customers = ArrayList<Customer>()
      d.debtors.forEach { c ->
        if (c.id in customerIds) { note("DUPLICATE_ID", "customer:${c.id}", "rows/customer" to -1); return@forEach }
        val (id, _) = newId("customer", c.id, c.createdAt)
        customerIds[c.id] = id
        customers += Customer(id, shopId, c.name, c.phone, c.notes, c.createdAt)
      }
      val customerLegacy = customerIds.entries.associate { it.value to it.key }

      /* ---------------- فروش ---------------- */
      val saleIds = HashMap<String, String>()
      val drafts = ArrayList<Pair<ir.vil3ntec.tohid.data.Sale, DbSale>>()
      val paymentsBySale = HashMap<String, MutableList<SalePayment>>()
      d.sales.forEach { s ->
        val (id, o) = newId("sale", s.id, s.createdAt)
        if (o == 1) saleIds[s.id] = id else note("DUPLICATE_ID", "sale:${s.id}")
        val active = s.status != "cancelled"
        drafts += s to DbSale(
          id = id, shopId = shopId, deviceId = deviceId, number = null,
          displayNo = s.invoiceNumber?.toString() ?: "", status = "draft", currency = CURRENCY,
          subtotalMinor = m(s.total), discountMinor = m(s.discount), totalMinor = m(s.finalTotal),
          customerId = customer(s.debtorId), seller = s.seller, cancelReason = null, businessDate = s.date,
          createdAt = s.createdAt, finalizedAt = null, cancelledAt = null, chainSeq = null, prevHash = null, hash = null,
        )
        val pays = paymentsBySale.getOrPut(id) { ArrayList() }
        fun pay(method: String, value: Double, key: String) {
          val minor = m(value)
          when {
            minor > 0 -> pays += SalePayment(Ids.derive("salePayment", "$id/$method", 1, s.createdAt), id, method, minor, CURRENCY, s.createdAt)
            minor < 0 -> note("NEGATIVE_PAYMENT", s.id, key to if (active) -minor else 0)
          }
        }
        pay("cash", s.paidAmount, "pay/salesCash")
        pay("account", s.debtGiven, "pay/salesOnAccount")
        if (active && m(s.paidAmount) + m(s.debtGiven) != m(s.finalTotal)) note("PAYMENTS_NOT_EQUAL_TOTAL", s.id)
      }
      d.sales.mapNotNull { it.invoiceNumber }.groupingBy { it }.eachCount()
        .filter { it.value > 1 }.keys.forEach { note("DUPLICATE_INVOICE_NO", it.toString()) }

      val legacySale = d.sales.associateBy { it.id }
      val cancelledLegacy = d.sales.filter { it.status == "cancelled" }.map { it.id }.toSet()
      val linesBySale = HashMap<String, MutableList<SaleLine>>()
      val lineOfItem = HashMap<String, SaleLine>()
      val itemOfLine = HashMap<String, SaleItem>()
      d.saleItems.forEach { item ->
        val saleId = saleIds[item.saleId]
        if (saleId == null) {
          val stockDelta = if (item.productId in realProducts && item.saleId !in cancelledLegacy) {
            q(item.quantity) - q(item.returnedQty)
          } else 0L
          note("ORPHAN_SALE_ITEM", item.id, "rows/saleLine" to -1, "stock/${item.productId}" to stockDelta)
          return@forEach
        }
        val sale = legacySale.getValue(item.saleId)
        val (id, _) = newId("saleItem", item.id, sale.createdAt)
        val list = linesBySale.getOrPut(saleId) { ArrayList() }
        val line = SaleLine(
          id = id, saleId = saleId, lineNo = list.size + 1L, productId = product(item.productId), name = item.name,
          qtyMilli = q(item.quantity), unitPriceMinor = m(item.unitPrice), lineTotalMinor = m(item.totalPrice),
          unitCostMinor = m(item.purchasePrice),
        )
        list += line
        lineOfItem.putIfAbsent(item.id, line)
        itemOfLine[id] = item
      }

      //  زنجیره: به ترتیبِ زمانِ ثبت، بعد شناسه
      val sales = ArrayList<SaleBundle>()
      var prev = HashChain.GENESIS
      drafts.sortedWith(compareBy({ it.second.createdAt }, { it.second.id })).forEachIndexed { i, (_, draft) ->
        val lines = linesBySale[draft.id].orEmpty()
        val pays = paymentsBySale[draft.id].orEmpty().sortedBy { it.id }
        val hash = HashChain.hash(prev, Docs.sale(draft, lines, pays))
        sales += SaleBundle(draft, lines, pays, i + 1L, prev, hash)
        prev = hash
      }

      /* ---------------- حرکتِ کالا: فروش ---------------- */
      val moves = ArrayList<StockMove>()
      fun move(key: String, productId: String, kind: String, qty: Long, cost: Long?, refType: String, refId: String,
               reason: String, date: String, at: Long) {
        moves += StockMove(
          id = Ids.derive("stockMove", key, 1, at), shopId = shopId, deviceId = deviceId, warehouseId = warehouseId,
          productId = productId, kind = kind, qtyMilli = qty, unitCostMinor = cost, refType = refType, refId = refId,
          reason = reason, businessDate = date, createdAt = at,
        )
      }
      sales.forEach { b ->
        b.lines.forEach { l ->
          val pid = l.productId ?: return@forEach
          move("sale/${l.id}", pid, "sale", -l.qtyMilli, l.unitCostMinor, "sale", b.draft.id, "", b.draft.businessDate, b.draft.createdAt)
        }
      }

      /* ---------------- مرجوعی ---------------- */
      val returns = ArrayList<DbSaleReturn>()
      val returnedByLine = HashMap<String, Long>()
      val legacyItem = d.saleItems.associateBy { it.id }
      val draftById = sales.associate { it.draft.id to it.draft }
      d.saleReturns.forEach { r ->
        val saleId = saleIds[r.saleId]
        val line = lineOfItem[r.saleItemId]
        val qty = q(r.quantity)
        val amount = m(r.amount)
        if (saleId == null || line == null || line.saleId != saleId || qty < 0 || amount < 0) {
          val ls = legacySale[r.saleId]
          val counted = ls == null || ls.status != "cancelled"
          val deltas = ArrayList<Pair<String, Long>>()
          deltas += "rows/saleReturn" to -1L
          if (counted) {
            val item = legacyItem[r.saleItemId]
            val cost = if (item != null) qty * m(item.purchasePrice) else 0L
            val impact = amount * 1000 - cost
            deltas += "sales/returnAmount" to -amount
            deltas += "sales/grossProfit" to impact
            deltas += "sales/netProfit" to impact
            Jalali.ofIso(r.date)?.let {
              val k = LegacyFigures.monthKey(it.year, it.month)
              deltas += "month/$k/grossProfit" to impact
              deltas += "month/$k/netProfit" to impact
            }
          }
          note("ORPHAN_RETURN", r.id, *deltas.toTypedArray())
          return@forEach
        }
        val (id, _) = newId("saleReturn", r.id, r.createdAt)
        returns += DbSaleReturn(id, saleId, line.id, qty, amount, r.reason, r.date, r.createdAt)
        returnedByLine.merge(line.id, qty, Long::plus)
        line.productId?.let { pid ->
          move("return/$id", pid, "customer_return", qty, line.unitCostMinor, "saleReturn", id, r.reason, r.date, r.createdAt)
        }
      }

      //  `returnedQty`ِ ردیف باید با جمعِ ردیف‌های مرجوعی بخواند؛ موجودیِ قدیم از اولی، تازه از دومی
      val firstSales = saleIds.values.toHashSet()
      sales.forEach { b ->
        if (b.draft.id !in firstSales) return@forEach
        b.lines.forEach { l ->
          val item = itemOfLine.getValue(l.id)
          val inserted = returnedByLine[l.id] ?: 0L
          val recorded = q(item.returnedQty)
          if (inserted != recorded && item.saleId !in cancelledLegacy) {
            if (item.productId in realProducts) {
              note("RETURNED_QTY_MISMATCH", item.id, "stock/${item.productId}" to (inserted - recorded))
            } else {
              note("RETURNED_QTY_MISMATCH", item.id)
            }
          }
        }
      }

      /* ---------------- لغو ---------------- */
      val cancelAudit = d.auditLog.filter { it.type.contains("cancel") && it.refId != null }
        .groupBy { it.refId!! }.mapValues { (_, v) -> v.minOf { it.createdAt } }
      val cancels = ArrayList<Cancel>()
      drafts.forEach { (s, draft) ->
        if (s.status != "cancelled") return@forEach
        val reason = s.cancelReason.trim().ifEmpty {
          note("CANCEL_WITHOUT_REASON", s.id)
          NO_REASON
        }
        val at = cancelAudit[s.id] ?: s.createdAt
        cancels += Cancel(draft.id, reason, at)
        linesBySale[draft.id].orEmpty().forEach { l ->
          val pid = l.productId ?: return@forEach
          val back = l.qtyMilli - (returnedByLine[l.id] ?: 0L)
          if (back != 0L) move("cancel/${l.id}", pid, "sale_cancel", back, l.unitCostMinor, "sale", draft.id, reason, draftById.getValue(draft.id).businessDate, at)
        }
      }

      /* ---------------- ورودِ انبار ---------------- */
      d.warehouseEntries.forEach { e ->
        val pid = product(e.productId)
        if (pid == null) { note("ORPHAN_STOCK_ENTRY", e.id, "rows/stockReceipt" to -1); return@forEach }
        val o = occ("warehouseEntry", e.id)
        val kind = when {
          e.isAdjustment -> "adjustment"
          e.purchaseId != null -> "purchase_in"
          else -> "receipt"
        }
        val reason = if (e.isAdjustment) e.notes.trim().ifEmpty { note("ADJUSTMENT_WITHOUT_REASON", e.id); NO_REASON } else e.notes
        move("entry/${e.id}/$o", pid, kind, q(e.units), m(e.price), "warehouseEntry", e.id, reason, e.date, e.createdAt)
        legacyIds += LegacyId("warehouseEntry", e.id, o.toLong(), moves.last().id)
        anyLegacy.putIfAbsent(e.id, moves.last().id)
      }

      /* ---------------- دفترِ مشتری ---------------- */
      val ledger = ArrayList<CustomerLedgerEntry>()
      d.transactions.forEach { t ->
        val give = t.type == "give"
        val amount = m(t.amount)
        val cid = customer(t.debtorId)
        if (cid == null) {
          note("ORPHAN_LEDGER_ENTRY", t.id, "rows/customerLedgerEntry" to -1,
            (if (give) "pay/customerGive" else "pay/customerReceive") to -amount)
          return@forEach
        }
        val (id, _) = newId("ledger", t.id, t.createdAt)
        val delta = if (give) amount else -amount
        val kind = if (delta > 0 || (delta == 0L && give)) "give" else "receive"
        if (amount < 0) note("NEGATIVE_LEDGER_AMOUNT", t.id, "pay/customerGive" to -amount, "pay/customerReceive" to -amount)
        ledger += CustomerLedgerEntry(id, cid, kind, delta, CURRENCY, null, t.notes, t.date, t.createdAt)
      }

      /* ---------------- خرید و پرداخت به تأمین‌کننده ---------------- */
      val invoices = ArrayList<SupplierInvoice>()
      val supplierPayments = ArrayList<DbSupplierPayment>()
      d.purchases.forEach { p ->
        val (id, _) = newId("purchase", p.id, p.createdAt)
        val sid = supplier(p.supplierId)
        invoices += SupplierInvoice(
          id = id, shopId = shopId, supplierId = sid, productId = product(p.productId), qtyMilli = q(p.quantity),
          unitCostMinor = m(p.purchasePrice), totalMinor = m(p.totalAmount), currency = CURRENCY,
          legacyInvoiceNo = p.invoiceNo.toLong(), notes = p.notes, businessDate = p.date, createdAt = p.createdAt,
        )
        val paid = m(p.paidAmount)
        if (paid != 0L) {
          supplierPayments += DbSupplierPayment(Ids.derive("supplierPayment", "purchase/$id", 1, p.createdAt), shopId, sid, id, paid, CURRENCY, "", p.date, p.createdAt)
        }
      }
      d.supplierPayments.forEach { p ->
        val (id, _) = newId("supplierPayment", p.id, p.createdAt)
        supplierPayments += DbSupplierPayment(id, shopId, supplier(p.supplierId), null, m(p.amount), CURRENCY, p.notes, p.date, p.createdAt)
      }

      /* ---------------- مصرف ---------------- */
      val expenses = d.expenses.map { e ->
        val (id, _) = newId("expense", e.id, e.createdAt)
        DbExpense(id, shopId, e.title, e.category, m(e.amount), CURRENCY, e.notes, e.date, e.createdAt)
      }

      /* ---------------- صندوق ---------------- */
      val closes = d.auditLog.filter { it.type == "shift_close" && it.refId != null }.associateBy { it.refId }
      val shifts = ArrayList<CashShift>()
      d.auditLog.filter { it.type == "shift_open" }.forEach { open ->
        val opening = m(open.amount)
        if (opening < 0) { note("NEGATIVE_SHIFT_OPENING", open.id, "rows/cashShift" to -1); return@forEach }
        val (id, _) = newId("cashShift", open.id, open.createdAt)
        val close = closes[open.id]
        shifts += CashShift(
          id = id, shopId = shopId, deviceId = deviceId, openedAt = open.createdAt, openingMinor = opening,
          openedBy = "", businessDate = open.date, closedAt = close?.createdAt, countedMinor = close?.let { m(it.amount) },
          expectedMinor = null, closeNote = close?.notes ?: "",
        )
      }

      /* ---------------- سابقهٔ عملیات (زنجیرهٔ هش) ---------------- */
      val audit = ArrayList<AuditLog>()
      var head = HashChain.GENESIS
      d.auditLog.withIndex().sortedWith(compareBy({ it.value.createdAt }, { it.value.id }, { it.index }))
        .forEachIndexed { i, (_, e) ->
          val (id, _) = newId("auditLog", e.id, e.createdAt)
          val draft = AuditLog(
            id = id, shopId = shopId, deviceId = deviceId, seq = i + 1L, type = e.type,
            refId = e.refId?.let { anyLegacy[it] ?: it }, amountMinor = m(e.amount), notes = e.notes,
            businessDate = e.date, createdAt = e.createdAt, prevHash = head, hash = "",
          )
          val hash = HashChain.hash(head, Docs.audit(draft))
          audit += draft.copy(hash = hash)
          head = hash
        }

      d.sales.filter { Jalali.ofIso(it.date) == null }.forEach { note("DATE_NOT_ISO", "sale:${it.id}") }

      return NewModel(
        currency = Currency(CURRENCY, 2, 100),
        shop = Shop(shopId, storeName, CURRENCY, "Asia/Kabul", 0, firstAt),
        device = Device(deviceId, shopId, SERIES, "دفترِ پیش از مهاجرت", 1, firstAt),
        warehouse = Warehouse(warehouseId, shopId, "انبارِ اصلی", firstAt),
        units = units,
        suppliers = suppliers + placeholderSuppliers,
        products = products + placeholderProducts,
        conversions = conversions,
        barcodes = barcodes,
        priceLists = listOf(retail, wholesale),
        prices = prices,
        customers = customers + placeholderCustomers,
        sales = sales,
        returns = returns,
        cancels = cancels,
        ledger = ledger,
        stockMoves = moves,
        invoices = invoices,
        supplierPayments = supplierPayments,
        expenses = expenses,
        shifts = shifts,
        audit = audit,
        legacyIds = legacyIds,
        anomalies = anomalies,
        productLegacy = productLegacy,
        customerLegacy = customerLegacy,
        supplierLegacy = supplierLegacy,
      )
    }
  }

  /** ضریبِ Double ⇐ کسرِ ساده (صورت، مخرج)؛ اگر با ≤ ۹ رقمِ اعشار نوشته نشود، null */
  fun fraction(f: Double): Pair<Long, Long>? {
    if (!f.isFinite() || f <= 0) return null
    val dec = java.math.BigDecimal(f.toString()).stripTrailingZeros()
    if (dec.scale() > 9) return null
    val den = java.math.BigInteger.TEN.pow(maxOf(dec.scale(), 0))
    val num = dec.movePointRight(maxOf(dec.scale(), 0)).toBigIntegerExact()
    val g = num.gcd(den)
    return num.divide(g).longValueExact() to den.divide(g).longValueExact()
  }
}
