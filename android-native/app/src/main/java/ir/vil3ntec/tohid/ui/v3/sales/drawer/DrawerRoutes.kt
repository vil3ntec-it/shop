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
import ir.vil3ntec.tohid.data.MultiDrawerPref
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.TillDrawer
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
  //  PAY-13: نمای کور = کارمند + شمارشِ کورِ روشنِ دکان (`ShopPolicy.blindCount`، پیش‌فرض روشن)
  val blind = remember { ShopPolicy.blindHere(context) }
  //  PAY (v3 A4): صندوقِ شیفت با هستهٔ pay-core — فقط با پرچم؛ خاموش ⇒ صندوقِ ساده
  val payV2 = remember { ShopPolicy.payV2(context) }
  val multi = remember { MultiDrawerPref.on(context) }
  val scope = rememberCoroutineScope()
  val shift = remember(data, TillDrawer.picked) { CashShift.current(data) }
  val now = System.currentTimeMillis()
  val totals = if (!payV2) shift?.let { CashShift.totalsOf(data, it, now) } else null
  val x = if (payV2) remember(data, shift) { shift?.let { PayFlow.report(data, it.open, null, now) } } else null
  DrawerScreen(
    DrawerView(
      shift = shift,
      totals = totals,
      payV2 = payV2,
      blind = payV2 && blind,
      expectedMinor = x?.valueOrNull()?.report?.let { DrawerModel.forViewer(it, seesExpected = !blind) }?.expected?.get(PayFlow.CASH),
      reportError = (x as? PayFlow.Outcome.Bad)?.let { PayFlow.message(it.problem) },
      skipped = x?.valueOrNull()?.skipped?.size ?: 0,
      history = remember(data) { CashShift.list(data).take(60) },
      scanner = shift?.let { DrawerModel.scannerLine(data, it.open.createdAt, now, seesMoney = !staff) },
      //  PAY-17: کشوهای باز فقط با «چند کشو» ِ روشنِ همین دستگاه
      drawers = if (multi) remember(data) { TillDrawer.open(data) } else null,
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
      pick = { sh -> if (sh == null) MultiDrawerPref.pick(context, null) else scope.launch { resumeHere(context, sh, me(context)) } },
      //  PAY-17: «همهٔ کشوها با هم» — نه برای نمای کور (موردِ انتظار و اختلاف را لو می‌دهد)
      all = if (multi && !blind) ({ nav.open(SalesPaths.DRAWER_ALL) }) else null,
    ),
  )
}

