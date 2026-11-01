package ir.vil3ntec.tohid.core.sync

import java.io.File

/**
 *  خواندنِ `core/test-vectors/<name>` برای آزمون‌های jvmTest (پرونده java.io می‌خواهد) — بی کتابخانهٔ JSON
 *  (core وابستگیِ تازه نمی‌گیرد): بخش‌ها و ردیف‌های تخت با regex.
 */
internal object SyncVectors {
  fun text(name: String): String {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/$name").takeIf { it.isFile }
        ?: File(dir, "test-vectors/$name").takeIf { it.isFile }
      if (f != null) return f.readText()
      dir = dir.parentFile
    }
    error("core/test-vectors/$name پیدا نشد")
  }

  /** بدنهٔ آرایهٔ `"section": [ … ]` (ردیف‌ها بی `]` درونِ رشته) */
  fun section(text: String, section: String): String =
    Regex("\"$section\"\\s*:\\s*\\[(.*?)\\n\\s*\\]", RegexOption.DOT_MATCHES_ALL).find(text)!!.groupValues[1]

  /** هر `{ … }` ِ تخت ⇐ کلید ⇐ مقدارِ خام (رشته بی گیومه، عدد همان‌طور) */
  fun rows(body: String): List<Map<String, String>> =
    Regex("\\{([^{}]*)\\}").findAll(body).map { m ->
      Regex("\"(\\w+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|-?[\\d.]+)").findAll(m.groupValues[1])
        .associate { it.groupValues[1] to (if (it.groupValues[2].startsWith("\"")) it.groupValues[3] else it.groupValues[2]) }
    }.toList()

  /** آرایهٔ رشته‌هایِ `"key": ["…", …]` درونِ یک ردیف */
  fun strings(row: String, key: String): List<String> {
    val body = Regex("\"$key\"\\s*:\\s*\\[(.*?)\\]").find(row)!!.groupValues[1]
    return Regex("\"([^\"]*)\"").findAll(body).map { it.groupValues[1] }.toList()
  }
}
