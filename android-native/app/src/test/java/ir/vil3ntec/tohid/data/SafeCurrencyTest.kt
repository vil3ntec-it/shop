package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.core.safe.SafeCurrency
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.ui.v3.shell.HeroMoney
import ir.vil3ntec.tohid.ui.v3.shell.HomeHero
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  گاوصندوقِ چندارزی (خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴): هر ارز دفترِ جدای خودش، نبودنِ `currency` = افغانی، هیچ جمع و تبدیلی
 *  میانِ ارزها، روی خط بی‌کم‌وکاست، خروجیِ اکسل با ستونِ «ارز»، فهرستِ ارزهای دکان و خواندنِ مبلغِ هر ارز.
 *  قاعدهٔ خودِ هسته با بردارهای مشترک در `SafeCoreTest`؛ سرور در `server/test/safe-currency.test.js`.
 */
class SafeCurrencyTest {

  private var n = 0
  private val ids = { "01SCUR${(++n).toString().padStart(20, '0')}" }

  @After fun reset() {
    I18n.source = { Lang.FA }
  }

  private fun ok(r: SafeBook.Result): SafeBook.Result.Ok {
    assertTrue("$r", r is SafeBook.Result.Ok)
    return r as SafeBook.Result.Ok
  }

  private fun failed(r: SafeBook.Result, code: String): SafeBook.Result.Failed {
    assertTrue("$r", r is SafeBook.Result.Failed)
    assertEquals(code, (r as SafeBook.Result.Failed).code)
    return r
  }

  /** افغانی ۱۰۰۰ شمرده، ۲۵ دالر و ۵۰ یورو واریز، ۲۰۰ افغانی برداشت */
  private fun mixed(): ShopData {
    var d = ok(SafeBook.count(ShopData(), 100_000, null, "صاحب", 1_000, ids)).data
    d = ok(SafeBook.deposit(d, 2_500, "USD", "deposit", null, "صاحب", 2_000, ids)).data
    d = ok(SafeBook.deposit(d, 5_000, "EUR", "deposit", null, "صاحب", 3_000, ids)).data
    d = ok(SafeBook.withdraw(d, 20_000, "owner_withdrawal", null, "صاحب", 4_000, ids)).data
    return d
  }

  @Test fun `ماندهٔ هر ارز جدا، افغانی اول`() {
    val d = mixed()
    val v = SafeBook.views(d)
    assertEquals(listOf("AFN", "EUR", "USD"), v.keys.toList())
    assertEquals(80_000L, v.getValue("AFN").balanceMinor)
    assertEquals(2_500L, v.getValue("USD").balanceMinor)
    assertEquals(5_000L, v.getValue("EUR").balanceMinor)
    assertEquals(listOf(SafeCore.OUT, SafeCore.COUNT), v.getValue("AFN").history.map { it.entry.kind })
    assertEquals(listOf(SafeCore.IN), v.getValue("USD").history.map { it.entry.kind })
    assertNull("ارزِ تازه شمارشی ندارد", v.getValue("USD").lastCount)
    assertEquals(v.getValue("AFN"), SafeBook.view(d))
  }

  @Test fun `ردیفِ کهنهٔ بی ارز افغانی است و ردیفِ افغانیِ تازه ارز نمی‌نویسد`() {
    val old = Json { ignoreUnknownKeys = true }.decodeFromString(
      SafeEntry.serializer(), """{"id":"o1","kind":"in","amountMinor":7000,"createdAt":1000,"reason":"deposit"}""",
    )
    assertNull(old.currency)
    assertEquals(SafeCurrency.AFN, SafeCore.currencyOf(SafeBook.toCore(old)))
    val d = ShopData(safeEntries = listOf(old))
    assertEquals(7_000L, SafeBook.view(d).balanceMinor)
    assertEquals(listOf("AFN"), SafeBook.views(d).keys.toList())
    //  افغانیِ صریح در ورودی همان «بی ارز» نوشته می‌شود؛ دالر نوشته می‌شود
    val afn = ok(SafeBook.deposit(d, 100, "AFN", "deposit", null, "", 2_000, ids)).entry
    assertNull(afn.currency)
    val tree = LedgerDiff.toJson(ok(SafeBook.deposit(d, 100, "USD", "deposit", null, "", 2_000, ids)).data)
    val rows = (tree["safeEntries"] as JsonArray).map { it.jsonObject }
    assertFalse(rows[0].containsKey("currency"))
    assertEquals("USD", rows[1]["currency"]?.jsonPrimitive?.content)
  }

