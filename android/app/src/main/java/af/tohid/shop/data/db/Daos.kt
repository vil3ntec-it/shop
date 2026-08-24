package af.tohid.shop.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name") fun observeAll(): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products ORDER BY name") suspend fun all(): List<ProductEntity>
    @Query("SELECT * FROM products WHERE id = :id") suspend fun byId(id: String): ProductEntity?
    @Query("SELECT * FROM products WHERE barcodes LIKE '%' || :code || '%' LIMIT 1")
    suspend fun byBarcode(code: String): ProductEntity?
    @Query("SELECT * FROM products WHERE dirty = 1") suspend fun dirty(): List<ProductEntity>
    @Upsert suspend fun upsert(item: ProductEntity)
    @Upsert suspend fun upsertAll(items: List<ProductEntity>)
    @Query("DELETE FROM products WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE products SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
    @Query("SELECT COUNT(*) FROM products") fun count(): Flow<Int>
}

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouse_entries ORDER BY createdAt DESC") fun observeAll(): Flow<List<WarehouseEntryEntity>>
    @Query("SELECT * FROM warehouse_entries WHERE productId = :pid") suspend fun forProduct(pid: String): List<WarehouseEntryEntity>
    @Query("SELECT COALESCE(SUM(units), 0) FROM warehouse_entries WHERE productId = :pid")
    suspend fun inboundFor(pid: String): Double
    @Query("SELECT * FROM warehouse_entries WHERE id = :id") suspend fun byId(id: String): WarehouseEntryEntity?
    @Query("SELECT * FROM warehouse_entries WHERE dirty = 1") suspend fun dirty(): List<WarehouseEntryEntity>
    @Upsert suspend fun upsert(item: WarehouseEntryEntity)
    @Upsert suspend fun upsertAll(items: List<WarehouseEntryEntity>)
    @Query("DELETE FROM warehouse_entries WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE warehouse_entries SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY createdAt DESC") fun observeAll(): Flow<List<SaleEntity>>
    @Query("SELECT * FROM sales") suspend fun allOnce(): List<SaleEntity>
    @Query("SELECT * FROM sales WHERE id = :id") suspend fun byId(id: String): SaleEntity?
    @Query("SELECT * FROM sales WHERE date BETWEEN :from AND :to AND status != 'cancelled'")
    suspend fun inRange(from: String, to: String): List<SaleEntity>
    @Query("SELECT * FROM sales WHERE dirty = 1") suspend fun dirty(): List<SaleEntity>
    @Query("SELECT MAX(invoiceNumber) FROM sales") suspend fun maxInvoice(): Long?
    @Upsert suspend fun upsert(item: SaleEntity)
    @Upsert suspend fun upsertAll(items: List<SaleEntity>)
    @Query("DELETE FROM sales WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE sales SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface SaleItemDao {
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId") suspend fun forSale(saleId: String): List<SaleItemEntity>
    @Query("SELECT * FROM sale_items") suspend fun all(): List<SaleItemEntity>
    @Query("SELECT * FROM sale_items WHERE dirty = 1") suspend fun dirty(): List<SaleItemEntity>
    /**
     * تعداد فروخته‌شده‌ی خالص یک محصول: فروش‌های لغو‌نشده، منهای مرجوعی‌ها.
     * دقیقاً همان تعریفی که نسخه وب استفاده می‌کند.
     */
    @Query("""
        SELECT COALESCE(SUM(si.quantity - si.returnedQty), 0) FROM sale_items si
        LEFT JOIN sales s ON s.id = si.saleId
        WHERE si.productId = :pid AND (s.status IS NULL OR s.status != 'cancelled')
    """)
    suspend fun soldQtyFor(pid: String): Double
    @Upsert suspend fun upsert(item: SaleItemEntity)
    @Upsert suspend fun upsertAll(items: List<SaleItemEntity>)
    @Query("DELETE FROM sale_items WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE sale_items SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface ReturnDao {
    @Query("SELECT * FROM returns ORDER BY createdAt DESC") suspend fun all(): List<ReturnEntity>
    @Query("SELECT * FROM returns WHERE dirty = 1") suspend fun dirty(): List<ReturnEntity>
    @Upsert suspend fun upsert(item: ReturnEntity)
    @Upsert suspend fun upsertAll(items: List<ReturnEntity>)
    @Query("DELETE FROM returns WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE returns SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface DebtorDao {
    @Query("SELECT * FROM debtors ORDER BY name") fun observeAll(): Flow<List<DebtorEntity>>
    @Query("SELECT * FROM debtors WHERE id = :id") suspend fun byId(id: String): DebtorEntity?
    @Query("SELECT * FROM debtors WHERE dirty = 1") suspend fun dirty(): List<DebtorEntity>
    @Upsert suspend fun upsert(item: DebtorEntity)
    @Upsert suspend fun upsertAll(items: List<DebtorEntity>)
    @Query("DELETE FROM debtors WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE debtors SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE debtorId = :id ORDER BY createdAt DESC")
    suspend fun forDebtor(id: String): List<TransactionEntity>
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN type = 'give' THEN amount ELSE -amount END), 0)
        FROM transactions WHERE debtorId = :id
    """)
    suspend fun balanceOf(id: String): Double
    @Query("SELECT * FROM transactions WHERE dirty = 1") suspend fun dirty(): List<TransactionEntity>
    @Upsert suspend fun upsert(item: TransactionEntity)
    @Upsert suspend fun upsertAll(items: List<TransactionEntity>)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE transactions SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC") fun observeAll(): Flow<List<ExpenseEntity>>
    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE date BETWEEN :from AND :to")
    suspend fun totalInRange(from: String, to: String): Double
    @Query("SELECT * FROM expenses WHERE dirty = 1") suspend fun dirty(): List<ExpenseEntity>
    @Upsert suspend fun upsert(item: ExpenseEntity)
    @Upsert suspend fun upsertAll(items: List<ExpenseEntity>)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE expenses SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name") fun observeAll(): Flow<List<SupplierEntity>>
    @Query("SELECT * FROM suppliers WHERE dirty = 1") suspend fun dirty(): List<SupplierEntity>
    @Upsert suspend fun upsert(item: SupplierEntity)
    @Upsert suspend fun upsertAll(items: List<SupplierEntity>)
    @Query("DELETE FROM suppliers WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE suppliers SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY createdAt DESC") fun observeAll(): Flow<List<PurchaseEntity>>
    @Query("SELECT * FROM purchases WHERE dirty = 1") suspend fun dirty(): List<PurchaseEntity>
    @Query("SELECT COALESCE(SUM(totalAmount - paidAmount),0) FROM purchases WHERE supplierId = :id")
    suspend fun unpaidFor(id: String): Double
    @Upsert suspend fun upsert(item: PurchaseEntity)
    @Upsert suspend fun upsertAll(items: List<PurchaseEntity>)
    @Query("DELETE FROM purchases WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE purchases SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface SupplierPaymentDao {
    @Query("SELECT COALESCE(SUM(amount),0) FROM supplier_payments WHERE supplierId = :id")
    suspend fun paidTo(id: String): Double
    @Query("SELECT * FROM supplier_payments WHERE dirty = 1") suspend fun dirty(): List<SupplierPaymentEntity>
    @Upsert suspend fun upsert(item: SupplierPaymentEntity)
    @Upsert suspend fun upsertAll(items: List<SupplierPaymentEntity>)
    @Query("DELETE FROM supplier_payments WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE supplier_payments SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 200): List<StockMovementEntity>
    @Query("SELECT * FROM stock_movements WHERE dirty = 1") suspend fun dirty(): List<StockMovementEntity>
    @Upsert suspend fun upsert(item: StockMovementEntity)
    @Upsert suspend fun upsertAll(items: List<StockMovementEntity>)
    @Query("DELETE FROM stock_movements WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE stock_movements SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 200): List<AuditEntity>
    @Query("SELECT * FROM audit_log WHERE dirty = 1") suspend fun dirty(): List<AuditEntity>
    @Upsert suspend fun upsert(item: AuditEntity)
    @Upsert suspend fun upsertAll(items: List<AuditEntity>)
    @Query("DELETE FROM audit_log WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE audit_log SET dirty = 0 WHERE id IN (:ids)") suspend fun clearDirty(ids: List<String>)
}

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM tombstones WHERE dirty = 1") suspend fun dirty(): List<TombstoneEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(t: TombstoneEntity)
    @Query("UPDATE tombstones SET dirty = 0 WHERE collection = :c AND recordId = :id")
    suspend fun clearDirty(c: String, id: String)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM app_settings WHERE key = :key") suspend fun get(key: String): String?
    @Query("SELECT * FROM app_settings") suspend fun all(): List<SettingEntity>
    @Upsert suspend fun put(item: SettingEntity)
    @Upsert suspend fun putAll(items: List<SettingEntity>)
}
