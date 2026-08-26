package af.tohid.shop.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * جدول‌های دفتر دکان.
 *
 * هر ردیف دو فیلد همگام‌سازی دارد:
 *   updatedAt — زمان آخرین تغییر (برای «آخرین ویرایش برنده است»)
 *   dirty     — یعنی این تغییر هنوز به سرور نرفته است
 * ownerUserId نشان می‌دهد کدام عضو دکان این رکورد را ساخته — برای پیام کسری موجودی.
 */

@Serializable
@Entity(tableName = "products", indices = [Index("name"), Index("category")])
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "",
    val unit: String = "",
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val minStock: Double = 0.0,
    val barcodes: String = "",          // با کاما جدا شده
    val notes: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "warehouse_entries", indices = [Index("productId"), Index("date")])
data class WarehouseEntryEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val cartons: Double = 0.0,
    val perCarton: Double = 0.0,
    val units: Double = 0.0,            // می‌تواند منفی باشد (اصلاح موجودی)
    val unit: String = "",
    val price: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    val isAdjustment: Boolean = false,
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "sales", indices = [Index("date"), Index("debtorId"), Index("invoiceNumber")])
data class SaleEntity(
    @PrimaryKey val id: String,
    val invoiceNumber: Long = 0,
    val total: Double = 0.0,
    val discountType: String = "amount",
    val discountValue: Double = 0.0,
    val discount: Double = 0.0,
    val finalTotal: Double = 0.0,
    val paymentMethod: String = "cash",   // cash | credit
    val debtorId: String? = null,
    val paidAmount: Double = 0.0,
    val remaining: Double = 0.0,
    val status: String = "completed",     // completed | cancelled
    val debtGiven: Double = 0.0,
    val debtSettled: Double = 0.0,
    val date: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "sale_items", indices = [Index("saleId"), Index("productId")])
data class SaleItemEntity(
    @PrimaryKey val id: String,
    val saleId: String,
    val productId: String,
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val purchasePrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val returnedQty: Double = 0.0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "returns", indices = [Index("saleId"), Index("productId")])
data class ReturnEntity(
    @PrimaryKey val id: String,
    val saleId: String,
    val saleItemId: String,
    val productId: String,
    val quantity: Double = 0.0,
    val amount: Double = 0.0,
    val reason: String = "",
    val date: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "debtors", indices = [Index("name")])
data class DebtorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "transactions", indices = [Index("debtorId"), Index("date")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val debtorId: String,
    val type: String,                    // give | receive
    val amount: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "expenses", indices = [Index("date"), Index("category")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "suppliers", indices = [Index("name")])
data class SupplierEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "purchases", indices = [Index("supplierId"), Index("date")])
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val supplierId: String,
    val productId: String = "",
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val debt: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "supplier_payments", indices = [Index("supplierId"), Index("date")])
data class SupplierPaymentEntity(
    @PrimaryKey val id: String,
    val supplierId: String,
    val amount: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "stock_movements", indices = [Index("productId"), Index("date")])
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val type: String,                    // purchase_in | sale | customer_return | supplier_return | adjustment
    val qty: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    val refId: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

@Serializable
@Entity(tableName = "audit_log", indices = [Index("createdAt")])
data class AuditEntity(
    @PrimaryKey val id: String,
    val type: String,
    val refId: String = "",
    val notes: String = "",
    val date: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val dirty: Boolean = true,
    override val ownerUserId: String = "",
) : Syncable

/** سنگ قبر: رکوردی که پاک شده و باید حذفش به بقیه دستگاه‌ها هم برسد. */
@Serializable
@Entity(tableName = "tombstones", primaryKeys = ["collection", "recordId"])
data class TombstoneEntity(
    val collection: String,
    val recordId: String,
    val updatedAt: Long,
    val dirty: Boolean = true,
)

/** فهرست‌های ساده (دسته‌بندی‌ها، واحدها) و شمارنده فاکتور. */
@Serializable
@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = 0,
)

interface Syncable {
    val updatedAt: Long
    val dirty: Boolean
    val ownerUserId: String
}
