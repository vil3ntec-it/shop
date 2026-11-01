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
 *  ── نقش‌ها (STF-01، A16) ────────────────────────────────────────────
 *  owner/manager/staff همان سه نقشِ قدیم‌اند و رفتارشان عوض نمی‌شود. تازه‌ها:
 *  cashier (صندوق‌دار)، storekeeper (انباردار)، picker (جمع‌آور) و accountant
 *  (حسابدار، فقط‌خواندنی). `canWrite(role, table)` = جدول‌های همگام‌سازی که هر
 *  نقش می‌نویسد؛ سه نقشِ قدیم «*». پیک این‌جا نیست (توکنش دامنهٔ خودش را دارد).
 *
 *  Kotlin خالص است — بی android.*، بی کتابخانه.
 */
object PermCore {

  const val VERSION = 1

  val MODES: List<String> = listOf("allow", "approve", "deny")
  val ROLES: List<String> = listOf("owner", "manager", "staff", "cashier", "storekeeper", "picker", "accountant")

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

  /**
   *  پیش‌فرض‌ها به ترتیبِ `ROLES` — هر هفت نقش، صریح. `zip` نقشِ بی مقدار را بی‌صدا می‌انداخت؛ کلیدی که
   *  کمتر بدهد (مثلاً فقط سه نقشِ قدیم) حالا هنگامِ بارگذاری می‌شکند — همان `d()` ِ JS (بازبینی، یافتهٔ ۴).
   */
  private fun d(vararg modes: String): Map<String, String> {
    require(modes.size == ROLES.size && modes.all { it in MODES }) { "PermCore: d() needs ${ROLES.size} valid modes" }
    return ROLES.zip(modes.toList()).toMap()
  }

  private val OMD = d("allow", "allow", "deny", "deny", "deny", "deny", "deny")
  //  حسابدار سابقه و عددِ مالی را می‌بیند — کارش همین است
  private val OMA = d("allow", "allow", "deny", "deny", "deny", "deny", "allow")

  /** پیش‌فرض‌ها = رفتارِ امروز (MATRIXِ سرور و ShopRoleِ برنامه) */
  val CATALOG: Map<String, Spec> = mapOf(
    "sales.discount" to Spec(OMD, "restrict", true, listOf("maxPct", "maxAmount"), false),
    "sales.cancel" to Spec(OMD, "restrict", true, emptyList(), false),
    // POS-40: اصلاحِ روشِ پرداخت پس از فروش (`core/pay/PayFix`) — کارِ تازه: صاحب و مدیر خودشان، شاگرد و صندوق‌دار
    // «نیازمند تأیید»، بقیه نه؛ همان `d()` ِ JS. سرور هنوز اجرایش نمی‌کند (not_enforceable_yet)
    "sales.pay_fix" to Spec(d("allow", "allow", "approve", "approve", "deny", "deny", "deny"), "restrict", true, emptyList(), false),
    // برگشتِ کالا و بازپرداخت (STF-03): امروز هر عضوِ نویسنده ثبت می‌کند؛ از نقش‌های تازه فقط صندوق‌دار
    "returns.create" to Spec(d("allow", "allow", "allow", "allow", "deny", "deny", "deny"), "restrict", true, listOf("maxAmount"), false),
    "settings.write" to Spec(OMD, "restrict", false, emptyList(), false),
    "audit.view" to Spec(OMA, "restrict", false, emptyList(), false),
    "reports.money" to Spec(OMA, "full", false, emptyList(), true),
    // HW-03: باز کردنِ دستیِ کشو بی فروش (`no_sale`)؛ شاگرد فقط با تأییدکننده — همان no_sale ِ #286.
    // نقش‌های تازه هم «ممنوع» (صندوق‌دار هم: لایهٔ نقش می‌تواند «با تأیید» کند، مثلِ شاگرد)
    "drawer.open" to Spec(OMD, "restrict", true, emptyList(), false),
    // PAY-15: گزارشِ X و Z ِ صندوقِ شیفت — امروز همه (شاگرد نمای کور)؛ فقط نمایش، تنگ‌شدنی و بی تأیید.
    // نقش‌های تازه: صندوق‌دار و حسابدار می‌بینند؛ انباردار و جمع‌آور نه — همان `d()` ِ JS
    "shift.report" to Spec(d("allow", "allow", "allow", "allow", "deny", "deny", "allow"), "restrict", false, emptyList(), true),
    // گاوصندوق (PAY-18): شمارش، ورود، خروج، به بانک و به دخل؛ «از دخل» فقط نوشتن می‌خواهد
    "safe.move" to Spec(OMD, "restrict", false, emptyList(), false),
  )

