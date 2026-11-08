package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.core.i18n.StrExpenses
import ir.vil3ntec.tohid.core.purchase.ReorderCore

/**
 *  مصارف، خرید و تأمین‌کننده‌ها.
 *
 *  سه چیزِ به‌هم‌چسبیده: خرید هم‌زمان جنس را وارد انبار می‌کند، بدهیِ
 *  تأمین‌کننده را بالا می‌برد و اگر قیمتِ خرید عوض شده باشد آن را هم ثبت
 *  می‌کند. در نسخهٔ وب هم همین‌طور بود؛ اگر اینجا یکی‌شان جا بیفتد، عددِ
 *  انبار و عددِ حسابِ تأمین‌کننده از هم جدا می‌افتند.
 */
object LedgerEngine {

  sealed interface Result {
    data class Ok(val data: ShopData, val id: String) : Result
    data class Failed(val message: String) : Result
  }

  /* ============================== مصارف ============================== */

  data class ExpenseDraft(
    val title: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val notes: String = "",
  )

  fun addExpense(d: ShopData, draft: ExpenseDraft, today: String, now: Long, newId: () -> String): Result {
    if (draft.amount.isNaN() || draft.amount <= 0) return Result.Failed(StrExpenses.amountInvalid)
    val category = draft.category.trim().ifBlank { d.expenseCategories.firstOrNull().orEmpty() }
    if (category.isBlank()) return Result.Failed(StrExpenses.pickCategory)

    // «بابت چی؟» اختیاری است: نامِ دسته‌بندی به‌جایش می‌نشیند تا فهرستِ
    // مصارف ردیفِ بی‌عنوان نداشته باشد — همان کارِ نسخهٔ وب
    val title = draft.title.trim().ifBlank { category }
    val id = newId()

    return Result.Ok(
      d.copy(
        expenses = d.expenses + Expense(
          id = id,
          title = title,
          category = category,
          amount = draft.amount,
          date = draft.date.ifBlank { today },
          notes = draft.notes,
          createdAt = now,
        ),
        expenseCategories = withValue(d.expenseCategories, category),
      ),
      id = id,
    )
  }

  fun editExpense(d: ShopData, id: String, draft: ExpenseDraft, today: String): Result {
    val existing = d.expenses.find { it.id == id } ?: return Result.Failed(StrExpenses.expenseNotFound)
    if (draft.amount.isNaN() || draft.amount <= 0) return Result.Failed(StrExpenses.amountInvalid)
    val category = draft.category.trim().ifBlank { existing.category }
    val title = draft.title.trim().ifBlank { category }

    return Result.Ok(
      d.copy(
        expenses = d.expenses.map {
          if (it.id == id) it.copy(
            title = title,
            category = category,
            amount = draft.amount,
            date = draft.date.ifBlank { today },
          ) else it
        },
        expenseCategories = withValue(d.expenseCategories, category),
      ),
      id = id,
    )
  }

  fun deleteExpense(
    d: ShopData,
    id: String,
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): Result {
    val e = d.expenses.find { it.id == id } ?: return Result.Failed(StrExpenses.expenseNotFound)
    //  ردپا: هزینهٔ پاک‌شده سودِ خالص را بالا می‌برد و باید معلوم بماند
    val trace = AuditEntry(
      id = newId(), type = "expense_delete", date = today, refId = id, createdAt = now,
      notes = "حذفِ مصرفِ «${e.title}» ${money(e.amount)} افغانی (تاریخ ${e.date})",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
    )
    return Result.Ok(d.copy(expenses = d.expenses.filter { it.id != id }, auditLog = d.auditLog + trace), id = id)
  }

  /* ========================== تأمین‌کننده‌ها ========================== */

  data class SupplierDraft(
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    /** PUR-10 — زمانِ تأمینِ دستی (روز)؛ `null` ⇒ خالی (همان سنجیده از سفارش‌ها) */
    val leadDays: Int? = null,
    /** فرمی که این فیلد را دارد ⇒ true؛ فرمِ بی فیلد زمانِ پیشین را دست نمی‌زند */
    val leadSet: Boolean = false,
  )

  /** زمانِ دستیِ معتبر (۱ تا ۳۶۵) یا null */
  private fun lead(draft: SupplierDraft): Int? = draft.leadDays?.takeIf { it in 1..ReorderCore.MAX_MANUAL_LEAD_DAYS }

