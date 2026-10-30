package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncOp

/**
 *  مشتریِ تکراری — پیدا کردن و ادغام با حفظِ همهٔ سوابق (CRM-10).
 *
 *  ── ادغام چه می‌کند ───────────────────────────────────────────────────
 *  ۱) همهٔ تراکنش‌های قرض و همهٔ فاکتورهای «تکراری» به «ماندگار» منتقل می‌شوند
 *     (فقط `debtorId`؛ مبلغ، تاریخ، شرح و قلم‌ها دست نمی‌خورند). پس مانده، صورت‌حساب،
 *     دفترِ قرض، سنِ بدهی و فاکتورها همه یک‌جا و با همان تاریخ‌ها دیده می‌شوند و هیچ
 *     عددی از نو ساخته نمی‌شود: ماندهٔ ماندگار = جمعِ دو ماندهٔ پیشین.
 *  ۲) شماره، یادداشت، برچسب، گروه، لیستِ قیمت و سقفِ «تکراری» جاهای خالیِ ماندگار را
 *     پر می‌کنند؛ چیزی از ماندگار پاک نمی‌شود. شماره‌ای که جا نشد به یادداشت می‌رود.
 *     رضایتِ تبلیغ: سخت‌گیرانه‌تر برنده است — «نه» ِ صریح از هر کدام ⇒ نه؛ «بله» فقط وقتی
 *     هر دو «بله» گفته‌اند؛ وگرنه «نپرسیده» (یعنی نه). ادغام هرگز رضایت نمی‌سازد.
 *  ۳) ردیفِ «تکراری» **پاک نمی‌شود**: نام، شماره و شناسه‌اش می‌ماند با
 *     `mergedInto = ماندگار`. فاکتورِ چاپ‌شده با آن شناسه هنوز پیدا می‌شود.
 *  ۴) یک ردِ `customer_merge` در دفترچهٔ ثبت: `refId` = «تکراری»، شمارِ ردیف‌ها و ماندهٔ
 *     منتقل‌شده در شرح، و شناسهٔ تک‌تکِ ردیف‌های منتقل‌شده در `refIds` (برای برگرداندن).
 *
 *  ── دستگاهِ آفلاین ────────────────────────────────────────────────────
 *  دستگاهی که پیش از رسیدنِ ادغام روی «تکراری» فروخت، ردیفش با شناسهٔ کهنه می‌رسد.
 *  `sweep` آن‌ها را به ماندگار می‌برد (بی‌خطر و تکرارپذیر). دو ادغامِ وارونه روی دو
 *  دستگاه (X در Y و Y در X) حلقه می‌سازند: `sweep` کوچک‌ترین شناسه را دوباره فعال می‌کند.
 */
object CustomerMerge {

  /**
   *  حداکثر شناسهٔ ردیف در `refIds` ِ یک رد: هر شناسه با نامِ جدول حدودِ ۵۰ بایت است، پس
   *  ۴۰۰ تا حدودِ ۲۰KB — زیرِ سقفِ ۶۴KB ِ فیلدهای هر op در Sync v1. بیشتر ⇒ ردِ بعدی.
   */
  const val REF_CHUNK = 400

  /** ردِ `customer_merge` — اگر ردیف‌ها بیش از `REF_CHUNK` باشند، چند رد با همان `refId` */
  private fun trail(refId: String, notes: String, rows: List<String>, today: String, now: Long, newId: () -> String): List<AuditEntry> {
    val parts = rows.chunked(REF_CHUNK).ifEmpty { listOf(emptyList()) }
    return parts.mapIndexed { i, part ->
      AuditEntry(
        id = newId(), type = "customer_merge", date = today, refId = refId, createdAt = now,
        notes = if (i == 0) notes else "$notes (ادامهٔ فهرستِ ردیف‌ها، ${i + 1} از ${parts.size})",
        refIds = part.ifEmpty { null },
      )
    }
  }

  /* ------------------------------ پیدا کردن ------------------------------ */

  enum class Reason(val label: String) { PHONE("شمارهٔ یکسان"), NAME("نامِ یکسان") }

  data class Group(
    /** پیشنهادِ ماندگار اول، بقیه به ترتیبِ ثبت */
    val members: List<Debtor>,
    val reasons: Set<Reason>,
  ) {
    /** شمارهٔ یکسان قوی است؛ فقط نامِ یکسان ممکن است دو آدمِ جدا باشد */
    val strong: Boolean get() = Reason.PHONE in reasons
    val keep: Debtor get() = members.first()
  }

  private fun activity(d: ShopData, id: String): Int =
    d.transactions.count { it.debtorId == id } + d.sales.count { it.debtorId == id }

