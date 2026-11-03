package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.text.DariText
import org.junit.After
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

  /** کفِ پوشش (٪) — اندازهٔ همین کار؛ فقط بالا برود */
  private val minCorePercent = 15

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

  /** کدِ پرونده بی توضیح‌ها (`//`، `/* */`، سطرهای `*`) */
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
}
