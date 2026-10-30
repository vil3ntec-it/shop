package ir.vil3ntec.tohid.ui.v3.sales.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import kotlinx.coroutines.launch

/*
 *  چسبِ مسیرهای صندوق — همان کارهای کارتِ صندوقِ تاریخچهٔ پیشین (`CashShift`) و صندوقِ شیفت
 *  (`ShiftPanelV2`، `PayFlow`) با همان ذخیره و همان پیام‌ها. هر برگه مسیرِ خودش است.
 */

private fun RouteScope.me(context: android.content.Context): String =
  runCatching { SyncStore(context).accountName }.getOrDefault("")

/** `sales/drawer` */
@Composable
fun RouteScope.DrawerRoute() {
  val context = LocalContext.current
  val staff = remember { ShopRole.isStaff(context) }
  //  PAY (v3 A4): صندوقِ شیفت با هستهٔ pay-core — فقط با پرچم؛ خاموش ⇒ صندوقِ ساده
  val payV2 = remember { ShopPolicy.payV2(context) }
  val shift = remember(data) { CashShift.current(data) }
  val now = System.currentTimeMillis()
  val totals = if (!payV2) shift?.let { CashShift.totalsOf(data, it, now) } else null
  val x = if (payV2) remember(data, shift) { shift?.let { PayFlow.report(data, it.open, null, now) } } else null
  DrawerScreen(
    DrawerView(
      shift = shift,
      totals = totals,
      payV2 = payV2,
      blind = payV2 && staff,
      expectedMinor = x?.valueOrNull()?.report?.expected?.get(PayFlow.CASH),
      reportError = (x as? PayFlow.Outcome.Bad)?.let { PayFlow.message(it.problem) },
      skipped = x?.valueOrNull()?.skipped?.size ?: 0,
      history = remember(data) { CashShift.list(data).take(60) },
    ),
    DrawerActions(
      open = { nav.open(SalesPaths.DRAWER_OPEN) },
      close = { nav.open(SalesPaths.DRAWER_CLOSE) },
      move = { nav.open(SalesPaths.drawerMove(noSale = false)) },
      noSale = { nav.open(SalesPaths.drawerMove(noSale = true)) },
      xReport = {
        if (x?.valueOrNull() != null) nav.open(SalesPaths.DRAWER_X) else toasts.show(StrSale.reportFailed, V3Tone.ERROR)
      },
      shift = { nav.open(SalesPaths.drawerShift(it)) },
      back = backOrNull,
    ),
  )
}

/** `sales/drawer/open` — همان `CashShift.open` */
@Composable
fun RouteScope.DrawerOpenRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  if (CashShift.current(data) != null) { LaunchedEffect(Unit) { back() }; return }
  DrawerOpenSheet(
    onSubmit = { v ->
      when (val r = CashShift.open(data, v, me(context), todayIso(), System.currentTimeMillis(), Ulid::next)) {
        is CashShift.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
        is CashShift.Result.Ok -> {
          scope.launch { store.save(r.data) }
          toasts.show(StrSale.drawerOpened, V3Tone.SUCCESS)
          back()
        }
      }
    },
    onClose = { back() },
  )
}

/** `sales/drawer/close` — صندوقِ ساده (`CashShift.close`) یا شیفت (`PayFlow.closeRow` ⇒ گزارش Z) */
@Composable
fun RouteScope.DrawerCloseRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val shift = remember(data) { CashShift.current(data) }
  if (shift == null) { LaunchedEffect(Unit) { back() }; return }
  val payV2 = remember { ShopPolicy.payV2(context) }
  val staff = remember { ShopRole.isStaff(context) }
  val me = remember { me(context) }
  if (!payV2) {
    DrawerCloseSheet(
      totals = CashShift.totalsOf(data, shift, System.currentTimeMillis()),
      onSubmit = { v, note ->
        when (val r = CashShift.close(data, v, note, me, todayIso(), System.currentTimeMillis(), Ulid::next)) {
          is CashShift.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
          is CashShift.Result.Ok -> {
            scope.launch { store.save(r.data) }
            toasts.show(StrSale.drawerClosedDone, V3Tone.SUCCESS)
            back()
          }
        }
      },
      onClose = { back() },
    )
    return
  }
  var error by remember { mutableStateOf<String?>(null) }
  val x = remember(data, shift) { PayFlow.report(data, shift.open, null, System.currentTimeMillis()) }
  DrawerCountSheet(
    blind = staff,
    expectedMinor = x.valueOrNull()?.report?.expected?.get(PayFlow.CASH),
    error = error,
    onSubmit = { counts, reason ->
      when (val r = PayFlow.closeRow(data, shift.open, PayFlow.Count(counts, by = me, blind = staff, reason = reason), System.currentTimeMillis(), Ulid::next)) {
        is PayFlow.Outcome.Bad -> error = if (r.problem.code == "REASON_REQUIRED") StrSale.reasonOver50 else PayFlow.message(r.problem)
        is PayFlow.Outcome.Ok -> {
          val z = r.value.report
          val notes = DrawerNotes.close(z.expected?.get(PayFlow.CASH) ?: 0, r.value.row.amount, z.variance?.get(PayFlow.CASH) ?: 0L, reason, me)
          val row = r.value.row.copy(date = todayIso(), notes = notes)
          //  زیرِ قفلِ نوشتن روی دفترِ همان لحظه — فروشی که هم‌زمان ثبت شد گم نمی‌شود
          scope.launch {
            if (store.appendAudit { row }) toasts.show(StrSale.drawerClosedDone, V3Tone.SUCCESS)
            else toasts.show(StrSale.notSaved, V3Tone.ERROR)
          }
          //  همان لحظه گزارشِ Z ِ همین بستن (پیش از رسیدنِ دفترِ تازه)
          DrawerSession.z = z
          nav.replace(SalesPaths.DRAWER_Z)
        }
      }
    },
    onClose = { back() },
  )
}