  /**
   *  گروه‌های تکراری میانِ مشتریانِ فعال: شمارهٔ یکسان (هر کدام از دو شماره، با
   *  `CustomerCrm.phoneKey`) یا نامِ یکسان (`TextNormalizer.compareKey`). اول قوی‌ها.
   *  پیشنهادِ ماندگار: پرکارترین، سپس قدیمی‌ترین.
   */
  fun duplicates(d: ShopData): List<Group> {
    val list = CustomerCrm.active(d)
    if (list.size < 2) return emptyList()
    val parent = IntArray(list.size) { it }
    fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; var c = x; while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }; return r }
    fun union(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb) }

    val pairs = HashMap<Pair<Int, Int>, MutableSet<Reason>>()
    fun link(keyOf: (Debtor) -> List<String>, reason: Reason) {
      val first = HashMap<String, Int>()
      list.forEachIndexed { i, c ->
        for (k in keyOf(c).distinct()) {
          val j = first.putIfAbsent(k, i) ?: continue
          union(j, i)
          pairs.getOrPut(minOf(i, j) to maxOf(i, j)) { HashSet() } += reason
        }
      }
    }
    link({ c -> CustomerCrm.phones(c).mapNotNull(CustomerCrm::phoneKey) }, Reason.PHONE)
    link({ c -> listOfNotNull(TextNormalizer.compareKey(c.name).takeIf { it.isNotEmpty() }) }, Reason.NAME)

    val byRoot = list.indices.groupBy(::find).filterValues { it.size > 1 }
    val score = list.associate { it.id to activity(d, it.id) }
    return byRoot.values.map { idx ->
      val reasons = pairs.filterKeys { (a, b) -> a in idx && b in idx }.values.flatten().toSet()
      val members = idx.map { list[it] }
        .sortedWith(compareByDescending<Debtor> { score[it.id] ?: 0 }.thenBy { it.createdAt }.thenBy { it.id })
      Group(members, reasons)
    }.sortedWith(compareByDescending<Group> { it.strong }.thenBy { TextNormalizer.compareKey(it.keep.name) }.thenBy { it.keep.id })
  }

  /* -------------------------------- ادغام -------------------------------- */

  data class Preview(
    val keep: Debtor,
    val drop: Debtor,
    val transactions: Int,
    val sales: Int,
    /** ماندهٔ «تکراری» که به ماندگار می‌آید */
    val dropBalance: Double,
    val balanceAfter: Double,
    /** پرونده‌ای که ماندگار پس از ادغام خواهد داشت */
    val result: Debtor,
  )

  sealed interface Result {
    data class Ok(val data: ShopData, val preview: Preview) : Result
    data class Failed(val message: String) : Result
  }

  private sealed interface Check {
    data class Bad(val message: String) : Check
    data class Good(val keep: Debtor, val drop: Debtor) : Check
  }

  private fun check(d: ShopData, keepId: String, dropId: String): Check {
    if (keepId == dropId) return Check.Bad("یک مشتری با خودش ادغام نمی‌شود")
    val keep = d.debtors.find { it.id == keepId } ?: return Check.Bad("مشتریِ ماندگار پیدا نشد")
    val drop = d.debtors.find { it.id == dropId } ?: return Check.Bad("مشتریِ تکراری پیدا نشد")
    if (!CustomerCrm.isActive(keep)) return Check.Bad("«${keep.name}» خودش پیش‌تر ادغام شده است")
    if (!CustomerCrm.isActive(drop)) return Check.Bad("«${drop.name}» پیش‌تر ادغام شده است")
    return Check.Good(keep, drop)
  }

  /** پروندهٔ ماندگار پس از ادغام — فقط جاهای خالی پر می‌شوند */
  private fun fold(keep: Debtor, drop: Debtor): Debtor {
    val phones = mutableListOf(keep.phone.takeIf { it.isNotBlank() }, keep.phone2?.takeIf { it.isNotBlank() })
    val keys = phones.mapNotNull { it?.let(CustomerCrm::phoneKey) ?: it }.toMutableSet()
    val leftover = mutableListOf<String>()
    for (p in CustomerCrm.phones(drop)) {
      val k = CustomerCrm.phoneKey(p) ?: p
      if (!keys.add(k)) continue
      val slot = phones.indexOfFirst { it == null }
      if (slot >= 0) phones[slot] = p else leftover += p
    }
    val extra = buildList {
      drop.notes.trim().takeIf { it.isNotEmpty() && !keep.notes.contains(it) }?.let { add(it) }
      if (leftover.isNotEmpty()) add("تلفنِ دیگر: " + leftover.joinToString("، "))
    }
    val notes = (listOf(keep.notes.trim()).filter { it.isNotEmpty() } + extra).joinToString("\n")
    val tags = CustomerCrm.normalizeTags(keep.tags.orEmpty() + drop.tags.orEmpty()).ifEmpty { null }
    val consent = when {
      keep.marketingConsent == false || drop.marketingConsent == false -> false
      keep.marketingConsent == true && drop.marketingConsent == true -> true
      else -> null
    }
    return keep.copy(
      phone = phones[0].orEmpty(),
      phone2 = phones[1],
      notes = notes,
      tags = tags,
      group = keep.group ?: drop.group,
      priceList = keep.priceList ?: drop.priceList,
      creditLimit = keep.creditLimit ?: drop.creditLimit,
      marketingConsent = consent,
    )
  }

  /** پیش‌نمایشِ ادغام — برای پنجرهٔ تأیید؛ چیزی را عوض نمی‌کند */
  fun preview(d: ShopData, keepId: String, dropId: String): Result {
    val (keep, drop) = when (val c = check(d, keepId, dropId)) {
      is Check.Bad -> return Result.Failed(c.message)
      is Check.Good -> c.keep to c.drop
    }
    val dropBalance = ShopStore.debt(d, drop.id)
    return Result.Ok(
      d,
      Preview(
        keep = keep, drop = drop,
        transactions = d.transactions.count { it.debtorId == drop.id },
        sales = d.sales.count { it.debtorId == drop.id },
        dropBalance = dropBalance,
        balanceAfter = ShopStore.debt(d, keep.id) + dropBalance,
        result = fold(keep, drop),
      ),
    )
  }

  /** ادغامِ «تکراری» در «ماندگار» — همه یا هیچ */
  fun merge(
    d: ShopData,
    keepId: String,
    dropId: String,
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): Result {
    val p = when (val r = preview(d, keepId, dropId)) {
      is Result.Failed -> return r
      is Result.Ok -> r.preview
    }
    val rows = d.transactions.filter { it.debtorId == dropId }.map { "transactions/${it.id}" } +
      d.sales.filter { it.debtorId == dropId }.map { "sales/${it.id}" }
    //  ادغامی که رضایتِ ماندگار را عوض کرد (فقط سخت‌گیرانه‌تر) همین‌جا سند دارد
    val consent = if (p.result.marketingConsent == p.keep.marketingConsent) "" else
      " — رضایتِ تبلیغ: ${CustomerCrm.consentText(p.keep.marketingConsent)} ⇐ ${CustomerCrm.consentText(p.result.marketingConsent)}"
    val trace = trail(
      dropId,
      "ادغامِ «${p.drop.name}» (${p.drop.id}) در «${p.keep.name}» (${p.keep.id}): ${p.transactions} تراکنش و ${p.sales} فاکتور منتقل شد" +
        " — ماندهٔ منتقل‌شده ${money(p.dropBalance)} افغانی" + consent,
      rows, today, now, newId,
    )
    val data = d.copy(
      debtors = d.debtors.map {
        when {
          it.id == keepId -> p.result
          it.id == dropId -> it.copy(mergedInto = keepId)
          //  زنجیره صاف: آنچه پیش‌تر در «تکراری» ادغام شده بود حالا مستقیم به ماندگار اشاره می‌کند
          it.mergedInto == dropId -> it.copy(mergedInto = keepId)
          else -> it
        }
      },
      transactions = d.transactions.map { if (it.debtorId == dropId) it.copy(debtorId = keepId) else it },
      sales = d.sales.map { if (it.debtorId == dropId) it.copy(debtorId = keepId) else it },
      auditLog = d.auditLog + trace,
    )
    return Result.Ok(data, p)
  }

  /**
   *  ردیف‌های دیررسیده (تراکنش یا فاکتوری که هنوز به مشتریِ ادغام‌شده اشاره می‌کند،
   *  مثلاً از دستگاهِ آفلاین) ⇐ ماندگار. `null` یعنی کاری نبود.
   *
   *  حلقهٔ `mergedInto` (دو ادغامِ وارونه روی دو دستگاه) ترمیم می‌شود: کوچک‌ترین شناسهٔ
   *  حلقه (`CustomerCrm.resolve`) دوباره فعال و بقیه مستقیم به آن؛ همهٔ دستگاه‌ها همین را
   *  می‌سازند. مقصدِ ناپیدا (ردیفِ ماندگار هنوز نرسیده) دست نمی‌خورد.
   *  برای هر ماندگار یک ردِ `customer_merge` با `refId` = همان ماندگار و ردیف‌ها در `refIds`.
   */
  fun sweep(
    d: ShopData,
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): ShopData? {
    val merged = d.debtors.filter { !CustomerCrm.isActive(it) }
    if (merged.isEmpty()) return null
    val byId = d.debtors.associateBy { it.id }
    val target = merged.associate { it.id to CustomerCrm.resolve(byId, it.id) }
    //  سرِ حلقه: ادغام‌شده‌ای که زنجیره‌اش به خودش برمی‌گردد
    val revive = target.values.toSortedSet().filter { r ->
      byId[r]?.mergedInto?.takeIf { it.isNotBlank() }?.let { CustomerCrm.resolve(byId, it) } == r
    }.toSet()
    val debtors = d.debtors.map { c ->
      val to = target[c.id]
      when {
        c.id in revive -> c.copy(mergedInto = null)
        to != null && to in revive && c.mergedInto != to -> c.copy(mergedInto = to)
        else -> c
      }
    }
    val moves = target.filter { (a, b) -> a != b }
    val moved = sortedMapOf<String, MutableList<String>>()
    val transactions = d.transactions.map { t ->
      moves[t.debtorId]?.let { to -> moved.getOrPut(to) { mutableListOf() } += "transactions/${t.id}"; t.copy(debtorId = to) } ?: t
    }
    val salesOut = d.sales.map { s ->
      s.debtorId?.let(moves::get)?.let { to -> moved.getOrPut(to) { mutableListOf() } += "sales/${s.id}"; s.copy(debtorId = to) } ?: s
    }
    if (revive.isEmpty() && moved.isEmpty()) return null
    val trace = (revive + moved.keys).toSortedSet().flatMap { to ->
      val rows = moved[to].orEmpty()
      val tx = rows.count { it.startsWith("transactions/") }
      val head = if (to in revive) "حلقهٔ ادغام ترمیم شد: «${byId.getValue(to).name}» ($to) دوباره فعال است — " else ""
      trail(to, head + "انتقالِ ردیف‌های دیررسیده به مشتریِ ماندگار: $tx تراکنش و ${rows.size - tx} فاکتور", rows, today, now, newId)
    }
    return d.copy(debtors = debtors, transactions = transactions, sales = salesOut, auditLog = d.auditLog + trace)
  }

  /**
   *  opهای هدفمندِ همان [sweep] — نه تفاضلِ کلِ دفتر با سایه: فقط UPDATE ِ `debtorId` ِ
   *  تراکنش و فاکتورِ جابه‌جاشده، UPDATE ِ `mergedInto` ِ مشتری و INSERT ِ ردِ دفترچه. از
   *  [before] (دفترِ همین لحظهٔ دیسک) و [after] = `sweep(before)` ساخته می‌شوند، پس دفترِ
   *  کهنهٔ نمونهٔ دیگری از `ShopStore` هرگز DELETE نمی‌سازد.
   *  `null` ⇒ چیزی بیرون از این شکل دیده شد (نباید پیش بیاید) و sweep نباید بنشیند.
   */
  fun sweepOps(before: ShopData, after: ShopData, now: Long = System.currentTimeMillis()): List<SyncOp>? {
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(before), LedgerDiff.toJson(after), now)
    val ok = ops.all { op ->
      val keys = op.fields?.keys.orEmpty()
      when (op.table) {
        "transactions", "sales" -> op.type == SyncOp.UPDATE && keys == setOf("debtorId")
        "debtors" -> op.type == SyncOp.UPDATE && keys == setOf("mergedInto")
        "auditLog" -> op.type == SyncOp.INSERT
        else -> false
      }
    }
    return if (ok) ops else null
  }

  /* --------------------------------- برگه --------------------------------- */

  fun duplicatesSheet(d: ShopData, today: String): ReportCsv.Sheet {
    val rows = mutableListOf(listOf("شمارهٔ گروه", "نقش", "نام", "شماره تماس", "شمارهٔ دوم", "مانده", "تعداد تراکنش", "تعداد فاکتور", "دلیل"))
    duplicates(d).forEachIndexed { g, grp ->
      val why = grp.reasons.sortedBy { it.ordinal }.joinToString("، ") { it.label }
      grp.members.forEachIndexed { i, c ->
        rows += listOf(
          (g + 1).toString(), if (i == 0) "پیشنهادِ ماندگار" else "تکراری", c.name, c.phone, c.phone2.orEmpty(),
          Math.round(ShopStore.debt(d, c.id)).toString(),
          d.transactions.count { it.debtorId == c.id }.toString(), d.sales.count { it.debtorId == c.id }.toString(), why,
        )
      }
    }
    return ReportCsv.Sheet("مشتریان-تکراری-$today", rows)
  }
}
