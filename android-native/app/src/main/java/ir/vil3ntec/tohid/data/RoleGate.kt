package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.perm.PermCore

/**
 *  سدِ نقش پیش از نوشتنِ دفتر — STF-01، بستهٔ A16.
 *
 *  صندوق‌دار خرید نمی‌نویسد، انباردار فروش نمی‌زند و حسابدار هیچ نمی‌نویسد؛
 *  سرور opِ چنین کاری را در هر سه راهِ همگام‌سازی رد می‌کند (`canWrite`ِ
 *  هستهٔ مشترک). اگر برنامه آن را روی دستگاه می‌نشاند، دفترِ این دستگاه با
 *  دفترِ دکان برای همیشه فرق می‌کرد. پس `ShopStore.save` همین‌جا می‌پرسد و
 *  نوشتنِ بیرون از نقش را پیش از دیسک و پیش از ساختنِ op نگه می‌دارد.
 *
 *  ⛔ صاحب، مدیر و شاگرد «*»اند: برایشان حتی تفاضل حساب نمی‌شود — رفتار و
 *  هزینه همان امروز. نقشی که این نسخه نمی‌شناسد هم بسته نمی‌شود (سرور می‌سنجد).
 */
object RoleGate {

  /** بخش‌هایی از دفتر که میانِ دو حالت عوض شده‌اند، با نامِ جدولِ همگام‌سازی */
  fun changedTables(cur: ShopData, next: ShopData): List<String> = buildList {
    if (cur.debtors != next.debtors) add("debtors")
    if (cur.transactions != next.transactions) add("transactions")
    if (cur.expenses != next.expenses) add("expenses")
    if (cur.expenseCategories != next.expenseCategories) add("expenseCategories")
    if (cur.products != next.products) add("products")
    if (cur.productCategories != next.productCategories) add("productCategories")
    if (cur.productUnits != next.productUnits) add("productUnits")
    if (cur.warehouseEntries != next.warehouseEntries) add("warehouseEntries")
    if (cur.sales != next.sales) add("sales")
    if (cur.saleItems != next.saleItems) add("saleItems")
    if (cur.saleReturns != next.saleReturns) add("returns")
    if (cur.suppliers != next.suppliers) add("suppliers")
    if (cur.purchases != next.purchases) add("purchases")
    if (cur.supplierPayments != next.supplierPayments) add("supplierPayments")
    if (cur.stockMovements != next.stockMovements) add("stockMovements")
    if (cur.priceHistory != next.priceHistory) add("priceHistory")
    if (cur.auditLog != next.auditLog) add("auditLog")
  }

  /** نخستین جدولی که این نقش نمی‌نویسد و در این نوشتن عوض شده — یا null */
  fun blocked(role: String, cur: ShopData, next: ShopData): String? {
    if (role !in PermCore.ROLES || PermCore.writable(role) == PermCore.ALL_TABLES) return null
    return changedTables(cur, next).firstOrNull { !PermCore.canWrite(role, it) }
  }

  /** نامِ فارسیِ بخشِ دفتر برای پیام */
  fun tableTitle(table: String): String = when (table) {
    "debtors" -> "قرض‌داران"
    "transactions" -> "قرض و پرداخت"
    "expenses", "expenseCategories" -> "مصارف"
    "products", "productCategories", "productUnits" -> "کالاها"
    "priceHistory" -> "قیمتِ کالا"
    "warehouseEntries" -> "رسیدِ انبار"
    "sales", "saleItems" -> "فروش"
    "returns" -> "برگشتِ کالا"
    "suppliers" -> "تأمین‌کننده‌ها"
    "purchases" -> "خرید"
    "supplierPayments" -> "پرداخت به تأمین‌کننده"
    "stockMovements" -> "حرکتِ انبار"
    "auditLog" -> "سابقهٔ عملیات"
    else -> table
  }
}