  fun addSupplier(d: ShopData, draft: SupplierDraft, now: Long, newId: () -> String): Result {
    val name = draft.name.trim()
    if (name.isEmpty()) return Result.Failed(StrLedger.supplierNameNeeded)
    val id = newId()
    return Result.Ok(
      d.copy(
        suppliers = d.suppliers + Supplier(
          id = id,
          name = name,
          phone = ir.vil3ntec.tohid.LocaleText.phoneForSave(draft.phone),
          address = draft.address.trim(),
          notes = draft.notes.trim(),
          createdAt = now,
          leadDays = lead(draft),
        )
      ),
      id = id,
    )
  }

  fun editSupplier(d: ShopData, id: String, draft: SupplierDraft): Result {
    if (d.suppliers.none { it.id == id }) return Result.Failed(StrLedger.supplierNotFound)
    val name = draft.name.trim()
    if (name.isEmpty()) return Result.Failed(StrLedger.supplierNameNeeded)
    return Result.Ok(
      d.copy(
        suppliers = d.suppliers.map {
          if (it.id == id) it.copy(
            name = name,
            phone = ir.vil3ntec.tohid.LocaleText.phoneForSave(draft.phone),
            address = draft.address.trim(),
            notes = draft.notes.trim(),
            leadDays = if (draft.leadSet) lead(draft) else it.leadDays,
          ) else it
        }
      ),
      id = id,
    )
  }

  /**
   * حذفِ تأمین‌کننده.
   *
   * اگر خرید یا پرداختی به نامش ثبت شده باشد، حذف نمی‌شود: آن ارقام در
   * حسابِ دکان اثر دارند و بی‌صاحب کردنشان یعنی بدهی‌ای که دیگر معلوم
   * نیست به کیست.
   */
  fun deleteSupplier(d: ShopData, id: String): Result {
    val supplier = d.suppliers.find { it.id == id } ?: return Result.Failed(StrLedger.supplierNotFound)
    val purchases = d.purchases.count { it.supplierId == id }
    val payments = d.supplierPayments.count { it.supplierId == id }
    if (purchases > 0 || payments > 0) {
      return Result.Failed(StrLedger.supplierInUse(supplier.name))
    }
    return Result.Ok(d.copy(suppliers = d.suppliers.filter { it.id != id }), id = id)
  }

  /* ============================== خرید ============================== */

  data class PurchaseDraft(
    val supplierId: String = "",
    val productId: String = "",
    val quantity: Double = 0.0,
    val unit: String = "",
    val purchasePrice: Double = 0.0,
    val paidAmount: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    /** تاریخِ انقضا و تولیدِ همین محموله (اختیاری) — روی ورودِ انبار می‌نشیند */
    val expiryDate: String = "",
    val productionDate: String = "",
    /*  موتورِ تازهٔ موجودی (`StockLedger`) — `null` = فرمِ امروز (خاموش) */
    /** هزینهٔ جانبیِ همین خرید (کرایه، گمرک) به افغانی — روی بهای کالا، نه بدهیِ تأمین‌کننده (PUR-06) */
    val landedCost: Double? = null,
    val userId: String? = null,
    /** سررسیدِ پرداخت (`yyyy-MM-dd`، PUR-07)؛ `null` یا خالی = ندارد */
    val dueDate: String? = null,
    /**
     *  PRD-07 — ردیف به واحدِ خرید (کارتنِ ۲۴): با این، `quantity` و `purchasePrice` ِ بالا نادیده‌اند و موتور مقدار و بهای
     *  واحدِ پایه را از هسته می‌سازد (`PurchaseUnits.baseCost`)؛ جمعِ خرید، بدهی و ارزشِ انبار (`WarehouseEntry.costMinor`)
     *  خودِ [InUnit.totalMinor] است، بی گرد کردنِ دوباره. `null` = همان خریدِ امروز.
     */
    val inUnit: InUnit? = null,
  )

  /** ردیفِ خرید به واحدِ خرید: نام، مقدار به همان واحد (هزارم)، ضریب به واحدِ پایه (هزارم) و جمعِ دقیقِ ردیف (صدمِ افغانی) */
  data class InUnit(val name: String, val qtyMilli: Long, val factorMilli: Long, val totalMinor: Long)

