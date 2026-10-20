package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.CodeLoginRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 *  دفترِ تغییراتِ برنامه — بندِ ۲۱٫۱۳.
 *
 *  «هر نوشتن = یک opِ درست» چیزی نیست که با نگاه کردن به کد ثابت شود.
 *  این‌جا همان دفترِ واقعی (`ShopData`) عوض می‌شود و opهای ساخته‌شده
 *  شمرده و خوانده می‌شوند.
 *
 *  ⚠️ قرینهٔ این آزمون‌ها روی وب در `server/test/sync-client.test.js`
 *  است و آن یکی با **سرورِ واقعی** هم می‌دود. هر دو یک قرارداد را
 *  می‌سنجند؛ یکی را عوض کردید، آن یکی را هم.
 */
class SyncV1CoreTest {

  private fun empty() = ShopData()

  /* ------------------------------ ULID ------------------------------ */

  @Test
  fun `ULID بیست‌وشش نویسه است و در یک میلی‌ثانیه هم یکتا و صعودی می‌ماند`() {
    val made = (1..500).map { Ulid.next(1_700_000_000_000L) }
    made.forEach { assertEquals(26, it.length) }
    assertEquals("هیچ دو ULIDی نباید یکی باشد", 500, made.toSet().size)
    assertEquals("ترتیب باید حفظ شود", made.sorted(), made)
    assertTrue(Ulid.next(1_700_000_001_000L) > made.last())
  }

  /* ---------------------------- اثرِ انگشت ---------------------------- */

  @Test
  fun `اثرِ انگشت همان فرمولِ سرور است — کلیدها مرتب، عددها به شکلِ جاوااسکریپت`() {
    val fields = buildJsonObject {
      put("salePrice", JsonPrimitive(120))
      put("name", JsonPrimitive("برنج"))
    }
    //  کلیدها مرتب می‌شوند: name پیش از salePrice
    assertEquals("""{"name":"برنج","salePrice":120}""", OpHash.canonical(fields))

    val expected = MessageDigest.getInstance("SHA-256")
      .digest("""products|p-1|insert|{"name":"برنج","salePrice":120}""".toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
    assertEquals(expected, OpHash.of("products", "p-1", "insert", fields))
  }

  @Test
  fun `عددِ صحیح بی اعشار می‌رود، وگرنه سرور هشِ ما را رد می‌کند`() {
    //  ⚠️ این همان تله‌ای است که بی آن، **هر opِ قیمت‌دار** رد می‌شد:
    //  kotlinx می‌نویسد 120.0 و Node می‌نویسد 120.
    val tree = LedgerDiff.toJson(
      empty().copy(products = listOf(Product(id = "p-1", name = "برنج", salePrice = 120.0, purchasePrice = 2.5)))
    )
    val row = (tree["products"] as kotlinx.serialization.json.JsonArray)[0] as JsonObject
    assertEquals("120", (row["salePrice"] as JsonPrimitive).content)
    assertEquals("عددِ کسری دست نمی‌خورد", "2.5", (row["purchasePrice"] as JsonPrimitive).content)
  }

  /* ------------------------------ تفاضل ------------------------------ */

  @Test
  fun `درجِ یک کالا دقیقاً یک opِ insert می‌سازد و شناسه داخلِ fields تکرار نمی‌شود`() {
    val before = LedgerDiff.toJson(empty())
    val after = LedgerDiff.toJson(
      empty().copy(products = listOf(Product(id = "p-1", name = "برنج", salePrice = 120.0)))
    )
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(before), after)
    assertEquals(1, ops.size)
    assertEquals(SyncOp.INSERT, ops[0].type)
    assertEquals("products", ops[0].table)
    assertEquals("p-1", ops[0].rowId)
    assertNull(ops[0].fields!!["id"])
    assertEquals("برنج", (ops[0].fields!!["name"] as JsonPrimitive).content)
  }

