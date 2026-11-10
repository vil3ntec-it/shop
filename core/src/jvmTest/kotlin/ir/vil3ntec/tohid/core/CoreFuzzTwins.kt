package ir.vil3ntec.tohid.core

import ir.vil3ntec.tohid.core.audit.ChainEntry
import ir.vil3ntec.tohid.core.audit.HashChain
import ir.vil3ntec.tohid.core.audit.HashChainException
import ir.vil3ntec.tohid.core.audit.PostedDocument
import ir.vil3ntec.tohid.core.audit.ReversalRequest
import ir.vil3ntec.tohid.core.pay.CloseShiftInput
import ir.vil3ntec.tohid.core.pay.ExchangeInput
import ir.vil3ntec.tohid.core.pay.OpenShiftInput
import ir.vil3ntec.tohid.core.pay.PaidRow
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayFix
import ir.vil3ntec.tohid.core.pay.PayMethod
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.RefundInput
import ir.vil3ntec.tohid.core.pay.SettleInput
import ir.vil3ntec.tohid.core.pay.Shift
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.pay.ShiftEventInput
import ir.vil3ntec.tohid.core.pay.ShiftReport
import ir.vil3ntec.tohid.core.pay.ShiftRow
import ir.vil3ntec.tohid.core.pay.Tender
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.pick.PickCore
import ir.vil3ntec.tohid.core.pick.PickSubstitute
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 *  فازِ تفاضلی، دورِ دوم — دوندهٔ Kotlin ِ هم‌زادهای `core/` که دورِ اول نداشت: برگهٔ تأیید، مجوز، زنجیرهٔ هش،
 *  پرداخت، جمع‌آوری و سفارشِ خرید (مولّد: `server/scripts/core-fuzz-gen2.js`). `CoreFuzzTest` همان پیکره را
 *  می‌خواند و هر موردِ این خانواده‌ها را به این‌جا می‌دهد؛ خروجی همان شکلِ JSON ِ دوندهٔ JS است.
 *  ورودیِ Kotlin نوع‌دار است (Long، String)؛ مولّد برای این هسته‌ها فقط همان نوع‌ها را می‌سازد، جز برگهٔ تأیید
 *  که عمداً نوعِ نادرست هم دارد: برگه‌ای که به `Slip` درنمی‌آید [UNPARSED] است و فقط وقتی درست است که JS هم ردش کند.
 */
internal object CoreFuzzTwins {

  val FAMILIES = setOf("approval", "perm", "chain", "pay", "pick", "po")

  val UNPARSED = JsonObject(mapOf("unparsed" to JsonPrimitive(true)))

  /** برگهٔ نوع‌نادرستی که Kotlin نمی‌سازد، ولی JS هم با کدِ خطا رد کرده (کدِ خطا یکی نیست) */
  fun bothRefuse(fn: String, want: JsonElement, got: JsonElement): Boolean =
    fn == "approval.check" && got == UNPARSED && ((want as? JsonObject)?.get("check") as? JsonPrimitive)?.isString == true

  fun run(fn: String, x: JsonObject): JsonElement = when (fn.substringBefore('.')) {
    "approval" -> approval(fn, x)
    "perm" -> perm(fn, x)
    "chain" -> chain(fn, x)
    "pay" -> pay(fn, x)
    "pick" -> pick(fn, x)
    "po" -> po(fn, x)
    else -> error("خانوادهٔ ناشناخته: $fn")
  }

  // ── برگهٔ تأیید ─────────────────────────────────────

  private fun approval(fn: String, x: JsonObject): JsonElement {
    val slip = (plain(x["slip"]) as? Map<*, *>)?.let { ApprovalSlip.fromMap(it) } ?: return UNPARSED
    return when (fn) {
      "approval.check" -> {
        val c = ApprovalSlip.check(slip)
        obj(
          "check" to sn(c),
          "payload" to if (c == null) s(ApprovalSlip.payload(slip)) else JsonNull,
          "evalReq" to if (c == null) JsonObject(ApprovalSlip.evalReq(slip).mapValues { JsonPrimitive(it.value) }) else JsonNull,
        )
      }
      "approval.covers" -> {
        val need = x.getValue("need").jsonObject
        sn(ApprovalSlip.covers(slip, ApprovalSlip.Need(need.str("perm") ?: "", need.str("doc") ?: "", need.lOrNull("pct_bp"), need.lOrNull("amount"), need.lOrNull("at"))))
      }
      else -> error("تابعِ ناشناخته: $fn")
    }
  }

