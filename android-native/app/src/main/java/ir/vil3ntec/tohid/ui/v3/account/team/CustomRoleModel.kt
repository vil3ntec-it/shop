package ir.vil3ntec.tohid.ui.v3.account.team

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrPayFix
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.core.i18n.StrSetupPages
import ir.vil3ntec.tohid.core.i18n.StrShiftReview
import ir.vil3ntec.tohid.core.model.CatalogEntryDto
import ir.vil3ntec.tohid.core.model.CustomRoleDto
import ir.vil3ntec.tohid.core.model.MemberPermsDto
import ir.vil3ntec.tohid.core.model.ShopPermsDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 *  STF-01 — «نقشِ سفارشی» (آزمون: `CustomRoleModelTest`). ⛔ هیچ چیزی این‌جا تصمیم گرفته نمی‌شود: کلیدهای
 *  قابل‌تغییر، پیش‌فرضِ هر نقشِ پایه و سقف‌ها از کاتالوگِ سرور می‌آیند (`GET /api/shop/permissions`)، و سرور
 *  لایهٔ گشادتر از نقشِ پایه را رد می‌کند (`exceeds_role`). این‌جا فقط ردیف‌های فرم و لایهٔ فرستادنی.
 */
object CustomRoleModel {

  /** نقش‌های پایه — همان `BASE_ROLES` ِ سرور (همهٔ نقش‌های هسته جز صاحب) */
  val BASE_ROLES: List<String> = listOf("manager", "staff", "cashier", "storekeeper", "picker", "accountant")
  const val NAME_MAX = 40

  fun baseLabel(role: String): String = when (role) {
    "manager" -> StrAccount.roleManager
    "staff" -> StrAccount.roleStaff
    "cashier" -> StrAccount.roleCashier
    "storekeeper" -> StrAccount.roleStorekeeper
    "picker" -> StrAccount.rolePicker
    "accountant" -> StrAccount.roleAccountant
    else -> role
  }

  /** نامِ هر کلیدِ قابل‌تغییرِ سرور */
  fun permLabel(key: String): String = when (key) {
    "settings.write" -> StrAccount.permSettings
    "audit.view" -> StrAccount.permAudit
    "reports.money" -> StrAccount.permMoney
    "customers.contact" -> StrAccount.permContacts
    "sales.discount" -> Str.discount
    "sales.cancel" -> StrSetupPages.permVoid
    "sales.credit" -> StrAccount.permCredit
    "sales.price" -> StrAccount.permPrice
    "customers.credit_limit" -> StrAccount.permCreditLimit // CRM-03
    "sales.pay_fix" -> StrPayFix.menu // POS-40: سرور با پرچمِ `shop.sync.pay_fix` در v3 و v1 می‌سنجد
    "returns.create" -> StrSetupPages.permReturns
    "shift.report" -> StrShiftReview.permReports
    "safe.move" -> StrSetupPages.permSafe
    else -> key
  }

  fun modeLabel(mode: String): String = when (mode) {
    "allow" -> StrSalePages.tillGoodsPartOk
    "approve" -> StrSetupPages.withManagerOk
    else -> StrSetupPages.denied
  }

  /** یک ردیفِ فرم: حالت‌های ممکن برای همین نقشِ پایه، حالتِ کنونی و سقف‌ها (درصد؛ مبلغ به افغانی) */
  data class Row(
    val key: String,
    val label: String,
    val options: List<String>,
    val roleDefault: String,
    val mode: String,
    val caps: List<String>,
    val pct: String = "",
    val amount: String = "",
  ) {
    val showCaps: Boolean get() = mode == "allow" && caps.isNotEmpty()
  }

  private fun capText(v: Double?): String = when {
    v == null -> ""
    v == kotlin.math.floor(v) -> v.toLong().toString()
    else -> v.toString()
  }

