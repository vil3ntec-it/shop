package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.common.BulkPreviewRow
import ir.vil3ntec.tohid.ui.components.common.BulkRowResult

/**
 *  منطقِ خالصِ فهرستِ کالا (`DeskProductsScreen`) — کارت‌های آماری، زبانه‌ها،
 *  جست‌وجو، مرتب‌سازی، و پیش‌نمایش/نتیجهٔ کارِ گروهی.
 *
 *  عددها فقط از دادهٔ واقعیِ دفتر می‌آیند. تعریفِ «ناموجود» و «کم‌موجود»
 *  همان `DashboardEngine.board` است، پس شمارِ کارت‌ها با نشانِ منوی کناری
 *  و زنگِ هشدار یکی است:
 *
 *   • ناموجود = فعال و موجودی ≤ ۰
 *   • کم‌موجود = فعال، موجودی > ۰، حدِ هشدار > ۰ و موجودی ≤ حدِ هشدار
 *
 *  «پیش‌نویس» و «بایگانی» در دادهٔ کالا نیستند، پس زبانه‌شان هم نیست
 *  (VISUAL-DIRECTION §۳ بند ۳: «بسته به وضعیت‌هایی که واقعاً داریم»).
 */
object ProductListModel {

  /** نشانِ وضعیتِ هر کالا — مهم‌ترین حالت برنده است */
  enum class Status { ACTIVE, LOW, OUT, INACTIVE }

  enum class Tab(val label: String) {
    ALL("همه"),
    ACTIVE("فعال"),
    LOW("کم‌موجود"),
    OUT("ناموجود"),
    INACTIVE("غیرفعال"),
  }

  enum class Sort { NAME, CODE, CATEGORY, BUY, SELL, STOCK, STATUS }

  fun status(p: Product, stock: Double): Status = when {
    !p.active -> Status.INACTIVE
    stock <= 0.0 -> Status.OUT
    p.minStock > 0.0 && stock <= p.minStock -> Status.LOW
    else -> Status.ACTIVE
  }

  fun inTab(p: Product, stock: Double, tab: Tab): Boolean = when (tab) {
    Tab.ALL -> true
    Tab.ACTIVE -> p.active
    Tab.LOW -> status(p, stock) == Status.LOW
    Tab.OUT -> status(p, stock) == Status.OUT
    Tab.INACTIVE -> !p.active
  }

  /** جست‌وجو در نام، دسته، زیردسته و بارکد — ارقامِ فارسی/عربی هم */
  fun matches(p: Product, query: String): Boolean {
    val q = ir.vil3ntec.tohid.normalizeDigits(query.trim())
    if (q.isEmpty()) return true
    return p.name.contains(q, ignoreCase = true) ||
      p.category.contains(q, ignoreCase = true) ||
      p.subcategory.contains(q, ignoreCase = true) ||
      p.barcodes.any { it.contains(q) }
  }

  /**
   *  کارت‌های بالای صفحه.
   *
   *  @param added کالاهایی که در `periodDays` روزِ گذشته ساخته شده‌اند
   *  (`createdAt`). کالای قدیمیِ بی‌تاریخ (`createdAt = 0`) شمرده نمی‌شود —
   *  عددِ ساختگی جای «نمی‌دانیم» نمی‌نشیند.
   */
  data class Kpis(
    val total: Int,
    val active: Int,
    val low: Int,
    val out: Int,
    val inactive: Int,
    val added: Int,
    val periodDays: Int,
  )

  const val DAY_MS = 86_400_000L

  fun kpis(products: List<Product>, stockOf: (String) -> Double, now: Long, periodDays: Int = 7): Kpis {
    var active = 0; var low = 0; var out = 0; var added = 0
    val from = now - periodDays * DAY_MS
    for (p in products) {
      when (status(p, stockOf(p.id))) {
        Status.LOW -> low++
        Status.OUT -> out++
        else -> {}
      }
      if (p.active) active++
      if (p.createdAt in (from + 1)..now) added++
    }
    return Kpis(products.size, active, low, out, products.size - active, added, periodDays)
  }

  fun counts(products: List<Product>, stockOf: (String) -> Double): Map<Tab, Int> {
    val k = kpis(products, stockOf, now = 0L)
    return mapOf(Tab.ALL to k.total, Tab.ACTIVE to k.active, Tab.LOW to k.low, Tab.OUT to k.out, Tab.INACTIVE to k.inactive)
  }

