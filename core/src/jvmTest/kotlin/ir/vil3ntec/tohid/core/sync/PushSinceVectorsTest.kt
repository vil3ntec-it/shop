package ir.vil3ntec.tohid.core.sync

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  `PushSince` = `pushSinceNote` / `pushSince` ِ وب: همان `core/test-vectors/push-since-v1.json` که
 *  `server/test/sync-push-since.test.js` هم می‌خواند (jvmTest، چون خواندنِ پرونده java.io می‌خواهد).
 *  گردشِ کامل روی JVM و شبیه‌سازِ iOS: `commonTest/…/PushSinceTest.kt`.
 */
class PushSinceVectorsTest {

  private fun vectors(): String {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/push-since-v1.json").takeIf { it.isFile }
        ?: File(dir, "test-vectors/push-since-v1.json").takeIf { it.isFile }
      if (f != null) return f.readText()
      dir = dir.parentFile
    }
    error("core/test-vectors/push-since-v1.json پیدا نشد")
  }

  /** ردیف‌های یک بخش: هر `{ … }` ِ عددی/بولی، بی کتابخانهٔ JSON (core وابستگیِ تازه نمی‌گیرد) */
  private fun rows(section: String): List<Map<String, String>> {
    val text = vectors()
    val body = Regex("\"$section\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(text)!!.groupValues[1]
    return Regex("\\{([^}]*)\\}").findAll(body).map { m ->
      Regex("\"(\\w+)\"\\s*:\\s*(-?\\d+|true|false)").findAll(m.groupValues[1])
        .associate { it.groupValues[1] to it.groupValues[2] }
    }.toList()
  }

  @Test
  fun noteMatchesVectors() {
    val list = rows("note")
    assertTrue(list.size >= 9)
    for (r in list) {
      assertEquals(r.getValue("want").toLong(),
        PushSince.note(r.getValue("previous").toLong(), r.getValue("empty").toBoolean(), r.getValue("cursor").toLong()), "$r")
    }
  }

  @Test
  fun sinceMatchesVectors() {
    val list = rows("since")
    assertTrue(list.size >= 6)
    for (r in list) {
      assertEquals(r.getValue("want").toLong(), PushSince.since(r.getValue("noted").toLong(), r.getValue("cursor").toLong()), "$r")
    }
  }
}
