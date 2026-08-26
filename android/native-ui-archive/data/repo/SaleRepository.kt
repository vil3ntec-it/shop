package af.tohid.shop.data.repo

import af.tohid.shop.data.db.*
import af.tohid.shop.util.Format
import af.tohid.shop.util.Ids

/** یک قلم در سبد خرید. */
data class CartLine(
    val product: ProductEntity,
    val quantity: Double,
) {
    val lineTotal: Double get() = product.salePrice * quantity
}

/** نتیجه‌ی تلاش برای ثبت فروش. */
sealed interface SaleResult {
    data class Success(val saleId: String, val invoiceNumber: Long, val total: Double) : SaleResult
    data class NotEnoughStock(val productName: String, val available: Double, val message: String) : SaleResult
    data class Invalid(val message: String) : SaleResult
}

/**
 * ثبت فروش.
 *
 * محاسبه‌ها دقیقاً مثل نسخه وب است تا اعداد دو نسخه یکی باشد:
 *   موجودی = ورودی انبار − فروش خالص
 *   تخفیف درصدی روی جمع کل، یا مبلغ ثابت
 *   فروش نسیه: باقی‌مانده به حساب قرض‌دار می‌رود
 */
class SaleRepository(
    private val db: TohidDatabase,
    private val session: SessionStore,
    private val stock: StockRepository,
) {

    /** شماره فاکتور بعدی، داخل بازه‌ی همین عضو تا با بقیه تداخل نکند. */
    private suspend fun nextInvoiceNumber(): Long {
        val block = session.invoiceBlock().takeIf { it > 0 } ?: 1000L
        val maxUsed = db.sales().maxInvoice() ?: 0L
        return if (maxUsed < block) block else maxUsed + 1
    }

    fun computeTotals(
        lines: List<CartLine>,
        discountType: String,
        discountValue: Double,
    ): Triple<Double, Double, Double> {
        val subtotal = lines.sumOf { it.lineTotal }
        val discount = when {
            discountValue <= 0 -> 0.0
            discountType == "percent" -> (subtotal * discountValue / 100.0).coerceIn(0.0, subtotal)
            else -> discountValue.coerceIn(0.0, subtotal)
        }
        return Triple(subtotal, discount, subtotal - discount)
    }

    /**
     * ثبت فروش. پیش از نوشتن، موجودی دوباره بررسی می‌شود تا فروشِ
     * بدون موجودی ثبت نشود.
     */
    suspend fun checkout(
        lines: List<CartLine>,
        discountType: String,
        discountValue: Double,
        paymentMethod: String,          // cash | credit
        debtorId: String?,
        paidAmount: Double,
    ): SaleResult {
        if (lines.isEmpty()) return SaleResult.Invalid("سبد خرید خالی است")

        // بررسی موجودی، درست پیش از ثبت
        for (line in lines) {
            val available = stock.stockOf(line.product.id)
            if (line.quantity > available) {
                return SaleResult.NotEnoughStock(
                    productName = line.product.name,
                    available = available,
                    message = shortMessage(line.product, available),
                )
            }
        }

        val (subtotal, discount, finalTotal) = computeTotals(lines, discountType, discountValue)

        val paid = if (paymentMethod == "cash") finalTotal else paidAmount.coerceIn(0.0, finalTotal)
        val remaining = (finalTotal - paid).coerceAtLeast(0.0)

        if (paymentMethod == "credit" && remaining > 0 && debtorId.isNullOrBlank()) {
            return SaleResult.Invalid("برای فروش نسیه، قرض‌دار را انتخاب کنید")
        }

        val now = System.currentTimeMillis()
        val today = Format.today()
        val saleId = Ids.new()
        val invoice = nextInvoiceNumber()
        val me = session.userId()

        db.sales().upsert(
            SaleEntity(
                id = saleId, invoiceNumber = invoice,
                total = subtotal, discountType = discountType, discountValue = discountValue,
                discount = discount, finalTotal = finalTotal,
                paymentMethod = paymentMethod,
                debtorId = if (remaining > 0) debtorId else null,
                paidAmount = paid, remaining = remaining, status = "completed",
                debtGiven = if (remaining > 0 && debtorId != null) remaining else 0.0,
                debtSettled = 0.0,
                date = today, createdAt = now, updatedAt = now, dirty = true, ownerUserId = me,
            )
        )

        for (line in lines) {
            db.saleItems().upsert(
                SaleItemEntity(
                    id = Ids.new(), saleId = saleId, productId = line.product.id,
                    quantity = line.quantity, unitPrice = line.product.salePrice,
                    purchasePrice = line.product.purchasePrice,
                    totalPrice = line.lineTotal, returnedQty = 0.0,
                    updatedAt = now, dirty = true, ownerUserId = me,
                )
            )
            db.stockMovements().upsert(
                StockMovementEntity(
                    id = Ids.new(), productId = line.product.id, type = "sale",
                    qty = -line.quantity, date = today,
                    notes = "فاکتور #$invoice", refId = saleId, createdAt = now,
                    updatedAt = now, dirty = true, ownerUserId = me,
                )
            )
        }

        // فروش نسیه به حساب قرض‌دار می‌رود
        if (!debtorId.isNullOrBlank() && remaining > 0) {
            db.transactions().upsert(
                TransactionEntity(
                    id = Ids.new(), debtorId = debtorId, type = "give", amount = remaining,
                    date = today, notes = "فروش نسیه — فاکتور #$invoice", createdAt = now,
                    updatedAt = now, dirty = true, ownerUserId = me,
                )
            )
        }

        db.audit().upsert(
            AuditEntity(
                id = Ids.new(), type = "sale", refId = saleId,
                notes = "ثبت فروش فاکتور #$invoice به مبلغ ${Format.money(finalTotal)} افغانی",
                date = today, createdAt = now, updatedAt = now, dirty = true, ownerUserId = me,
            )
        )

        return SaleResult.Success(saleId, invoice, finalTotal)
    }

    /**
     * لغو فاکتور.
     *
     * موجودی خودکار برمی‌گردد، چون محاسبه‌ی «فروش خالص» فاکتورهای لغوشده را
     * نمی‌شمارد. اگر فروش نسیه بوده، بدهی طرف هم با یک تراکنش جبرانی صفر می‌شود
     * تا سابقه‌ی حساب دست‌نخورده بماند.
     */
    suspend fun cancelSale(saleId: String): OpResult {
        val sale = db.sales().byId(saleId) ?: return OpResult.Refused("فاکتور پیدا نشد")
        if (sale.status == "cancelled") return OpResult.Refused("این فاکتور قبلاً لغو شده است")

        val now = System.currentTimeMillis()
        val me = session.userId()
        db.sales().upsert(sale.copy(status = "cancelled", updatedAt = now, dirty = true))

        if (sale.debtGiven > 0 && !sale.debtorId.isNullOrBlank()) {
            db.transactions().upsert(
                TransactionEntity(
                    id = Ids.new(), debtorId = sale.debtorId, type = "receive",
                    amount = sale.debtGiven, date = Format.today(),
                    notes = "لغو فاکتور #${sale.invoiceNumber}", createdAt = now,
                    updatedAt = now, dirty = true, ownerUserId = me,
                )
            )
        }

        db.audit().upsert(
            AuditEntity(
                id = Ids.new(), type = "sale_cancel", refId = saleId,
                notes = "لغو فاکتور #${sale.invoiceNumber} به مبلغ ${Format.money(sale.finalTotal)} افغانی",
                date = Format.today(), createdAt = now, updatedAt = now, dirty = true, ownerUserId = me,
            )
        )
        return OpResult.Ok
    }

    /**
     * مرجوعی یک قلم از فاکتور.
     *
     * تعداد مرجوعی روی خودِ قلم ثبت می‌شود، پس موجودی خودبه‌خود برمی‌گردد و
     * سود گزارش‌ها هم درست می‌ماند (قیمت خرید همان قلم اعتبار می‌گیرد).
     */
    suspend fun returnItem(saleItemId: String, qty: Double, reason: String): OpResult {
        if (qty <= 0.0) return OpResult.Refused("تعداد مرجوعی را وارد کنید")

        val item = db.saleItems().all().firstOrNull { it.id == saleItemId }
            ?: return OpResult.Refused("قلم فاکتور پیدا نشد")
        val sale = db.sales().byId(item.saleId) ?: return OpResult.Refused("فاکتور پیدا نشد")
        if (sale.status == "cancelled") return OpResult.Refused("این فاکتور لغو شده است")

        val remaining = item.quantity - item.returnedQty
        if (qty > remaining) {
            return OpResult.Refused(
                "فقط ${Format.number(remaining)} واحد از این قلم قابل مرجوع است."
            )
        }

        val now = System.currentTimeMillis()
        val me = session.userId()
        val amount = item.unitPrice * qty

        db.saleItems().upsert(
            item.copy(returnedQty = item.returnedQty + qty, updatedAt = now, dirty = true)
        )
        db.returns().upsert(
            ReturnEntity(
                id = Ids.new(), saleId = sale.id, saleItemId = item.id, productId = item.productId,
                quantity = qty, amount = amount, reason = reason, date = Format.today(),
                createdAt = now, updatedAt = now, dirty = true, ownerUserId = me,
            )
        )
        db.stockMovements().upsert(
            StockMovementEntity(
                id = Ids.new(), productId = item.productId, type = "customer_return",
                qty = qty, date = Format.today(),
                notes = "مرجوعی فاکتور #${sale.invoiceNumber}", refId = sale.id,
                createdAt = now, updatedAt = now, dirty = true, ownerUserId = me,
            )
        )

        // اگر نسیه بوده، همین‌قدر از بدهی طرف کم می‌شود
        if (sale.remaining > 0 && !sale.debtorId.isNullOrBlank()) {
            val credit = minOf(amount, sale.remaining)
            if (credit > 0) {
                db.transactions().upsert(
                    TransactionEntity(
                        id = Ids.new(), debtorId = sale.debtorId, type = "receive",
                        amount = credit, date = Format.today(),
                        notes = "مرجوعی فاکتور #${sale.invoiceNumber}", createdAt = now,
                        updatedAt = now, dirty = true, ownerUserId = me,
                    )
                )
                db.sales().upsert(
                    sale.copy(remaining = sale.remaining - credit, updatedAt = now, dirty = true)
                )
            }
        }

        db.audit().upsert(
            AuditEntity(
                id = Ids.new(), type = "sale_return", refId = sale.id,
                notes = "مرجوعی ${Format.number(qty)} واحد از فاکتور #${sale.invoiceNumber}",
                date = Format.today(), createdAt = now, updatedAt = now, dirty = true, ownerUserId = me,
            )
        )
        return OpResult.Ok
    }

    /** همان پیام روشنی که نسخه وب می‌دهد. */
    private fun shortMessage(product: ProductEntity, available: Double): String {
        val u = if (product.unit.isBlank()) "" else " ${product.unit}"
        return if (available <= 0.0) {
            "«${product.name}» در برنامه موجودی ندارد. اگر جنس در دکان هست، اول ورودی انبار را ثبت کنید."
        } else {
            "«${product.name}»: فقط ${Format.number(available)}$u موجود است."
        }
    }
}
