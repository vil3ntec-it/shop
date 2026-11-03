package ir.vil3ntec.tohid.ui.v3.sales.drawer

import android.content.Context
import ir.vil3ntec.tohid.core.i18n.StrDrawers
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.MultiDrawerPref
import ir.vil3ntec.tohid.data.TillDrawer
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 *  PAY-17 — مرزِ پلتفرمِ «چند کشو و شیفتِ شناور» در صندوقِ نسخهٔ ۳: ساعتِ دستگاه، تنظیمِ همین دستگاه (`MultiDrawerPref`)
 *  و نوشتن روی دفترِ همان لحظه (`store.update`/`store.appendAudit`، هرگز تصویرِ کهنهٔ صفحه). قاعده‌ها `core/pay/Drawers`.
 */

/** «ادامه روی این دستگاه»: اول ردیفِ سابقهٔ `shift_resume`، و فقط پس از نشستنش همین دستگاه روی [sh] می‌رود */
internal suspend fun RouteScope.resumeHere(context: Context, sh: CashShift.Shift, by: String) {
  val row = TillDrawer.resumeRow(sh, by, todayIso(), System.currentTimeMillis(), Ulid.next())
  if (store.appendAudit { row }) {
    MultiDrawerPref.pick(context, sh.open.id)
    toasts.show(StrDrawers.nowOn(sh.open.drawer.orEmpty()), V3Tone.SUCCESS)
  } else toasts.show(StrSale.notSaved, V3Tone.ERROR)
}

/** بازکردنِ کشوی نام‌دارِ [name] با موجودیِ [v] و رفتنِ همین دستگاه رویش (`CashShift.openNamed`) */
internal fun RouteScope.openNamedHere(context: Context, scope: CoroutineScope, v: Double, name: String, by: String) {
  val today = todayIso()
  val now = System.currentTimeMillis()
  val id = Ulid.next()
  when (val r = CashShift.openNamed(data, v, name, by, today, now) { id }) {
    is CashShift.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
    is CashShift.Result.Ok -> {
      scope.launch {
        //  همان `openNamed` روی دفترِ همان لحظه، زیرِ قفلِ نوشتن
        val wrote = store.update { d -> (CashShift.openNamed(d, v, name, by, today, now) { id } as? CashShift.Result.Ok)?.data }
        if (wrote == true) {
          MultiDrawerPref.pick(context, id)
          toasts.show(StrSale.drawerOpened, V3Tone.SUCCESS)
        } else toasts.show(StrSale.notSaved, V3Tone.ERROR)
      }
      back()
    }
  }
}
