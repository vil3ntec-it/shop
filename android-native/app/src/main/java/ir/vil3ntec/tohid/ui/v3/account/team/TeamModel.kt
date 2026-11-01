package ir.vil3ntec.tohid.ui.v3.account.team

import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.StaffCodeDto
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat

/**
 *  حالتِ خالصِ صفحهٔ «کارمندان» (آزمون: `TeamModelTest`) — همان قاعده‌های صفحهٔ
 *  پیشین: صاحب دکان برداشتنی نیست (دکان بی‌صاحب نمی‌ماند)، کدِ خرج‌شده یا باطل
 *  «(باطل)» است و دیگر باطل‌کردنی نیست، سقفِ اعضا فقط وقتی سرور گفته باشد.
 */
object TeamModel {

  fun staffTitle(count: Int, max: Int): String = if (max > 0) StrAccount.staffCount(count, max) else StrAccount.staffTitle

  data class MemberRow(
    val id: String,
    val name: String,
    val initial: String,
    val contact: String,
    val role: String,
    val roleKind: AccountFormat.Role,
    val active: Boolean,
    val removable: Boolean,
  )

  fun memberRow(m: MemberDto): MemberRow {
    val kind = AccountFormat.roleOf(m.role)
    return MemberRow(
      id = m.id,
      name = m.name.trim().ifBlank { StrAccount.noName },
      initial = m.name.trim().take(1).ifBlank { "؟" },
      contact = m.contact,
      role = AccountFormat.teamRole(m.role),
      roleKind = kind,
      active = m.status == "active",
      removable = kind != AccountFormat.Role.OWNER,
    )
  }

  data class CodeRow(val id: String, val hint: String, val role: String, val uses: String, val expiry: String, val dead: Boolean)

  fun dead(c: StaffCodeDto): Boolean = c.status != "active" || (c.maxUses > 0 && c.usedCount >= c.maxUses)

  fun codeRow(c: StaffCodeDto): CodeRow = CodeRow(
    id = c.id,
    hint = "${c.hint}••••",
    role = AccountFormat.teamRole(c.role),
    uses = if (c.maxUses > 0) StrAccount.usesOf(c.usedCount, c.maxUses) else StrAccount.unlimited,
    expiry = c.expiresAt?.takeIf { it > 0 }?.let { StrAccount.validUntil(AccountFormat.date(it)) } ?: StrAccount.validAlways,
    dead = dead(c),
  )

  /** کدِ زنده اول، باطل‌ها پایین */
  fun codeRows(codes: List<StaffCodeDto>): List<CodeRow> = codes.map(::codeRow).sortedBy { it.dead }

  /** نامِ کارمند برای عنوانِ «برداشتنِ X؟» */
  fun removeName(m: MemberDto?): String = m?.name?.trim()?.ifBlank { null } ?: StrAccount.thisMember
}