  /**
   *  ردیف‌های معنادار برای نقشِ پایه — کلیدِ «restrict» که نقش ندارد و تأییدپذیر هم نیست چیزی برای تغییر ندارد
   *  (همان قاعدهٔ `MemberPermsSection`).
   */
  fun rows(catalog: List<CatalogEntryDto>, base: String, perms: JsonObject = JsonObject(emptyMap())): List<Row> =
    catalog.filter { it.edit != "none" }.mapNotNull { e ->
      val def = e.defaults[base] ?: "deny"
      if (e.edit == "restrict" && def != "allow" && !e.approvable) return@mapNotNull null
      val options = buildList {
        if (def == "allow" || e.edit == "full") add("allow")
        if (e.approvable) add("approve")
        add("deny")
      }
      val o = perms[e.key] as? JsonObject
      val saved = (o?.get("mode") as? JsonPrimitive)?.contentOrNull
      val caps = o?.get("caps") as? JsonObject
      Row(
        key = e.key, label = permLabel(e.key), options = options, roleDefault = def,
        mode = saved?.takeIf { it in options } ?: def.takeIf { it in options } ?: options.last(),
        caps = e.caps,
        pct = capText(caps?.get("maxPct")?.jsonPrimitive?.doubleOrNull),
        amount = capText(caps?.get("maxAmount")?.jsonPrimitive?.doubleOrNull?.div(100.0)),
      )
    }

  /** عددِ سقف از ورودی؛ خالی ⇒ null (بی سقف)؛ نادرست ⇒ NaN */
  fun capValue(s: String): Double? {
    val t = s.trim().replace('٫', '.').map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.joinToString("")
    if (t.isEmpty()) return null
    val v = t.toDoubleOrNull() ?: return Double.NaN
    return if (v < 0 || v.isNaN() || v.isInfinite()) Double.NaN else v
  }

  /** ردیفی که سقفِ نادرست دارد (یا درصدِ بیش از ۱۰۰) */
  fun capProblem(r: Row): Boolean {
    if (!r.showCaps) return false
    val pct = if ("maxPct" in r.caps) capValue(r.pct) else null
    val amount = if ("maxAmount" in r.caps) capValue(r.amount) else null
    return (pct != null && (pct.isNaN() || pct > 100)) || (amount != null && amount.isNaN())
  }

  /** ۵ نه ۵٫۰ — همان عددی که سرور ذخیره و برمی‌گرداند */
  private fun whole(v: Double): Number = if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong() else v

  /** لایهٔ فرستادنی: فقط آنچه با پیش‌فرضِ نقشِ پایه فرق دارد، و سقف فقط برای «مجاز» (مبلغ به Minor) */
  fun layer(rows: List<Row>): JsonObject = buildJsonObject {
    for (r in rows) {
      val caps: Map<String, Number> = if (!r.showCaps) emptyMap() else buildMap {
        if ("maxPct" in r.caps) capValue(r.pct)?.takeIf { !it.isNaN() }?.let { put("maxPct", whole(it)) }
        if ("maxAmount" in r.caps) capValue(r.amount)?.takeIf { !it.isNaN() }?.let { put("maxAmount", kotlin.math.round(it * 100.0).toLong()) }
      }
      if (r.mode == r.roleDefault && caps.isEmpty()) continue
      put(r.key, buildJsonObject {
        if (r.mode != r.roleDefault) put("mode", JsonPrimitive(r.mode))
        if (caps.isNotEmpty()) put("caps", buildJsonObject { caps.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
      })
    }
  }

  /** نامِ نقش — null یعنی درست است */
  fun nameProblem(name: String): String? {
    val n = name.trim()
    return if (n.isEmpty() || n.length > NAME_MAX || n.any { it == '\n' || it == '\r' || it == '\t' }) StrAccount.customRoleNameBad else null
  }

  /** زیرنویسِ هر نقش در فهرست */
  fun subtitle(r: CustomRoleDto): String = StrAccount.customRoleSubtitle(baseLabel(r.baseRole), r.members.size)

  /** گزینه‌های برگهٔ «نقشِ سفارشی» ِ یک عضو: «بی نقشِ سفارشی» و همهٔ نقش‌ها («نام · نقشِ پایه») */
  data class Choice(val id: String?, val label: String)

  fun choices(roles: List<CustomRoleDto>): List<Choice> =
    listOf(Choice(null, StrAccount.customRoleNone)) + roles.map { Choice(it.id, "${it.name} · ${baseLabel(it.baseRole)}") }

  /** نقشِ سفارشیِ کنونیِ عضو (از سرور: فقط وقتی با نقشِ پایه می‌خواند) */
  fun currentOf(page: ShopPermsDto?, m: MemberPermsDto?): String? = m?.customRole?.id?.takeIf { id -> page?.customRoles?.any { it.id == id } == true }

  /** بخشِ نقشِ سفارشی معنا دارد؟ پرچمِ روشن (`customRoles` آمده)، و نه صاحب و پیک */
  fun visibleFor(page: ShopPermsDto?, m: MemberPermsDto?): Boolean =
    page?.customRoles != null && m != null && m.role in BASE_ROLES
}
