package ir.vil3ntec.tohid.ui.v3.account.team

import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.data.repo.TillPinMemberDto

/**
 *  STF-04 — بخشِ «PIN ِ صندوق» ِ صفحهٔ کارمند (آزمون: `TillPinMemberModelTest`). حال فقط از `GET /shop/till-pin/members`
 *  (پرچمِ `shop.till_pin`؛ خاموش یا بی `members.view` ⇒ `null` و بی بخش). کارها همان اختیارِ سرور (`till-pin.mayManage`):
 *  صاحب همه را، مدیر فقط نقش‌های پایین‌تر از مدیر؛ هیچ‌کس قفل یا PIN ِ خودش را از این‌جا (خودش PIN ِ تازه می‌گذارد).
 *  سرور همان را دوباره می‌سنجد و تأییدِ دوبارهٔ ورود می‌خواهد — این فقط پنهان کردنِ دکمهٔ بی‌حاصل است.
 */
object TillPinMemberModel {

  enum class State { NONE, SET, LOCKED, INVALIDATED }

  data class Row(val state: State, val lockedUntil: Long?, val canUnlock: Boolean, val canRemove: Boolean)

  private fun rank(role: String): Int = when (role.trim().lowercase()) { "owner" -> 3; "manager" -> 2; else -> 1 }

  fun mayManage(viewerRole: String, targetRole: String): Boolean = when (viewerRole.trim().lowercase()) {
    "owner" -> true
    "manager" -> rank(targetRole) < rank("manager")
    else -> false
  }

  fun rowOf(pins: List<TillPinMemberDto>?, member: MemberDto, viewerRole: String, viewerUserId: String): Row? {
    val p = pins?.firstOrNull { it.memberId == member.id } ?: return null
    val state = when {
      p.pinSet && p.locked -> State.LOCKED
      p.pinSet -> State.SET
      p.invalidated -> State.INVALIDATED
      else -> State.NONE
    }
    val may = member.userId != viewerUserId && mayManage(viewerRole, member.role)
    return Row(state, if (state == State.LOCKED) p.lockedUntil else null, canUnlock = may && state == State.LOCKED, canRemove = may && p.pinSet)
  }
}
