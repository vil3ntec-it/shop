package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.text.DariText
import ir.vil3ntec.tohid.ui.screens.ProductListModel
import ir.vil3ntec.tohid.ui.screens.PromoText
import ir.vil3ntec.tohid.ui.screens.mediaKindName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 *  LOC-08 — «زبانِ دری، لفظ‌های دری به کار نرفته» (صاحب‌کار، ۱۴۰۵/۰۸/۱۳). دو چیز را می‌سنجد:
 *
 *  1. **پوشش**: چند درصد از نوشته‌های کارهای اصلی (صندوق، فاکتور و رسید، قرض، سفارش، پیک، خانهٔ تنظیمات،
 *     خطاها) در دری با فارسی فرق دارند. اندازهٔ پیش از این کار ۲٫۶٪ بود؛ کف همان اندازهٔ پس از این کار است
 *     (چرخ‌دنده‌ای: فقط بالا برود). فارسیِ برنامه خودش از پیش واژه‌های افغانی دارد (دکان، قرض‌دار، برد/رسید،
 *     الباقی، معاش، کرایه)، پس صد درصد هدف نیست: جملهٔ درستِ دری گاهی همان فارسی است.
 *  2. **دور زدنِ لایه**: هر نوشتهٔ فارسیِ خام در صفحه‌های نسخهٔ ۳ (`ui/v3`) و حالتِ پیک (`ui/courier`) از
 *     هیچ زبانی نمی‌گذرد و دری نمی‌شود. فقط داده یا منطق (سابقهٔ دکان، کلیدِ تنظیمات، نشانهٔ خطا) با دلیل
 *     در فهرستِ زیر می‌ماند؛ نوشتهٔ تازهٔ صفحه ⇒ جدولِ `Str*` با `t(…)`.
 *
 *  اندازهٔ هر صفحه (پوشه) هم چاپ می‌شود: `dari-screen <پوشه> <کلید> <دری‌شده> <٪>`.
 *
 *  3. **دورِ ۲** (همان گله): همهٔ `ui/components`، صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند و متنشان به جدول رفت
 *     ([movedScreens])، و یاورهای پیام ([movedHelpers]) هیچ متنِ خامی ندارند؛ سطری که داده است (یادداشتِ سابقه، کلیدِ
 *     تنظیمات، برگهٔ خروجی) با «// داده: دلیل» نشان داده می‌شود. متنِ خامِ باقی‌ماندهٔ `ui/screens` سقفِ چرخ‌دنده‌ای دارد
 *     ([maxRawOldScreens]) و خطای ورودیِ صفِ پیک (`CourierQueue` ِ هسته) از جدول می‌گذرد.
 *  4. **دورِ ۳**: دفترِ مشتری و کالا و خریدِ کامپیوتر، دفترِ موجودی، چاپگر و ترازو، سفارش و فاکتورِ خرید، تخفیف‌ها، حساب‌پیِ
 *     دکان و گزارشِ تازه ([movedScreens3]) هم متنِ خام ندارند؛ سقفِ `ui/screens` پایین‌تر آمد.
 *  5. **دورِ ۴**: سرورها، کارمندان، چاپگر و بارکدخوان، بانکِ تصاویر، متنِ تخفیف و پیوند، ادغامِ مشتری و کارِ گروهی روی کالا
 *     ([movedScreens4]) هم متنِ خام ندارند؛ جدول و برچسبِ `PromoText`/`ProductListModel` هر بار خوانده می‌شوند؛ سقف باز پایین آمد.
 *  6. **دورِ ۵**: صفحه‌های زندهٔ کوچک — لوگوی ویترین، نقشهٔ ساعت و روز، دوربینِ گوشی برای کامپیوتر، بسته‌ها و بارکدِ هر واحد، گزارشِ
 *     مشتریِ گوشی و اسکنِ بارکد ([movedScreens5]) هم متنِ خام ندارند؛ «دوربین/گوشی» ِ اسکن با `td(…)`؛ سقف باز پایین آمد.
 *  7. **دورِ ۶** (پوششِ کامل): هیچ نوشتهٔ دری در جدول‌ها، برنامهٔ خرید و ویترین شکلِ فقط‌فارسیِ [farsiOnlyForms] را ندارد
 *     (جز [farsiOnlyAllowed] ِ نام‌دار)؛ `StrDebtor` (پیش‌تر `t` ِ فقط‌فارسی) جدولِ همگانی است و نمایشگرِ مشتری ([movedScreens6])
 *     متنِ خام ندارد؛ سقف باز پایین آمد.
 *  8. **دورِ ۷**: لایه‌های دفتر، همگام‌سازی، چاپ و به‌روزرسانی ([appLayers]: `data/`، `sync/`، `print/`، `update/`؛ همچنین
 *     جایگزین‌های کامپیوترِ همان پوشه‌ها) هیچ متنِ خامی ندارند — خطا و حالِ کارشان از `StrLedger`، `StrGoods`، `StrAppData`، `StrSync`، `StrPrint` و `StrUpdate` می‌آید؛ یادداشتِ سابقه،
 *     برگهٔ خروجی، کلیدِ منطق و متنِ خامِ `lastError` ِ همگام‌سازی با «// داده: دلیل» می‌مانند. برچسب‌های فاکتور، رسیدِ هدیه و
 *     صورت‌حسابِ چاپی در دری از لایهٔ واژه می‌گذرند (`Receipts.labels`)؛ چند متنِ زندهٔ قدیمیِ `ui/screens` هم؛ سقف باز پایین آمد.
 */
class DariCoverageTest {

  @After fun reset() {
    I18n.source = { Lang.FA }
    I18n.audit = null
  }

  /** جدول‌های کارهای اصلی */
  private val core = listOf(
    StrPos, StrSale, StrPay, StrTill, // صندوق
    StrInvoices, StrOrderSlip, // فاکتور، رسید و برگهٔ سفارش
    StrMoney, // قرض و قرض‌داران
    StrOrderOps, StrDeliver, // سفارش‌ها و تحویلِ بعدی
    StrCourier, StrCourierApp, // پیک
    StrSettingsHome, // خانهٔ تنظیمات
  )

  /** کفِ پوشش (٪) — دورِ ۱: ۱۵، دورِ ۲: ۱۶ (اندازه ۱۶٫۵٪)؛ فقط بالا برود */
  private val minCorePercent = 16

  private class Row(val table: String, val key: String, val fa: String, val dari: String)

  private fun methods(table: Any) = table.javaClass.declaredMethods.filter {
    Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.returnType == String::class.java
  }