  /** «همه» — رفتارِ امروزِ سه نقشِ قدیم */
  const val ALL_TABLES = "*"

  /**
   *  جدول‌های همگام‌سازی که هر نقش می‌نویسد (نام‌های Sync v1/v3، همان
   *  `SyncEngine.COLLECTIONS`). `auditLog` برای هر نقشِ نویسنده است.
   */
  val WRITES: Map<String, List<String>> = mapOf(
    "owner" to listOf(ALL_TABLES),
    "manager" to listOf(ALL_TABLES),
    "staff" to listOf(ALL_TABLES),
    //  `warehouseEntries` فقط با قاعدهٔ ردیفِ `return_adjustment` (`ROW_RULES`): اصلاحِ موجودیِ
    //  برگشتِ ضایعات و بی‌رسید (#318)، نه رسیدِ انبارِ دستی
    "cashier" to listOf("auditLog", "debtors", "returns", "saleItems", "sales", "stockMovements", "transactions", "warehouseEntries"),
    "storekeeper" to listOf(
      "auditLog", "priceHistory", "productCategories", "productUnits", "products", "purchases",
      "stockCounts", "stockMovements", "suppliers", "warehouseEntries",
    ),
    "picker" to listOf("auditLog"),
    "accountant" to emptyList(),
    //  گاوصندوق (`safeEntries`، #364) عمداً در فهرستِ هیچ نقشِ تازه‌ای نیست؛ با مرجِ #364، `safe.move`
    //  هم برای هر چهار نقشِ تازه صریحاً «deny» است (همان یادداشتِ WRITES در perm-core.js)
  )

  /** این نقش این جدولِ همگام‌سازی را می‌نویسد؟ نقشِ ناشناخته ⇐ نه */
  fun canWrite(role: String, table: String): Boolean {
    val w = WRITES[role] ?: return false
    if (table.isEmpty()) return false
    return w == listOf(ALL_TABLES) || table in w
  }

  /** نوعِ اصلاحِ موجودی که «برگشت» شمرده می‌شود — همان `RETURN_ADJ_KINDS`ِ JS */
  val RETURN_ADJ_KINDS: List<String> = listOf("customer_return", "damage")

  /** نوعِ حرکت ⇐ سندی که `refId` به آن اشاره می‌کند — همان `DOC_MOVE_TYPES`ِ JS */
  val DOC_MOVE_TYPES: Map<String, String> =
    mapOf("sale" to "sale", "sale_cancel" to "sale", "customer_return" to "return", "damage" to "return")

  /** نوع‌هایی که مقدارشان منفی است (بیرون رفتن از موجودی)؛ بقیه مثبت */
  private val NEGATIVE_KINDS = setOf("damage", "sale")

  /** قاعدهٔ ردیف: جدولی که نقش فقط ردیف‌های تازهٔ معینی از آن را می‌نویسد — همان `ROW_RULES`ِ JS */
  private val ROW_RULES: Map<String, Map<String, String>> =
    mapOf("cashier" to mapOf("warehouseEntries" to "return_adjustment", "stockMovements" to "doc_movement"))

  /** نامِ قاعدهٔ ردیفِ این نقش برای این جدول، یا null */
  fun rowRule(role: String, table: String): String? = ROW_RULES[role]?.get(table)

  /** یک قلمِ برگشت: `own` = همین دسته، یا ثبت‌شده با همین دستگاه/کاربر */
  data class ReturnLine(val id: String, val docId: String?, val productId: String, val qty: Double, val own: Boolean)

  /** اصلاحِ ثبت‌شده‌ای که قلمی از برگشت را پیش‌تر گرفته است */
  data class Taken(val refId: String, val productId: String, val units: Double?)

  /** یک قلمِ فاکتور: سقفِ حرکتِ `sale` و `sale_cancel` ِ همان کالا */
  data class SaleLine(val id: String, val saleId: String, val productId: String, val qty: Double?)

  /** حرکتِ انبارِ ثبت‌شده‌ای که از قلمی از سند برداشته است */
  data class Moved(val refId: String, val productId: String, val type: String, val qty: Double?)

