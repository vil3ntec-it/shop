package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrQuickRegister
import ir.vil3ntec.tohid.data.QuickRegister
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.ui.components.pos.quickNext
import ir.vil3ntec.tohid.ui.components.pos.saveQuick
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  چسبِ `sale/quick-register?code=` — فقط با «ثبتِ سریع» روشن و نقشِ مجاز (`quickNext`)؛ پیوندِ دستی یا کهنه ⇒
 *  برگشت با پیام. ثبت همان `QuickRegister.register` روی دفترِ همین لحظه و `saveQuick` ِ صندوقِ کامپیوتر: کالا با
 *  موجودیِ در دست به همین سبد، با بوقِ «خواند».
 */
@Composable
fun RouteScope.QuickRegisterRoute() {
  val context = LocalContext.current
  val code = remember { args.query("code").orEmpty().trim() }
  val open = remember { code.isNotEmpty() && quickNext(context) == QuickRegister.Next.QUICK }
  if (!open) {
    LaunchedEffect(Unit) { toasts.show(StrQuickRegister.unavailable, V3Tone.WARNING); back() }
    return
  }
  QuickRegisterSheet(
    barcode = code,
    onSave = { draft ->
      when (val r = QuickRegister.register(store.data.value, code, draft, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)) {
        is QuickRegister.Result.Failed -> r.message
        is QuickRegister.Result.Ok -> {
          val saved = saveQuick(r, store, cart)
          if (saved == null) StrQuickRegister.notSaved
          else {
            if (saved.added) ScanFeedback.ok(context)
            toasts.show(saved.message, if (saved.added) V3Tone.SUCCESS else V3Tone.WARNING)
            null
          }
        }
      }
    },
    onClose = { back() },
  )
}
