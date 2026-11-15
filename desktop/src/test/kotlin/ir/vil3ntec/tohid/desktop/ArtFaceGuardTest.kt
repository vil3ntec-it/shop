package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import ir.vil3ntec.tohid.ui.v3.art.ArtGroup
import ir.vil3ntec.tohid.ui.v3.art.ArtMorph
import ir.vil3ntec.tohid.ui.v3.art.ArtNode
import ir.vil3ntec.tohid.ui.v3.art.ArtScene
import ir.vil3ntec.tohid.ui.v3.art.ArtScenes
import ir.vil3ntec.tohid.ui.v3.art.ArtShape
import ir.vil3ntec.tohid.ui.v3.art.V3ArtKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 *  دستهٔ ۶۵ (e-rows-65a2، صاحب‌کار ۱۴۰۵/۰۸/۲۵: «دماغ و دهنش از چهره‌اش بیرون زده») — نگهبانِ چهره روی همان دادهٔ برنامه
 *  (`ArtData*.kt`، نه منبعِ طراحی): در هر صحنه، برای هر سر (`<p>.head`) و ۴۸ لحظهٔ چرخه، جعبهٔ دورِ هر مسیرِ چهره —
 *  چشم، ابرو، دماغ، دهان، سبیل، گونه، عینک — با همهٔ تبدیل‌های میانی (چرخشِ چهره، نگاه، پلک، زاویهٔ ابرو، دهانِ همان
 *  لحظه، نیمِ پهنای خط) باید درونِ پوستِ سر (`<p>.skin`) باشد، بی هیچ رواداری: جعبه منهای پوست تهی. همتای
 *  `tools/art/check.mjs` (که `gen.mjs` پیش از نوشتنِ داده اجرا می‌کند).
 */
class ArtFaceGuardTest {

  private val features = listOf("eyes", "browL", "browR", "nose", "mouth", "mustache", "blush", "glasses")

  private fun named(n: ArtNode, out: HashMap<String, ArtGroup> = HashMap()): HashMap<String, ArtGroup> {
    if (n is ArtGroup) {
      n.name?.let { out[it] = n }
      for (k in n.kids) named(k, out)
    }
    return out
  }

  private fun leaves(n: ArtNode, out: MutableList<ArtNode> = ArrayList()): List<ArtNode> {
    if (n is ArtGroup) for (k in n.kids) leaves(k, out) else out += n
    return out
  }

  /** `[a b c d e f]` با x' = a·x + c·y + e، y' = b·x + d·y + f — همان ریاضیِ `ArtScene.world` و `engine.mjs/local` */
  private fun local(g: ArtGroup, t: Float, loop: Float): FloatArray {
    val r = (g.r?.at(t, loop, 0f) ?: 0f) * PI.toFloat() / 180f
    val x = g.x?.at(t, loop, 0f) ?: 0f
    val y = g.y?.at(t, loop, 0f) ?: 0f
    val sx = g.sx?.at(t, loop, 1f) ?: 1f
    val sy = g.sy?.at(t, loop, 1f) ?: 1f
    val c = cos(r)
    val s = sin(r)
    val a = c * sx
    val b = s * sx
    val cc = -s * sy
    val d = c * sy
    return floatArrayOf(a, b, cc, d, x + g.px - (a * g.px + cc * g.py), y + g.py - (b * g.px + d * g.py))
  }

  private fun mul(m: FloatArray, n: FloatArray) = floatArrayOf(
    m[0] * n[0] + m[2] * n[1], m[1] * n[0] + m[3] * n[1],
    m[0] * n[2] + m[2] * n[3], m[1] * n[2] + m[3] * n[3],
    m[0] * n[4] + m[2] * n[5] + m[4], m[1] * n[4] + m[3] * n[5] + m[5],
  )