  @Test
  fun `عوض کردنِ یک حرف از یک اسم یک opِ update با یک فیلد می‌سازد`() {
    val one = empty().copy(debtors = listOf(Debtor(id = "c-1", name = "احمد", phone = "070")))
    val two = one.copy(debtors = listOf(one.debtors[0].copy(name = "احمدی")))
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(LedgerDiff.toJson(one)), LedgerDiff.toJson(two))
    assertEquals(1, ops.size)
    assertEquals(SyncOp.UPDATE, ops[0].type)
    assertEquals(setOf("name"), ops[0].fields!!.keys)
  }

  @Test
  fun `حذف یک opِ بی فیلد است و بدنه‌اش زیرِ یک کیلوبایت می‌ماند`() {
    val one = empty().copy(debtors = listOf(Debtor(id = "c-2", name = "کریم", phone = "0700000000")))
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(LedgerDiff.toJson(one)), LedgerDiff.toJson(empty()))
    assertEquals(1, ops.size)
    assertEquals(SyncOp.DELETE, ops[0].type)
    assertNull(ops[0].fields)
    val body = kotlinx.serialization.json.JsonArray(ops.map { it.toJson() }).toString()
    assertTrue("بدنهٔ حذف ${body.toByteArray().size} بایت شد", body.toByteArray(Charsets.UTF_8).size < 1024)
  }

  @Test
  fun `هیچ تغییری یعنی هیچ opی`() {
    val one = empty().copy(products = listOf(Product(id = "p-1", name = "شکر")))
    assertEquals(0, LedgerDiff.diff(LedgerDiff.shadowOf(LedgerDiff.toJson(one)), LedgerDiff.toJson(one)).size)
  }

  @Test
  fun `شمارنده‌ها دلتا می‌روند و بقیه مقدارِ تازه`() {
    val one = empty().copy(
      saleItems = listOf(SaleItem(id = "si-1", saleId = "s-1", quantity = 5.0, returnedQty = 0.0)),
      purchases = listOf(Purchase(id = "pu-1", totalAmount = 1000.0, paidAmount = 200.0, debt = 800.0)),
    )
    val two = one.copy(
      saleItems = listOf(one.saleItems[0].copy(returnedQty = 2.0)),
      purchases = listOf(one.purchases[0].copy(paidAmount = 500.0, debt = 500.0)),
    )
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(LedgerDiff.toJson(one)), LedgerDiff.toJson(two))
    val si = ops.first { it.table == "saleItems" }
    val pu = ops.first { it.table == "purchases" }
    assertEquals(2.0, incOf(si.fields!!, "returnedQty"), 0.0001)
    assertEquals(300.0, incOf(pu.fields!!, "paidAmount"), 0.0001)
    assertEquals(-300.0, incOf(pu.fields!!, "debt"), 0.0001)
  }

  private fun incOf(fields: JsonObject, name: String): Double =
    ((fields[name] as JsonObject)["\$inc"] as JsonPrimitive).content.toDouble()

  @Test
  fun `فیلدی که از ردیف برداشته شود صریح null می‌رود`() {
    val one = empty().copy(debtors = listOf(Debtor(id = "c-3", name = "نور", notes = "قدیمی")))
    val two = one.copy(debtors = listOf(one.debtors[0].copy(notes = "")))
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(LedgerDiff.toJson(one)), LedgerDiff.toJson(two))
    assertEquals(1, ops.size)
    assertEquals("", (ops[0].fields!!["notes"] as JsonPrimitive).content)
  }

  /* --------------------- اعمالِ opهای دستگاهِ دیگر --------------------- */

  @Test
  fun `opهای رسیده روی دفتر می‌نشینند — insert و update و inc و delete`() {
    var tree = LedgerDiff.toJson(empty())
    tree = LedgerDiff.applyRemote(
      tree,
      listOf(SyncOp("o1", 1, "products", "p-9", SyncOp.INSERT, buildJsonObject {
        put("name", JsonPrimitive("روغن")); put("salePrice", JsonPrimitive(10))
      })),
    )
    var data = LedgerDiff.fromJson(tree)
    assertEquals(1, data.products.size)
    assertEquals("روغن", data.products[0].name)

    tree = LedgerDiff.applyRemote(
      tree,
      listOf(SyncOp("o2", 2, "products", "p-9", SyncOp.UPDATE, buildJsonObject {
        put("salePrice", JsonPrimitive(15))
      })),
    )
    data = LedgerDiff.fromJson(tree)
    assertEquals(15.0, data.products[0].salePrice, 0.0001)
    assertEquals("فیلدهای دیگر دست نمی‌خورند", "روغن", data.products[0].name)

    tree = LedgerDiff.applyRemote(
      tree,
      listOf(
        SyncOp("o3", 3, "saleItems", "si-9", SyncOp.INSERT, buildJsonObject { put("returnedQty", JsonPrimitive(1)) }),
        SyncOp("o4", 4, "saleItems", "si-9", SyncOp.UPDATE, buildJsonObject {
          put("returnedQty", buildJsonObject { put("\$inc", JsonPrimitive(2)) })
        }),
      ),
    )
    assertEquals(3.0, LedgerDiff.fromJson(tree).saleItems[0].returnedQty, 0.0001)

    tree = LedgerDiff.applyRemote(tree, listOf(SyncOp("o5", 5, "products", "p-9", SyncOp.DELETE, null)))
    assertEquals(0, LedgerDiff.fromJson(tree).products.size)
  }

  @Test
  fun `جدولِ ناشناخته اعمال نمی‌شود و دفتر را خراب نمی‌کند`() {
    val tree = LedgerDiff.applyRemote(
      LedgerDiff.toJson(empty()),
      listOf(SyncOp("x", 1, "AppUser", "u1", SyncOp.INSERT, buildJsonObject { put("p", JsonPrimitive(1)) })),
    )
    assertNull(tree["AppUser"])
  }

  @Test
  fun `اعمالِ opهای رسیده هیچ opِ تازه‌ای نمی‌سازد — حلقهٔ رفت‌وبرگشت بسته است`() {
    val mine = LedgerDiff.toJson(empty().copy(products = listOf(Product(id = "p-1", name = "نمک"))))
    val after = LedgerDiff.applyRemote(
      mine,
      listOf(SyncOp("o1", 1, "products", "p-2", SyncOp.INSERT, buildJsonObject { put("name", JsonPrimitive("فلفل")) })),
    )
    //  همان کاری که موتور می‌کند: سایه در همان لحظه جلو می‌رود
    val shadow = LedgerDiff.shadowOf(after)
    //  و دفترِ محلی هم از همان درخت خوانده می‌شود
    val local = LedgerDiff.toJson(LedgerDiff.fromJson(after))
    assertEquals(0, LedgerDiff.diff(shadow, local).size)
  }

  /* ----------------------------- Snapshot ----------------------------- */

  @Test
  fun `Snapshot دفترِ گوشیِ نو را پر می‌کند`() {
    val snap = buildJsonObject {
      put("cursor", JsonPrimitive(42))
      put("tables", buildJsonObject {
        put("debtors", kotlinx.serialization.json.JsonArray(listOf(
          buildJsonObject {
            put("id", JsonPrimitive("c-7"))
            put("data", buildJsonObject { put("name", JsonPrimitive("احمد")); put("phone", JsonPrimitive("0700")) })
          },
        )))
      })
    }
    val tree = LedgerDiff.fromSnapshot(snap, LedgerDiff.toJson(empty()))
    val data = LedgerDiff.fromJson(tree)
    assertEquals(1, data.debtors.size)
    assertEquals("c-7", data.debtors[0].id)
    assertEquals("احمد", data.debtors[0].name)
  }

  /* ----------------------- سه روز آفلاین ----------------------- */

  @Test
  fun `سه روز کارِ آفلاین یک به یک op می‌سازد و ترتیبشان به‌هم نمی‌ریزد`() {
    var data = empty()
    var shadow = LedgerDiff.shadowOf(LedgerDiff.toJson(data))
    val all = ArrayList<SyncOp>()
    var n = 0
    val day = 24L * 60 * 60 * 1000
    val start = System.currentTimeMillis() - 3 * day

    for (d in 0 until 3) {
      for (k in 0 until 40) {
        n++
        val at = start + d * day + k * 60_000
        data = data.copy(
          products = data.products + Product(id = "off-p-$n", name = "کالا $n", salePrice = n.toDouble(), createdAt = at),
          sales = data.sales + Sale(id = "off-s-$n", finalTotal = n * 2.0, createdAt = at),
          saleItems = data.saleItems + SaleItem(id = "off-i-$n", saleId = "off-s-$n", productId = "off-p-$n", quantity = 1.0),
          transactions = if (k % 10 == 0)
            data.transactions + DebtTransaction(id = "off-t-$n", debtorId = "c-1", amount = k.toDouble(), createdAt = at)
          else data.transactions,
        )
        val tree = LedgerDiff.toJson(data)
        val ops = LedgerDiff.diff(shadow, tree, at)
        all += ops
        shadow = LedgerDiff.shadowOf(tree)
      }
    }
    //  سه ردیفِ همیشگی + یک ردیفِ هر ده تا
    assertEquals(3 * 40 * 3 + 12, all.size)
    assertEquals("هیچ opی نباید تکراری باشد", all.size, all.map { it.opId }.toSet().size)
    assertEquals("ترتیبِ op_id باید همان ترتیبِ ساخت بماند", all.map { it.opId }.sorted(), all.map { it.opId })
    //  و خودِ دفتر سالم مانده
    assertEquals(120, data.products.size)
  }

  /* ------------------------------ چراغ ------------------------------ */

  @Test
  fun `چراغ — سبز همگام، زرد در صف، خاکستری آفلاین، قرمز خطا`() {
    fun dot(error: Boolean = false, online: Boolean = true, signedIn: Boolean = true, queued: Int = 0, busy: Boolean = false) =
      SyncDot.of(error, online, signedIn, configured = true, queued = queued, busy = busy)
    assertEquals(SyncDot.GREEN, dot())
    assertEquals(SyncDot.YELLOW, dot(queued = 3))
    assertEquals(SyncDot.GREY, dot(online = false))
    assertEquals(SyncDot.GREY, dot(signedIn = false))
    assertEquals(SyncDot.RED, dot(error = true))
    //  خطا از همه بالاتر است، وگرنه پنهان می‌ماند
    assertEquals(SyncDot.RED, dot(error = true, online = false))
  }

  /* ------------------------ ارقامِ کدِ ورود ------------------------ */

  @Test
  fun `ارقامِ فارسی و عربی به انگلیسی برمی‌گردند`() {
    assertEquals("483920", CodeLoginRepository.englishDigits("۴۸۳۹۲۰"))
    assertEquals("483920", CodeLoginRepository.englishDigits("٤٨٣٩٢٠"))
    assertEquals("483920", CodeLoginRepository.digitsOnly(" ۴۸۳ ۹۲۰ "))
    assertEquals("123456", CodeLoginRepository.digitsOnly("code: 123456"))
  }

  @Test
  fun `opِ حذف داخلِ JSON فیلد ندارد و hash می‌برد`() {
    val op = SyncOp("o", 1, "debtors", "c-1", SyncOp.DELETE, null)
    val body = op.toJson()
    assertNull(body["fields"])
    assertNotNull(body["hash"])
    assertEquals(OpHash.of("debtors", "c-1", "delete", null), (body["hash"] as JsonPrimitive).content)
  }

  /* --------------------- دسته: یک ثبت بریده نمی‌شود --------------------- */

  private fun saveOps(ts: Long, n: Int, tag: String) =
    (0 until n).map { SyncOp("$tag-$it", ts, "saleItems", "$tag-$it", SyncOp.INSERT, buildJsonObject { put("q", JsonPrimitive(it)) }) }

  @Test
  fun `سقفِ دسته وسطِ یک ثبت نمی‌افتد — آن ثبت کامل به دستهٔ بعد می‌رود`() {
    //  ثبتِ اول ۱۵۰ op، ثبتِ دوم (فاکتورِ نسیه با قلم‌ها و بردش) ۸۰ op ⇒ سقفِ ۲۰۰ وسطِ ثبتِ دوم
    val ops = saveOps(1000, 150, "a") + saveOps(2000, 80, "b")
    val sizes = ops.map { 10 }
    val first = OpQueue.cut(ops, sizes, maxOps = 200, maxBytes = 1_000_000)
    assertEquals(150, first.size)
    assertTrue(first.all { it.ts == 1000L })
    //  ثبتی که خودش از سقف بزرگ‌تر است ناچار بریده می‌شود (دستهٔ خالی = صفِ قفل)
    val big = saveOps(3000, 260, "c")
    assertEquals(200, OpQueue.cut(big, big.map { 10 }, maxOps = 200, maxBytes = 1_000_000).size)
    //  سقفِ بایت هم همین‌طور
    val bytes = OpQueue.cut(ops, sizes, maxOps = 500, maxBytes = 2 + 10 * 170)
    assertEquals(150, bytes.size)
    //  ثبت کامل جا شد ⇒ همان دستهٔ همیشه
    assertEquals(230, OpQueue.cut(ops, sizes, maxOps = 500, maxBytes = 1_000_000).size)
  }

  @Test
  fun `در یک ثبت ردیف‌های برگشت پیش از شمارنده‌هایشان می‌روند`() {
    //  اگر ثبتِ بزرگِ برگشت ناچار بریده شود، سرِ برگشت در دستهٔ اول داوری می‌شود و شمارنده‌ها پشتش
    val one = empty().copy(
      sales = listOf(Sale(id = "s1", finalTotal = 900.0)),
      saleItems = listOf(SaleItem(id = "si1", saleId = "s1", quantity = 3.0)),
    )
    val two = one.copy(
      sales = listOf(Sale(id = "s1", finalTotal = 900.0, debtSettled = 600.0)),
      saleItems = listOf(SaleItem(id = "si1", saleId = "s1", quantity = 3.0, returnedQty = 2.0)),
      saleReturns = listOf(ir.vil3ntec.tohid.data.SaleReturn(id = "r1", saleId = "s1", saleItemId = "si1", quantity = 2.0, amount = 600.0)),
    )
    val order = LedgerDiff.diff(LedgerDiff.shadowOf(LedgerDiff.toJson(one)), LedgerDiff.toJson(two)).map { it.table }
    assertEquals(order.toString(), "returns", order.first())
    assertTrue(order.toString(), order.indexOf("sales") > 0 && order.indexOf("saleItems") > 0)
  }
}
