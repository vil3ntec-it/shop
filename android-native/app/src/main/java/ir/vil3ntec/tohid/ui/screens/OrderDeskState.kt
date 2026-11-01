package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrOrderSlip
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDesk
import ir.vil3ntec.tohid.data.repo.OrderDeskRepository
import ir.vil3ntec.tohid.data.repo.OrderDeskRepository.Desk
import ir.vil3ntec.tohid.ui.OrderAlertsLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 *  حالتِ میزِ سفارش (ORD-03/04/08/09) برای هر دو رابط: `OrderDeskPanel` ِ امروز و `OrderDeskStrip` ِ نسخهٔ ۳
 *  فقط همین را می‌کشند، پس رفتارشان یکی است. همهٔ تصمیم‌ها با سرور (`lib/order-desk.js`)؛ پرچمِ خاموشِ
 *  `shop.orders.desk` ⇒ `{enabled:false}` ⇒ هیچ‌چیز دیده نمی‌شود.
 */

internal fun deskReasonText(r: String): String = when (r) {
  "busy" -> StrOrderSlip.deskReasonBusy
  "out_of_stock" -> StrOrderSlip.deskReasonStock
  "staff" -> StrOrderSlip.deskReasonStaff
  "technical" -> StrOrderSlip.deskReasonTech
  else -> StrOrderSlip.deskReasonOther
}

@Stable
internal class OrderDeskController(val repo: OrderDeskRepository, private val scope: CoroutineScope) {
  var desk by mutableStateOf<Desk?>(null)
    private set

  /** پیامِ آخرین نوشتن: (موفق؟، متن) */
  var note by mutableStateOf<Pair<Boolean, String>?>(null)
    private set
  var busy by mutableStateOf(false)
    private set

  /** هر نیم دقیقه از سرور؛ «دقیقهٔ دیگر» با زمانِ همان پاسخِ سرور (ساعتِ دستگاه مرجع نیست) */
  suspend fun poll() {
    while (true) {
      repo.state().onSuccess { desk = it; OrderAlertsLive.deskOn.value = it.enabled }
      delay(30_000)
    }
  }

  fun act(call: suspend () -> ApiResult<Desk>) {
    if (busy) return
    busy = true
    note = null
    scope.launch {
      //  پاسخِ نوشتن زمانِ سرور ندارد ⇒ پرسشِ تازه، تا «دقیقهٔ دیگر» با همان ساعتِ سرور باشد
      call().onSuccess { repo.state().onSuccess { fresh -> desk = fresh } }.onFailure { f -> note = false to (if (f.code == "version_conflict") StrOrderSlip.deskChanged else f.userMessage) }
      busy = false
    }
  }
}

@Composable
internal fun rememberOrderDesk(api: ApiClient): OrderDeskController {
  val scope = rememberCoroutineScope()
  val c = remember(api) { OrderDeskController(OrderDeskRepository(api), scope) }
  LaunchedEffect(c) { c.poll() }
  return c
}

/** برگهٔ توقف: مدت، دلیل و توضیح — همان `OrderDesk.canPause` ِ سرور */
@Stable
internal class DeskPauseForm {
  var minutes by mutableStateOf(OrderDesk.PAUSE_MINUTES[1])
  var reason by mutableStateOf("")
  var text by mutableStateOf("")
    private set

  fun edit(raw: String) { text = raw.take(200) }
  val ok: Boolean get() = OrderDesk.canPause(minutes, reason, text)
}

/** برگهٔ «قبول و توقفِ خودکار» (صاحب و مدیر): عددها نوشته‌اند و فقط در بازه پذیرفته می‌شوند */
@Stable
internal class DeskSettingsForm(d: Desk) {
  var aaOn by mutableStateOf(d.autoAcceptOn)
  var aaMax by mutableStateOf("${d.autoAcceptMaxOpen}")
  var aaReady by mutableStateOf("${d.autoAcceptReadyMinutes}")
  var apOn by mutableStateOf(d.autoPauseOn)
  var apMax by mutableStateOf("${d.autoPauseMaxOpen}")
  var apOld by mutableStateOf("${d.autoPauseOldestMinutes}")
  var apMin by mutableStateOf("${d.autoPauseMinutes}")

  /** همه در بازه ⇒ میزِ تازه برای `repo.settings`؛ وگرنه null */
  fun result(d: Desk): Desk? {
    val aaMaxN = OrderDesk.bounded(aaMax, 1, 500) ?: return null
    val aaReadyN = OrderDesk.bounded(aaReady, 1, 1440) ?: return null
    val apMaxN = OrderDesk.bounded(apMax, 1, 500) ?: return null
    val apOldN = OrderDesk.bounded(apOld, 1, 1440) ?: return null
    val apMinN = OrderDesk.bounded(apMin, 5, 1440) ?: return null
    return d.copy(
      autoAcceptOn = aaOn, autoAcceptMaxOpen = aaMaxN, autoAcceptReadyMinutes = aaReadyN,
      autoPauseOn = apOn, autoPauseMaxOpen = apMaxN, autoPauseOldestMinutes = apOldN, autoPauseMinutes = apMinN,
    )
  }
}
