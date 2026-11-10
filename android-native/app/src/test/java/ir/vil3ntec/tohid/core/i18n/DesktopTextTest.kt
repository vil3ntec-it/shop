package ir.vil3ntec.tohid.core.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 *  LOC-06 — نوشته‌های خودِ کامپیوتر (`desktop/src/main/kotlin`، `desktop/src/shim/kotlin`)؛ کدِ مشترکِ اندروید را
 *  `I18nCoverageTest` و `DariCoverageTest` می‌سنجند، این پوسته را هیچ‌کدام (جز لایه‌های دفتر/همگام‌سازی/چاپ/به‌روزرسانی).
 *
 *  1. هر متنِ خامی که به جدول رفت فارسی‌اش **بایت‌به‌بایت** همان پیشین است و انگلیسی و پشتو دارد.
 *  2. `StrDesktop` ترجمهٔ تازه ندارد: انگلیسی و پشتوی هر ردیف رونوشتِ همان جملهٔ بازبینی‌شدهٔ جدولِ دیگر است و فارسیِ دو جمله
 *     فقط در کسرهٔ اضافه یا نیم‌فاصله فرق دارد (وگرنه همان جدول مستقیم خوانده می‌شد).
 *  3. متنِ خامِ باقی‌مانده (بی ترجمهٔ بازبینی‌شده؛ فهرست برای صاحب‌کار در `docs/i18n-glossary.md`، بخشِ «پوستهٔ کامپیوتر») سقفِ چرخ‌دنده‌ای دارد.
 */
class DesktopTextTest {

  @After fun reset() {
    I18n.source = { Lang.FA }
    I18n.audit = null
  }

  private fun at(lang: Lang, text: () -> String): String {
    I18n.source = { lang }
    return text()
  }

  /** متنِ خامِ پیشینِ کامپیوتر ⇐ نوشتهٔ جدول (`Main.kt`، `Contracts.kt`، `PlatformDesk.kt`) */
  private val moved: List<Pair<String, () -> String>> = listOf(
    "فروشگاه من" to { Str.appName }, // راهنمای سینی و عنوانِ پنجره
    "باز کردنِ فروشگاه من" to { StrDesktop.trayOpen },
    "نمایشگرِ مشتری — فروشگاه من" to { "${StrDevices.kindDisplay} — ${Str.appName}" },
    "همه‌جا" to { StrCourierArea.everywhere }, // فهرستِ میان‌برها (تنظیمِ «راهنمای کلید» خاموش)
    "مرکزِ مدیریت" to { Str.adminCenter },
    "برگشت" to { Str.back },
    "بارکدخوان" to { StrSettings.scannerRow },
    "صندوق" to { StrAdmin.register },
    "کالاها" to { StrProducts.listTitle },
    "انتخابِ مشتری" to { StrPos.pickCustomer },
    "ثبتِ فروش" to { StrPos.recordSaleBtn },
    "دریافت از مشتری" to { StrHome.receiveFromCustomer },
    "خروجیِ اکسل" to { StrProductPages.excelExport },
    "بستن" to { Str.close },
    "انتخاب فایل" to { StrDesktop.pickFile }, // پنجرهٔ فایلِ سیستم
    "ذخیره" to { StrKit.save },
    "انتخاب تصویر" to { StrProducts.pickImage },
  )

  @Test fun `فارسیِ هر نوشتهٔ جابه‌جاشدهٔ کامپیوتر همان متنِ پیشین است و انگلیسی و پشتو دارد`() {
    for ((fa, text) in moved) {
      val seen = ArrayList<I18n.Entry>()
      I18n.audit = { seen += it }
      assertEquals(fa, at(Lang.FA, text))
      I18n.audit = null
      assertTrue("$fa از جدول نمی‌خواند", seen.isNotEmpty())
      for (e in seen) assertTrue("$fa: ترجمهٔ «${e.fa}» جاافتاده است", e.en != null && e.ps != null)
      assertNotEquals("$fa: انگلیسی همان فارسی است", fa, at(Lang.EN, text))
    }
  }

  /** فارسیِ بی کسرهٔ اضافه و نیم‌فاصله و فاصله — برای «همان جمله» */
  private fun bare(s: String) = s.replace("\u200c", "").replace("\u0650", "").replace(" ", "")

  /** هر ردیفِ `StrDesktop` ⇐ جملهٔ بازبینی‌شده‌ای که ترجمه‌اش رونوشت شد */
  private val copied: List<Triple<String, () -> String, () -> String>> = listOf(
    Triple("trayOpen", { StrDesktop.trayOpen }, { StrAppData.fpTitle }),
    Triple("pickFile", { StrDesktop.pickFile }, { StrReach.importPick }),
  )