  /** تبدیلِ [from] (خودش هم) تا زیرِ [top] */
  private fun relative(from: ArtGroup, top: ArtGroup, t: Float, loop: Float): Matrix {
    val chain = ArrayList<ArtGroup>()
    var n: ArtGroup? = from
    while (n != null && n !== top) { chain += n; n = n.parent }
    var m = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    for (i in chain.indices.reversed()) m = mul(m, local(chain[i], t, loop))
    return Matrix().apply {
      values[Matrix.ScaleX] = m[0]; values[Matrix.SkewY] = m[1]
      values[Matrix.SkewX] = m[2]; values[Matrix.ScaleY] = m[3]
      values[Matrix.TranslateX] = m[4]; values[Matrix.TranslateY] = m[5]
    }
  }

  private fun parentOf(scene: ArtScene, leaf: ArtNode): ArtGroup {
    fun find(g: ArtGroup): ArtGroup? = if (leaf in g.kids) g else g.kids.filterIsInstance<ArtGroup>().firstNotNullOfOrNull { find(it) }
    return find(scene.root)!!
  }

  @Test fun everyFaceFeatureStaysInsideItsHeadInEveryFrame() {
    var checked = 0
    for (kind in V3ArtKind.entries) {
      val scene = ArtScenes.of(kind)
      val groups = named(scene.root)
      val heads = groups.keys.filter { it.endsWith(".head") }
      assertTrue("$kind: صحنه آدمک ندارد", heads.isNotEmpty())
      for (h in heads) {
        val p = h.removeSuffix(".head")
        val head = groups.getValue(h)
        val skin = groups["$p.skin"] ?: error("$kind: $p.skin نیست")
        assertTrue("$kind: $p چهره‌ای برای سنجیدن ندارد", features.any { "$p.$it" in groups })
        for (s in 0 until 48) {
          val t = s * scene.loop / 48f
          val bound = Path()
          for (leaf in leaves(skin)) {
            val shape = leaf as ArtShape
            bound.addPath(Path().apply { addPath(shape.path); transform(relative(parentOf(scene, leaf), head, t, scene.loop)) })
          }
          for (f in features) {
            val g = groups["$p.$f"] ?: continue
            for (leaf in leaves(g)) {
              val (path, width) = when (leaf) {
                is ArtShape -> Path().apply { addPath(leaf.path) } to leaf.width
                is ArtMorph -> Path().apply { addPath(scene.morphAt(leaf, t, Path())) } to leaf.width
                else -> continue
              }
              path.transform(relative(parentOf(scene, leaf), head, t, scene.loop))
              val r = path.getBounds().inflate(width / 2f)
              val box = Path().apply { addRect(r) }
              val outside = Path.combine(PathOperation.Difference, box, bound)
              assertTrue("$kind: $p.$f از سر بیرون زد در t=${"%.3f".format(t)}s (جعبه $r)", outside.isEmpty)
              checked++
            }
          }
        }
      }
    }
    //  مرزِ مقابل: سنجه کور نیست — ده صحنه، هر کدام چند شکلِ چهره در ۴۸ لحظه
    assertTrue("سنجه چیزی نسنجید: $checked", checked > 10 * 48 * 6)
  }

  @Test fun theGuardCatchesAFeatureOutsideTheHead() {
    //  همان روشِ جعبه‌منهای‌پوست، با جعبه‌ای که عمداً بیرون است ⇒ تهی نیست
    val head = Path().apply { addOval(androidx.compose.ui.geometry.Rect(-12f, -13f, 12f, 13f)) }
    val inside = Path().apply { addRect(androidx.compose.ui.geometry.Rect(-3f, -2f, 3f, 2f)) }
    val outside = Path().apply { addRect(androidx.compose.ui.geometry.Rect(9f, -2f, 15f, 2f)) }
    assertEquals(true, Path.combine(PathOperation.Difference, inside, head).isEmpty)
    assertEquals(false, Path.combine(PathOperation.Difference, outside, head).isEmpty)
  }
}