  /**
   * ثبتِ خرید — یک کار، چهار اثر:
   *   • جنس وارد انبار می‌شود
   *   • حرکتِ انبار ثبت می‌شود
   *   • باقیِ مبلغ به حسابِ تأمین‌کننده می‌رود
   *   • اگر قیمتِ خرید عوض شده، هم روی کالا می‌نشیند هم در تاریخچه می‌ماند
   *
   * ویرایشِ خرید عمداً نیست — همان‌طور که در نسخهٔ وب. تغییرِ یک خریدِ
   * ثبت‌شده باید هم انبار، هم بدهی و هم تاریخچهٔ قیمت را عقب ببرد، و
   * نیمه‌کاره انجام‌شدنش بدتر از نبودنش است. برای اصلاح، ورودی‌اش حذف و
   * دوباره ثبت می‌شود.
   */
  fun addPurchase(d: ShopData, draft: PurchaseDraft, today: String, now: Long, newId: () -> String): Result {
    val supplier = d.suppliers.find { it.id == draft.supplierId }
      ?: return Result.Failed(StrPurchasePages.chooseSupplier)
    val product = d.products.find { it.id == draft.productId }
      ?: return Result.Failed(StrLedger.pickProduct)
    //  PRD-07: ردیف به واحدِ خرید ⇒ مقدار و بهای واحدِ پایه از هسته (`PurchaseUnits.baseCost`)، جمع همان جمعِ دقیقِ ردیف
    val pu = draft.inUnit?.let { u ->
      if (u.name.isBlank() || u.qtyMilli <= 0 || u.factorMilli <= 0) return Result.Failed(StrStock.errAmount)
      if (u.totalMinor < 0) return Result.Failed(StrStock.errPrice)
      runCatching { ir.vil3ntec.tohid.core.product.PurchaseUnits.baseCost(u.qtyMilli, u.factorMilli, u.totalMinor) }.getOrNull()
        ?: return Result.Failed(StrStock.errAmount)
    }
    val quantity = pu?.let { it.baseQtyMilli / 1000.0 } ?: draft.quantity
    val unitPrice = pu?.let { it.unitCostMinor / 100.0 } ?: draft.purchasePrice
    if (quantity.isNaN() || quantity <= 0) return Result.Failed(StrStock.errAmount)
    if (unitPrice.isNaN() || unitPrice < 0) return Result.Failed(StrStock.errPrice)
    if (draft.landedCost != null && (draft.landedCost.isNaN() || draft.landedCost < 0)) return Result.Failed(StrPurchasePages.landedBad)
    val due = draft.dueDate?.trim()?.ifEmpty { null }
    if (due != null && !ir.vil3ntec.tohid.core.stock.StockCore.isoDateOk(due)) return Result.Failed(StrLedger.dueBad)
    val landed = draft.landedCost?.takeIf { it > 0 }

    val date = draft.date.ifBlank { today }
    if (due != null && due < date) return Result.Failed(PurchaseBook.DUE_BEFORE_DATE)
    val unit = draft.unit.ifBlank { product.unit }
    // گِرد کردن دقیقاً همان‌جای نسخهٔ وب است
    val totalAmount = pu?.let { it.totalMinor / 100.0 } ?: Math.round(quantity * unitPrice).toDouble()
    var paid = if (draft.paidAmount.isNaN() || draft.paidAmount < 0) 0.0 else draft.paidAmount
    paid = minOf(paid, totalAmount)

    val purchaseId = newId()
    //  شناسهٔ ساخته از کلیدِ فرم (`PurchaseBook.record(key)`، `keyedIds`) که پیش‌تر نشسته ⇒ همان خرید دوباره (PUR-05)
    if (d.purchases.any { it.id == purchaseId }) return Result.Failed(PurchaseBook.ALREADY_RECORDED)
    val entryId = newId()

    var products = d.products
    var history = d.priceHistory
    var audit = d.auditLog

    // قیمتِ خریدِ کالا با آخرین خرید به‌روز می‌شود، و تغییرش می‌ماند
    if (product.purchasePrice != unitPrice) {
      history = history + PriceChange(
        id = newId(),
        productId = product.id,
        oldPrice = product.purchasePrice,
        newPrice = unitPrice,
        date = today,
        createdAt = now,
      )
      audit = audit + AuditEntry(
        id = newId(),
        type = "price_change",
        date = today,
        refId = product.id,
        notes = "تغییر قیمت خرید از ${money(product.purchasePrice)} به ${money(unitPrice)} افغانی",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        createdAt = now,
      )
      products = products.map {
        if (it.id == product.id) it.copy(purchasePrice = unitPrice) else it
      }
    }

    audit = audit + AuditEntry(
      id = newId(),
      type = "purchase",
      date = date,
      refId = purchaseId,
      notes = "ثبت خرید ${draft.inUnit?.let { "${money(it.qtyMilli / 1000.0)} ${it.name} = " }.orEmpty()}${money(quantity)} $unit از «${supplier.name}» به مبلغ ${money(totalAmount)} افغانی",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
      createdAt = now,
    )

    return Result.Ok(
      d.copy(
        products = products,
        priceHistory = history,
        auditLog = audit,
        warehouseEntries = d.warehouseEntries + WarehouseEntry(
          id = entryId,
          productId = product.id,
          cartons = 0.0,
          perCarton = 0.0,
          units = quantity,
          unit = unit,
          price = unitPrice,
          date = date,
          notes = draft.notes.ifBlank { "خرید از تأمین‌کننده" },  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
          purchaseId = purchaseId,
          createdAt = now,
          expiryDate = draft.expiryDate,
          productionDate = draft.productionDate,
          landedCost = landed,
          landedCostMinor = landed?.let(StockLedger::minor),
          userId = draft.userId?.trim()?.ifEmpty { null },
          costMinor = pu?.totalMinor,
        ),
        purchases = d.purchases + Purchase(
          id = purchaseId,
          productId = product.id,
          supplierId = supplier.id,
          quantity = quantity,
          unit = unit,
          purchasePrice = unitPrice,
          totalAmount = totalAmount,
          date = date,
          notes = draft.notes,
          paidAmount = paid,
          debt = totalAmount - paid,
          warehouseEntryId = entryId,
          createdAt = now,
          dueDate = due,
          //  خریدِ تکی: هزینهٔ جانبی روی ردیفِ خرید هم (مثلِ فاکتورِ چندردیفه، `PurchaseBook`)
          landedCost = landed,
          landedCostMinor = landed?.let(StockLedger::minor),
          unitName = draft.inUnit?.name,
          unitQtyMilli = draft.inUnit?.qtyMilli,
          unitFactorMilli = draft.inUnit?.factorMilli,
        ),
        stockMovements = d.stockMovements + StockMovement(
          id = newId(),
          productId = product.id,
          type = "purchase_in",
          qty = quantity,
          date = date,
          notes = draft.notes.ifBlank { "ثبت خرید" },  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
          refId = purchaseId,
          createdAt = now,
        ),
      ),
      id = purchaseId,
    )
  }

