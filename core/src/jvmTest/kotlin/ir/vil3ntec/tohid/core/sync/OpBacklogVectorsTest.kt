package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `OpBacklog` = `QUEUE_WARN` / `faCount` / `backlogWarning` ِ وب: همان `core/test-vectors/op-backlog-v1.json` */
class OpBacklogVectorsTest {
  private val text = SyncVectors.text("op-backlog-v1.json")

  @Test
  fun warnAtMatchesVectors() {
    assertEquals(OpBacklog.WARN_AT, Regex("\"warnAt\"\\s*:\\s*(\\d+)").find(text)!!.groupValues[1].toInt())
  }

  @Test
  fun countMatchesVectors() {
    val list = SyncVectors.rows(SyncVectors.section(text, "count"))
    assertTrue(list.size >= 8)
    for (r in list) assertEquals(r.getValue("want"), OpBacklog.faCount(r.getValue("n").toInt()), "$r")
  }

  @Test
  fun warningMatchesVectors() {
    val list = SyncVectors.rows(SyncVectors.section(text, "warning"))
    assertTrue(list.size >= 6)
    for (r in list) assertEquals(r.getValue("want"), OpBacklog.warning(r.getValue("queued").toInt()), "$r")
  }
}