  /** نشانی‌های قاعدهٔ ردیف — همان `refs`ِ JS */
  data class RowRefs(
    val returns: List<ReturnLine> = emptyList(),
    val sales: Collection<String> = emptyList(),
    val taken: List<Taken> = emptyList(),
    val saleItems: List<SaleLine> = emptyList(),
    val cancelled: Collection<String> = emptyList(),
    val moved: List<Moved> = emptyList(),
  )

  /** یک ردیفِ دسته: `row` = نمای درجِ تازه؛ null = ویرایش، حذف، ردیفِ موجود یا شناسهٔ تکراری */
  data class RowIn(val table: String, val row: Map<String, Any?>?)

  data class RowCheck(val ok: Boolean, val reason: String)

  private fun no(reason: String) = RowCheck(false, reason)
  private val YES = RowCheck(true, "")
  private fun num(v: Any?): Double? = (v as? Number)?.toDouble()?.takeIf { it.isFinite() }
  private fun signOk(kind: String, v: Double?): Boolean = v != null && (if (kind in NEGATIVE_KINDS) v < 0 else v > 0)
  private fun linesOf(refs: RowRefs, ref: String): List<ReturnLine> =
    refs.returns.filter { it.id.isNotEmpty() && (it.id == ref || it.docId == ref) }
  private fun ofProduct(lines: List<ReturnLine>, productId: Any?): List<ReturnLine> =
    if (productId !is String || productId.isEmpty()) emptyList() else lines.filter { it.productId == productId }
  private fun fits(lines: List<ReturnLine>, need: Double): Boolean = lines.any { it.qty.isFinite() && it.qty >= need }

  /** کوچک‌ترین قلمِ آزادی که `need` در آن جا می‌شود (اولی در برابری) — همان `bestFit`ِ JS */
  private fun bestFit(lines: List<ReturnLine>, need: Double, used: Set<String>): ReturnLine? {
    var best: ReturnLine? = null
    for (l in lines) {
      if (l.id in used || !l.qty.isFinite() || l.qty < need) continue
      if (best == null || l.qty < best.qty) best = l
    }
    return best
  }

  private fun checkAdjustment(row: Map<String, Any?>, refs: RowRefs, used: MutableSet<String>): RowCheck {
    if (row["isAdjustment"] != true) return no("adjustment")
    for (k in listOf("price", "cartons", "perCarton")) {
      val m = row[k]
      if (m != null && !(m is Number && m.toDouble() == 0.0)) return no("price")
    }
    val kind = row["adjKind"] as? String
    if (kind == null || kind !in RETURN_ADJ_KINDS) return no("kind")
    val units = num(row["units"])
    if (!signOk(kind, units)) return no("sign")
    val ref = row["refId"] as? String
    if (ref.isNullOrEmpty()) return no("ref")
    var lines = linesOf(refs, ref)
    if (lines.isEmpty()) return no("ref")
    lines = lines.filter { it.own }
    if (lines.isEmpty()) return no("owner")
    lines = ofProduct(lines, row["productId"])
    if (lines.isEmpty()) return no("product")
    val need = kotlin.math.abs(units!!)
    if (!fits(lines, need)) return no("qty")
    val line = bestFit(lines, need, used) ?: return no("used")
    used += line.id
    return YES
  }

  /** ریزِ گرد کردنِ جمعِ اعشاری — همان `EPS` ِ JS */
  private const val EPS = 1e-9

  private class Slot(val key: String, val qty: Double?)

  /** قلم‌هایی که حرکتِ این نوع با این نشانی و این کالا از آن‌ها برمی‌دارد — همان `moveLines` ِ JS */
  private fun moveLines(refs: RowRefs, type: String, ref: String, productId: Any?): List<Slot> {
    if (productId !is String || productId.isEmpty()) return emptyList()
    if (DOC_MOVE_TYPES[type] == "sale") {
      return refs.saleItems.filter { it.id.isNotEmpty() && it.saleId == ref && it.productId == productId }
        .map { Slot("$type\u0000s\u0000${it.id}", it.qty?.takeIf { q -> q.isFinite() }) }
    }
    return ofProduct(linesOf(refs, ref), productId)
      .map { Slot("$type\u0000r\u0000${it.id}", it.qty.takeIf { q -> q.isFinite() }) }
  }

  private fun room(lines: List<Slot>, spent: Map<String, Double>): Double {
    var free = 0.0
    for (l in lines) {
      val q = l.qty
      if (q != null && q > 0) free += maxOf(0.0, q - (spent[l.key] ?: 0.0))
    }
    return free
  }

