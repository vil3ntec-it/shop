package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  `KeepPending.verdicts` = `keepPendingVerdicts` ِ وب: همان `core/test-vectors/keep-pending-v1.json` که
 *  `server/test/sync-keep-local.test.js` هم می‌خواند. شکلِ op و حکم سرِ همان پرونده نوشته شده است.
 */
class KeepPendingVectorsTest {

  private fun op(s: String): KeepPending.Op {
    val p = s.trim().split(' ')
    val set = LinkedHashSet<String>()
    val inc = LinkedHashMap<String, Double>()
    for (f in p.drop(3)) {
      val (name, v) = f.split(':', limit = 2)
      if (v.startsWith("+")) inc[name] = (inc[name] ?: 0.0) + v.drop(1).toDouble() else set += name
    }
    return KeepPending.Op(p[0], p[1], delete = p[2] == "d", set = set, inc = inc)
  }

  private fun num(d: Double): String = if (d == kotlin.math.floor(d)) d.toLong().toString() else d.toString()

  private fun show(v: KeepPending.Verdict): String = when (v) {
    KeepPending.Keep -> "keep"
    KeepPending.Drop -> "drop"
    is KeepPending.Trim -> (listOf("trim") + v.drop.map { "-$it" } + v.rebase.map { (f, d) -> "+$f=${num(d)}" }).joinToString(" ")
  }

  @Test
  fun verdictsMatchVectors() {
    val text = SyncVectors.text("keep-pending-v1.json")
    val cases = Regex("\\{\"name\".*?\\]\\}").findAll(SyncVectors.section(text, "cases")).map { it.value }.toList()
    assertTrue(cases.size >= 15)
    for (c in cases) {
      val name = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").find(c)!!.groupValues[1]
      val got = KeepPending.verdicts(SyncVectors.strings(c, "incoming").map(::op), SyncVectors.strings(c, "pending").map(::op))
      assertEquals(SyncVectors.strings(c, "want"), got.map(::show), name)
    }
  }
}
