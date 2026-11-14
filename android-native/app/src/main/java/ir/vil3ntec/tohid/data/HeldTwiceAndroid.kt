package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.audit.InvoiceVoid
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.sync.platform.DocChainSync
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.SalesVoid

/**
 *  S09 — مرزِ سکوی لغوِ خودکارِ فروشِ دومِ «یک فاکتور در دو دستگاه» (منطق و شرح: `HeldTwice`). پس از هر دورِ
 *  پشتیبانِ نگه‌داشته‌ها (`HeldSync.kick`) و فقط با پرچمِ روشنِ `shop.held_twice_cancel` (`HeldSyncEngine.autoCancel`).
 *
 *  لغو **همان راهِ عادیِ برنامه** است ([cancelOf]): `SalesVoid.run` (`InvoiceVoid.check` ⇐ `SalesEngine.cancel`: وضعیتِ
 *  «لغوشده»، برگشتِ انبار، رسیدِ قرض) ⇐ `ShopStore.save` (op ِ عادیِ همگام‌سازی، با همهٔ نگهبان‌های سرور) ⇐ سندِ معکوسِ زنجیرهٔ
 *  اسناد (`DocChainSync.recordVoid`) ⇐ آزاد شدنِ کدهای تخفیفِ همان فاکتور (`PromoLive.releaseOnVoid`). ⛔ حالتِ آموزشی هرگز.
 */
object HeldTwiceAndroid {

  suspend fun sweep(context: Context, engine: HeldSyncEngine) {
    if (TrainingMode.on || !engine.autoCancel || engine.twiceOpen().isEmpty()) return
    val app = context.applicationContext ?: context
    val store = ShopStore(app)
    store.load()
    val device = runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(app).deviceId() }.getOrDefault("")
    HeldTwice.sweep(
      engine, ledger = { store.data.value }, canCancel = ShopRole.canCancelSale(app), now = System.currentTimeMillis(),
      device = device, cancel = { sale -> cancelOf(app, store, sale) },
    )
  }

  /** همان لغوِ صفحهٔ «لغوِ فاکتور» (`HistoryRoutes.CancelRoute`) با دلیلِ ثابتِ `HeldTwice.REASON`؛ نشست ⇒ دفترِ تازه */
  private suspend fun cancelOf(app: Context, store: ShopStore, sale: Sale): ShopData? {
    val before = store.data.value
    val r = SalesVoid.run(
      before, sale.id, canCancel = true, kind = InvoiceVoid.Kind.VOID, reason = HeldTwice.REASON, today = todayIso(),
      now = System.currentTimeMillis(), newId = Ulid::next, by = FraudTrailPref.by(app), noPermission = StrSale.cancelDenied,
    )
    if (r !is SalesVoid.Outcome.Ok || !store.save(before, r.data)) return null
    DocChainSync.recordVoid(app, before, sale.id, InvoiceVoid.Kind.VOID, r.reason)
    ir.vil3ntec.tohid.ui.PromoLive.releaseOnVoid(app, store, sale)
    return store.data.value
  }
}