  /**
   *  ردیف‌های دیده‌شده: زبانه ∩ دسته ∩ جست‌وجو، مرتب‌شده.
   *
   *  @param category `null` یعنی همهٔ دسته‌ها؛ رشتهٔ خالی یعنی «بی‌دسته»
   */
  fun rows(
    products: List<Product>,
    stockOf: (String) -> Double,
    query: String,
    tab: Tab,
    category: String?,
    sort: Sort,
    asc: Boolean,
  ): List<Product> {
    val list = products.filter { p ->
      inTab(p, stockOf(p.id), tab) && (category == null || p.category == category) && matches(p, query)
    }
    val sorted = when (sort) {
      Sort.NAME -> list.sortedBy { it.name }
      Sort.CODE -> list.sortedBy { it.barcodes.firstOrNull().orEmpty() }
      Sort.CATEGORY -> list.sortedBy { it.category }
      Sort.BUY -> list.sortedBy { it.purchasePrice }
      Sort.SELL -> list.sortedBy { it.salePrice }
      Sort.STOCK -> list.sortedBy { stockOf(it.id) }
      Sort.STATUS -> list.sortedBy { status(it, stockOf(it.id)).ordinal }
    }
    return if (asc) sorted else sorted.reversed()
  }

  /** دسته‌هایی که واقعاً کالا دارند — برای فیلتر */
  fun categories(products: List<Product>): List<String> =
    products.map { it.category }.distinct().sortedWith(compareBy({ it.isEmpty() }, { it }))

  /* ───────────────────────── کارِ گروهی ───────────────────────── */

  /** فعال/غیرفعال کردن: کدام ردیف عوض می‌شود و کدام از قبل همان است */
  fun previewActive(products: List<Product>, selected: Set<String>, active: Boolean): List<BulkPreviewRow> =
    products.filter { it.id in selected }.map { p ->
      if (p.active == active) BulkPreviewRow(p.id, p.name, changes = false, note = if (active) "از قبل فعال است" else "از قبل غیرفعال است")
      else BulkPreviewRow(p.id, p.name, changes = true, note = if (active) "فعال می‌شود" else "غیرفعال می‌شود — در فروش دیده نمی‌شود")
    }

  /**
   *  نتیجهٔ هر ردیف پس از فعال/غیرفعال.
   *  @param error پیامِ شکستِ کلِ کار (`WarehouseEngine.Result.Failed`) — آن‌وقت هیچ ردیفی عوض نشده
   */
  fun resultActive(preview: List<BulkPreviewRow>, active: Boolean, error: String?): List<BulkRowResult> =
    preview.map { r ->
      when {
        error != null -> BulkRowResult(r.id, r.label, ok = false, note = error)
        r.changes -> BulkRowResult(r.id, r.label, ok = true, note = if (active) "فعال شد" else "غیرفعال شد")
        else -> BulkRowResult(r.id, r.label, ok = true, note = "بی‌تغییر — ${r.note}")
      }
    }

  /** حذف: هشدارِ کالایی که فروش داشته (فاکتورهای قبلی می‌مانند) */
  fun previewDelete(products: List<Product>, selected: Set<String>, soldOf: (String) -> Double): List<BulkPreviewRow> =
    products.filter { it.id in selected }.map { p ->
      val sold = soldOf(p.id)
      BulkPreviewRow(
        p.id, p.name, changes = true,
        note = if (sold > 0) "فروش داشته — فاکتورهای قبلی می‌مانند و نامش «حذف‌شده» نشان داده می‌شود"
        else "کالا و سوابقِ ورودِ انبارش حذف می‌شود",
      )
    }

  fun resultDelete(preview: List<BulkPreviewRow>, error: String?): List<BulkRowResult> =
    preview.map { r ->
      if (error != null) BulkRowResult(r.id, r.label, ok = false, note = error)
      else BulkRowResult(r.id, r.label, ok = true, note = "حذف شد")
    }

  /**
   *  دلیلِ حذف در سابقهٔ عملیات — کنارِ همان ردیفِ `delete_product` که
   *  `WarehouseEngine.deleteProduct` با همین `now` نوشته است. قالبِ داده
   *  عوض نمی‌شود؛ فقط متنِ `notes` دلیل را هم می‌گیرد.
   */
  fun withDeleteReason(d: ShopData, ids: Set<String>, now: Long, reason: String): ShopData {
    val why = reason.trim()
    if (why.isEmpty()) return d
    return d.copy(
      auditLog = d.auditLog.map { a ->
        if (a.type == "delete_product" && a.createdAt == now && a.refId in ids) a.copy(notes = "${a.notes} — دلیل: $why") else a
      },
    )
  }
}