  private fun spend(lines: List<Slot>, need0: Double, spent: MutableMap<String, Double>) {
    var need = need0
    for (l in lines) {
      if (need <= EPS) break
      val q = l.qty
      if (q == null || q <= 0) continue
      val take = minOf(need, maxOf(0.0, q - (spent[l.key] ?: 0.0)))
      spent[l.key] = (spent[l.key] ?: 0.0) + take
      need -= take
    }
  }

  /** آنچه حرکت‌های ثبت‌شده (`moved`) از قلم‌ها برداشته‌اند — همان `spentLines` ِ JS */
  private fun spentLines(refs: RowRefs): MutableMap<String, Double> {
    val spent = HashMap<String, Double>()
    for (m in refs.moved) {
      if (m.type !in DOC_MOVE_TYPES || m.refId.isEmpty()) continue
      val q = m.qty?.takeIf { it.isFinite() } ?: continue
      spend(moveLines(refs, m.type, m.refId, m.productId), kotlin.math.abs(q), spent)
    }
    return spent
  }

  private fun checkMovement(row: Map<String, Any?>, refs: RowRefs, spent: MutableMap<String, Double>): RowCheck {
    val type = row["type"] as? String
    val doc = type?.let { DOC_MOVE_TYPES[it] } ?: return no("kind")
    val qty = num(row["qty"])
    if (!signOk(type, qty)) return no("sign")
    val ref = row["refId"] as? String
    if (ref.isNullOrEmpty()) return no("ref")
    if (doc == "sale") {
      if (ref !in refs.sales) return no("ref")
      if (type == "sale_cancel" && ref !in refs.cancelled) return no("cancel")
    } else if (linesOf(refs, ref).isEmpty()) return no("ref")
    val lines = moveLines(refs, type, ref, row["productId"])
    if (lines.isEmpty()) return no("product")
    val need = kotlin.math.abs(qty!!)
    if (need > room(lines, spent) + EPS) return no("qty")
    spend(lines, need, spent)
    return YES
  }

  /** قلم‌هایی که اصلاحِ ثبت‌شده دارند — همان `usedLines`ِ JS */
  private fun usedLines(refs: RowRefs): MutableSet<String> {
    val used = HashSet<String>()
    for (t in refs.taken) {
      if (t.refId.isEmpty()) continue
      val lines = ofProduct(linesOf(refs, t.refId), t.productId)
      val u = t.units?.takeIf { it.isFinite() }
      val line = bestFit(lines, if (u == null) 0.0 else kotlin.math.abs(u), used) ?: bestFit(lines, 0.0, used)
      if (line != null) used += line.id
    }
    return used
  }

  /**
   *  داوریِ ردیف‌های یک دسته، به ترتیب — همان `checkRows`ِ JS (و همان دلیل‌ها:
   *  table · not_insert · adjustment · price · kind · sign · ref · cancel · owner · product · qty · used · rule).
   */
  fun checkRows(role: String, rows: List<RowIn>, refs: RowRefs): List<RowCheck> {
    val used = usedLines(refs)
    val spent = spentLines(refs)
    return rows.map { r ->
      val rule = rowRule(role, r.table)
      val row = r.row
      when {
        !canWrite(role, r.table) -> no("table")
        rule == null -> YES
        row == null -> no("not_insert")
        rule == "return_adjustment" -> checkAdjustment(row, refs, used)
        rule == "doc_movement" -> checkMovement(row, refs, spent)
        else -> no("rule")
      }
    }
  }

  /** این نقش همین یک ردیف را می‌نویسد؟ — `checkRows` با یک ردیف */
  fun canWriteRow(role: String, table: String, row: Map<String, Any?>?, refs: RowRefs): Boolean =
    checkRows(role, listOf(RowIn(table, row)), refs)[0].ok

  /** «*»، فهرستِ مرتب، یا null برای نقشِ ناشناخته — همان `writable`ِ JS */
  fun writable(role: String): Any? {
    val w = WRITES[role] ?: return null
    return if (w == listOf(ALL_TABLES)) ALL_TABLES else w.sorted()
  }

  private fun isCapValue(v: Any?): Boolean =
    v is Number && v.toDouble().let { it.isFinite() && it >= 0.0 }

  fun keys(): List<String> = CATALOG.keys.sorted()

  /** حالتِ پیش‌فرضِ یک نقش — یا null برای نقش/مجوزِ ناشناخته */
  fun defaultMode(role: String, key: String): String? = CATALOG[key]?.defaults?.get(role)?.takeIf { it in MODES }

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