  /** پرداخت به تأمین‌کننده — بدهیِ ما به او را کم می‌کند */
  fun paySupplier(
    d: ShopData,
    supplierId: String,
    amount: Double,
    date: String,
    notes: String,
    today: String,
    now: Long,
    newId: () -> String,
    /**
     *  کلیدِ یکتای همین پرداخت (برگه یک بار می‌سازد، PUR-07): شناسهٔ پرداخت و سابقه‌اش از آن ساخته می‌شود، پس دو
     *  لمسِ پشتِ‌سرِهم یک پرداخت است — با دفترِ کهنه همان شناسه (ادغام یکی‌اش می‌کند)، با دفترِ تازه رد. `null` = پیشین.
     */
    key: String? = null,
  ): Result {
    val k = key?.trim()?.ifEmpty { null }
    if (k != null && d.supplierPayments.any { it.id == k }) return Result.Failed(ALREADY_PAID)
    val supplier = d.suppliers.find { it.id == supplierId } ?: return Result.Failed(StrLedger.supplierNotFound)
    if (amount.isNaN() || amount <= 0) return Result.Failed(StrLedger.amountBad)

    val id = k ?: newId()
    val when_ = date.ifBlank { today }
    return Result.Ok(
      d.copy(
        supplierPayments = d.supplierPayments + SupplierPayment(
          id = id,
          supplierId = supplierId,
          amount = amount,
          date = when_,
          notes = notes.trim(),
          createdAt = now,
        ),
        auditLog = d.auditLog + AuditEntry(
          id = if (k != null) "$k-a" else newId(),
          type = "supplier_payment",
          date = when_,
          refId = id,
          notes = "پرداخت به «${supplier.name}» به مبلغ ${money(amount)} افغانی",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
          createdAt = now,
        ),
      ),
      id = id,
    )
  }

  /** همان کلیدِ پرداخت به تأمین‌کننده دوباره (دو لمس، PUR-07) — پرداخت یک بار ثبت شده است */
  val ALREADY_PAID: String get() = StrLedger.alreadyPaid

  /* ============================== خلاصه‌ها ============================== */

  /** مصارفِ یک بازهٔ زمانی، دسته به دسته */
  fun expensesByCategory(d: ShopData, from: String, to: String): List<Pair<String, Double>> =
    d.expenses
      .filter { it.date in from..to }
      .groupBy { it.category }
      .map { (category, list) -> category to list.sumOf { it.amount } }
      .sortedByDescending { it.second }

  fun expenseTotal(d: ShopData, from: String, to: String): Double =
    d.expenses.filter { it.date in from..to }.sumOf { it.amount }

  private fun withValue(list: List<String>, value: String): List<String> {
    val v = value.trim()
    return if (v.isEmpty() || list.contains(v)) list else list + v
  }
}
