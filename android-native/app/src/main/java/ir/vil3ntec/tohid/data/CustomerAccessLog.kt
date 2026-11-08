package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.crm.CustomerAccess

/**
 *  CRM-15 — «چه کسی پروندهٔ مشتری را دید» روی **دفترِ دستگاه** (بی‌اینترنت هم): هر دیدن یک ردیفِ فقط‌افزودنیِ
 *  `auditLog` با `type = customer_view` ([CustomerAccess.TYPE])، `refId` = شناسهٔ پرونده (یا [CustomerAccess.ALL] برای
 *  برگهٔ همهٔ مشتریان)، `viewer`/`role`/`device`/`view` و نامِ بیننده در `by`. همان راهِ همگام‌سازیِ امروز ِ `auditLog`
 *  (راهِ قدیم، v1، v3؛ هر نقشِ نویسنده — `PermCore.canWrite`) ردیف را به سرور و دستگاه‌های دیگرِ دکان می‌برد.
 *
 *  ⛔ بی دادهٔ شخصیِ مشتری: نه نام، نه شماره، نه نشانی، نه یادداشت (`notes` خالی).
 *  ⛔ همان بیننده + مشتری + نوع در ۱۰ دقیقه ⇐ هیچ نوشتن (`null`، برای `ShopStore.update`) — ردیفِ سابقه ویرایش نمی‌شود.
 *  منطقِ خالص؛ مرزِ سکو (ساعت، نقش، دستگاه، دیسک) در `CustomerAccessLogAndroid.kt`.
 */
object CustomerAccessLog {

  /** کسی که پرونده را دید — از نشستِ همین دستگاه (کاربرِ صندوق اگر روشن است) */
  data class Viewer(val id: String, val name: String, val role: String, val device: String)

  /** ردیف‌های دیدنِ دفتر ⇐ همان شکلِ [CustomerAccess.View] */
  fun views(d: ShopData): List<CustomerAccess.View> = d.auditLog.mapNotNull(::viewOf)

  private fun viewOf(e: AuditEntry): CustomerAccess.View? {
    if (e.type != CustomerAccess.TYPE) return null
    val kind = CustomerAccess.Kind.of(e.view) ?: return null
    val ref = e.refId?.takeIf { it.isNotBlank() } ?: return null
    return CustomerAccess.View(
      id = e.id, customerId = ref, viewerId = e.viewer.orEmpty(), viewerName = e.by.orEmpty(), role = e.role.orEmpty(),
      device = e.device.orEmpty(), kind = kind, at = e.createdAt,
    )
  }

  /**
   *  دفترِ تازه با ردیفِ دیدن، یا `null` وقتی چیزی برای نوشتن نیست: تکرار در ۱۰ دقیقه، پروندهٔ ناموجود (جز
   *  [CustomerAccess.ALL])، یا شناسهٔ خالی.
   */
  fun record(
    d: ShopData,
    customerId: String,
    kind: CustomerAccess.Kind,
    who: Viewer,
    today: String,
    now: Long,
    newId: () -> String = ::auditId,
  ): ShopData? {
    val id = customerId.trim()
    if (id.isEmpty()) return null
    if (id != CustomerAccess.ALL && d.debtors.none { it.id == id }) return null
    if (CustomerAccess.isRepeat(views(d), who.id, id, kind, now)) return null
    val row = AuditEntry(
      id = newId(), type = CustomerAccess.TYPE, date = today, refId = id, createdAt = now,
      by = who.name.trim().take(NAME_MAX).ifEmpty { null },
      viewer = who.id.take(ID_MAX), role = who.role.take(ID_MAX).ifEmpty { null }, device = who.device.take(ID_MAX).ifEmpty { null },
      view = kind.key,
    )
    return d.copy(auditLog = d.auditLog + row)
  }

  /** نوعِ دیدنِ پرونده هنگامِ باز شدن: شمارهٔ کامل پیداست ⇒ `phone`، وگرنه `detail` */
  fun openKind(canSeeContacts: Boolean, debtor: Debtor?): CustomerAccess.Kind =
    if (canSeeContacts && debtor != null && (debtor.phone.isNotBlank() || !debtor.phone2.isNullOrBlank())) CustomerAccess.Kind.PHONE
    else CustomerAccess.Kind.DETAIL

  private const val NAME_MAX = 60
  private const val ID_MAX = 80
}