  private fun keyOf(m: java.lang.reflect.Method): String =
    if (m.parameterCount == 0 && m.name.startsWith("get")) m.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) else m.name

  private fun rows(tables: List<Any>): List<Row> {
    val out = ArrayList<Row>()
    for (table in tables) for (m in methods(table)) {
      val args = m.parameterTypes.mapIndexed { i, type ->
        when (type) {
          Int::class.javaPrimitiveType, Int::class.javaObjectType -> 7301 + i
          Long::class.javaPrimitiveType, Long::class.javaObjectType -> 7301L + i
          Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> true
          else -> (7301 + i).toString()
        }
      }.toTypedArray()
      var entry: I18n.Entry? = null
      I18n.audit = { entry = it }
      I18n.source = { Lang.FA }
      val fa = m.invoke(table, *args) as String
      if (entry == null) continue
      I18n.source = { Lang.PRS }
      val dari = m.invoke(table, *args) as String
      out += Row(table.javaClass.simpleName, keyOf(m), fa, dari)
    }
    I18n.audit = null
    I18n.source = { Lang.FA }
    return out
  }

  private fun pct(part: Int, all: Int) = if (all == 0) 0.0 else 100.0 * part / all

  @Test fun `کارهای اصلی در دری دست‌کم کفِ پوشش با فارسی فرق دارند`() {
    val all = rows(core)
    assertTrue("جدولی پیدا نشد", all.size > 1000)
    for ((table, list) in all.groupBy { it.table }) {
      val c = list.count { it.dari != it.fa }
      println("dari-table $table ${list.size} $c ${"%.1f".format(pct(c, list.size))}%")
    }
    val changed = all.count { it.dari != it.fa }
    val p = pct(changed, all.size)
    println("dari-core ${all.size} $changed ${"%.1f".format(p)}%")
    assertTrue("پوششِ دریِ کارهای اصلی ${"%.1f".format(p)}٪ < $minCorePercent٪", p >= minCorePercent)
    //  هر جدولِ اصلی دست‌کم یک نوشتهٔ دری دارد (هیچ‌کدام کاملاً بیرون از لایه نیست)
    for ((table, list) in all.groupBy { it.table }) assertTrue("$table در دری هیچ فرقی ندارد", list.any { it.dari != it.fa })
  }

  /* ------------------------------- اندازهٔ هر صفحه ------------------------------- */

  private val app: File get() = DesktopPhrases.repo().resolve("android-native/app/src/main/java/ir/vil3ntec/tohid")

  private fun screens(): List<File> = listOf("ui/v3", "ui/courier").flatMap { d ->
    app.resolve(d).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
  }

  @Test fun `اندازهٔ دریِ هر صفحهٔ نسخهٔ ۳ چاپ می‌شود و پیک دیگر بیرون از لایه نیست`() {
    val byKey = rows(I18nTables.all).groupBy { it.table + "." + it.key }.mapValues { (_, l) -> l.any { it.dari != it.fa } }
    val groups = LinkedHashMap<String, MutableSet<String>>()
    for (f in screens()) {
      val rel = f.relativeTo(app.resolve("ui")).invariantSeparatorsPath
      val parts = rel.split('/')
      val group = when {
        parts[0] == "courier" -> "courier"
        parts.size > 3 -> parts.subList(1, 3).joinToString("/")
        parts.size == 3 -> parts[1]
        else -> "kit"
      }
      val keys = groups.getOrPut(group) { LinkedHashSet() }
      for (m in Regex("""\b(Str\w*)\.(\w+)""").findAll(code(f))) {
        val k = m.groupValues[1] + "." + m.groupValues[2]
        if (k in byKey) keys += k
      }
    }
    var total = 0
    var changed = 0
    for ((g, keys) in groups.entries.sortedByDescending { it.value.size }) {
      if (keys.isEmpty()) continue
      val c = keys.count { byKey[it] == true }
      total += keys.size
      changed += c
      println("dari-screen $g ${keys.size} $c ${"%.1f".format(pct(c, keys.size))}%")
    }
    println("dari-screens $total $changed ${"%.1f".format(pct(changed, total))}%")
    val courier = groups["courier"].orEmpty()
    assertTrue("پیک از جدول‌ها نمی‌خواند", courier.size >= 100)
    assertTrue("پیک در دری هیچ فرقی ندارد", courier.any { byKey[it] == true })
  }

  /* ------------------------------- دور زدنِ لایه ------------------------------- */

  /** کدِ پرونده بی سطرهای توضیح و بی توضیحِ پایانِ سطر */
  private fun code(f: File): String = f.readLines().filterNot { it.trimStart().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
    .joinToString("\n") { it.replace(Regex("""\s//\s.*$"""), "") }

  /** حرفِ فارسی (نه ؋، ؟، ٪ یا ،) */
  private val letter = Regex("[\\u0621-\\u063A\\u0641-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC]")

  /** پرونده‌هایی که متنِ فارسی‌شان داده یا منطق است، نه نوشتهٔ صفحه — با دلیل */
  private val dataFiles = mapOf(
    "ui/v3/sales/drawer/DrawerModel.kt" to "DrawerNotes: یادداشتِ سابقهٔ دکان (داده)، در همهٔ زبان‌ها یکی",
    "ui/v3/sales/history/HistoryModel.kt" to "دلیلِ لغوِ «ویرایش در سبد» و نامِ «قلم آزاد» در دفتر (داده)",
    "ui/v3/account/subscription/PlansScreen.kt" to "پیامِ خریدِ اشتراک به فروشنده و یادداشتِ درخواست به سرور (داده)",
    "ui/v3/shell/HomeView.kt" to "big(): نامِ نمونهٔ بلند برای آزمونِ جا",
    "ui/v3/settings/sections/SyncModel.kt" to "نشانه‌های خطای خام برای دسته‌بندی (منطق)",
    "ui/v3/settings/sections/BackupController.kt" to "نامِ کار در سابقهٔ دکان (داده)",
    "ui/v3/stock/MovementsModel.kt" to "نامِ پروندهٔ CSV (داده)",
    "ui/v3/stock/BulkEntryModel.kt" to "واحدِ پیش‌فرضِ کالا «عدد» (داده)",
    "ui/v3/analytics/SheetTable.kt" to "سرستون‌های بی‌جمع (منطق)",
  )

  /** کلیدِ صفحه‌های تنظیمات (`SettingsLayout.labelOf` آن را به نوشتهٔ هر زبان برمی‌گرداند) و حرفِ نشانِ پیک */
  private val keyLiterals = setOf(
    "هشدارها", "ظاهر", "زبان", "تاریخ و رقم", "قفل برنامه", "کد شاگرد", "دستگاه‌های وارد شده", "تغییر رمز حساب",
    "کالای منقضی", "همگام‌سازی", "داده‌ها",
    "پ", // حرفِ نخستِ «پیک» برای دایرهٔ نام وقتی نامی نیست
  )

  @Test fun `هیچ نوشتهٔ فارسیِ خامی در صفحه‌های نسخهٔ ۳ و پیک از لایهٔ زبان بیرون نمی‌ماند`() {
    val bad = ArrayList<String>()
    var files = 0
    for (f in screens()) {
      val rel = f.relativeTo(app).invariantSeparatorsPath
      if (rel.startsWith("ui/v3/gallery/") || rel in dataFiles) continue
      files++
      for (m in Regex(""""((?:[^"\\\n]|\\.)*)"""").findAll(code(f))) {
        val s = m.groupValues[1]
        if (letter.containsMatchIn(s) && s !in keyLiterals) bad += "$rel: «$s»"
      }
    }
    assertTrue("صفحه‌ای پیدا نشد", files > 300)
    for (k in dataFiles.keys) assertTrue("$k دیگر نیست — از فهرست بردارید", app.resolve(k).isFile)
    assertTrue(
      "نوشتهٔ فارسیِ خام در صفحه (دری و زبان‌های دیگر آن را نمی‌بینند) — به جدولِ Str* با t(…) ببرید:\n" + bad.joinToString("\n"),
      bad.isEmpty(),
    )
  }

  @Test fun `جملهٔ کاملِ دری فقط وقتی می‌نشیند که همهٔ جای خالی‌ها asIs باشند`() {
    //  نمونهٔ زنده: `StrPos.pinWrong` با عدد ⇒ جملهٔ کامل؛ مقدار همان
    I18n.source = { Lang.PRS }
    assertTrue(StrPos.pinWrong(3).contains("بار دیگر می‌توانید کوشش کنید"))
    assertTrue(StrPos.pinWrong(3).contains("3"))
    I18n.source = { Lang.FA }
    assertTrue(StrPos.pinWrong(3).contains("تلاش مانده"))
    assertTrue(DariText.PHRASES.keys.none { it.contains(DariText.OPEN) || it.contains(DariText.CLOSE) })
  }

  /* ---------- دورِ ۲: جزءهای `ui/components`، صفحه‌های قدیمیِ `ui/screens` و یاورهای پیام ---------- */

  /** پرونده‌های `ui/screens` که همهٔ نوشتهٔ صفحه‌شان در جدول است (دورِ ۲)؛ متنِ خام فقط در سطرِ «// داده:» */
  private val movedScreens = listOf(
    "DayEndPanel.kt", "DebtBookUi.kt", "DebtorListModel.kt", "DeliveryAddressFix.kt", "DeliveryCashReport.kt", "DeskInvoices.kt",
    "DeskSale.kt", "EasySetupSettingsAndroid.kt", "HandoverQr.kt", "HesabPayAccountAndroid.kt", "OrderDeliveryPrefsUi.kt",
    "OrderRefundUi.kt", "OrderSubstitutionUi.kt", "PickSheet.kt", "ReturnsV2.kt", "SaleDialogs.kt", "SalesHistory.kt",
    "ShiftV2.kt", "SmartInsightsAndroid.kt", "StatementImageButton.kt", "TillGoodsSettingsAndroid.kt", "TillReceiveDialog.kt",
    "TillReceivePhoneAndroid.kt", "Tohid.kt",
  )

  /** یاورهای بی صفحه که پیامِ صفحه می‌سازند (پیش‌تر متنِ خام) */
  private val movedHelpers = listOf(
    "core/report/OwnerReportPlan.kt", "core/storefront/StorefrontEtaApi.kt", "core/storefront/PromoGuardApi.kt",
  )

  /** سقفِ متنِ خامِ باقی‌ماندهٔ `ui/screens` (بی سطرهای «// داده:»)؛ چرخ‌دنده‌ای: فقط پایین بیاید — دورِ ۲: ۳۶۳۳، دورِ ۳: ۲۶۰۲، دورِ ۴: ۲۰۵۲، دورِ ۵: ۱۹۸۴، دورِ ۶: ۱۸۳۵، دورِ ۷: ۱۸۳۱، LOC-06: ۱۴۷۰، LOC-06 دورِ ۲: ۱۷۸، LOC-06 دورِ ۳: ۷۳ */
  //  دستهٔ ۲۷: ۱۸۳۵ ِ دورِ ۶ روی پایه‌ای بی #971 شمرده شد؛ #971 سه سطرِ هم‌شکلِ STORE_PLACE (برچسبِ L.STORE_STOCK و
  //  کلیدواژه‌های جست‌وجوی تنظیمات در SettingsLayout/SettingsSearchIndex) پیش از آن افزوده بود ⇒ ۱۸۳۸ = همان شمارِ ادغام.
  //  دورِ ۷: هفت متنِ زندهٔ `MoreLayout`، `ReportFreshModel`، `ServerPulse`، `VoiceSearch`، `DeskMenu` و `DeliveryDesk` ⇒ ۱۸۳۱
  //  (بقیهٔ متنِ زنده کلیدِ تنظیمات و جدول‌های گزارشِ `DayEndModel`/`HeatmapModel` است — منتظرِ تصمیمِ صاحب‌کار؛ باقی صفحه‌های مرده)
  //  LOC-06 (۱۴۰۵/۰۸/۲۲): ۱۷ صفحهٔ مرده (هیچ راهی از برنامه، کامپیوتر یا آزمون؛ رسیدنِ اعلانی با بسته و import سنجیده شد) و
  //  بخشِ مردهٔ `Storefront.kt` (جز `StorefrontGate` ⇐ پروندهٔ جدا) برداشته شدند ⇒ ۱۴۷۰ در ۲۰۲ پرونده
  //  LOC-06 دورِ ۲ (۱۴۰۵/۰۸/۲۲): (۱) کلیدهای بخشِ تنظیمات (`SettingsLayout`، `SettingsTiers`، `settingsIcon`، ثابت‌های
  //  `…_TITLE`) و واژه‌های سه‌زبانهٔ جست‌وجوی تنظیمات (`SettingsSearchIndex.KEYWORDS`) سطرِ «// داده:» گرفتند (۳۵۴، با یادداشتِ سابقهٔ `Settings.kt`)؛
  //  (۲) زنجیره‌های مرده (هیچ راهی از برنامه، کامپیوتر، آیفون، آزمون یا پیش‌نمایش؛ نامِ هم‌نامِ نسخهٔ ۳ با بسته و import
  //  جدا شد) برداشته شدند: `DeliveryCash`، `Devices` و بخشِ مردهٔ `Orders`، `Reports`، `Vip`، `DeliveryDesk`، `DeliveryDialogs`،
  //  `StorefrontOwnerSettings`، `OwnerReportSettings`، `StockBook`، `SettingsParts` (۶۸۹)؛ مرده‌هایی که ردیفی منتظرِ جایشان در
  //  نسخهٔ ۳ است (`WarehouseDialogs`، `PhoneClaimStep`، `ProductDetail`، `Receipts`، `movementLabelOf`) ماندند؛
  //  (۳) ۲۴۹ متنی که عیناً ویژگیِ بی‌آرگومانِ جدولی است به همان ویژگی رفت (فارسی بایت‌به‌بایت همان) ⇒ ۱۷۸ در ۲۰۱ پرونده.
  //  ماندهٔ همه: ستون‌های `DayEndModel`/`HeatmapModel` (صفحه و خروجی؛ تصمیمِ صاحب‌کار)، صفحه‌های فقط‌پیش‌نمایشِ گوشی
  //  (`PhonePreview` ِ DevShot: `Sale`، `QuickSale`، `Profile`، `Settings`، `Debtors`…) و مرده‌های نگه‌داشته
  //  LOC-06 دورِ ۳ (`claude/e-rows-28l`): ۷۴ متنِ صفحه‌های فقط‌پیش‌نمایش (`Sale`، `QuickSale`، `Profile`، `ProfileModel`، `Settings`،
  //  `Debtors`، `DebtorDetail`، `DebtorTxSheet`، `NavEditor`، `PhoneHomeModel`، `IdleLockChoice`، `VoiceSearch`) و ۲۷ ِ مرده‌های
  //  بی جانشینِ کامل (`WarehouseDialogs`، `PhoneClaimStep`) به `Str*` (فارسی همان؛ `PreviewPagesTextTest`)؛ `ui/screens/ProductDetail.kt`
  //  (+ `movementLabelOf`) و `ui/screens/Receipts.kt` با جانشینِ نسخهٔ ۳ (`ui/v3/products/ProductDetailScreen`، `ui/v3/money/ReceiptsScreen`)
  //  برداشته شدند (۴) ⇒ ۷۳: فقط ستون‌های `DayEndModel` (۵۳) و `HeatmapModel` (۲۰)، صفحه و سرستونِ خروجیِ اکسل با هم — تصمیمِ صاحب‌کار
  //  LOC-06 دورِ ۴ (`claude/e-rows-50d`، تصمیمِ ۱۴۰۵/۰۸/۲۳ #۲۴ «به زبانِ برنامه»): همان ۷۳ به `StrDayEnd` ⇒ ۰ (`DayEndHeatmapLangTest`)
  private val maxRawOldScreens = 0

  /** کفِ پوششِ دریِ نوشته‌های جابه‌جاشدهٔ دورِ ۲ (٪) — اندازهٔ همین کار؛ فقط بالا برود */
  private val minMovedPercent = 14

  private fun movedFiles(): List<File> =
    app.resolve("ui/components").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() +
      movedScreens.map { app.resolve("ui/screens/$it") } + movedHelpers.map { app.resolve(it) }

  /** متنِ فارسیِ خامِ یک پرونده؛ سطرِ «// داده:» (دادهٔ ذخیره‌شده، کلید یا خروجی) شمرده نمی‌شود */
  private fun raw(f: File): List<String> {
    val text = f.readLines().filterNot { it.contains("// داده") }
      .filterNot { it.trimStart().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
      .joinToString("\n") { it.replace(Regex("""\s//\s.*$"""), "") }
    return Regex(""""((?:[^"\\\n]|\\.)*)"""").findAll(text).map { it.groupValues[1] }.filter { letter.containsMatchIn(it) }.toList()
  }

  @Test fun `جزءهای ui_components و صفحه‌های قدیمیِ جابه‌جاشده و یاورها متنِ خامِ فارسی ندارند`() {
    val bad = ArrayList<String>()
    val files = movedFiles()
    for (f in files) {
      assertTrue("${f.path} دیگر نیست — از فهرست بردارید", f.isFile)
      for (s in raw(f)) bad += "${f.relativeTo(app).invariantSeparatorsPath}: «$s»"
    }
    assertTrue("پرونده‌ای پیدا نشد", files.size > 60)
    assertTrue(
      "نوشتهٔ فارسیِ خام (دری و زبان‌های دیگر آن را نمی‌بینند) — به جدولِ Str* با t(…) ببرید؛ داده را با «// داده: دلیل» نشان بدهید:\n" +
        bad.joinToString("\n"),
      bad.isEmpty(),
    )
  }

  /* ---------------- دورِ ۳: صفحه‌های بزرگِ کامپیوتر، گدام، خرید، تخفیف، حساب‌پی و گزارش ---------------- */

  /** پرونده‌های `ui/screens` که دورِ ۳ همهٔ نوشتهٔ صفحه‌شان را به جدول برد؛ متنِ خام فقط در سطرِ «// داده:» (برگهٔ اکسل، نامِ پرونده، منطق) */
  private val movedScreens3 = listOf(
    "DeskCustomers.kt", "DeskProducts.kt", "DeskPurchasing.kt", "DevicesHw.kt", "HesabPayAccountSettings.kt", "Promos.kt",
    "PurchaseBills.kt", "PurchaseOrders.kt", "PurchaseSuggestView.kt", "ReportFresh.kt",
  )

  /** کفِ پوششِ دریِ کلیدهای پرونده‌های دورِ ۳ (٪) — اندازهٔ همین کار (۱۴٫۸٪ = ۱۲۱ از ۸۱۶)؛ فقط بالا برود */
  private val minMoved3Percent = 14

  @Test fun `پرونده‌های جابه‌جاشدهٔ دورِ ۳ متنِ خامِ فارسی ندارند و پوششِ دری‌شان از کف پایین نمی‌آید`() {
    val bad = ArrayList<String>()
    val files = movedScreens3.map { app.resolve("ui/screens/$it") }
    for (f in files) {
      assertTrue("${f.path} دیگر نیست — از فهرست بردارید", f.isFile)
      for (s in raw(f)) bad += "${f.name}: «$s»"
    }
    assertTrue(
      "نوشتهٔ فارسیِ خام (دری و زبان‌های دیگر آن را نمی‌بینند) — به جدولِ Str* با t(…) ببرید؛ داده را با «// داده: دلیل» نشان بدهید:\n" +
        bad.joinToString("\n"),
      bad.isEmpty(),
    )
    val byKey = rows(I18nTables.all).groupBy { it.table + "." + it.key }.mapValues { (_, l) -> l.any { it.dari != it.fa } }
    val keys = LinkedHashSet<String>()
    for (f in files + app.resolve("ui/screens/StockBook.kt")) for (m in Regex("""\b(Str\w*)\.(\w+)""").findAll(code(f))) {
      val k = m.groupValues[1] + "." + m.groupValues[2]
      if (k in byKey) keys += k
    }
    val c = keys.count { byKey[it] == true }
    val p = pct(c, keys.size)
    println("dari-moved3 ${keys.size} $c ${"%.1f".format(p)}%")
    assertTrue("جدولی خوانده نشد", keys.size > 400)
    assertTrue("پوششِ دریِ نوشته‌های دورِ ۳ ${"%.1f".format(p)}٪ < $minMoved3Percent٪", p >= minMoved3Percent)
  }

  @Test fun `فارسیِ نوشته‌های دورِ ۳ همان متنِ پیشین است و دری و زبان‌های دیگر عوض می‌شوند`() {
    I18n.source = { Lang.FA }
    assertEquals("بهای میانگینِ واحد", StrStockPages.avgUnitCost)
    assertEquals("شمارش ثبت شد — 3 اختلاف اصلاح شد", StrStockPages.countSaved(3))
    assertEquals("بیشینهٔ موجودی", StrProductPages.maxStock)
    assertEquals("«برنج» ثبت شد", StrProductPages.nameRecorded("برنج"))
    assertEquals("فاکتورِ خرید #12 ثبت شد — 1,200 ؋، کالاها به انبار رفت", StrPurchasePages.purchaseSaved(12, "1,200"))
    assertEquals("موجودی و بها", ir.vil3ntec.tohid.ui.screens.SettingsLayout.labelOf(ir.vil3ntec.tohid.ui.screens.SettingsLayout.STOCK_MODE))
    I18n.source = { Lang.PRS }
    //  واژه‌ای که در فهرستِ دری نیست همان فارسی می‌ماند
    assertEquals("بهای میانگینِ واحد", StrStockPages.avgUnitCost)
    assertEquals("شمارش ثبت گردید — 3 اختلاف اصلاح گردید", StrStockPages.countSaved(3))
    assertEquals("نامِ مشابه در دکان: ", StrProductPages.similarName)
    assertEquals("فیصدِ تخفیف", StrPromoPages.discountPercent)
    assertEquals("فاکتورِ خرید #12 ثبت گردید — 1,200 ؋، کالاها به گدام رفت", StrPurchasePages.purchaseSaved(12, "1,200"))
    //  نامِ کالا (داده) در دری همان می‌ماند، حتی اگر واژهٔ فهرست باشد
    assertEquals("«انبار تازه» ثبت گردید", StrProductPages.nameRecorded("انبار تازه"))
    I18n.source = { Lang.EN }
    val layout = ir.vil3ntec.tohid.ui.screens.SettingsLayout
    assertEquals("Stock & cost", layout.labelOf(layout.STOCK_MODE))
    assertEquals(layout.STOCK_MODE, layout.keyOf(layout.labelOf(layout.STOCK_MODE)))
    assertEquals("Save 1 difference", StrStockPages.saveDiffs(1))
    assertEquals("Save 2 differences", StrStockPages.saveDiffs(2))
  }

  /* ---------------- دورِ ۴: سرورها، کارمندان، چاپگر و بارکدخوان، بانکِ تصاویر، تخفیف و پیوند، کارِ گروهی روی کالا ---------------- */

  /** پرونده‌های `ui/screens` که دورِ ۴ همهٔ نوشتهٔ صفحه‌شان را به جدول برد؛ متنِ خام فقط در سطرِ «// داده:» (کلید، نامِ ذخیره‌شده، خروجی) */
  private val movedScreens4 = listOf(
    "BankPhotoSource.kt", "BulkPriceDialog.kt", "BulkSheets.kt", "CrmProfileUi.kt", "EasySetupAndroid.kt", "ImageBankPicker.kt",
    "ImageBulkImport.kt", "ProductImportDialog.kt", "ProductListModel.kt", "PromoText.kt", "Servers.kt", "StorefrontCampaigns.kt",
    "SupplierExtras.kt", "TeamStaff.kt",
  )

  /** کفِ پوششِ دریِ کلیدهای پرونده‌های دورِ ۴ (٪) — اندازهٔ همین کار (۱۱٫۴٪ = ۵۷ از ۵۰۱)؛ فقط بالا برود */
  private val minMoved4Percent = 11

  @Test fun `پرونده‌های جابه‌جاشدهٔ دورِ ۴ متنِ خامِ فارسی ندارند`() {
    val bad = ArrayList<String>()
    val files = movedScreens4.map { app.resolve("ui/screens/$it") }
    for (f in files) {
      assertTrue("${f.path} دیگر نیست — از فهرست بردارید", f.isFile)
      for (s in raw(f)) bad += "${f.name}: «$s»"
    }
    assertTrue(
      "نوشتهٔ فارسیِ خام (دری و زبان‌های دیگر آن را نمی‌بینند) — به جدولِ Str* با t(…) ببرید؛ داده را با «// داده: دلیل» نشان بدهید:\n" +
        bad.joinToString("\n"),
      bad.isEmpty(),
    )
  }

  @Test fun `پوششِ دریِ نوشته‌های دورِ ۴ چاپ می‌شود و از کف پایین نمی‌آید`() {
    val byKey = rows(I18nTables.all).groupBy { it.table + "." + it.key }.mapValues { (_, l) -> l.any { it.dari != it.fa } }
    val keys = LinkedHashSet<String>()
    for (f in movedScreens4.map { app.resolve("ui/screens/$it") }) for (m in Regex("""\b(Str\w*)\.(\w+)""").findAll(code(f))) {
      val k = m.groupValues[1] + "." + m.groupValues[2]
      if (k in byKey) keys += k
    }
    val c = keys.count { byKey[it] == true }
    val p = pct(c, keys.size)
    println("dari-moved4 ${keys.size} $c ${"%.1f".format(p)}%")
    assertTrue("جدولی خوانده نشد", keys.size > 400)
    assertTrue("پوششِ دریِ نوشته‌های دورِ ۴ ${"%.1f".format(p)}٪ < $minMoved4Percent٪", p >= minMoved4Percent)
  }

  @Test fun `فارسیِ نوشته‌های دورِ ۴ همان متنِ پیشین است و دری و زبان‌های دیگر عوض می‌شوند`() {
    val promo = PromoText
    val rule = ir.vil3ntec.tohid.core.promo.PromoCore.Rule(id = "r1", channels = listOf("pos"), kind = "bxgy", buy = 2, get = 1, bp = 10000)
    val days = rule.copy(window = ir.vil3ntec.tohid.core.promo.PromoCore.Window(days = listOf(5L, 6L)))
    I18n.source = { Lang.FA }
    assertEquals("فعال: سرورِ دکان", StrSetupPages.activeServer("سرورِ دکان"))
    assertEquals("قفل پس از 5 خطا", StrSetupPages.lockedAfter(5))
    assertEquals("بی سقف", StrSetupPages.noCap)
    assertEquals("ردیف 3 «برنج»: قیمت", StrCatalogPages.rowChanges(3, "برنج", "قیمت"))
    assertEquals("فاکتورِ #12", StrCatalogPages.invoiceNo(12))
    //  جدول‌ها، روزها و برچسبِ enum ِ `PromoText` و `ProductListModel` همان فارسیِ پیشین‌اند
    assertEquals(listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"), promo.DAYS.map { it.second })
    assertEquals(listOf(6L, 7L, 1L, 2L, 3L, 4L, 5L), promo.DAYS.map { it.first })
    assertEquals("شنبه، جمعه", promo.windowText(days))
    assertEquals("همیشه", promo.windowText(rule))
    assertEquals("قیمتِ ویژه", promo.kindLabel("price"))
    assertEquals("بخر 2 بگیر 1 رایگان", promo.valueText(rule))
    assertEquals("زمان‌بندی‌شده", PromoText.Status.SCHEDULED.label)
    assertEquals("کم‌موجود", ProductListModel.Tab.LOW.label)
    I18n.source = { Lang.PRS }
    assertEquals("بدون سقف", StrSetupPages.noCap)
    assertEquals("انترنت قطع است — دوباره کوشش کنید", StrImagePages.offlineRetry)
    //  نامِ سرور (داده) در دری همان می‌ماند، حتی اگر واژهٔ فهرست باشد
    assertEquals("این همان «سرورِ تازه» است؛ آدرس به آن افزوده می‌شود.", StrSetupPages.sameServer("سرورِ تازه"))
    I18n.source = { Lang.EN }
    //  هر بار خوانده می‌شوند، پس عوض کردنِ زبان بی بازکردنِ دوباره اثر دارد؛ «همیشه» ِ کارتِ «زمان‌دار» با همین سنجیده می‌شود
    assertEquals("Always", promo.windowText(rule))
    assertEquals(StrMarketPages.always, promo.windowText(rule))
    assertEquals("Sat، Fri", promo.windowText(days))
    assertEquals("Special price", promo.kindLabel("price"))
    assertEquals("Buy 2 get 1 free", promo.valueText(rule))
    assertEquals("Scheduled", PromoText.Status.SCHEDULED.label)
    assertEquals("Low stock", ProductListModel.Tab.LOW.label)
    assertEquals("Save 2 items and update 1", StrCatalogPages.saveAndUpdate(2, 1))
    assertEquals("1 warning", StrCatalogPages.warningsN(1))
    assertEquals("3 warnings", StrCatalogPages.warningsN(3))
  }

  /* ---------------- دورِ ۵: لوگوی ویترین، نقشهٔ ساعت و روز، دوربینِ گوشی، بسته‌ها، گزارشِ مشتریِ گوشی، اسکنِ بارکد ---------------- */

  /** پرونده‌های زندهٔ کوچکِ `ui/screens` که دورِ ۵ همهٔ نوشتهٔ صفحه‌شان را به جدول برد (`HeatmapModel`/`DayEndModel` نه: ستونِ خروجی) */
  private val movedScreens5 = listOf(
    "CustomerReportsPhone.kt", "HeatmapPanel.kt", "PackEditor.kt", "PhoneLinkHost.kt", "ScanSheet.kt", "StorefrontMediaPanel.kt",
  )

  @Test fun `پرونده‌های جابه‌جاشدهٔ دورِ ۵ متنِ خامِ فارسی ندارند`() {
    val bad = ArrayList<String>()
    for (f in movedScreens5.map { app.resolve("ui/screens/$it") }) {
      assertTrue("${f.path} دیگر نیست — از فهرست بردارید", f.isFile)
      for (s in raw(f)) bad += "${f.name}: «$s»"
    }
    assertTrue(
      "نوشتهٔ فارسیِ خام (دری و زبان‌های دیگر آن را نمی‌بینند) — به جدولِ Str* با t(…) ببرید؛ داده را با «// داده: دلیل» نشان بدهید:\n" +
        bad.joinToString("\n"),
      bad.isEmpty(),
    )
  }

  /**
   *  کفِ پوششِ دریِ کلیدهای پرونده‌های دورِ ۵ (٪) — اندازهٔ همین کار روی کامپیوتر (۱۹٫۰٪ = ۱۲ از ۶۳؛ روی کامپیوتر جدولِ `phrases`
   *  «تنظیمات گوشی» ِ `StrSale.cameraPermissionDenied` را پیشاپیش «تنظیمات سیستم» کرده، پس دری چیزی برای عوض کردن ندارد)؛ فقط بالا برود.
   *  تنها پایین آمدن (۱۹ ⇐ ۱۸، ۱۴۰۵/۰۸/۲۲): برداشتنِ `crmTabs` ِ مردهٔ `CustomerReportsPhone.kt` دو کلیدِ `StrReach.tabAging`/`tabRfm`
   *  را از این پرونده‌ها برد (۱۸٫۰٪ = ۱۱ از ۶۱) — هر دو در `ReportsScreen`/`ReportsDesk` ِ نسخهٔ ۳ زنده‌اند؛ پوششِ هیچ نوشته‌ای کم نشد.
   */
  private val minMoved5Percent = 18

  @Test fun `پوششِ دریِ نوشته‌های دورِ ۵ چاپ می‌شود و از کف پایین نمی‌آید`() {
    val byKey = rows(I18nTables.all).groupBy { it.table + "." + it.key }.mapValues { (_, l) -> l.any { it.dari != it.fa } }
    val keys = LinkedHashSet<String>()
    for (f in movedScreens5.map { app.resolve("ui/screens/$it") }) for (m in Regex("""\b(Str\w*)\.(\w+)""").findAll(code(f))) {
      val k = m.groupValues[1] + "." + m.groupValues[2]
      if (k in byKey) keys += k
    }
    val c = keys.count { byKey[it] == true }
    val p = pct(c, keys.size)
    println("dari-moved5 ${keys.size} $c ${"%.1f".format(p)}%")
    assertTrue("جدولی خوانده نشد", keys.size > 50)
    assertTrue("پوششِ دریِ نوشته‌های دورِ ۵ ${"%.1f".format(p)}٪ < $minMoved5Percent٪", p >= minMoved5Percent)
  }

  @Test fun `فارسیِ نوشته‌های دورِ ۵ همان متنِ پیشین است و دری و زبان‌های دیگر عوض می‌شوند`() {
    //  `LocaleChoice` روی کامپیوتر `I18n.desktop` را روشن می‌کند و آزمونِ دیگری در همین JVM شاید پیش‌تر آن را ساخته باشد
    val wasDesk = I18n.desktop
    I18n.desktop = false
    try { round5Texts() } finally { I18n.desktop = wasDesk }
  }

  private fun round5Texts() {
    val kinds = ir.vil3ntec.tohid.core.image.StorefrontMediaSpec.Kind.entries
    val logo = ir.vil3ntec.tohid.core.image.StorefrontMediaSpec.Kind.LOGO
    I18n.source = { Lang.FA }
    //  نامِ نوع همان برچسبِ فارسیِ `Kind.label` است که پیامِ خطای هسته هنوز با آن ساخته می‌شود
    for (k in kinds) assertEquals(k.label, mediaKindName(k))
    assertEquals("لوگو ثبت شد", StrPanelPages.mediaSaved(mediaKindName(logo)))
    assertEquals("12 فروش", StrPanelPages.salesN(12))
    assertEquals(" — کهنه‌ترین 30 روز", StrPanelPages.oldestDays(30L))
    assertEquals("3 خرید · 1,200 ؋ · آخرین خرید 5 روز پیش", StrPanelPages.rfmLine(3, "1,200", 5L))
    assertEquals(listOf("سنِ بدهی", "تحلیلِ خرید (RFM)"), listOf(StrReach.tabAging, StrReach.tabRfm))
    assertEquals("چند عدد", StrScanPages.howMany("عدد"))
    assertEquals("بارکدهای بسته (با «،» جدا)", StrScanPages.packBarcodes(StrScanPages.pack))
    assertEquals("عکسِ «برنج» را بگیرید؛ همان لحظه در کامپیوتر باز می‌شود.", StrScanPages.takePhotoOf("برنج"))
    I18n.source = { Lang.PRS }
    assertEquals("بدون فروش", StrPanelPages.noSales)
    assertEquals("لوگو ثبت گردید", StrPanelPages.mediaSaved(mediaKindName(logo)))
    //  نامِ کالا (داده) در دری همان می‌ماند، حتی اگر واژهٔ فهرست باشد
    assertEquals("عکسِ مبایل روی «گوشی سامسونگ» نشست", StrScanPages.phonePhotoSet("گوشی سامسونگ"))
    I18n.source = { Lang.EN }
    assertEquals(listOf("Logo", "Header image"), kinds.map { mediaKindName(it) })
    assertEquals("Logo saved", StrPanelPages.mediaSaved(mediaKindName(logo)))
    assertEquals("1 sale", StrPanelPages.salesN(1))
    assertEquals("12 sales", StrPanelPages.salesN(12))
    assertEquals(listOf("Debt aging", "Purchase analysis (RFM)"), listOf(StrReach.tabAging, StrReach.tabRfm))
    assertEquals("Pack barcodes (separated by “,”)", StrScanPages.packBarcodes(StrScanPages.pack))
    //  اسکنِ یک کادر: روی گوشی از دوربین، روی کامپیوتر از بارکدخوان (`td`)
    assertEquals("Hold the barcode up to the camera — the field fills on the first read", StrScanPages.scanOnce)
    I18n.desktop = true
    assertEquals("Scan the barcode with the barcode scanner — the field fills on the first read", StrScanPages.scanOnce)
  }

  /* ---------------- دورِ ۶: پوششِ کامل — شکل‌های فقط‌فارسی، حسابِ قرض‌دار، نمایشگرِ مشتری، برنامهٔ خرید ---------------- */

  /**
   *  شکل‌هایی که دری‌شان در خودِ `DariText.PHRASES` آمده («دوباره بزنید» ⇐ «دوباره کوشش کنید»، «مهلت» ⇐ «میعاد»،
   *  «نمی‌خواند» ⇐ «برابر نیست»، «ایستا» ⇐ «ثابت»، «پاسخ نداد» ⇐ «جواب نداد»، «شلوغ» ⇐ «مصروف»، «خیلی» ⇐ «زیاد»)
   *  ولی واژه‌به‌واژه درست نمی‌شوند؛ هر نوشتهٔ تازه با یکی از این‌ها جملهٔ کاملِ دری می‌خواهد.
   */
  private val farsiOnlyForms = listOf("دوباره بزنید", "دوباره بکوشید", "مهلت", "نمی‌خواند", "ایستا", "پاسخ نداد", "شلوغ", "خیلی")

  /** همان شکل با معنای دیگر که در دری هم درست است — با دلیل؛ هر تازه‌ای یعنی تصمیم */
  private val farsiOnlyAllowed = listOf(
    "رمز را دوباره بزنید", "رمزِ حساب را دوباره بزنید", // «زدن» ِ رمز = نوشتنِ دوباره، نه «دوباره کوشش»
    "» را دوباره بزنید", // زدنِ دکمهٔ نام‌دار
    "چیزی نمی‌خواند", // خواندنِ پرونده، نه «برابر نبودن»
  )

  private fun farsiOnlyLeft(text: String): List<String> {
    var t = text
    for (a in farsiOnlyAllowed) t = t.replace(a, "")
    return farsiOnlyForms.filter { t.contains(it) }
  }

  @Test fun `هیچ نوشتهٔ دری شکلِ فقط‌فارسیِ فهرست را ندارد — برنامهٔ دکان، برنامهٔ خرید و ویترین`() {
    val bad = ArrayList<String>()
    for (r in rows(I18nTables.all)) farsiOnlyLeft(r.dari).forEach { bad += "${r.table}.${r.key}: «$it» در «${r.dari}»" }
    //  برنامهٔ خرید (`ShopperText`) و ویترینِ وب همان `DariText.of` را می‌گذرانند؛ هر `{}` یک مقدارِ asIs
    for (k in DariSources.shopperTexts() + DariSources.webTexts()) {
      var i = 0
      val marked = k.split("{}").reduce { a, b -> a + DariText.protect("v${i++}") + b }
      farsiOnlyLeft(DariText.of(marked)).forEach { bad += "خرید/وب: «$it» در «$k»" }
    }
    assertTrue("شکلِ فقط‌فارسی در دری ماند — جملهٔ کاملِ دری در DariText.PHRASES بنویسید:\n" + bad.joinToString("\n"), bad.isEmpty())
  }

  /** پرونده‌های زندهٔ `ui/screens` که دورِ ۶ همهٔ نوشته‌شان را به جدول برد */
  private val movedScreens6 = listOf("CustomerDisplay.kt")

  @Test fun `حسابِ قرض‌دار و نمایشگرِ مشتری از لایهٔ زبان می‌گذرند`() {
    //  `StrDebtor` تا دورِ ۵ یک `t` ِ محلیِ فقط‌فارسی داشت؛ حالا جدولِ همگانی است
    assertTrue(I18nTables.all.contains(StrDebtor))
    assertFalse("ui/screens/StrDebtor.kt برگشت", app.resolve("ui/screens/StrDebtor.kt").exists())
    for (f in movedScreens6.map { app.resolve("ui/screens/$it") }) {
      assertTrue("${f.path} دیگر نیست — از فهرست بردارید", f.isFile)
      assertTrue("${f.name}: نوشتهٔ فارسیِ خام: ${raw(f)}", raw(f).isEmpty())
    }
    I18n.source = { Lang.FA }
    assertEquals("بدهی ندارد", StrDebtor.noDebt)
    assertEquals("3 ماه است که بدهکار است — از 1405/08/01", StrDebtor.daysInDebt("3 ماه", "1405/08/01"))
    assertEquals("قرض‌دار تازه", StrDebtor.newDebtor)
    assertEquals("سپاس از خرید شما", StrPanelPages.thanksForPurchase)
    assertEquals("3 تازه", StrCatalogPages.freshN(3))
    I18n.source = { Lang.PRS }
    assertEquals("قرض ندارد", StrDebtor.noDebt)
    assertEquals("قرض‌دار جدید", StrDebtor.newDebtor)
    assertEquals("تشکر از خرید شما", StrPanelPages.thanksForPurchase)
    //  نامِ مشتری (داده) در دری همان می‌ماند، حتی اگر واژهٔ فهرست باشد
    assertEquals("«فروشگاه گوشی» حذف گردید", StrDebtor.debtorDeleted("فروشگاه گوشی"))
    I18n.source = { Lang.EN }
    assertEquals("No debt", StrDebtor.noDebt)
    assertEquals("Thank you for your purchase", StrPanelPages.thanksForPurchase)
  }

  @Test fun `فارسیِ نوشته‌های دورِ ۶ همان متنِ پیشین است و دری جملهٔ کامل می‌گیرد`() {
    I18n.source = { Lang.FA }
    assertEquals("ایستا", StrDevicePages.stable)
    assertEquals("شلوغ: +5 دقیقه", StrSettingsPages.etaBusyNow(5))
    assertEquals("مهلت: 10:30", StrPay.expiresIn("10:30"))
    I18n.source = { Lang.PRS }
    assertEquals("ثابت", StrDevicePages.stable)
    assertEquals("مصروف: +5 دقیقه", StrSettingsPages.etaBusyNow(5))
    assertEquals("سفارش ختم شده و میعادِ «مشکل دارم» گذشته است؛ این گفت‌وگو فقط خواندنی است.", StrOrdersHub.closedOrder(null))
    assertEquals("میعاد: 10:30", StrPay.expiresIn("10:30"))
    I18n.source = { Lang.EN }
    assertEquals("stable", StrDevicePages.stable)
    assertEquals("Busy: +5 min", StrSettingsPages.etaBusyNow(5))
  }

  @Test fun `متنِ خامِ باقی‌ماندهٔ صفحه‌های قدیمی فقط کم می‌شود`() {
    val files = app.resolve("ui/screens").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    val n = files.sumOf { raw(it).size }
    println("dari-old-raw ${files.size} $n")
    assertTrue("صفحه‌ای پیدا نشد", files.size > 150)
    assertTrue("متنِ خامِ تازه در ui/screens ($n > $maxRawOldScreens) — به جدولِ Str* ببرید", n <= maxRawOldScreens)
  }

  /* ---------------- دورِ ۷: لایه‌های دفتر، همگام‌سازی، چاپ و به‌روزرسانی ---------------- */

  /** لایه‌هایی که پیام‌شان (خطا و حالِ کار) به کاربر می‌رسد؛ دورِ ۷ همه را به جدول برد — متنِ خام فقط در سطرِ «// داده:» */
  private val appLayers = listOf("data", "sync", "print", "update")

  @Test fun `لایه‌های دفتر، همگام‌سازی، چاپ و به‌روزرسانی متنِ خامِ فارسی ندارند`() {
    val bad = ArrayList<String>()
    var files = 0
    //  جایگزین‌های کامپیوتر برای همان لایه‌ها (`desktop/src/main/kotlin`: `UpdaterDesk`، `AtRestKeysDesk`، `ReportPrintDesk`…) هم
    val desk = DesktopPhrases.repo().resolve("desktop/src/main/kotlin/ir/vil3ntec/tohid")
    for (base in listOf(app, desk)) for (d in appLayers) {
      val dir = base.resolve(d)
      if (base == app) assertTrue("پوشهٔ $d نیست", dir.isDirectory)
      if (!dir.isDirectory) continue
      for (f in dir.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
        files++
        for (s in raw(f)) bad += "${f.relativeTo(base.parentFile.parentFile.parentFile).invariantSeparatorsPath}: «$s»"
      }
    }
    assertTrue("پرونده‌ای پیدا نشد ($files)", files > 250)
    assertTrue(
      "نوشتهٔ فارسیِ خام در لایه‌های دفتر/همگام‌سازی/چاپ/به‌روزرسانی (دری و زبان‌های دیگر آن را نمی‌بینند) — به جدولِ " +
        "StrLedger/StrGoods/StrAppData/StrSync/StrPrint/StrUpdate با t(…) ببرید؛ داده را با «// داده: دلیل» نشان بدهید:\n" +
        bad.joinToString("\n"),
      bad.isEmpty(),
    )
  }

  @Test fun `فارسیِ پیام‌های دورِ ۷ همان متنِ پیشین است و دری و زبان‌های دیگر عوض می‌شوند`() {
    I18n.source = { Lang.FA }
    assertEquals("مشتری پیدا نشد", StrLedger.customerNotFound)
    assertEquals("کشوی «اصلی» از قبل باز است", StrLedger.drawerAlreadyOpen("اصلی"))
    assertEquals("فقط 3 کیلو موجود است", StrGoods.onlyInStock("3", "کیلو"))
    assertEquals("چاپگر در شبکه جواب نداد (10.0.0.9) — روشن است؟", StrPrint.netNoAnswer("10.0.0.9"))
    assertEquals("همگام با سرور", StrSync.dotGreen)
    assertEquals("نسخهٔ شما تازه‌ترین است.", StrUpdate.upToDate)
    assertEquals("این فایل، پشتیبانِ فروشگاه من نیست", StrAppData.notOurBackup)
    I18n.source = { Lang.PRS }
    //  نامِ مشتری و کالا (داده) در دری همان می‌ماند
    assertEquals("«فروشگاه گوشی»: 2 موجود است، شما 5 خواستید.", StrLedger.shortStock("فروشگاه گوشی", "2", "", "5"))
    assertEquals("این فایل، پشتیبانِ فروشگاه من نیست", StrAppData.notOurBackup) // «فروشگاه من» نامِ برنامه است
    I18n.source = { Lang.EN }
    assertEquals("Customer not found", StrLedger.customerNotFound)
    assertEquals("In sync with the server", StrSync.dotGreen)
    I18n.source = { Lang.PS }
    assertEquals("پېرودونکی پیدا نه شو", StrLedger.customerNotFound)
  }

  @Test fun `برچسب‌های فاکتور و صورت‌حسابِ چاپی در دری از لایهٔ واژه می‌گذرند و در فارسی همان پیشین‌اند`() {
    I18n.source = { Lang.FA }
    assertEquals(ir.vil3ntec.tohid.core.receipt.ReceiptLabels.FA, ir.vil3ntec.tohid.print.Receipts.labels())
    assertEquals(ir.vil3ntec.tohid.core.receipt.StatementLabels.FA, ir.vil3ntec.tohid.print.Receipts.statementLabels())
    //  #348: انگلیسی و پشتو دیگر برچسبِ فارسی نمی‌گیرند (پیش‌تر همان `ReceiptLabels.FA`) — آزمونِ کاملشان `ReceiptLangTest`
    I18n.source = { Lang.EN }
    assertEquals("Invoice no.", ir.vil3ntec.tohid.print.Receipts.labels().invoiceNo)
    I18n.source = { Lang.PRS }
    val l = ir.vil3ntec.tohid.print.Receipts.labels()
    assertEquals("دکان", l.fallbackShop)
    assertEquals(DariText.of(ir.vil3ntec.tohid.core.receipt.ReceiptLabels.FA.footer), l.footer)
    assertEquals("دکان", ir.vil3ntec.tohid.print.Receipts.statementLabels().fallbackShop)
    assertEquals(l, ir.vil3ntec.tohid.print.Receipts.giftLabels().base)
    //  هیچ برچسبِ کاغذی در دری شکلِ فقط‌فارسی یا نشانهٔ جای خالی ندارد
    val all = listOf(l.toString(), ir.vil3ntec.tohid.print.Receipts.statementLabels().toString(), ir.vil3ntec.tohid.print.Receipts.giftLabels().toString())
    for (t in all) {
      assertTrue(t, farsiOnlyLeft(t).isEmpty())
      assertFalse(t, t.any { it == DariText.OPEN || it == DariText.CLOSE })
      assertFalse("«فروشگاه» در کاغذِ دری: $t", Regex("(?<![\\p{L}\\p{M}\\u200C])فروشگاه(?! من)").containsMatchIn(t))
    }
  }

  @Test fun `اندازهٔ دریِ نوشته‌های جابه‌جاشدهٔ دورِ ۲ چاپ می‌شود و از کف پایین نمی‌آید`() {
    val byKey = rows(I18nTables.all).groupBy { it.table + "." + it.key }.mapValues { (_, l) -> l.any { it.dari != it.fa } }
    val keys = LinkedHashSet<String>()
    for (f in movedFiles()) for (m in Regex("""\b(Str\w*)\.(\w+)""").findAll(code(f))) {
      val k = m.groupValues[1] + "." + m.groupValues[2]
      if (k in byKey) keys += k
    }
    val c = keys.count { byKey[it] == true }
    val p = pct(c, keys.size)
    println("dari-moved ${keys.size} $c ${"%.1f".format(p)}%")
    assertTrue("جدولی خوانده نشد", keys.size > 300)
    assertTrue("پوششِ دریِ نوشته‌های جابه‌جاشده ${"%.1f".format(p)}٪ < $minMovedPercent٪", p >= minMovedPercent)
  }

  @Test fun `خطای ورودیِ صفِ پیک از جدول می‌گذرد و فارسی همان پیامِ هسته است`() {
    for (m in ir.vil3ntec.tohid.core.courier.CourierInputText.ALL) {
      I18n.source = { Lang.FA }
      assertEquals(m, courierInputText(m))
      I18n.source = { Lang.PRS }
      assertEquals(DariText.of(m), courierInputText(m))
      I18n.source = { Lang.EN }
      val en = courierInputText(m).orEmpty()
      assertTrue("$m: انگلیسی ندارد", en.isNotBlank() && !letter.containsMatchIn(en))
    }
    assertEquals("x", courierInputText("x"))
    //  هسته پیامِ خامِ تازه‌ای کنارِ این ثابت‌ها نمی‌سازد
    val core = DesktopPhrases.repo().resolve("core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/courier/CourierQueue.kt").readText()
    assertFalse("CourierInputError با متنِ خام", Regex("""CourierInputError\("[^"]*",\s*"""").containsMatchIn(core))
  }

  @Test fun `فارسیِ نوشته‌های دورِ ۲ همان متنِ پیشین است و دری عوض می‌شود`() {
    I18n.source = { Lang.FA }
    assertEquals("دریافتِ 1,200 ؋ از «کریم» ثبت شد", StrSalePages.received("1,200", "کریم"))
    assertEquals("نشانه یا نشانی لازم است.", StrOrderPages.addrNeedOne)
    assertEquals(
      "هر روز ساعتِ 21:00: امروز تا ساعتِ 21:00",
      ir.vil3ntec.tohid.core.report.OwnerReportPlan.coverage(ir.vil3ntec.tohid.core.report.OwnerReportPlan.Settings(hour = 21)),
    )
    assertEquals("قرص حداکثر 5 جا دارد", ir.vil3ntec.tohid.ui.components.common.NavLayouts.Problem.PILL_FULL.message)
    assertEquals("ثبتِ سریع و واحدِ بزرگ در صندوق", ir.vil3ntec.tohid.ui.screens.SettingsLayout.labelOf(ir.vil3ntec.tohid.ui.screens.SettingsLayout.TILL_GOODS))
    I18n.source = { Lang.PRS }
    assertEquals("دریافتِ 1,200 ؋ از «کریم» ثبت گردید", StrSalePages.received("1,200", "کریم"))
    assertEquals("نشانه یا آدرس لازم است.", StrOrderPages.addrNeedOne)
    assertEquals("ترازو هنوز ثابت نیست — صبر کنید و دوباره کوشش کنید", StrSalePages.scaleMotion)
    //  نامِ مشتری با واژهٔ فهرست در دری همان می‌ماند
    assertTrue(StrSalePages.received("1,200", "فروشگاه گوشی").contains("«فروشگاه گوشی»"))
    I18n.source = { Lang.EN }
    val layout = ir.vil3ntec.tohid.ui.screens.SettingsLayout
    assertEquals("Quick add and big units at the till", layout.labelOf(layout.TILL_GOODS))
    assertEquals(layout.TILL_GOODS, layout.keyOf(layout.labelOf(layout.TILL_GOODS)))
  }
}