  @Test fun `هیچ جمعی میانِ ارزها نیست — برداشت فقط از ماندهٔ همان ارز`() {
    val d = mixed()
    //  ۸۰۰ افغانی هست ولی ۲۵٫۰۱ دالر نیست
    failed(SafeBook.withdraw(d, 2_501, "USD", "owner_withdrawal", null, "", 5_000, ids), SafeCore.Code.INSUFFICIENT_FUNDS)
    ok(SafeBook.withdraw(d, 2_500, "USD", "owner_withdrawal", null, "", 5_000, ids))
    //  دالر و یورو افغانیِ بیشتر نمی‌سازند
    failed(SafeBook.withdraw(d, 80_001, "owner_withdrawal", null, "", 5_000, ids), SafeCore.Code.INSUFFICIENT_FUNDS)
    failed(SafeBook.toBank(d, 5_001, "EUR", "K-1", null, "", 5_000, ids), SafeCore.Code.INSUFFICIENT_FUNDS)
    //  «واریز/برداشتِ امروز» ِ کادرِ خانه هم از دفترِ همان ارز
    val day = HomeHero.safeToday(SafeBook.view(d, "USD").history, 0L)
    assertEquals(HomeHero.Flow(2_500, 0), day)
    assertEquals(HomeHero.Flow(0, 20_000), HomeHero.safeToday(SafeBook.view(d).history, 0L))
  }

  @Test fun `شمارشِ ارزِ دیگر — ماندهٔ همان ارز، هر اختلاف دلیل می‌خواهد با پیامِ همان ارز`() {
    I18n.source = { Lang.EN }
    val d = mixed()
    val c = ok(SafeBook.count(d, 2_500, "USD", null, "", 5_000, ids))
    assertEquals(2_500L, c.entry.expectedMinor)
    val f = failed(SafeBook.count(c.data, 2_499, "USD", null, "", 6_000, ids), SafeCore.Code.REASON_REQUIRED)
    assertEquals(-1L, f.varianceMinor)
    assertEquals("USD", f.currency)
    assertEquals(StrSafeCur.varianceNeedsReason(false, "0.01 USD"), f.message)
    //  افغانی همان آستانهٔ امروز: اختلافِ ۱ پول دلیل نمی‌خواهد
    ok(SafeBook.count(d, 79_999, null, "", 6_000, ids))
  }

  @Test fun `انتقال با دخل فقط افغانی است`() {
    val e = SafeCore.Entry("t1", SafeCore.FROM_DRAWER, 100, 1_000, shiftId = "sh", auditId = "a", currency = "USD")
    assertEquals(SafeCore.Code.BAD_CURRENCY, SafeCore.check(e))
    assertNull(SafeCore.check(e.copy(currency = null)))
    assertTrue(SafeBook.message(SafeCore.Code.BAD_CURRENCY).isNotBlank())
  }

  /** همان `SafeEntry` ِ ساختِ پیش از ارز (۱۴۰۵/۰۸/۲۳) — مدلی که `currency` را نمی‌شناسد */
  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  private data class OldSafeEntry(
    val id: String, val kind: String = "", val amountMinor: Long = 0, val createdAt: Long = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val by: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val reason: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val note: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val bagNo: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val expectedMinor: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val shiftId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val auditId: String? = null,
  )

