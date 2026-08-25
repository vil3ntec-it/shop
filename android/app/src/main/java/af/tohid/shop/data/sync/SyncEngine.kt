package af.tohid.shop.data.sync

import af.tohid.shop.data.db.*
import af.tohid.shop.data.remote.*
import af.tohid.shop.data.repo.SessionStore
import kotlinx.serialization.json.JsonElement

/**
 * همگام‌سازی دفتر دکان با سرور.
 *
 * قاعده‌ها:
 *   - هر رکورد شناسه‌ی یکتا دارد، پس دو نفر که آفلاین فروخته‌اند
 *     رکورد یکدیگر را پاک نمی‌کنند.
 *   - فقط رکوردهای dirty فرستاده می‌شوند.
 *   - فقط تغییرات بعد از rev آخر گرفته می‌شود، نه کل دفتر.
 *   - در تعارض ویرایش، updatedAt بزرگ‌تر برنده است.
 */
class SyncEngine(
    private val db: TohidDatabase,
    private val session: SessionStore,
) {

    data class Result(val pushed: Int, val pulled: Int, val rev: Long)

    private val json = ApiClient.json

    suspend fun sync(): Result {
        val api = ApiClient.api(session) ?: throw SyncException("آدرس سرور تنظیم نشده است")
        if (!session.isLoggedIn()) throw SyncException("ابتدا وارد حساب شوید")

        val pushed = pushLocal(api)
        val pulled = pullRemote(api)
        session.setLastSyncAt(System.currentTimeMillis())
        return Result(pushed, pulled, session.rev())
    }

    // ---------- فرستادن ----------
    private suspend fun pushLocal(api: TohidApi): Int {
        val changes = mutableListOf<SyncChange>()
        val now = System.currentTimeMillis()
        val me = session.userId()

        // هر نوع صریح رمزگذاری می‌شود تا سریالایزر در زمان کامپایل مشخص باشد
        fun <T : Syncable> add(
            collection: String,
            rows: List<T>,
            id: (T) -> String,
            serializer: kotlinx.serialization.KSerializer<T>,
        ) {
            rows.forEach { row ->
                changes += SyncChange(
                    collection = collection, id = id(row),
                    updatedAt = if (row.updatedAt > 0) row.updatedAt else now,
                    deleted = false, userId = me,
                    data = json.encodeToJsonElement(serializer, row),
                )
            }
        }

        add("products", db.products().dirty(), { it.id }, ProductEntity.serializer())
        add("warehouseEntries", db.warehouse().dirty(), { it.id }, WarehouseEntryEntity.serializer())
        add("sales", db.sales().dirty(), { it.id }, SaleEntity.serializer())
        add("saleItems", db.saleItems().dirty(), { it.id }, SaleItemEntity.serializer())
        add("returns", db.returns().dirty(), { it.id }, ReturnEntity.serializer())
        add("debtors", db.debtors().dirty(), { it.id }, DebtorEntity.serializer())
        add("transactions", db.transactions().dirty(), { it.id }, TransactionEntity.serializer())
        add("expenses", db.expenses().dirty(), { it.id }, ExpenseEntity.serializer())
        add("suppliers", db.suppliers().dirty(), { it.id }, SupplierEntity.serializer())
        add("purchases", db.purchases().dirty(), { it.id }, PurchaseEntity.serializer())
        add("supplierPayments", db.supplierPayments().dirty(), { it.id }, SupplierPaymentEntity.serializer())
        add("stockMovements", db.stockMovements().dirty(), { it.id }, StockMovementEntity.serializer())
        add("auditLog", db.audit().dirty(), { it.id }, AuditEntity.serializer())

        // حذف‌ها
        db.tombstones().dirty().forEach { t ->
            changes += SyncChange(
                collection = t.collection, id = t.recordId,
                updatedAt = t.updatedAt, deleted = true, userId = me, data = null,
            )
        }

        if (changes.isEmpty()) return 0

        api.push(PushRequest(deviceId = session.deviceId(), changes = changes))

        // پس از تأیید سرور، پرچم dirty پاک می‌شود
        clearDirtyFlags(changes)
        return changes.size
    }

    private suspend fun clearDirtyFlags(changes: List<SyncChange>) {
        val byCollection = changes.filter { !it.deleted }.groupBy({ it.collection }, { it.id })
        byCollection["products"]?.let { db.products().clearDirty(it) }
        byCollection["warehouseEntries"]?.let { db.warehouse().clearDirty(it) }
        byCollection["sales"]?.let { db.sales().clearDirty(it) }
        byCollection["saleItems"]?.let { db.saleItems().clearDirty(it) }
        byCollection["returns"]?.let { db.returns().clearDirty(it) }
        byCollection["debtors"]?.let { db.debtors().clearDirty(it) }
        byCollection["transactions"]?.let { db.transactions().clearDirty(it) }
        byCollection["expenses"]?.let { db.expenses().clearDirty(it) }
        byCollection["suppliers"]?.let { db.suppliers().clearDirty(it) }
        byCollection["purchases"]?.let { db.purchases().clearDirty(it) }
        byCollection["supplierPayments"]?.let { db.supplierPayments().clearDirty(it) }
        byCollection["stockMovements"]?.let { db.stockMovements().clearDirty(it) }
        byCollection["auditLog"]?.let { db.audit().clearDirty(it) }
        changes.filter { it.deleted }.forEach { db.tombstones().clearDirty(it.collection, it.id) }
    }

    // ---------- گرفتن ----------
    private suspend fun pullRemote(api: TohidApi): Int {
        var since = session.rev()
        var total = 0
        var guard = 0

        while (guard++ < 100) {
            val page = api.pull(since)
            if (page.changes.isEmpty()) { since = maxOf(since, page.rev); break }
            total += applyRemote(page.changes)
            since = page.rev
            session.setRev(since)
            if (!page.hasMore) break
        }
        session.setRev(since)
        return total
    }

    private suspend fun applyRemote(changes: List<SyncChange>): Int {
        var applied = 0
        for (ch in changes) {
            // تغییرات خودِ این دستگاه دوباره اعمال نمی‌شوند
            val data = ch.data
            if (ch.deleted) {
                deleteLocal(ch.collection, ch.id)
                applied++
                continue
            }
            if (data == null) continue
            runCatching { upsertLocal(ch.collection, data, ch.userId) }
                .onSuccess { applied++ }
        }
        return applied
    }

    private suspend fun upsertLocal(collection: String, data: JsonElement, ownerUserId: String) {
        when (collection) {
            "products" -> db.products().upsert(
                json.decodeFromJsonElement(ProductEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "warehouseEntries" -> db.warehouse().upsert(
                json.decodeFromJsonElement(WarehouseEntryEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "sales" -> db.sales().upsert(
                json.decodeFromJsonElement(SaleEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "saleItems" -> db.saleItems().upsert(
                json.decodeFromJsonElement(SaleItemEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "returns" -> db.returns().upsert(
                json.decodeFromJsonElement(ReturnEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "debtors" -> db.debtors().upsert(
                json.decodeFromJsonElement(DebtorEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "transactions" -> db.transactions().upsert(
                json.decodeFromJsonElement(TransactionEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "expenses" -> db.expenses().upsert(
                json.decodeFromJsonElement(ExpenseEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "suppliers" -> db.suppliers().upsert(
                json.decodeFromJsonElement(SupplierEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "purchases" -> db.purchases().upsert(
                json.decodeFromJsonElement(PurchaseEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "supplierPayments" -> db.supplierPayments().upsert(
                json.decodeFromJsonElement(SupplierPaymentEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "stockMovements" -> db.stockMovements().upsert(
                json.decodeFromJsonElement(StockMovementEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
            "auditLog" -> db.audit().upsert(
                json.decodeFromJsonElement(AuditEntity.serializer(), data)
                    .copy(dirty = false, ownerUserId = ownerUserId))
        }
    }

    private suspend fun deleteLocal(collection: String, id: String) {
        when (collection) {
            "products" -> db.products().delete(id)
            "warehouseEntries" -> db.warehouse().delete(id)
            "sales" -> db.sales().delete(id)
            "saleItems" -> db.saleItems().delete(id)
            "returns" -> db.returns().delete(id)
            "debtors" -> db.debtors().delete(id)
            "transactions" -> db.transactions().delete(id)
            "expenses" -> db.expenses().delete(id)
            "suppliers" -> db.suppliers().delete(id)
            "purchases" -> db.purchases().delete(id)
            "supplierPayments" -> db.supplierPayments().delete(id)
            "stockMovements" -> db.stockMovements().delete(id)
            "auditLog" -> db.audit().delete(id)
        }
    }
}

class SyncException(message: String) : Exception(message)
