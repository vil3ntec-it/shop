package ir.vil3ntec.tohid.ui.v3.online.delivery

import ir.vil3ntec.tohid.core.delivery.CourierCash
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.delivery.SettleRow
import ir.vil3ntec.tohid.core.delivery.SettlementMath

/**
 *  سنجه‌های خالصِ برگه‌های میزِ تحویل — عیناً همان شرط‌های پنجره‌های پیشین (`DeliveryDialogs.kt`،
 *  `DeliveryCash.kt`)، بیرون از نمایش و با آزمون (`DeliveryFormsTest`). سرور هر کار را دوباره می‌سنجد.
 */
object DeliveryForms {

  /** سپردنِ دستی: پیکِ دکان (با جای خالیِ نقد) یا شرکتِ بیرونی، با کرایهٔ همین تحویل */
  data class Assign(
    val mode: String,
    val courierId: String = "",
    val override: Boolean = false,
    val company: String = "",
    val ref: String = "",
    val fare: String = "",
  )

  fun modes(cfg: DeskConfig): List<String> = buildList { if (cfg.shopCourier) add("shop_courier"); if (cfg.external) add("external") }

  fun startMode(cfg: DeskConfig, couriers: List<CourierRow>): String =
    if (cfg.shopCourier && couriers.any { it.active }) "shop_courier" else modes(cfg).lastOrNull() ?: "external"

  /** پیکِ انتخاب‌شده با این سفارش از سقف می‌گذرد؟ */
  fun overCap(a: Assign, couriers: List<CourierRow>, expected: Long): Boolean =
    couriers.firstOrNull { it.id == a.courierId }?.let { !CourierCash.fits(it, expected) } ?: false

  fun assignValid(a: Assign, couriers: List<CourierRow>, expected: Long): Boolean {
    if (minorOf(a.fare) == null) return false
    return when (a.mode) {
      "shop_courier" -> {
        val chosen = couriers.firstOrNull { it.id == a.courierId }
        chosen != null && chosen.active && (CourierCash.fits(chosen, expected) || a.override)
      }
      else -> a.company.isNotBlank()
    }
  }

  /** یک کارِ کارمند روی مأموریت */
  data class Act(val reason: String = "", val note: String = "", val cash: String = "", val cashReason: String = "", val code: String = "")

  fun reasonKey(act: DeskActions.Act): String? = when (act) {
    DeskActions.Act.WITHDRAW -> "withdrawn"
    DeskActions.Act.FAIL -> "failed"
    DeskActions.Act.RECALL -> "recall"
    else -> null
  }

  fun startAct(m: Mission): Act = Act(cash = if (m.expectedCollectMinor > 0) fieldOf(m.expectedCollectMinor) else "")

  fun deliver(act: DeskActions.Act): Boolean = act == DeskActions.Act.DELIVER

  fun needCode(m: Mission, act: DeskActions.Act): Boolean = deliver(act) && m.hasCode && !m.codeUsed

  fun cashMinor(act: DeskActions.Act, f: Act): Long? = if (deliver(act)) minorOf(f.cash) else 0L

  fun cashDiffers(m: Mission, act: DeskActions.Act, f: Act): Boolean {
    val c = cashMinor(act, f)
    return deliver(act) && c != null && c != m.expectedCollectMinor
  }

  fun noteNeeded(m: Mission, act: DeskActions.Act, f: Act): Boolean =
    DeskActions.needsNote(m, act) || f.reason == "other" || f.cashReason == "other" || (needCode(m, act) && f.code.isBlank())

  fun actValid(m: Mission, act: DeskActions.Act, cfg: DeskConfig, f: Act): Boolean =
    (reasonKey(act) == null || f.reason.isNotEmpty()) &&
      (!noteNeeded(m, act, f) || f.note.isNotBlank()) &&
      cashMinor(act, f) != null && (!cashDiffers(m, act, f) || f.cashReason.isNotEmpty()) &&
      (!needCode(m, act) || f.code.isNotBlank() || cfg.perms.assign)

  /** تحویلِ حضوری: کدِ ۶ رقمی یا تأییدِ دستیِ مدیر با توضیح، و نقدِ صندوق */
  data class Handover(val code: String = "", val manual: Boolean = false, val note: String = "", val cash: String = "", val cashReason: String = "")

  fun startHandover(m: Mission): Handover = Handover(cash = if (m.expectedCollectMinor > 0) fieldOf(m.expectedCollectMinor) else "0")

  fun handoverDiffers(m: Mission, h: Handover): Boolean = minorOf(h.cash)?.let { it != m.expectedCollectMinor } ?: false

  fun handoverValid(m: Mission, h: Handover): Boolean {
    val cash = minorOf(h.cash) ?: return false
    val differs = cash != m.expectedCollectMinor
    return (!differs || h.cashReason.isNotEmpty()) &&
      (if (h.manual) h.note.isNotBlank() else h.code.length == CODE_LENGTH) &&
      (h.cashReason != "other" || h.note.isNotBlank())
  }

  /** تسویهٔ پایانِ روز: گفتهٔ پیک (اختیاری) و شمارشِ مدیر (لازم) */
  data class Settle(val farePaid: Boolean = false, val handed: String = "", val received: String = "", val reason: String = "", val note: String = "")

  fun handedMinor(s: Settle): Long? = if (s.handed.isBlank()) null else minorOf(s.handed)

  fun receivedMinor(s: Settle): Long? = if (s.received.isBlank()) null else minorOf(s.received)

  fun totals(rows: List<SettleRow>, s: Settle): SettlementMath.Totals =
    SettlementMath.totals(rows, handedMinor(s) ?: 0L, receivedMinor(s) ?: 0L, s.farePaid)

  fun settleNeedsReason(rows: List<SettleRow>, s: Settle): Boolean = receivedMinor(s) != null && totals(rows, s).reasonRequired

  fun settleValid(rows: List<SettleRow>, s: Settle): Boolean =
    receivedMinor(s) != null && (s.handed.isBlank() || handedMinor(s) != null) && (!settleNeedsReason(rows, s) || s.reason.isNotBlank())

  /** سقفِ نقدِ پیک: عدد لازم است */
  fun capMinor(text: String): Long? = if (text.isBlank()) null else minorOf(text)

  const val CODE_LENGTH = 6
  const val NUMBER_MAX = 14
  const val NOTE_MAX = 300
  const val REASON_MAX = 200
  const val NAME_MAX = 80
}