  @Test fun `ترجمهٔ StrDesktop رونوشتِ همان جملهٔ بازبینی‌شده است و ترجمهٔ تازه ندارد`() {
    for ((key, desk, source) in copied) {
      assertEquals(key, at(Lang.EN, source), at(Lang.EN, desk))
      assertEquals(key, at(Lang.PS, source), at(Lang.PS, desk))
      assertEquals("$key: جملهٔ دیگری است", bare(at(Lang.FA, source)), bare(at(Lang.FA, desk)))
      assertNotEquals("$key: همان جمله است — مستقیم از همان جدول بخوانید", at(Lang.FA, source), at(Lang.FA, desk))
    }
    val rows = StrDesktop.javaClass.declaredMethods
      .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.returnType == String::class.java }
      .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) }.toSet()
    assertEquals("هر ردیفِ تازهٔ StrDesktop منبعش را این‌جا می‌خواهد", copied.map { it.first }.toSet(), rows)
  }

  /* ---------------- متنِ خامِ باقی‌مانده ---------------- */

  /** حرفِ فارسی (همان `DariCoverageTest`) */
  private val letter = Regex("[\\u0621-\\u063A\\u0641-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC]")

  /** متنِ فارسیِ خامِ یک پرونده؛ سطرِ «// داده:» و توضیح شمرده نمی‌شود (همان `DariCoverageTest.raw`) */
  private fun raw(f: File): List<String> {
    val text = f.readLines().filterNot { it.contains("// داده") }
      .filterNot { it.trimStart().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
      .joinToString("\n") { it.replace(Regex("""\s//\s.*$"""), "") }
    return Regex(""""((?:[^"\\\n]|\\.)*)"""").findAll(text).map { it.groupValues[1] }.filter { letter.containsMatchIn(it) }.toList()
  }

  /**
   *  سقفِ متنِ خامِ پوستهٔ کامپیوتر (بی عکس‌های خودکارِ dev: `desktop/shots/` و `*Preview.kt`)؛ چرخ‌دنده‌ای: فقط پایین بیاید.
   *  پیش از LOC-06 ِ این دور ۸۱ بود (۲۰ به جدول رفت، ۲ دادهٔ نشان‌دار)؛ مانده: پیامِ بستنِ پنجره، «خروج» ِ سینی، فهرستِ میان‌برهای قدیم، راهنمای کادرِ بارکدخوان،
   *  پنجرهٔ پوشهٔ پشتیبان، خطای درگاهِ COM و چند پیامِ برنامه‌نویس (`error(…)`) — هیچ‌کدام ترجمهٔ بازبینی‌شده ندارند.
   */
  private val maxRawDesktop = 59

  private fun deskFiles(repo: File): List<File> =
    listOf(repo.resolve("desktop/src/main/kotlin/ir/vil3ntec/tohid"), repo.resolve("desktop/src/shim/kotlin"))
      .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
      .filterNot { it.relativeTo(repo).invariantSeparatorsPath.contains("/desktop/shots/") || it.name.endsWith("Preview.kt") }

  @Test fun `متنِ خامِ فارسیِ پوستهٔ کامپیوتر فقط کم می‌شود`() {
    val repo = DesktopPhrases.repo()
    val files = deskFiles(repo)
    assertTrue("پرونده‌ای پیدا نشد (${files.size})", files.size > 50)
    val found = files.flatMap { f -> raw(f).map { "${f.relativeTo(repo).invariantSeparatorsPath}: «$it»" } }
    println("desk-raw ${found.size}/$maxRawDesktop\n" + found.joinToString("\n"))
    assertTrue(
      "متنِ فارسیِ خامِ تازه در پوستهٔ کامپیوتر (${found.size} > $maxRawDesktop) — به جدولِ Str* با t(…) ببرید:\n" + found.joinToString("\n"),
      found.size <= maxRawDesktop,
    )
    //  و متن‌هایی که این دور به جدول رفتند در همان پرونده‌ها دیگر خام نیستند
    val gone = mapOf(
      "desktop/src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt" to listOf(
        "فروشگاه من", "باز کردنِ فروشگاه من", "نمایشگرِ مشتری — فروشگاه من", "همه‌جا", "مرکزِ مدیریت", "برگشت", "بارکدخوان", "صندوق",
        "کالاها", "انتخابِ مشتری", "ثبتِ فروش", "دریافت از مشتری", "خروجیِ اکسل", "بستن",
      ),
      "desktop/src/shim/kotlin/androidx/activity/result/contract/Contracts.kt" to listOf("انتخاب فایل", "ذخیره", "انتخاب تصویر"),
      "desktop/src/main/kotlin/ir/vil3ntec/tohid/ui/PlatformDesk.kt" to listOf("ذخیره"),
    )
    for ((path, texts) in gone) {
      val f = repo.resolve(path)
      assertTrue("$path نیست", f.isFile)
      val left = raw(f).filter { it in texts }
      assertEquals("$path هنوز متنِ خام دارد", emptyList<String>(), left)
    }
  }
}
