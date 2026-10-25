package ir.vil3ntec.tohid.core.perm

/**
 *  هستهٔ سنجشِ مجوز — STF-01 تا STF-03 (بخشِ ۱۰.۱ِ مشخصات).
 *
 *  همتای دقیقِ `license/perm-core.js` (وب) و `server/src/lib/perm-core.js` (سرور).
 *  هر سه با یک فایلِ مرجع سنجیده می‌شوند: `core/test-vectors/perm-v1.json`
 *  (آزمونش: `android-native/app/src/test/.../core/perm/PermCoreTest.kt`).
 *  ⛔ رفتارِ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.
 *
 *  ── مدل ──────────────────────────────────────────────────────────
 *  هر مجوز سه حالت دارد: allow، deny، approve («نیازمند تأیید»). پیش‌فرضِ هر
 *  نقش «رفتارِ امروز» است و لایه‌ها (نقش در این دکان، سپس همین کاربر) به‌ترتیب
 *  رویش می‌نشینند. لایه‌ها درختِ JSON‌اند (Map / List / String / Number / null)
 *  تا همان ورودیِ نسخهٔ JS را بی تبدیلِ نوع بپذیرند.
 *   • مالک همیشه همه‌کاره است؛ نقش یا مجوزِ ناشناخته ⇐ ممنوع.
 *   • مجوزِ «restrict» فقط تنگ‌تر از نقش می‌شود؛ «none» از لایه اثر نمی‌گیرد.
 *     استثنا: «نیازمند تأیید» برای مجوزِ تأییدپذیر، چون به خودِ کاربر قدرتی
 *     نمی‌دهد — سرور op را فقط با «برگهٔ امضاشده»ی کسی می‌پذیرد که خودش
 *     همان کار را «مجاز» دارد (بخشِ ۱۰.۱؛ `ApprovalSlip`).
 *   • سقف فقط با حالتِ allow اثر دارد؛ بیش از سقف ⇐ approve (تأییدپذیر) یا deny.
 *
 *  Kotlin خالص است — بی android.*، بی کتابخانه.
 */
object PermCore {

  const val VERSION = 1

  val MODES: List<String> = listOf("allow", "approve", "deny")
  val ROLES: List<String> = listOf("owner", "manager", "staff")

  /** نامِ سقف ⇐ نامِ فیلدِ درخواست */
  val CAP_FIELDS: Map<String, String> = mapOf("maxPct" to "pct", "maxAmount" to "amount")

  private val RANK = mapOf("deny" to 0, "approve" to 1, "allow" to 2)

  class Spec(
    val defaults: Map<String, String>,
    /** none | restrict | full */
    val edit: String,
    val approvable: Boolean,
    val caps: List<String>,
    /** فقط نمایش است (داده روی دستگاه هست؛ امنیت نیست) */
    val display: Boolean,
  )

  data class Resolved(val mode: String, val caps: Map<String, Double>)

  data class Decision(
    val decision: String,
    val reason: String,
    val cap: String? = null,
    val limit: Double? = null,
  )

  private val OMD = mapOf("owner" to "allow", "manager" to "allow", "staff" to "deny")
  private val ALL = mapOf("owner" to "allow", "manager" to "allow", "staff" to "allow")

  /** پیش‌فرض‌ها = رفتارِ امروز (MATRIXِ سرور و ShopRoleِ برنامه) */
  val CATALOG: Map<String, Spec> = mapOf(
    "sales.discount" to Spec(OMD, "restrict", true, listOf("maxPct", "maxAmount"), false),
    "sales.cancel" to Spec(OMD, "restrict", true, emptyList(), false),
    // برگشتِ کالا و بازپرداخت (STF-03): امروز هر عضوِ نویسنده ثبت می‌کند
    "returns.create" to Spec(ALL, "restrict", true, listOf("maxAmount"), false),
    "settings.write" to Spec(OMD, "restrict", false, emptyList(), false),
    "audit.view" to Spec(OMD, "restrict", false, emptyList(), false),
    "reports.money" to Spec(OMD, "full", false, emptyList(), true),
    // HW-03: باز کردنِ دستیِ کشو بی فروش (`no_sale`)؛ شاگرد فقط با تأییدکننده — همان no_sale ِ #286
    "drawer.open" to Spec(OMD, "restrict", true, emptyList(), false),
  )

  private fun isCapValue(v: Any?): Boolean =
    v is Number && v.toDouble().let { it.isFinite() && it >= 0.0 }

  fun keys(): List<String> = CATALOG.keys.sorted()

  /** حالتِ پیش‌فرضِ یک نقش — یا null برای نقش/مجوزِ ناشناخته */
  fun defaultMode(role: String, key: String): String? = CATALOG[key]?.defaults?.get(role)