  // ── مجوز ─────────────────────────────────────────────

  private fun resolved(r: PermCore.Resolved) = obj("mode" to s(r.mode), "caps" to JsonObject(r.caps.mapValues { JsonPrimitive(it.value) }))

  private fun dbl(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.doubleOrNull

  @Suppress("UNCHECKED_CAST")
  private fun perm(fn: String, x: JsonObject): JsonElement {
    val role = x.str("role") ?: ""
    val layers = plain(x["layers"]) as? List<Any?>
    return when (fn) {
      "perm.resolve" -> {
        val key = x.str("key")!!
        val req = (x["req"] as? JsonObject)?.entries?.mapNotNull { (k, v) -> dbl(v)?.let { k to it } }?.toMap()
        val d = PermCore.evaluate(role, layers, key, req)
        val dj = linkedMapOf<String, JsonElement>("decision" to s(d.decision), "reason" to s(d.reason))
        d.cap?.let { dj["cap"] = s(it) }
        d.limit?.let { dj["limit"] = JsonPrimitive(it) }
        obj("resolve" to resolved(PermCore.resolve(role, layers, key)), "evaluate" to JsonObject(dj), "allows" to JsonPrimitive(PermCore.allows(role, layers, key)))
      }
      "perm.effective" -> JsonObject(PermCore.effective(role, layers).mapValues { resolved(it.value) })
      "perm.validate" -> sn(PermCore.validate(role, x.str("key")!!, plain(x["o"])))
      "perm.discountBp" -> obj("bp" to n(PermCore.discountBp(x.l("d"), x.l("t"))), "pct" to JsonPrimitive(PermCore.discountPct(x.l("d"), x.l("t"))))
      "perm.rows" -> {
        val f = x.getValue("refs").jsonObject
        val refs = PermCore.RowRefs(
          returns = f.objs("returns").map {
            PermCore.ReturnLine(it.str("id") ?: "", it.str("docId"), it.str("productId") ?: "", dbl(it["qty"]) ?: Double.NaN, it.bool("own") == true)
          },
          sales = f.strs("sales"),
          taken = f.objs("taken").map { PermCore.Taken(it.str("refId") ?: "", it.str("productId") ?: "", dbl(it["units"])) },
          saleItems = f.objs("saleItems").map { PermCore.SaleLine(it.str("id") ?: "", it.str("saleId") ?: "", it.str("productId") ?: "", dbl(it["qty"])) },
          cancelled = f.strs("cancelled"),
          moved = f.objs("moved").map { PermCore.Moved(it.str("refId") ?: "", it.str("productId") ?: "", it.str("type") ?: "", dbl(it["qty"])) },
        )
        val rows = x.getValue("rows").jsonArray.map { e ->
          val o = e as? JsonObject
          PermCore.RowIn(o?.str("table") ?: "", plain(o?.get("row")) as? Map<String, Any?>)
        }
        JsonArray(PermCore.checkRows(role, rows, refs).map { obj("ok" to JsonPrimitive(it.ok), "reason" to s(it.reason)) })
      }
      "perm.write" -> {
        val table = x.str("table") ?: ""
        obj(
          "canWrite" to JsonPrimitive(PermCore.canWrite(role, table)),
          "writable" to when (val w = PermCore.writable(role)) {
            null -> JsonNull
            is String -> s(w)
            is List<*> -> JsonArray(w.map { s(it as String) })
            else -> error("writable: $w")
          },
          "rowRule" to sn(PermCore.rowRule(role, table)),
        )
      }
      else -> error("تابعِ ناشناخته: $fn")
    }
  }

  // ── زنجیرهٔ هش ───────────────────────────────────────

  private fun <T> attempt(f: () -> T): Pair<T?, String?> = try { f() to null } catch (e: HashChainException) { null to e.code }

  @Suppress("UNCHECKED_CAST")
  private fun chain(fn: String, x: JsonObject): JsonElement = when (fn) {
    "chain.canonical" -> {
      val v = plain(x["v"])
      val (json, err) = attempt { HashChain.canonicalJson(v) }
      val (hash, hashErr) = attempt { HashChain.entryHash(x.str("prev")!!, v) }
      obj("json" to sn(json), "error" to sn(err), "hash" to sn(hash), "hashError" to sn(hashErr))
    }
    "chain.verify" -> {
      val entries = x.getValue("entries").jsonArray.map { e ->
        val o = e as? JsonObject
        ChainEntry((o?.get("seq") as? JsonPrimitive)?.longOrNull, o?.str("prevHash") ?: "", o?.str("hash") ?: "", plain(o?.get("doc")))
      }
      val v = HashChain.verify(entries)
      if (v.ok) obj("ok" to JsonPrimitive(true), "length" to n(v.length.toLong()), "head" to s(v.head))
      else obj("ok" to JsonPrimitive(false), "index" to n(v.index.toLong()), "reason" to sn(v.reason))
    }
    "chain.reverse" -> {
      val o = x.getValue("opts").jsonObject
      val req = ReversalRequest(o.str("id")!!, o.l("no"), o.l("postedAt"), o.str("reason")!!, o.str("by")!!)
      val existing = x.getValue("existing").jsonArray.map { plain(it) as Map<String, Any?> }
      val (doc, err) = attempt { PostedDocument.reverse(plain(x["orig"]) as Map<String, Any?>, req, existing) }
      obj("doc" to (doc?.let(::j) ?: JsonNull), "error" to sn(err))
    }
    "chain.guard" -> try {
      val g = PostedDocument.guardEdit(plain(x["before"]) as? Map<String, Any?>, plain(x["after"]) as Map<String, Any?>)
      if (g.code == null) obj("ok" to JsonPrimitive(g.ok)) else obj("ok" to JsonPrimitive(g.ok), "code" to s(g.code!!))
    } catch (e: HashChainException) {
      obj("error" to s(e.code))
    }
    else -> error("تابعِ ناشناخته: $fn")
  }

  // ── پرداخت (همان نگاشتِ `PayCoreTest`) ─────────────────

  private fun methods(o: JsonObject): List<PayMethod>? = (o["methods"] as? JsonArray)?.map {
    val m = it.jsonObject
    PayMethod(m.str("id") ?: "", m.str("kind") ?: "", m.str("name") ?: "", m.bool("needsRef") ?: false, m.lOrNull("limit"), m.bool("enabled") ?: true)
  }

  private fun <T> res(r: PayResult<T>, ok: (T) -> Map<String, JsonElement>): JsonElement = when (r) {
    is PayResult.Ok -> JsonObject(
      linkedMapOf<String, JsonElement>("ok" to JsonPrimitive(true)) + (if (r.duplicate) mapOf("duplicate" to JsonPrimitive(true)) else emptyMap()) + ok(r.value),
    )
    is PayResult.Err -> JsonObject(
      linkedMapOf<String, JsonElement>("ok" to JsonPrimitive(false), "error" to s(r.code)).apply {
        r.index?.let { put("index", n(it.toLong())) }
        r.field?.let { put("field", s(it)) }
        r.method?.let { put("method", s(it)) }
        r.denomination?.let { put("denomination", s(it)) }
        r.refundable?.let { put("refundable", n(it)) }
        r.variance?.let { put("variance", JsonObject(it.mapValues { (_, v) -> nn(v) })) }
      },
    )
  }

  private fun fixOf(o: JsonObject) = PayFix.Fix(
    o.str("id") ?: "", o.str("saleId") ?: "", o.str("from") ?: "", o.str("to") ?: "", o.l("amount"), o.l("at"),
    o.str("by") ?: "", o.str("approvedBy") ?: "", o.str("reason") ?: "", o.str("ref"),
  )

  private fun fixJson(f: PayFix.Fix) = obj(
    "id" to s(f.id), "saleId" to s(f.saleId), "from" to s(f.from), "to" to s(f.to), "amount" to n(f.amount), "at" to n(f.at),
    "by" to s(f.by), "approvedBy" to s(f.approvedBy), "reason" to s(f.reason), "ref" to sn(f.ref),
  )

  /** همان `reportJson` ِ `PayCoreTest` */
  private fun reportJson(r: ShiftReport): JsonElement {
    val m = linkedMapOf<String, JsonElement>(
      "kind" to s(r.kind), "shiftId" to s(r.shiftId), "currency" to s(r.currency), "at" to n(r.at), "by" to sn(r.by), "openedAt" to n(r.openedAt),
      "openedBy" to sn(r.openedBy), "float" to n(r.float),
      "methods" to JsonObject(r.methods.mapValues { (_, t) -> obj("sales" to n(t.sales), "refunds" to n(t.refunds), "receipts" to n(t.receipts), "net" to n(t.net)) }),
      "moves" to obj("in" to n(r.moves.cashIn), "out" to n(r.moves.cashOut)),
      "events" to obj(
        "sales" to n(r.events.sales.toLong()), "refunds" to n(r.events.refunds.toLong()), "receipts" to n(r.events.receipts.toLong()),
        "cashIn" to n(r.events.cashIn.toLong()), "cashOut" to n(r.events.cashOut.toLong()), "noSale" to n(r.events.noSale.toLong()),
      ),
    )
    r.expected?.let { m["expected"] = JsonObject(it.mapValues { (_, v) -> n(v) }) }
    if (r.kind == "Z") {
      m["counted"] = r.counted?.let { JsonObject(it.mapValues { (_, v) -> n(v) }) } ?: JsonNull
      m["denominations"] = r.denominations?.let { JsonObject(it.mapValues { (_, v) -> n(v) }) } ?: JsonNull
      r.variance?.let { m["variance"] = JsonObject(it.mapValues { (_, v) -> nn(v) }) }
      m["reason"] = sn(r.reason)
      m["needsReview"] = r.needsReview?.let(::JsonPrimitive) ?: JsonNull
      m["blind"] = r.blind?.let(::JsonPrimitive) ?: JsonNull
    }
    return JsonObject(m)
  }

  private fun longs(e: JsonElement?): Map<String, Long> = (e as? JsonObject)?.mapValues { it.value.jsonPrimitive.content.toLong() } ?: emptyMap()

  private fun shift(x: JsonObject): JsonElement {
    var sh: Shift? = null
    fun keep(r: PayResult<Shift>): PayResult<Shift> = r.also { if (it is PayResult.Ok) sh = it.value }
    return JsonArray(
      x.objs("steps").map { st ->
        val o = st["input"] as? JsonObject ?: JsonObject(emptyMap())
        when (st.str("op")) {
          "open" -> res(
            keep(
              ShiftCore.open(
                OpenShiftInput(
                  o.str("id"), o.l("at"), o.l("float"), o.str("by"), o.lOrNull("threshold") ?: Pay.DEFAULT_VARIANCE_THRESHOLD,
                  o.bool("coins") ?: false, methods(o), o.str("currency") ?: "AFN",
                ),
              ),
            ),
          ) { emptyMap() }
          "record" -> res(
            keep(
              ShiftCore.record(
                sh,
                ShiftEventInput(
                  o.str("id"), o.str("type") ?: "", o.l("at"), (o["rows"] as? JsonArray)?.map { it.jsonObject }?.map { ShiftRow(it.str("method") ?: "", it.l("amount")) },
                  o.str("method"), o.lOrNull("amount"), o.str("reason"), o.str("note"), o.str("by"), o.str("approvedBy"), o.str("currency"),
                ),
              ),
            ),
          ) { emptyMap() }
          "x" -> res(ShiftCore.xReport(sh, o.l("at"), o.str("by"))) { mapOf("report" to reportJson(it)) }
          "close" -> res(
            keep(ShiftCore.close(sh, CloseShiftInput(o.str("id"), o.l("at"), longs(o["counts"]), longs(o["methodCounts"]), o.str("by"), o.bool("blind") ?: false, o.str("reason")))),
          ) { mapOf("report" to reportJson(it.z!!)) }
          "z" -> res(ShiftCore.zReport(sh)) { mapOf("report" to reportJson(it)) }
          else -> error("گامِ ناشناخته: $st")
        }
      },
    )
  }

  private fun pay(fn: String, x: JsonObject): JsonElement = when (fn) {
    "pay.shift" -> shift(x)
    "pay.settle" -> {
      val input = SettleInput(
        total = x.l("total"),
        tenders = x.objs("tenders").map { Tender(it.str("method") ?: "", it.l("amount"), it.str("currency"), it.str("ref")) },
        currency = x.str("currency") ?: "AFN",
        cashRounding = x.lOrNull("cashRounding") ?: 0,
        customerId = x.str("customerId"),
        methods = methods(x),
      )
      obj(
        "settle" to res(Pay.settle(input)) { st ->
          mapOf(
            "currency" to s(st.currency), "status" to s(st.status), "total" to n(st.total), "rounding" to n(st.rounding), "due" to n(st.due),
            "tendered" to n(st.tendered), "change" to n(st.change), "remaining" to n(st.remaining), "toAccount" to n(st.toAccount),
            "payments" to JsonArray(
              st.payments.map {
                obj("method" to s(it.method), "kind" to s(it.kind), "amount" to n(it.amount), "ref" to sn(it.ref), "status" to s(it.status), "verification" to sn(it.verification))
              },
            ),
          )
        },
        "exact" to res(Pay.exactCash(input)) { mapOf("amount" to n(it)) },
      )
    }
    "pay.exchange" -> {
      val from = x["from"] as? JsonObject
      res(
        Pay.exchange(
          ExchangeInput(
            fromCurrency = from?.str("currency") ?: "", fromAmount = from?.lOrNull("amount") ?: 0, to = x.str("to") ?: "AFN",
            rate = x.lOrNull("rate") ?: 0, source = x.str("source"), at = x.lOrNull("at"), approvedBy = x.str("approvedBy"),
            per = x.lOrNull("per") ?: 1,
          ),
        ),
      ) { e ->
        mapOf(
          "record" to obj(
            "from" to obj("currency" to s(e.fromCurrency), "amount" to n(e.fromAmount)), "to" to obj("currency" to s(e.toCurrency), "amount" to n(e.toAmount)),
            "rate" to n(e.rate), "per" to n(e.per), "source" to s(e.source), "at" to n(e.at), "approvedBy" to s(e.approvedBy),
          ),
        )
      }
    }
    "pay.refund" -> res(
      Pay.refund(
        RefundInput(
          payments = x.objs("payments").map { PaidRow(it.str("method") ?: "", it.l("amount"), it.lOrNull("refunded") ?: 0, it.str("status") ?: "") },
          amount = x.l("amount"), mode = x.str("mode") ?: "", approvedBy = x.str("approvedBy"), customerId = x.str("customerId"),
          accountOutstanding = x.lOrNull("accountOutstanding"), methods = methods(x), currency = x.str("currency") ?: "AFN",
        ),
      ),
    ) { rf ->
      mapOf(
        "currency" to s(rf.currency), "amount" to n(rf.amount), "approvedBy" to s(rf.approvedBy),
        "payouts" to JsonArray(rf.payouts.map { obj("method" to s(it.method), "kind" to s(it.kind), "amount" to n(it.amount)) }),
        "payments" to JsonArray(rf.payments.map { obj("method" to s(it.method), "amount" to n(it.amount), "refunded" to n(it.refunded), "status" to s(it.status)) }),
      )
    }
    "pay.fix" -> {
      val sale = x.getValue("sale").jsonObject
      val input = PayFix.Input(
        id = x.str("id") ?: "",
        sale = PayFix.SaleInfo(
          sale.str("id") ?: "", sale.objs("rows").map { PayFix.Row(it.str("method") ?: "", it.l("amount")) },
          sale.bool("cancelled") == true, sale.bool("online") == true, sale.bool("returned") == true,
        ),
        from = x.str("from") ?: "", to = x.str("to") ?: "", amount = x.l("amount"), at = x.l("at"), by = x.str("by"),
        decision = x.str("decision") ?: "", approvedBy = x.str("approvedBy"), reason = x.str("reason"), ref = x.str("ref"),
        closed = x.bool("closed") == true, existing = x.objs("existing").map(::fixOf), methods = methods(x),
      )
      when (val r = PayFix.check(input)) {
        is PayResult.Ok -> obj("ok" to JsonPrimitive(true), "duplicate" to JsonPrimitive(r.duplicate), "fix" to fixJson(r.value))  // JS همیشه duplicate دارد
        is PayResult.Err -> res(r) { emptyMap() }
      }
    }
    "pay.fixApply" -> {
      val a = PayFix.apply(
        x.str("saleId") ?: "", x.objs("rows").map { PayFix.Row(it.str("method") ?: "", it.l("amount")) }, x.objs("fixes").map(::fixOf),
        x.lOrNull("until") ?: Long.MAX_VALUE,
      )
      obj(
        "rows" to JsonArray(a.rows.map { obj("method" to s(it.method), "amount" to n(it.amount)) }),
        "applied" to JsonArray(a.applied.map(::s)), "skipped" to JsonArray(a.skipped.map(::s)),
      )
    }
    "pay.fixClosed" -> JsonPrimitive(
      PayFix.closed(x.l("saleAt"), x.str("saleDay"), x.objs("shifts").map { PayFix.Window(it.l("openedAt"), it.lOrNull("closedAt"), it.str("day") ?: "") }),
    )
    else -> error("تابعِ ناشناخته: $fn")
  }

  // ── جمع‌آوری ─────────────────────────────────────────

  private const val MAX_SAFE = 9_007_199_254_740_991L

  private fun item(o: JsonObject) = PickSubstitute.Item(
    o.str("productId") ?: "", o.str("category") ?: "", o.str("subcategory") ?: "", o.str("halal") ?: "", o.str("unit") ?: "",
    o.lOrNull("priceMinor"), o.str("brand") ?: "", o.str("name") ?: "",
  )

  private fun pick(fn: String, x: JsonObject): JsonElement = when (fn) {
    "pick.line" -> n(PickCore.lineTotalMinor(x.l("q"), x.l("p")))
    "pick.order" -> JsonArray(
      PickCore.pickOrder(
        x.objs("lines").map { PickCore.SheetLine(it.l("lineNo").toInt(), it.str("name") ?: "", it.str("aisle") ?: "", it.str("shelf") ?: "", it.str("level") ?: "") },
      ).map { n(it.toLong()) },
    )
    "pick.barcode" -> obj(
      "matches" to JsonPrimitive(PickCore.barcodeMatches(x.strs("codes"), x.str("scanned")!!)), "norm" to s(PickCore.normBarcode(x.str("scanned")!!)),
    )
    "pick.text" -> {
      val t = x.str("s")!!
      obj(
        "unit" to JsonPrimitive(PickCore.isWeighedUnit(t)), "loc" to s(PickCore.locPart(t)), "norm" to s(PickCore.normBarcode(t)),
        "halal" to s(PickSubstitute.halalOf(t)), "category" to s(PickSubstitute.normCategory(t)), "latin" to s(PickCore.latin(t)),
      )
    }
    "pick.weigh" -> s(PickCore.weighVerdict(x.l("ordered"), x.l("actual"), x.lOrNull("bp")?.toInt() ?: PickCore.DEFAULT_TOLERANCE_BP).key)
    "pick.settle" -> {
      val st = PickCore.settle(
        x.objs("lines").map {
          PickCore.SettleLine(it.l("lineNo").toInt(), it.str("status") ?: "", it.l("orderedQtyMilli"), it.l("pickedQtyMilli"), it.l("unitPriceMinor"), it.l("originalTotalMinor"))
        },
        x.lOrNull("discountMinor") ?: 0, x.lOrNull("deliveryFeeMinor") ?: 0,
      )
      obj(
        "lines" to JsonArray(st.lines.map { obj("lineNo" to n(it.lineNo.toLong()), "qtyMilli" to n(it.qtyMilli), "totalMinor" to n(it.totalMinor)) }),
        "subtotalMinor" to n(st.subtotalMinor), "discountMinor" to n(st.discountMinor), "deliveryFeeMinor" to n(st.deliveryFeeMinor),
        "totalMinor" to n(st.totalMinor), "deductedMinor" to n(st.deductedMinor),
        "removedLines" to JsonArray(st.removedLines.map { n(it.toLong()) }), "changedLines" to JsonArray(st.changedLines.map { n(it.toLong()) }),
        "pending" to n(st.pending.toLong()),
      )
    }
    "pick.metrics" -> {
      val m = PickCore.metrics(x.objs("picks").map { p -> PickCore.Run(p.lOrNull("startedAt"), p.lOrNull("packedAt"), p.objs("lines").map { it.str("status") ?: "" }) })
      obj(
        "orders" to n(m.orders.toLong()), "lines" to n(m.lines.toLong()), "found" to n(m.found.toLong()), "notFound" to n(m.notFound.toLong()),
        "short" to n(m.short.toLong()), "substituted" to n(m.substituted.toLong()), "avgPickMs" to nn(m.avgPickMs), "medianPickMs" to nn(m.medianPickMs),
        "foundRateBp" to nn(m.foundRateBp), "substitutionRateBp" to nn(m.substitutionRateBp),
      )
    }
    "pick.sub" -> {
      val orig = item(x.getValue("orig").jsonObject)
      val raw = x.getValue("cands").jsonArray.map { (it as? JsonObject)?.let(::item) }
      val cands = raw.filterNotNull()
      val safe = { p: Long? -> p != null && p in -MAX_SAFE..MAX_SAFE }
      val c0 = cands.firstOrNull { safe(it.priceMinor) }
      obj(
        "blocks" to JsonArray(raw.map { sn(PickSubstitute.block(orig, it)) }),
        "rank" to JsonArray(PickSubstitute.rank(orig, cands, x.lOrNull("limit")?.toInt() ?: 2).map(::s)),
        "charge" to if (c0 != null && safe(orig.priceMinor)) {
          PickSubstitute.charge(orig.priceMinor!!, c0.priceMinor!!, x.l("qty")).let { obj("chargedUnitPriceMinor" to n(it.chargedUnitPriceMinor), "totalMinor" to n(it.totalMinor)) }
        } else JsonNull,
      )
    }
    else -> error("تابعِ ناشناخته: $fn")
  }

  // ── سفارشِ خرید ──────────────────────────────────────

  private fun order(o: JsonObject) = PurchaseOrderCore.Order(
    o.str("supplierId") ?: "", o.str("state") ?: "",
    o.objs("lines").map { PurchaseOrderCore.Line(it.str("id") ?: "", it.str("productId") ?: "", it.l("qtyMilli"), it.l("priceMinor")) },
  )

  private fun receipt(o: JsonObject) = PurchaseOrderCore.Receipt(
    o.str("id") ?: "",
    o.objs("lines").map { PurchaseOrderCore.ReceiptLine(it.str("lineId") ?: "", it.l("receivedMilli"), it.l("damagedMilli"), it.l("priceMinor")) },
  )

  private fun failure(f: PurchaseOrderCore.Failure?): JsonElement =
    f?.let { obj("code" to s(it.code), "lineId" to sn(it.lineId), "index" to n(it.index.toLong())) } ?: JsonNull

  private fun receiptJson(r: PurchaseOrderCore.Receipt) = obj(
    "id" to s(r.id),
    "lines" to JsonArray(r.lines.map { obj("lineId" to s(it.lineId), "receivedMilli" to n(it.receivedMilli), "damagedMilli" to n(it.damagedMilli), "priceMinor" to n(it.priceMinor)) }),
  )

  private fun outcome(o: PurchaseOrderCore.Outcome): JsonElement = when (o) {
    is PurchaseOrderCore.Outcome.Changed -> obj("changed" to s(o.state))
    PurchaseOrderCore.Outcome.Unchanged -> obj("unchanged" to JsonPrimitive(true))
    is PurchaseOrderCore.Outcome.Failed -> obj("failure" to failure(o.failure))
  }

  private fun po(fn: String, x: JsonObject): JsonElement = when (fn) {
    "po.mul" -> n(PurchaseOrderCore.mulDivRound(x.l("a"), x.l("b"), x.l("c")))
    "po.check" -> failure(PurchaseOrderCore.checkOrder(order(x.getValue("order").jsonObject)))
    "po.flow" -> {
      var o = order(x.getValue("order").jsonObject)
      val receipts = ArrayList<PurchaseOrderCore.Receipt>()
      val steps = x.objs("ops").map { s ->
        when (s.str("op")) {
          "receive" -> when (val r = PurchaseOrderCore.receive(o, receipts, receipt(s.getValue("input").jsonObject), s.bool("allowOver") == true)) {
            is PurchaseOrderCore.ReceiveResult.Ok -> {
              receipts += r.receipt
              obj(
                "ok" to obj(
                  "receipt" to receiptJson(r.receipt),
                  "lines" to JsonArray(
                    r.lines.map {
                      obj(
                        "lineId" to s(it.lineId), "expectedMilli" to n(it.expectedMilli), "receivedMilli" to n(it.receivedMilli), "damagedMilli" to n(it.damagedMilli),
                        "acceptedMilli" to n(it.acceptedMilli), "shortMilli" to n(it.shortMilli), "overMilli" to n(it.overMilli), "priceMinor" to n(it.priceMinor),
                        "totalMinor" to n(it.totalMinor),
                      )
                    },
                  ),
                  "totalMinor" to n(r.totalMinor), "status" to s(r.status.wire),
                ),
              )
            }
            is PurchaseOrderCore.ReceiveResult.Duplicate -> obj("duplicate" to receiptJson(r.receipt))
            is PurchaseOrderCore.ReceiveResult.Failed -> obj("failure" to failure(r.failure))
          }
          "send" -> PurchaseOrderCore.send(o, receipts).also { if (it is PurchaseOrderCore.Outcome.Changed) o = o.copy(state = it.state) }.let(::outcome)
          "cancel" -> PurchaseOrderCore.cancel(o, receipts, s.str("reason")).also { if (it is PurchaseOrderCore.Outcome.Changed) o = o.copy(state = it.state) }.let(::outcome)
          "edit" -> {
            val next = order(s.getValue("next").jsonObject)
            PurchaseOrderCore.edit(o, receipts, next).also { if (it is PurchaseOrderCore.Outcome.Changed) o = next }.let(::outcome)
          }
          else -> error("گامِ ناشناخته: $s")
        }
      }
      val after = try {
        val t = PurchaseOrderCore.totals(o, receipts)
        obj(
          "status" to s(PurchaseOrderCore.status(o, receipts).wire),
          "totals" to obj("orderedMinor" to n(t.orderedMinor), "acceptedMinor" to n(t.acceptedMinor), "remainingMinor" to n(t.remainingMinor)),
          "progress" to JsonArray(
            PurchaseOrderCore.progress(o, receipts).map {
              obj(
                "lineId" to s(it.lineId), "productId" to s(it.productId), "orderedMilli" to n(it.orderedMilli), "receivedMilli" to n(it.receivedMilli),
                "damagedMilli" to n(it.damagedMilli), "acceptedMilli" to n(it.acceptedMilli), "remainingMilli" to n(it.remainingMilli), "overMilli" to n(it.overMilli),
              )
            },
          ),
        )
      } catch (e: Exception) {
        THROWS
      }
      obj("steps" to JsonArray(steps), "after" to after)
    }
    "po.match" -> {
      val tol = x["tol"] as? JsonObject
      val m = PurchaseOrderCore.match(
        order(x.getValue("order").jsonObject), x.objs("receipts").map(::receipt),
        x.objs("bills").map { PurchaseOrderCore.Bill(it.str("lineId"), it.str("productId") ?: "", it.l("qtyMilli"), it.l("totalMinor")) },
        PurchaseOrderCore.Tolerance(tol?.lOrNull("qtyMilli") ?: 0, tol?.lOrNull("priceBp") ?: 0, tol?.lOrNull("absMinor") ?: 0),
      )
      obj(
        "lines" to JsonArray(
          m.lines.map {
            obj(
              "lineId" to sn(it.lineId), "productId" to s(it.productId), "orderedMilli" to n(it.orderedMilli), "acceptedMilli" to n(it.acceptedMilli),
              "billedMilli" to n(it.billedMilli), "orderPriceMinor" to n(it.orderPriceMinor), "expectedMinor" to n(it.expectedMinor), "billedMinor" to n(it.billedMinor),
              "issues" to JsonArray(it.issues.map { i -> s(i.wire) }),
            )
          },
        ),
        "matched" to JsonPrimitive(m.matched), "orderedMinor" to n(m.orderedMinor), "acceptedMinor" to n(m.acceptedMinor), "billedMinor" to n(m.billedMinor),
      )
    }
    else -> error("تابعِ ناشناخته: $fn")
  }

  // ── کمک‌ها ───────────────────────────────────────────

  private val THROWS = JsonObject(mapOf("throws" to JsonPrimitive(true)))

  private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
  private fun JsonObject.l(k: String): Long = getValue(k).jsonPrimitive.content.toLong()
  private fun JsonObject.lOrNull(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.longOrNull
  private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
  private fun JsonObject.objs(k: String): List<JsonObject> = (this[k] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
  private fun JsonObject.strs(k: String): List<String> = (this[k] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

  private fun n(v: Long) = JsonPrimitive(v)
  private fun nn(v: Long?): JsonElement = v?.let(::n) ?: JsonNull
  private fun s(v: String) = JsonPrimitive(v)
  private fun sn(v: String?): JsonElement = v?.let(::s) ?: JsonNull
  private fun obj(vararg kv: Pair<String, JsonElement>) = JsonObject(linkedMapOf(*kv))

  /** درختِ Map/List/String/Long/Double/Boolean ⇐ JSON (همان `plain` ِ `CoreFuzzTest`) */
  fun plain(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> e.mapValues { plain(it.value) }
    is JsonArray -> e.map { plain(it) }
    is JsonPrimitive -> when {
      e.isString -> e.content
      e.booleanOrNull != null -> e.booleanOrNull
      e.content.any { it == '.' || it == 'e' || it == 'E' } -> e.content.toDouble()
      else -> e.content.toLongOrNull() ?: e.content.toDouble()
    }
  }

  /** درخت ⇐ JSON */
  fun j(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to j(x) })
    is List<*> -> JsonArray(v.map { j(it) })
    else -> error("نوعِ ناشناخته: $v")
  }
}