/** `sales/drawer/move?mode=no_sale|cash` — `PayFlow.moveRow` با همان یادداشتِ سابقه */
@Composable
fun RouteScope.DrawerMoveRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val shift = remember(data) { CashShift.current(data) }
  if (shift == null) { LaunchedEffect(Unit) { back() }; return }
  val staff = remember { ShopRole.isStaff(context) }
  val me = remember { me(context) }
  val noSale = args.query("mode") == SalesPaths.MOVE_NO_SALE
  var error by remember { mutableStateOf<String?>(null) }
  DrawerMoveSheet(
    noSale = noSale,
    staff = staff,
    me = me,
    error = error,
    onSubmit = { m ->
      when (val r = PayFlow.moveRow(data, shift.open, m, System.currentTimeMillis(), Ulid::next)) {
        is PayFlow.Outcome.Bad -> error = PayFlow.message(r.problem)
        is PayFlow.Outcome.Ok -> {
          val row: AuditEntry = r.value
          val notes = if (noSale) DrawerNotes.noSale(m.reason.orEmpty(), row.approvedBy)
          else DrawerNotes.move(m.type, row.reason, row.amount, row.note, me)
          val stamped = row.copy(date = todayIso(), notes = notes)
          //  زیرِ قفلِ نوشتن روی دفترِ همان لحظه — فروشی که هم‌زمان ثبت شد گم نمی‌شود
          scope.launch {
            if (store.appendAudit { stamped }) {
              toasts.show(if (noSale) StrSale.noSaleRecorded else StrSale.recorded, V3Tone.SUCCESS)
            } else toasts.show(StrSale.notSaved, V3Tone.ERROR)
          }
          back()
        }
      }
    },
    onClose = { back() },
  )
}

/** گزارشِ Z ِ همین بستن، تا دفترِ تازه برسد */
object DrawerSession {
  var z by mutableStateOf<ir.vil3ntec.tohid.core.pay.ShiftReport?>(null)
}

/** `sales/drawer/report` — گزارشِ X ِ صندوقِ باز؛ `?kind=z` ⇒ Z ِ همین بستن */
@Composable
fun RouteScope.DrawerXRoute() {
  val context = LocalContext.current
  val staff = remember { ShopRole.isStaff(context) }
  val shift = remember(data) { CashShift.current(data) }
  val rep = if (args.query("kind") == "z") DrawerSession.z
  else remember(data, shift) { shift?.let { PayFlow.report(data, it.open, null, System.currentTimeMillis()).valueOrNull()?.report } }
  if (rep == null) { LaunchedEffect(Unit) { back() }; return }
  ShiftReportSheet(rep, staff, title = null, notes = emptyList(), onClose = { back() })
}

/** `sales/drawer/shift/{id}` — ریزِ یک صندوق: گزارشِ Z اگر ذخیره شده، وگرنه یادداشت‌ها */
@Composable
fun RouteScope.DrawerShiftRoute() {
  val context = LocalContext.current
  val staff = remember { ShopRole.isStaff(context) }
  val id = args["id"].orEmpty()
  val shift = remember(data, id) { CashShift.list(data).firstOrNull { it.open.id == id } }
  if (shift == null) { LaunchedEffect(Unit) { back() }; return }
  val z = shift.close?.z?.let { PayFlow.reportOf(it) }
  if (z != null) ShiftReportSheet(z, staff, title = null, notes = listOfNotNull(shift.open.notes.ifBlank { null }, shift.close?.notes?.ifBlank { null }), onClose = { back() })
  else ShiftNotesSheet(shift, onClose = { back() })
}
