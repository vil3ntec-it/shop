package ir.vil3ntec.tohid.ios

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 *  نگهبانِ ارزانِ پیوندِ چارچوب (کارِ `ios-ui-klib` روی لینوکس، پیش از دقیقهٔ گرانِ macOS).
 *
 *  چارچوبِ ObjC هر ردهٔ عمومیِ سطحِ بالای این ماژول را `TohidShared<نامِ ساده>` می‌نامد (بسته در نام نیست). Kotlin/Native
 *  ردهٔ جعبهٔ عدد را هم با همان پیشوند در زمانِ اجرای خودش می‌سازد (`TohidSharedNumber`، `TohidSharedDouble`…) و نامگذار
 *  این‌ها را رزرو نمی‌کند ⇒ ردهٔ عمومی‌ای به همین نام‌ها (مثلِ shim ِ `java.lang.Double` ِ گامِ ۷) پیوند را با
 *  «OBJC_CLASS_$_TohidSharedDouble: symbol multiply defined» می‌شکند — فقط در `linkDebugFrameworkIosSimulatorArm64`
 *  ِ macOS، که klib ِ لینوکس نمی‌بیند. shimهای هم‌نام `internal`اند (API ِ Swift نیستند).
 *  (`Base`، `MutableSet` و `MutableDictionary` را نامگذار خودش رزرو می‌کند و ردهٔ هم‌نام `_` می‌گیرد؛ این‌جا نیستند.)
 */
class ObjcNamesJvmTest {
  private val runtimeClasses = setOf(
    "Number", "Byte", "UByte", "Short", "UShort", "Int", "UInt", "Long", "ULong", "Float", "Double", "Boolean",
  )

  /** همان ریشه‌هایی که در چارچوب کامپایل می‌شوند: shimها و کدِ مشترکِ این ماژول و کدِ کپی‌شدهٔ اندروید */
  private val roots = listOf("src/iosMain/kotlin", "src/commonMain/kotlin", "../../android-native/app/src/main/java")

  //  اعلانِ سطحِ بالا (بی تورفتگی)، پس از حاشیه‌نویسی‌ها و واژه‌های دسترسی
  private val decl = Regex("""^(?:@[\w.]+(?:\([^)]*\))?\s+)*((?:(?:public|internal|private|open|abstract|sealed|data|enum|annotation|value|inline|fun|expect|actual|final)\s+)*)(?:class|object|interface)\s+(\w+)""")

  @Test
  fun noPublicTopLevelClassNamedLikeAKotlinNativeRuntimeClass() {
    val hits = mutableListOf<String>()
    var files = 0
    for (root in roots) {
      val dir = File(root)
      assertTrue(dir.isDirectory, "ریشهٔ منبع نیست: ${dir.absolutePath}")
      dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
        files++
        f.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
          val m = decl.find(line) ?: return@forEachIndexed
          val modifiers = m.groupValues[1].split(Regex("\\s+"))
          if (m.groupValues[2] in runtimeClasses && "internal" !in modifiers && "private" !in modifiers) {
            hits += "${f.path}:${i + 1}: ${line.trim()}"
          }
        }
      }
    }
    assertTrue(files > 1000, "پرونده‌های کمتر از انتظار: $files")
    assertEquals(emptyList(), hits, "ردهٔ عمومی هم‌نامِ ردهٔ زمانِ اجرای Kotlin/Native در چارچوب ⇒ internal کنید")
  }

  @Test
  fun guardCatchesThePhase7Shape() {
    assertEquals("Double", decl.find("object Double {")?.groupValues?.get(2))
    assertEquals("internal ", decl.find("internal object Double {")?.groupValues?.get(1))
    assertEquals("Long", decl.find("@Suppress(\"x\") public data class Long(val v: Int)")?.groupValues?.get(2))
    assertEquals(null, decl.find("  object Double {"))
  }
}
