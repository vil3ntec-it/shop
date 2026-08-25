package af.tohid.shop.data.repo

import af.tohid.shop.data.db.*
import af.tohid.shop.util.Format
import af.tohid.shop.util.Ids

/** نتیجه‌ی یک عملیات نوشتن — یا انجام شد، یا با دلیلِ روشن رد شد. */
sealed interface OpResult {
    data object Ok : OpResult
    data class Refused(val message: String) : OpResult
    /** انجام شد، ولی کاربر باید بداند چه اتفاقی افتاد. */
    data class OkWithWarning(val message: String) : OpResult
}

/**
 * نوشتن روی کالاها، انبار، قرض‌داران و مصارف.
 *
 * همان محافظ‌هایی که در نسخه‌ی وب اضافه شد اینجا هم هست:
 *  • حذف ورودی انبار اگر موجودی را منفی کند، رد می‌شود.
 *  • حذف قرض‌داری که بدهی باز دارد، هشدار می‌دهد.
 *  • حذف کالایی که سابقه‌ی فروش دارد، هشدار می‌دهد.
 */
class CatalogRepository(
    private val db: TohidDatabase,
    private val session: SessionStore,
    private val stock: StockRepository,
) {

    private fun now() = System.currentTimeMillis()
    private fun me() = session.userId()

    private suspend fun audit(type: String, refId: String, notes: String) {
        db.audit().upsert(
            AuditEntity(
                id = Ids.new(), type = type, refId = refId, notes = notes,
                date = Format.today(), createdAt = now(),
                updatedAt = now(), dirty = true, ownerUserId = me(),
            )
        )
    }

    private suspend fun tombstone(collection: String, id: String) {
        db.tombstones().add(TombstoneEntity(collection, id, now(), dirty = true))
    }

    /* ---------------------------------------------------------------- */
    /*  کالاها                                                           */
    /* ---------------------------------------------------------------- */

    suspend fun saveProduct(p: ProductEntity, isNew: Boolean): OpResult {
        if (p.name.isBlank()) return OpResult.Refused("نام کالا را بنویسید")
        if (p.salePrice < 0 || p.purchasePrice < 0) {
            return OpResult.Refused("قیمت نمی‌تواند منفی باشد")
        }
        val clash = db.products().all().firstOrNull {
            it.id != p.id && it.name.trim() == p.name.trim()
        }
        if (clash != null) return OpResult.Refused("کالایی به همین نام از قبل ثبت شده است")

        db.products().upsert(
            p.copy(
                name = p.name.trim(),
                createdAt = if (p.createdAt == 0L) now() else p.createdAt,
                updatedAt = now(), dirty = true,
                ownerUserId = if (p.ownerUserId.isBlank()) me() else p.ownerUserId,
            )
        )
        audit(
            if (isNew) "product_add" else "product_edit", p.id,
            (if (isNew) "افزودن کالا: " else "ویرایش کالا: ") + p.name.trim(),
        )
        return OpResult.Ok
    }

    /**
     * حذف کالا. اگر سابقه‌ی فروش دارد اجازه داده می‌شود ولی کاربر باید بداند
     * که گزارش‌های گذشته به این کالا ارجاع می‌دهند.
     */
    suspend fun deleteProduct(id: String): OpResult {
        val p = db.products().byId(id) ?: return OpResult.Refused("کالا پیدا نشد")
        val sold = db.saleItems().soldQtyFor(id)
        val inStock = stock.stockOf(id)

        db.products().delete(id)
        tombstone("products", id)
        audit("product_delete", id, "حذف کالا: ${p.name}")

        return when {
            sold > 0.0 -> OpResult.OkWithWarning(
                "«${p.name}» حذف شد. توجه: ${Format.number(sold)} واحد از این کالا قبلاً فروخته شده " +
                    "و در گزارش‌های گذشته باقی می‌ماند."
            )
            inStock > 0.0 -> OpResult.OkWithWarning(
                "«${p.name}» حذف شد، در حالی که ${Format.number(inStock)} واحد موجودی داشت."
            )
            else -> OpResult.Ok
        }
    }

    /** بررسی پیش از حذف — برای نشان دادن هشدار در گفتگوی تأیید. */
    suspend fun productDeleteWarning(id: String): String? {
        val p = db.products().byId(id) ?: return null
        val sold = db.saleItems().soldQtyFor(id)
        val inStock = stock.stockOf(id)
        return when {
            sold > 0.0 -> "این کالا ${Format.number(sold)} واحد سابقه‌ی فروش دارد. " +
                "با حذف آن، گزارش‌های گذشته ناقص نمایش داده می‌شوند."
            inStock > 0.0 -> "این کالا ${Format.number(inStock)} واحد موجودی دارد."
            else -> null
        }
    }

    /* ---------------------------------------------------------------- */
    /*  انبار                                                            */
    /* ---------------------------------------------------------------- */

    suspend fun saveWarehouseEntry(e: WarehouseEntryEntity, isNew: Boolean): OpResult {
        val product = db.products().byId(e.productId)
            ?: return OpResult.Refused("اول کالا را انتخاب کنید")
        if (e.units == 0.0) return OpResult.Refused("تعداد را وارد کنید")

        // ویرایش نباید موجودی را منفی کند
        if (!isNew) {
            val old = db.warehouse().byId(e.id)
            if (old != null) {
                val after = stock.stockOf(e.productId) - old.units + e.units
                if (after < 0) {
                    return OpResult.Refused(
                        "با این تغییر، موجودی «${product.name}» به ${Format.number(after)} می‌رسد. " +
                            "چون این مقدار قبلاً فروخته شده، تعداد را کمتر از این نکنید."
                    )
                }
            }
        }

        db.warehouse().upsert(
            e.copy(
                unit = if (e.unit.isBlank()) product.unit else e.unit,
                date = if (e.date.isBlank()) Format.today() else e.date,
                createdAt = if (e.createdAt == 0L) now() else e.createdAt,
                updatedAt = now(), dirty = true,
                ownerUserId = if (e.ownerUserId.isBlank()) me() else e.ownerUserId,
            )
        )
        db.stockMovements().upsert(
            StockMovementEntity(
                id = Ids.new(), productId = e.productId,
                type = if (e.isAdjustment) "adjustment" else "purchase_in",
                qty = e.units, date = e.date.ifBlank { Format.today() },
                notes = e.notes, refId = e.id, createdAt = now(),
                updatedAt = now(), dirty = true, ownerUserId = me(),
            )
        )
        audit(
            if (isNew) "stock_in" else "stock_edit", e.id,
            "${if (isNew) "ورود" else "ویرایش ورود"} ${Format.number(e.units)} ${product.unit} «${product.name}»",
        )
        return OpResult.Ok
    }

    /**
     * حذف ورودی انبار — فقط وقتی موجودی بعد از حذف منفی نشود.
     * (باگی که در نسخه‌ی وب موجودی را به منفی می‌برد.)
     */
    suspend fun deleteWarehouseEntry(id: String): OpResult {
        val e = db.warehouse().byId(id) ?: return OpResult.Refused("ورودی پیدا نشد")
        val product = db.products().byId(e.productId)
        val after = stock.stockOf(e.productId) - e.units
        if (after < 0) {
            val name = product?.name ?: "این کالا"
            return OpResult.Refused(
                "این ورودی حذف نمی‌شود: موجودی «$name» به ${Format.number(after)} می‌رسد، " +
                    "یعنی بیشتر از چیزی که فروخته شده. اول فروش‌های مربوطه را اصلاح کنید."
            )
        }
        db.warehouse().delete(id)
        tombstone("warehouse_entries", id)
        audit("stock_delete", id, "حذف ورود انبار «${product?.name ?: ""}»")
        return OpResult.Ok
    }

    /* ---------------------------------------------------------------- */
    /*  قرض‌داران                                                        */
    /* ---------------------------------------------------------------- */

    suspend fun saveDebtor(d: DebtorEntity, isNew: Boolean): OpResult {
        if (d.name.isBlank()) return OpResult.Refused("نام قرض‌دار را بنویسید")
        db.debtors().upsert(
            d.copy(
                name = d.name.trim(),
                createdAt = if (d.createdAt == 0L) now() else d.createdAt,
                updatedAt = now(), dirty = true,
                ownerUserId = if (d.ownerUserId.isBlank()) me() else d.ownerUserId,
            )
        )
        audit(
            if (isNew) "debtor_add" else "debtor_edit", d.id,
            (if (isNew) "افزودن قرض‌دار: " else "ویرایش قرض‌دار: ") + d.name.trim(),
        )
        return OpResult.Ok
    }

    suspend fun debtorDeleteWarning(id: String): String? {
        val balance = db.transactions().balanceOf(id)
        if (balance <= 0.0) return null
        return "این شخص ${Format.money(balance)} افغانی بدهی باز دارد. " +
            "با حذف او، این بدهی از دفتر پاک می‌شود و دیگر قابل پیگیری نیست."
    }

    suspend fun deleteDebtor(id: String): OpResult {
        val d = db.debtors().byId(id) ?: return OpResult.Refused("قرض‌دار پیدا نشد")
        val balance = db.transactions().balanceOf(id)
        for (t in db.transactions().forDebtor(id)) {
            db.transactions().delete(t.id)
            tombstone("transactions", t.id)
        }
        db.debtors().delete(id)
        tombstone("debtors", id)
        audit("debtor_delete", id, "حذف قرض‌دار: ${d.name}")
        return if (balance > 0.0) {
            OpResult.OkWithWarning("«${d.name}» با ${Format.money(balance)} افغانی بدهی باز حذف شد.")
        } else OpResult.Ok
    }

    /** ثبت «دادن» (بدهی جدید) یا «گرفتن» (پرداخت). */
    suspend fun addTransaction(
        debtorId: String,
        type: String,          // give | receive
        amount: Double,
        notes: String,
    ): OpResult {
        if (amount <= 0.0) return OpResult.Refused("مبلغ را وارد کنید")
        val d = db.debtors().byId(debtorId) ?: return OpResult.Refused("قرض‌دار پیدا نشد")

        if (type == "receive") {
            val balance = db.transactions().balanceOf(debtorId)
            if (amount > balance) {
                return OpResult.Refused(
                    "«${d.name}» فقط ${Format.money(balance)} افغانی بدهی دارد. " +
                        "مبلغ دریافتی نمی‌تواند بیشتر از این باشد."
                )
            }
        }

        db.transactions().upsert(
            TransactionEntity(
                id = Ids.new(), debtorId = debtorId, type = type, amount = amount,
                date = Format.today(), notes = notes, createdAt = now(),
                updatedAt = now(), dirty = true, ownerUserId = me(),
            )
        )
        audit(
            if (type == "give") "debt_give" else "debt_receive", debtorId,
            "${if (type == "give") "بدهی جدید" else "دریافت"} ${Format.money(amount)} افغانی — ${d.name}",
        )
        return OpResult.Ok
    }

    /* ---------------------------------------------------------------- */
    /*  تأمین‌کننده‌ها و خریداری                                          */
    /* ---------------------------------------------------------------- */

    suspend fun saveSupplier(sp: SupplierEntity, isNew: Boolean): OpResult {
        if (sp.name.isBlank()) return OpResult.Refused("نام تأمین‌کننده را بنویسید")
        db.suppliers().upsert(
            sp.copy(
                name = sp.name.trim(),
                createdAt = if (sp.createdAt == 0L) now() else sp.createdAt,
                updatedAt = now(), dirty = true,
                ownerUserId = if (sp.ownerUserId.isBlank()) me() else sp.ownerUserId,
            )
        )
        audit(
            if (isNew) "supplier_add" else "supplier_edit", sp.id,
            (if (isNew) "افزودن تأمین‌کننده: " else "ویرایش تأمین‌کننده: ") + sp.name.trim(),
        )
        return OpResult.Ok
    }

    /** بدهی باز به یک تأمین‌کننده = (کل خرید − پرداخت‌های همراه خرید) − پرداخت‌های جداگانه. */
    suspend fun supplierBalance(id: String): Double =
        db.purchases().unpaidFor(id) - db.supplierPayments().paidTo(id)

    suspend fun deleteSupplier(id: String): OpResult {
        val sp = db.suppliers().byId(id) ?: return OpResult.Refused("تأمین‌کننده پیدا نشد")
        val balance = supplierBalance(id)
        if (balance > 0.0) {
            return OpResult.Refused(
                "«${sp.name}» ${Format.money(balance)} افغانی بدهی باز دارد. " +
                    "اول حساب را تسویه کنید یا خریدهای مربوطه را اصلاح کنید."
            )
        }
        db.suppliers().delete(id)
        tombstone("suppliers", id)
        audit("supplier_delete", id, "حذف تأمین‌کننده: ${sp.name}")
        return OpResult.Ok
    }

    suspend fun addPurchase(
        supplierId: String,
        productId: String,
        quantity: Double,
        unitPrice: Double,
        paidAmount: Double,
        notes: String,
    ): OpResult {
        val sp = db.suppliers().byId(supplierId) ?: return OpResult.Refused("تأمین‌کننده را انتخاب کنید")
        if (quantity <= 0.0) return OpResult.Refused("تعداد را وارد کنید")
        if (unitPrice < 0.0) return OpResult.Refused("قیمت نمی‌تواند منفی باشد")

        val total = quantity * unitPrice
        val paid = paidAmount.coerceIn(0.0, total)

        db.purchases().upsert(
            PurchaseEntity(
                id = Ids.new(), supplierId = supplierId, productId = productId,
                quantity = quantity, unitPrice = unitPrice, totalAmount = total,
                paidAmount = paid, debt = total - paid, date = Format.today(), notes = notes,
                createdAt = now(), updatedAt = now(), dirty = true, ownerUserId = me(),
            )
        )
        audit(
            "purchase_add", supplierId,
            "خرید ${Format.money(total)} افغانی از «${sp.name}»" +
                if (total - paid > 0) " — ${Format.money(total - paid)} افغانی بدهی" else "",
        )
        return OpResult.Ok
    }

    suspend fun paySupplier(supplierId: String, amount: Double, notes: String): OpResult {
        if (amount <= 0.0) return OpResult.Refused("مبلغ را وارد کنید")
        val sp = db.suppliers().byId(supplierId) ?: return OpResult.Refused("تأمین‌کننده پیدا نشد")
        val balance = supplierBalance(supplierId)
        if (amount > balance) {
            return OpResult.Refused(
                "بدهی شما به «${sp.name}» ${Format.money(balance)} افغانی است. " +
                    "پرداخت نمی‌تواند بیشتر از این باشد."
            )
        }
        db.supplierPayments().upsert(
            SupplierPaymentEntity(
                id = Ids.new(), supplierId = supplierId, amount = amount,
                date = Format.today(), notes = notes, createdAt = now(),
                updatedAt = now(), dirty = true, ownerUserId = me(),
            )
        )
        audit("supplier_payment", supplierId, "پرداخت ${Format.money(amount)} افغانی به «${sp.name}»")
        return OpResult.Ok
    }

    /* ---------------------------------------------------------------- */
    /*  مصارف                                                            */
    /* ---------------------------------------------------------------- */

    suspend fun saveExpense(e: ExpenseEntity, isNew: Boolean): OpResult {
        if (e.title.isBlank()) return OpResult.Refused("عنوان مصرف را بنویسید")
        if (e.amount <= 0.0) return OpResult.Refused("مبلغ را وارد کنید")
        db.expenses().upsert(
            e.copy(
                title = e.title.trim(),
                date = e.date.ifBlank { Format.today() },
                createdAt = if (e.createdAt == 0L) now() else e.createdAt,
                updatedAt = now(), dirty = true,
                ownerUserId = if (e.ownerUserId.isBlank()) me() else e.ownerUserId,
            )
        )
        audit(
            if (isNew) "expense_add" else "expense_edit", e.id,
            "${if (isNew) "ثبت" else "ویرایش"} مصرف «${e.title.trim()}» ${Format.money(e.amount)} افغانی",
        )
        return OpResult.Ok
    }

    suspend fun deleteExpense(id: String): OpResult {
        val e = db.expenses().byId(id) ?: return OpResult.Refused("مصرف پیدا نشد")
        db.expenses().delete(id)
        tombstone("expenses", id)
        audit("expense_delete", id, "حذف مصرف «${e.title}»")
        return OpResult.Ok
    }
}