  @Test fun `ساختِ کهنه ارز را پاک نمی‌کند — ردیفی که دوباره می‌فرستد بخشی از همان ردیف است`() {
    val d = mixed()
    val server = (LedgerDiff.toJson(d)["safeEntries"] as JsonArray).map { it.jsonObject }.single { it["currency"] != null && it["currency"]!!.jsonPrimitive.content == "USD" }
    //  ساختِ کهنه ردیف را با مدلِ خودش می‌خواند و دوباره می‌نویسد: `currency` نمی‌آید، هیچ null ای هم نمی‌آید
    val json = Json { ignoreUnknownKeys = true }
    val resent = json.encodeToJsonElement(OldSafeEntry.serializer(), json.decodeFromJsonElement(OldSafeEntry.serializer(), server)).jsonObject
    assertFalse(resent.containsKey("currency"))
    for ((k, v) in resent) assertEquals(k, server[k], v)
    //  ساختِ امروز همان ردیف را پس از pull دوباره ذخیره می‌کند ⇒ هیچ op ای (ارز همان می‌ماند)
    val shadow = LedgerDiff.shadowOf(d)
    val again = LedgerDiff.toJson(LedgerDiff.fromJson(LedgerDiff.toJson(d)))
    assertTrue(LedgerDiff.diff(shadow, again).none { it.table == "safeEntries" })
    assertTrue(LedgerDiff.isModelField("safeEntries", "currency"))
    //  opِ دالریِ دستگاهِ دیگر با همان ارز می‌نشیند
    val op = LedgerDiff.diff(LedgerDiff.shadowOf(ShopData()), LedgerDiff.toJson(d)).filter { it.table == "safeEntries" }
    assertEquals(List(4) { SyncOp.INSERT }, op.map { it.type })
    val mine = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(ShopData()), op))
    assertEquals(2_500L, SafeBook.view(mine, "USD").balanceMinor)
    assertEquals(80_000L, SafeBook.view(mine).balanceMinor)
  }

  @Test fun `خروجیِ اکسل — ستونِ «ارز» پس از مبلغ و برگهٔ ماندهٔ هر ارز، بی جمعِ دو ارز`() {
    val d = mixed()
    val tree = OwnerExport.tree(d, OwnerExport.Meta(1_760_000_000_000, "desktop", "1.0.1", "دکان"))
    val sheets = OwnerExport.sheets(tree)
    val safe = sheets.single { it.name == "گاوصندوق" }
    val head = safe.rows[0].map { (it as Xlsx.Cell.Text).s }
    val col = head.indexOf("ارز")
    assertEquals(head.indexOf("مبلغ (پول)") + 1, col)
    assertEquals(listOf("AFN", "USD", "EUR", "AFN"), safe.rows.drop(1).map { (it[col] as Xlsx.Cell.Text).s })
    val totals = sheets.single { it.name == OwnerExportSafe.SHEET }
    val byCode = totals.rows.drop(1).associate { (it[0] as Xlsx.Cell.Text).s to (it[2] as Xlsx.Cell.Num).v }
    assertEquals(mapOf("AFN" to 80_000.0, "EUR" to 5_000.0, "USD" to 2_500.0), byCode)
    assertEquals(25.0, (totals.rows.drop(1).single { (it[0] as Xlsx.Cell.Text).s == "USD" }[1] as Xlsx.Cell.Num).v, 0.0)
    //  JSON ِ خروجی همان دفتر است (بازیابی) — ردیفِ افغانی `currency` نمی‌گیرد
    val rows = (tree["safeEntries"] as JsonArray).map { it.jsonObject }
    assertFalse(rows[0].containsKey("currency"))
    assertEquals(d, OwnerExport.parse(OwnerExport.json(d, OwnerExport.Meta(1, "desktop", "1", "دکان"))).data)
    //  دفترِ بی گاوصندوق برگهٔ ماندهٔ ارزها ندارد
    assertTrue(OwnerExport.sheets(OwnerExport.tree(ShopData(), OwnerExport.Meta(1, "desktop", "1", "دکان"))).none { it.name == OwnerExportSafe.SHEET })
  }

  @Test fun `فهرستِ ارزهای دکان — افزودن با سنجش، خواندن و نوشتن`() {
    val custom = (SafeCurrencies.add(emptyList(), " gbp ", " پوند ") as SafeCurrencies.Added.Ok)
    assertEquals("GBP", custom.code)
    assertEquals(listOf(SafeCurrencies.Item("GBP", "پوند")), custom.list)
    assertEquals(listOf("AFN", "USD", "EUR", "IRR", "PKR", "GBP"), SafeCurrencies.all(custom.list))
    assertEquals(listOf("AFN", "USD", "EUR", "IRR", "PKR", "GBP", "CHF"), SafeCurrencies.all(custom.list, listOf("AFN", "CHF", "bad")))
    assertTrue(SafeCurrencies.add(custom.list, "gbp", "x") is SafeCurrencies.Added.Bad)
    assertTrue(SafeCurrencies.add(emptyList(), "USD", "دالر") is SafeCurrencies.Added.Bad)
    assertTrue(SafeCurrencies.add(emptyList(), "G1P", "x") is SafeCurrencies.Added.Bad)
    assertTrue(SafeCurrencies.add(emptyList(), "پوند", "x") is SafeCurrencies.Added.Bad)
    assertTrue(SafeCurrencies.add(emptyList(), "CHF", "  ") is SafeCurrencies.Added.Bad)
    //  رفت‌وبرگشتِ تنظیم؛ ردیفِ خراب یا تکراری کنار می‌رود
    assertEquals(custom.list, SafeCurrencies.decode(SafeCurrencies.encode(custom.list)))
    assertEquals(
      listOf(SafeCurrencies.Item("GBP", "پوند")),
      SafeCurrencies.decode("""[{"code":"GBP","name":"پوند"},{"code":"gbp","name":"x"},{"code":"USD","name":"y"},{"code":"GBP","name":"z"},5]"""),
    )
    assertEquals(emptyList<SafeCurrencies.Item>(), SafeCurrencies.decode("نه"))
    //  پیش‌فرضِ کادر: آخرین ارزِ همین دستگاه اگر هنوز هست
    assertEquals("USD", SafeCurrencies.pick("USD", SafeCurrencies.all(emptyList())))
    assertEquals("AFN", SafeCurrencies.pick("GBP", SafeCurrencies.all(emptyList())))
    assertEquals("پوند (GBP)", SafeCurrencies.name("GBP", custom.list))
  }

  @Test fun `مبلغِ هر ارز به واحدِ کوچکِ خودش — ریال بی اعشار، افغانی همان نوشتهٔ امروز`() {
    assertEquals(12_550L, SafeCurrencies.parse("125.5", "USD"))
    assertEquals(1_500L, SafeCurrencies.parse("1,500", "IRR"))
    assertNull("ریال اعشار ندارد", SafeCurrencies.parse("12.5", "IRR"))
    assertEquals(150_000L, SafeCurrencies.parse("۱۵۰۰", "AFN"))
    assertNull(SafeCurrencies.parse("abc", "USD"))
    assertNull("بیش از سقفِ ردیف", SafeCurrencies.parse("1000000001", "USD"))
    assertEquals("1,500 ؋", SafeCurrencies.format(150_000, "AFN"))
    assertEquals("125.50 USD", SafeCurrencies.format(12_550, "USD"))
    assertEquals("1,500 IRR", SafeCurrencies.format(1_500, "IRR"))
    assertEquals(HeroMoney.Amount.Ok(12_550), HeroMoney.amount("125.5", "USD"))
    assertEquals(HeroMoney.Amount.Bad(HeroMoney.AMOUNT_REQUIRED), HeroMoney.amount(" ", "USD"))
    assertEquals(HeroMoney.Amount.Bad(HeroMoney.INVALID_AMOUNT), HeroMoney.amount("0", "EUR"))
    assertEquals(HeroMoney.amount("2500"), HeroMoney.amount("2500", "AFN"))
    //  نمودارِ ریال: همان سری با مقیاسِ خودش (÷۱ نه ÷۱۰۰)؛ افغانی دست نمی‌خورد
    val s = HomeHero.Series(listOf(15.0), 0, listOf(""), false)
    assertEquals(1_500.0, HomeHero.perMajor(s, SafeCurrency.minorPerMajor("IRR")).current, 1e-6)
    assertEquals(s, HomeHero.perMajor(s, SafeCurrency.minorPerMajor("AFN")))
  }

  @Test fun `تنظیم‌ها — فهرستِ ارزها مالِ دکان، آخرین ارز و ارزِ نمودار مالِ همین دستگاه`() {
    assertEquals(ShopSettingsKeys.Scope.SHOP, ShopSettingsKeys.scopeOf(ShopSettingsKeys.POLICY, "safe_currencies"))
    assertEquals(ShopSettingsKeys.Scope.DEVICE, ShopSettingsKeys.scopeOf(ShopSettingsKeys.POLICY, "safe_last_currency"))
    assertEquals(ShopSettingsKeys.Scope.DEVICE, ShopSettingsKeys.scopeOf(ShopSettingsKeys.POLICY, "safe_view_currency"))
  }

  @Test fun `کارایی — دفترِ ۶۰۰۰ ردیفیِ سه‌ارزی زیرِ ۱۰۰ میلی‌ثانیه`() {
    val codes = listOf(null, "USD", "EUR")
    val rows = (0 until 6_000).map { i ->
      SafeEntry("r$i", SafeCore.IN, 100L + i, 1_000L + i, reason = "deposit", currency = codes[i % 3])
    }
    val d = ShopData(safeEntries = rows)
    repeat(3) { SafeBook.views(d) }   // گرم شدنِ JIT
    val t0 = System.nanoTime()
    val v = SafeBook.views(d)
    val ms = (System.nanoTime() - t0) / 1_000_000
    println("safe-views-6000 ${ms}ms")
    assertTrue("${ms}ms", ms < 100)
    assertEquals(2_000, v.getValue("USD").history.size)
    assertEquals(rows.filter { it.currency == null }.sumOf { it.amountMinor }, v.getValue("AFN").balanceMinor)
  }

  @Test fun `بی کلیدِ null روی خط و JSON ِ ارز همان ShopData`() {
    val d = mixed()
    val tree = LedgerDiff.toJson(d)
    assertEquals(d, LedgerDiff.fromJson(tree))
    val usd = (tree["safeEntries"] as JsonArray).map { it.jsonObject }.first { (it["currency"] as? JsonPrimitive)?.content == "USD" }
    assertEquals(JsonPrimitive(2_500L), usd["amountMinor"])
    assertTrue(JsonObject(usd).keys.none { usd[it] is kotlinx.serialization.json.JsonNull })
  }
}
