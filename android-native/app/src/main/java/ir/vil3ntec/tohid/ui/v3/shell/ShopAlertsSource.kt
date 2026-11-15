package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty

/**
 *  ردیف‌هایی که فقط زنگِ قدیم داشت، از دفترِ همین دستگاه — همان شمارش‌های `rememberAlerts`
 *  ِ سربرگِ قدیم (`ShopStore.stockStatus`، `supplierDebt`، `debt`، `BackupClock`)، یک بار
 *  به ازای هر دفتر. قاعده‌ای عوض نشده؛ فقط هر ردیف شناسهٔ خودش را دارد تا مستقیم باز شود.
 */
fun shopAlertExtras(d: ShopData, backupStale: Boolean, backupText: String, today: String = ""): ShopAlerts.Extras {
  val stock = ShopStore.index(d)
  val owed = d.suppliers.map { it.id to ShopStore.supplierDebt(d, it.id) }.filter { it.second > 0 }
  val supplierTotal = owed.sumOf { it.second }
  return ShopAlerts.Extras(
    out = d.products.filter { stock.status(it) == "out" }.map { ShopAlerts.Product(it.id, it.name) },
    low = d.products.filter { stock.status(it) == "low" }.map { ShopAlerts.Low(it.id, it.name, qty(stock.stock(it.id))) },
    supplierDebt = if (supplierTotal > 0) money(supplierTotal) else null,
    onlySupplierId = owed.singleOrNull()?.first,
    backupStale = backupStale,
    backupText = backupText,
    topDebtors = d.debtors
      .map { it to ShopStore.debt(d, it.id) }
      .filter { it.second > 0 }
      .sortedByDescending { it.second }
      .take(3)
      .map { (debtor, amount) -> ShopAlerts.Owing(debtor.id, debtor.name, money(amount)) },
    //  e-rows-63b: همان ماندهٔ واقعیِ `PurchaseBook.open` (پرداخت‌های بعدی و سندهای کسر)، سررسیدِ پیش از امروز
    supplierDue = if (today.isBlank()) null else ir.vil3ntec.tohid.data.PurchaseBook.overdue(d, today).takeIf { it.isNotEmpty() }?.let { late ->
      ShopAlerts.SupplierDue(late.size, money(late.sumOf { it.remaining }), late.map { it.supplierId }.distinct().singleOrNull())
    },
  )
}

/** هشدارهای خانه + ردیف‌های زنگِ قدیم، گروه‌بندی‌شده (فوری / بقیه) */
@Composable
fun rememberShopAlerts(d: ShopData, home: List<ir.vil3ntec.tohid.ui.screens.HomeAlerts.Alert>): ShopAlerts.Groups {
  val context = LocalContext.current
  val stale = remember { BackupClock.isStale(context) }
  val backupText = remember { BackupClock.text(context) }
  val today = remember { ir.vil3ntec.tohid.todayIso() }
  val extras = remember(d, stale, today) { shopAlertExtras(d, stale, backupText, today) }
  return remember(home, extras) { ShopAlerts.build(home, extras) }
}
