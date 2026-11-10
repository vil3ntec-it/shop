package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  تصمیمِ ۳-۲۲ (۱۴۰۵/۰۸/۱۹): «گزارشِ تازه» در نسخهٔ بعدیِ برنامه **پیش‌فرض روشن** است؛ دستگاهی که خودش خاموش کرده
 *  (`false` ِ ذخیره‌شده) خاموش می‌ماند. همتای `license/report-ui.js` ِ وب (`'0'` ⇒ خاموش، نیامده ⇒ روشن).
 */
class ReportFreshDefaultTest {

  @Test fun neverSavedIsOnAndAnExplicitOffStaysOff() {
    assertTrue(ReportFresh.stored(null))
    assertTrue(ReportFresh.stored(true))
    assertFalse(ReportFresh.stored(false))
  }

  @Test fun loadReadsTheSavedChoiceThroughStored() {
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/screens/ReportFresh.kt") }.firstOrNull { it.isFile }?.readText()
      ?: error("ReportFresh.kt پیدا نشد (cwd=${File(".").absoluteFile})")
    assertTrue(src.contains("stored(if (it.contains(KEY)) it.getBoolean(KEY, true) else null)"))
    assertFalse("پیش‌فرضِ خاموشِ پیشین برنگشته باشد", src.contains("getBoolean(KEY, false)"))
  }
}
