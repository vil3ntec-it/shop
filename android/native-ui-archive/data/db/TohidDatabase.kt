package af.tohid.shop.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class, WarehouseEntryEntity::class,
        SaleEntity::class, SaleItemEntity::class, ReturnEntity::class,
        DebtorEntity::class, TransactionEntity::class,
        ExpenseEntity::class,
        SupplierEntity::class, PurchaseEntity::class, SupplierPaymentEntity::class,
        StockMovementEntity::class, AuditEntity::class,
        TombstoneEntity::class, SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TohidDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun warehouse(): WarehouseDao
    abstract fun sales(): SaleDao
    abstract fun saleItems(): SaleItemDao
    abstract fun returns(): ReturnDao
    abstract fun debtors(): DebtorDao
    abstract fun transactions(): TransactionDao
    abstract fun expenses(): ExpenseDao
    abstract fun suppliers(): SupplierDao
    abstract fun purchases(): PurchaseDao
    abstract fun supplierPayments(): SupplierPaymentDao
    abstract fun stockMovements(): StockMovementDao
    abstract fun audit(): AuditDao
    abstract fun tombstones(): TombstoneDao
    abstract fun settings(): SettingDao

    companion object {
        @Volatile private var instance: TohidDatabase? = null

        fun get(context: Context): TohidDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, TohidDatabase::class.java, "tohid-shop.db"
            )
                // داده‌ی دکان با ارزش است: هرگز در به‌روزرسانی پاک نمی‌شود.
                // مهاجرت‌ها باید صریح نوشته شوند.
                .build()
                .also { instance = it }
        }
    }
}
