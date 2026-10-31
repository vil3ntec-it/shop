package ir.vil3ntec.tohid.ui.v3.sales.approval

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.core.perm.PinGuard
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.pos.KeypadInput

/** آنچه برگهٔ تأیید باید بداند — صدازننده (تسویه) پیش از باز کردنش می‌گذارد */
data class ApprovalAsk(val title: String, val summary: String, val need: ApprovalSlip.Need)

/** چرا تأیید ممکن نیست (پیش از PIN) */
enum class ApprovalBlock { NO_SHOP, NO_APPROVER }

/**
 *  برگهٔ «درخواستِ تأیید» (STF-02، S07/S08) — خالص: همان قاعده‌های `ApprovalDialog`ِ پیشین.
 *  مدیر یا صاحبِ دکان روی همین دستگاه نامش را برمی‌گزیند، دلیل می‌نویسد و PINِ شش‌رقمی‌اش را
 *  می‌زند؛ امضا و شمارشِ خطا با `ApproverVault` و `PinGuard` است، نه این‌جا.
 */
object ApprovalModel {

  /** رقم‌ها و «پاک کردن» — جای خالی پیش از «۰» */
  val KEYS: List<Char> = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', ' ', '0', KeypadInput.BACKSPACE)

  fun block(shopId: String, approvers: List<ApproverVault.Record>): ApprovalBlock? = when {
    shopId.isBlank() -> ApprovalBlock.NO_SHOP
    approvers.isEmpty() -> ApprovalBlock.NO_APPROVER
    else -> null
  }

  fun blockText(b: ApprovalBlock): String = when (b) {
    ApprovalBlock.NO_SHOP -> StrSale.approvalNoShop
    ApprovalBlock.NO_APPROVER -> StrSale.approvalNoApprover
  }

  fun name(r: ApproverVault.Record?): String =
    r?.name?.ifBlank { null } ?: when (r?.role) {
      "owner" -> StrSale.shopOwner
      null -> StrSale.approverWord
      else -> StrSale.managerWord
    }

  /** یک کلید روی PIN؛ بیش از طولِ PIN پذیرفته نمی‌شود */
  fun press(pin: String, key: Char, length: Int = PinGuard.NEW_LEN): String = when {
    key == KeypadInput.BACKSPACE -> pin.dropLast(1)
    !key.isDigit() -> pin
    pin.length >= length -> pin
    else -> pin + key
  }

  fun complete(pin: String, length: Int = PinGuard.NEW_LEN): Boolean = pin.length == length

  fun hint(reason: String, who: String): String =
    if (reason.isBlank()) StrSale.approvalReasonFirst else StrSale.approvalPinOf(who)

  fun lockedText(who: String): String = StrSale.approvalLocked(who, plain(PinGuard.MAX_FAILURES))

  fun wrongText(left: Int): String = StrSale.approvalWrongPin(plain(left))
}