/** `sales/drawer/open` — همان `CashShift.open` */
@Composable
fun RouteScope.DrawerOpenRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  //  PAY-17: با «چند کشو» کشوی نام‌دار هم باز می‌شود (هر کدام صندوقِ خودش)؛ بی آن همان یک صندوق
  val multi = remember { MultiDrawerPref.on(context) }
  if (!multi && CashShift.current(data) != null) { LaunchedEffect(Unit) { back() }; return }
  DrawerOpenSheet(
    onSubmitNamed = if (!multi) null else { v, name -> openNamedHere(context, scope, v, name, me(context)) },
    onSubmit = { v ->
      val by = me(context)
      val today = todayIso()
      val now = System.currentTimeMillis()
      val id = Ulid.next()
      when (val r = CashShift.open(data, v, by, today, now) { id }) {
        is CashShift.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
        is CashShift.Result.Ok -> {
          //  همان CashShift.open روی دفترِ همان لحظه، زیرِ قفلِ نوشتن — نه تصویرِ کهنهٔ صفحه
          scope.launch {
            val wrote = store.update { d -> (CashShift.open(d, v, by, today, now) { id } as? CashShift.Result.Ok)?.data }
            if (wrote == true) {
              //  PAY-17: کشوی اصلیِ تازه ⇒ همین دستگاه رویش (کشوی نام‌دارِ دیگر باز می‌ماند)
              if (multi) MultiDrawerPref.pick(context, null)
              toasts.show(StrSale.drawerOpened, V3Tone.SUCCESS)
            } else toasts.show(StrSale.notSaved, V3Tone.ERROR)
          }
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
  //  PAY-13: بستنِ کور = کارمند + شمارشِ کورِ روشنِ دکان
  val blind = remember { ShopPolicy.blindHere(context) }
  val me = remember { me(context) }
  if (!payV2) {
    DrawerCloseSheet(
      totals = CashShift.totalsOf(data, shift, System.currentTimeMillis()),
      onSubmit = { v, note ->
        val today = todayIso()
        val now = System.currentTimeMillis()
        val id = Ulid.next()
        when (val r = CashShift.close(data, v, note, me, today, now) { id }) {
          is CashShift.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
          is CashShift.Result.Ok -> {
            //  «موردِ انتظار» از دفترِ همان لحظه — فروشی که هم‌زمان ثبت شد هم در آن است و گم نمی‌شود
            scope.launch {
              val wrote = store.update { d -> (CashShift.close(d, v, note, me, today, now) { id } as? CashShift.Result.Ok)?.data }
              if (wrote == true) {
                //  PAY-17: کشوی نام‌دارِ بسته دیگر روی این دستگاه نیست
                if (shift.open.drawer != null) MultiDrawerPref.pick(context, null)
                toasts.show(StrSale.drawerClosedDone, V3Tone.SUCCESS)
              } else toasts.show(StrSale.notSaved, V3Tone.ERROR)
            }
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
    blind = blind,
    expectedMinor = x.valueOrNull()?.report?.let { DrawerModel.forViewer(it, seesExpected = !blind) }?.expected?.get(PayFlow.CASH),
    error = error,
    onSubmit = { counts, reason ->
      when (val r = PayFlow.closeRow(data, shift.open, PayFlow.Count(counts, by = me, blind = blind, reason = reason), System.currentTimeMillis(), Ulid::next)) {
        is PayFlow.Outcome.Bad -> error = if (r.problem.code == "REASON_REQUIRED") StrSale.reasonOver50 else PayFlow.message(r.problem)
        is PayFlow.Outcome.Ok -> {
          val z = r.value.report
          val notes = DrawerNotes.close(z.expected?.get(PayFlow.CASH) ?: 0, r.value.row.amount, z.variance?.get(PayFlow.CASH) ?: 0L, reason, me, blind = blind)
          val row = r.value.row.copy(date = todayIso(), notes = notes)
          //  زیرِ قفلِ نوشتن روی دفترِ همان لحظه — فروشی که هم‌زمان ثبت شد گم نمی‌شود
          scope.launch {
            if (store.appendAudit { row }) {
              if (shift.open.drawer != null) MultiDrawerPref.pick(context, null)
              toasts.show(StrSale.drawerClosedDone, V3Tone.SUCCESS)
            } else toasts.show(StrSale.notSaved, V3Tone.ERROR)
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
  //  PAY-07: غیرنقدِ «در انتظار»/«نیازمند بررسی» ِ همین صندوق در جمع نیست — فقط یادداشت، تا صندوقدار بداند چرا؛
  //  و خطِ «ثبت از اسکنر» (کارتِ فرمان، #840)
  val waiting = if (args.query("kind") == "z" || shift == null) 0L
  else remember(data, shift) { DrawerNotes.unreceived(data, shift.open.createdAt, System.currentTimeMillis()) }
  //  حساب‌پی به تفکیکِ حسابِ گیرنده (نام و ۴ رقم از تاریخچهٔ سرور)
  val known = ir.vil3ntec.tohid.ui.screens.rememberHesabPayees()
  ShiftReportSheet(rep, remember { ShopPolicy.blindHere(context) }, title = null,
    notes = listOfNotNull(DrawerModel.scannerLine(data, rep.openedAt, rep.at, seesMoney = !staff)) + DrawerNotes.unreceivedNotes(waiting) +
      DrawerModel.hesabPayLines(data, rep.openedAt, rep.at, known, seesMoney = !staff) + DrawerModel.fxLines(data, rep.openedAt, rep.at, seesMoney = !staff),
    onClose = { back() })
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
  val known = ir.vil3ntec.tohid.ui.screens.rememberHesabPayees()
  if (z != null) ShiftReportSheet(z, remember { ShopPolicy.blindHere(context) }, title = null, notes = listOfNotNull(shift.open.notes.ifBlank { null }, shift.close?.notes?.ifBlank { null }, DrawerModel.scannerLine(data, z.openedAt, z.at, seesMoney = !staff)) + DrawerModel.hesabPayLines(data, z.openedAt, z.at, known, seesMoney = !staff) + DrawerModel.fxLines(data, z.openedAt, z.at, seesMoney = !staff), onClose = { back() })
  else ShiftNotesSheet(shift, onClose = { back() })
}