  fun resolve(role: String, layers: List<Any?>?, key: String): Resolved {
    val spec = CATALOG[key]
    val base = defaultMode(role, key)
    if (spec == null || base == null) return Resolved("deny", emptyMap())
    if (role == "owner") return Resolved("allow", emptyMap())
    var mode: String = base
    val caps = HashMap<String, Double>()
    if (spec.edit != "none" && layers != null) {
      for (layer in layers) {
        val o = (layer as? Map<*, *>)?.get(key) as? Map<*, *> ?: continue
        val raw = o["mode"] as? String
        if (raw != null && raw in MODES) {
          var m: String = raw
          if (m == "approve" && !spec.approvable) m = "deny"
          if (spec.edit == "restrict" && RANK.getValue(m) > RANK.getValue(base) && m != "approve") m = base
          mode = m
        }
        val oc = o["caps"] as? Map<*, *>
        if (oc != null) {
          for (name in spec.caps) {
            if (!oc.containsKey(name)) continue
            val v = oc[name]
            if (v == null) caps.remove(name)
            else if (isCapValue(v)) caps[name] = (v as Number).toDouble()
          }
        }
      }
    }
    return Resolved(mode, caps.keys.sorted().associateWith { caps.getValue(it) })
  }

  /** تصمیم برای یک کارِ مشخص؛ `req` مثلاً mapOf("pct" to 10.0) */
  fun evaluate(role: String, layers: List<Any?>?, key: String, req: Map<String, Double>? = null): Decision {
    val r = resolve(role, layers, key)
    if (r.mode == "deny") return Decision("deny", "denied")
    if (r.mode == "approve") return Decision("approve", "needs_approval")
    val spec = CATALOG.getValue(key)
    for (name in spec.caps) {
      val limit = r.caps[name] ?: continue
      val value = req?.get(CAP_FIELDS.getValue(name)) ?: continue
      if (value.isFinite() && value > limit) {
        return Decision(if (spec.approvable) "approve" else "deny", "over_cap", name, limit)
      }
    }
    return Decision("allow", "ok")
  }

  /** نیم افغانیِ گرد کردن که در سنجشِ درصدِ تخفیف بخشیده می‌شود */
  const val ROUNDING_MINOR: Long = 50

  /**
   *  درصدِ تخفیف به «صدمِ درصد» (۵٪ = ۵۰۰) — همان `discountBp`ِ JS.
   *  تخفیفِ گردشده تا نیم افغانی بخشیده می‌شود؛ جمعِ صفر با تخفیف ⇐ ۱۰۰٪.
   */
  fun discountBp(discountMinor: Long, totalMinor: Long): Long {
    if (discountMinor <= 0) return 0
    if (totalMinor <= 0) return 10_000
    val forgiven = maxOf(0L, discountMinor - ROUNDING_MINOR)
    return minOf(10_000L, forgiven * 10_000L / totalMinor)
  }

  fun discountPct(discountMinor: Long, totalMinor: Long): Double = discountBp(discountMinor, totalMinor) / 100.0

  /** بی درخواستِ عددی: دکمه دیده شود و بی‌واسطه کار کند */
  fun allows(role: String, layers: List<Any?>?, key: String): Boolean =
    evaluate(role, layers, key, null).decision == "allow"

  fun effective(role: String, layers: List<Any?>?): Map<String, Resolved> =
    keys().associateWith { resolve(role, layers, it) }

  /** سنجشِ یک ردیفِ لایه پیش از ذخیره — null یعنی درست است، وگرنه کدِ خطا */
  fun validate(role: String, key: String, o: Any?): String? {
    val spec = CATALOG[key] ?: return "unknown_permission"
    if (role == "owner") return "owner_fixed"
    val base = defaultMode(role, key) ?: return "unknown_role"
    if (spec.edit == "none") return "not_editable"
    if (o !is Map<*, *>) return "bad_override"
    for (f in o.keys) if (f != "mode" && f != "caps") return "bad_override"
    if (o.containsKey("mode")) {
      val m = o["mode"] as? String
      if (m == null || m !in MODES) return "bad_mode"
      if (m == "approve" && !spec.approvable) return "not_approvable"
      if (spec.edit == "restrict" && RANK.getValue(m) > RANK.getValue(base) && m != "approve") return "exceeds_role"
    }
    if (o.containsKey("caps")) {
      val c = o["caps"] as? Map<*, *> ?: return "bad_cap"
      for ((n, v) in c) {
        if (n !is String || n !in spec.caps) return "unknown_cap"
        if (v != null && !isCapValue(v)) return "bad_cap"
        if (n == "maxPct" && v != null && (v as Number).toDouble() > 100.0) return "bad_cap"
      }
    }
    return null
  }
}
