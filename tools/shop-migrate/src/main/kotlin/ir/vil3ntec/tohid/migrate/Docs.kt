package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.db.AuditLog
import ir.vil3ntec.tohid.db.SaleLine
import ir.vil3ntec.tohid.db.SalePayment
import ir.vil3ntec.tohid.db.Sale as DbSale

/**
 *  «سند»ی که هش می‌شود (۱۰.۳) — یک تعریف، هم برای ساختن و هم برای بازسنجی.
 *
 *  بیرون از سند: وضعیت و لغو (رویدادِ جداست)، جایگاه در زنجیره (همان
 *  prevHash است)، نامِ فروشنده و یادداشت‌ها (ممکن است دادهٔ شخصی باشند).
 */
object Docs {

  fun sale(s: DbSale, lines: List<SaleLine>, payments: List<SalePayment>): Map<String, Any?> = mapOf(
    "id" to s.id,
    "shopId" to s.shopId,
    "deviceId" to s.deviceId,
    "number" to s.number,
    "displayNo" to s.displayNo,
    "currency" to s.currency,
    "subtotalMinor" to s.subtotalMinor,
    "discountMinor" to s.discountMinor,
    "totalMinor" to s.totalMinor,
    "customerId" to s.customerId,
    "businessDate" to s.businessDate,
    "createdAt" to s.createdAt,
    "lines" to lines.sortedBy { it.lineNo }.map {
      mapOf(
        "id" to it.id, "lineNo" to it.lineNo, "productId" to it.productId, "name" to it.name,
        "qtyMilli" to it.qtyMilli, "unitPriceMinor" to it.unitPriceMinor,
        "lineTotalMinor" to it.lineTotalMinor, "unitCostMinor" to it.unitCostMinor,
      )
    },
    "payments" to payments.sortedBy { it.id }.map {
      mapOf("id" to it.id, "method" to it.method, "amountMinor" to it.amountMinor, "currency" to it.currency, "paidAt" to it.paidAt)
    },
  )

  fun audit(a: AuditLog): Map<String, Any?> = mapOf(
    "id" to a.id,
    "shopId" to a.shopId,
    "deviceId" to a.deviceId,
    "seq" to a.seq,
    "type" to a.type,
    "refId" to a.refId,
    "amountMinor" to a.amountMinor,
    "businessDate" to a.businessDate,
    "createdAt" to a.createdAt,
  )
}
