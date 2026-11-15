package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 *  موتورِ صحنه‌های تخت (دستهٔ ۶۵، e-rows-65a). هر صحنه درختی است از گروه (لولا + چرخش/جابه‌جایی/مقیاس/شفافیتِ
 *  متحرک) و برگ (مسیرِ پر یا خط، مسیرِ دگرگون‌شونده میانِ چند شکل، یا سیمی که دو سرش دنبالِ دو گروه است). دادهٔ هر صحنه
 *  را `tools/art/gen.mjs` از منبعِ طراحی (`tools/art/scenes`) می‌نویسد؛ دست نزنید، از همان‌جا بسازید.
 *
 *  کارایی: هر مسیر یک بار (نخستین بار) ساخته می‌شود؛ در هر قاب فقط چند عدد حساب و همان مسیرها با تبدیلِ تازه کشیده
 *  می‌شوند — دگرگونی و سیم در همان یک `Path` ِ از پیش ساخته بازنویسی می‌شوند (بی ساختنِ چیزِ تازه در قاب).
 *  فرمول‌های زمان (`ArtTrack.at`، نرمی‌ها) همتای `tools/art/engine.mjs` هستند.
 */

/** کادرِ صحنه (واحدِ طراحی): همهٔ صحنه‌ها روی ۴۰۰×۲۰۰ کشیده و همین ۳۴۰×۱۷۰ ِ میانی (۲:۱) دیده می‌شود */
internal const val ART_VIEW_X = 30f
internal const val ART_VIEW_Y = 22f
internal const val ART_VIEW_W = 340f
internal const val ART_VIEW_H = 170f

/** خطِ زمانی: کلیدها (زمان، مقدار، نرمی) که در چرخه می‌چرخند + موجِ سینوسیِ اختیاری (`cycles` دور در هر چرخه) */
internal class ArtTrack(
  private val time: FloatArray,
  private val value: FloatArray,
  private val ease: ByteArray,
  private val amp: Float,
  private val cycles: Int,
  private val phase: Float,
) {
  fun at(t: Float, loop: Float, def: Float): Float {
    var v = def
    val n = time.size
    if (n == 1) v = value[0]
    else if (n > 1) {
      var i = n - 1
      for (j in 0 until n) if (time[j] <= t) i = j
      val next = if (i + 1 < n) i + 1 else 0
      val ta = time[i]
      val tb = if (i + 1 < n) time[next] else time[next] + loop
      val tt = if (t < ta) t + loop else t
      val span = tb - ta
      val u = if (span <= 0f) 0f else ((tt - ta) / span).coerceIn(0f, 1f)
      v = value[i] + (value[next] - value[i]) * eased(ease[i].toInt(), u)
    }
    if (amp != 0f) v += amp * sin(2f * PI.toFloat() * cycles * t / loop + phase)
    return v
  }

  companion object {
    const val LINEAR = 0
    const val IN_OUT = 1
    const val IN = 2
    const val OUT = 3
    const val STEP = 4
    const val BACK = 5

    fun eased(e: Int, u: Float): Float = when (e) {
      LINEAR -> u
      IN -> u * u * u
      OUT -> 1f - (1f - u).pow(3)
      STEP -> 0f
      BACK -> {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        1f + c3 * (u - 1f).pow(3) + c1 * (u - 1f).pow(2)
      }
      else -> if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
    }

    /** `t,v,e/t,v,e~amp,cycles,phase` */
    fun parse(s: String): ArtTrack {
      val keys = s.substringBefore('~')
      val wave = if ('~' in s) s.substringAfter('~').split(',') else emptyList()
      val parts = if (keys.isEmpty()) emptyList() else keys.split('/')
      val t = FloatArray(parts.size)
      val v = FloatArray(parts.size)
      val e = ByteArray(parts.size)
      parts.forEachIndexed { i, k ->
        val p = k.split(',')
        t[i] = p[0].toFloat()
        v[i] = p[1].toFloat()
        e[i] = when (p.getOrNull(2)) {
          "l" -> LINEAR; "i" -> IN; "o" -> OUT; "s" -> STEP; "b" -> BACK; else -> IN_OUT
        }.toByte()
      }
      return ArtTrack(t, v, e, wave.getOrNull(0)?.toFloat() ?: 0f, wave.getOrNull(1)?.toInt() ?: 0, wave.getOrNull(2)?.toFloat() ?: 0f)
    }
  }
}

internal sealed class ArtNode

internal class ArtGroup(
  val name: String?,
  val px: Float,
  val py: Float,
  val r: ArtTrack?,
  val x: ArtTrack?,
  val y: ArtTrack?,
  val sx: ArtTrack?,
  val sy: ArtTrack?,
  val a: ArtTrack?,
) : ArtNode() {
  var parent: ArtGroup? = null
  var clip: Path? = null
  val kids = ArrayList<ArtNode>()
}

/** مسیرِ ثابت (پر یا خط) */
internal class ArtShape(val role: ArtRole, width: Float, val path: Path) : ArtNode() {
  val style: DrawStyle = artStyle(width)
}

/** مسیرِ دگرگون‌شونده: همهٔ گونه‌ها یک فهرستِ دستورِ یکسان (M/C/Z) دارند؛ [track] ⇒ ۰ … n−۱ */
internal class ArtMorph(val role: ArtRole, width: Float, val track: ArtTrack, val ops: CharArray, val variants: Array<FloatArray>) : ArtNode() {
  val style: DrawStyle = artStyle(width)
  val path = Path()
}

/** سیم: خمِ مکعبی از نقطه‌ای در گروهِ [a] تا نقطه‌ای در گروهِ [b] (null ⇒ مختصاتِ صحنه)، با دو بردارِ مماس */
internal class ArtRope(val role: ArtRole, width: Float, val aName: String, val pa: FloatArray, val bName: String, val pb: FloatArray) : ArtNode() {
  val style: DrawStyle = artStyle(width)
  val path = Path()
  var a: ArtGroup? = null
  var b: ArtGroup? = null
}

private fun artStyle(w: Float): DrawStyle = if (w > 0f) Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill

/**
 *  یک صحنه: چرخهٔ [loop] ثانیه، ژستِ ایستای [rest] (ثانیه‌ای از چرخه که «کاهشِ حرکت» و بیرون از دید نشان می‌دهد)،
 *  و قابِ مربعِ [badge] (x، y، ضلع) برای نشانِ کوچک.
 */
internal class ArtScene(val loop: Float, val rest: Float, val badge: FloatArray, val root: ArtGroup) {
  private val scratch = FloatArray(2)

  /** زمانِ صحنه پس از [elapsed] ثانیه حرکت (null ⇒ ژستِ ایستا): از همان ژستِ ایستا آغاز می‌شود، بی پرش */
  fun timeAt(elapsed: Float?): Float {
    if (elapsed == null) return rest
    val x = (rest + elapsed) / loop
    return (x - floor(x)) * loop
  }

  fun draw(scope: DrawScope, t: Float, palette: V3ArtPalette) = scope.node(root, t, 1f, palette)

  private fun DrawScope.node(n: ArtNode, t: Float, alpha: Float, pal: V3ArtPalette) {
    when (n) {
      is ArtGroup -> {
        val a = alpha * (n.a?.at(t, loop, 1f) ?: 1f)
        if (a <= 0.004f) return
        val r = n.r?.at(t, loop, 0f) ?: 0f
        val x = n.x?.at(t, loop, 0f) ?: 0f
        val y = n.y?.at(t, loop, 0f) ?: 0f
        val sx = n.sx?.at(t, loop, 1f) ?: 1f
        val sy = n.sy?.at(t, loop, 1f) ?: 1f
        val plain = r == 0f && x == 0f && y == 0f && sx == 1f && sy == 1f && n.clip == null
        val kids = n.kids
        if (plain) {
          for (i in kids.indices) node(kids[i], t, a, pal)
        } else {
          withTransform({
            translate(x + n.px, y + n.py)
            if (r != 0f) rotate(r, Offset.Zero)
            if (sx != 1f || sy != 1f) scale(sx, sy, Offset.Zero)
            translate(-n.px, -n.py)
            n.clip?.let { clipPath(it) }
          }) { for (i in kids.indices) node(kids[i], t, a, pal) }
        }
      }
      is ArtShape -> drawPath(n.path, pal[n.role], alpha = alpha, style = n.style)
      is ArtMorph -> {
        val v = n.track.at(t, loop, 0f).coerceIn(0f, (n.variants.size - 1).toFloat())
        val i = min(n.variants.size - 2, floor(v).toInt())
        val u = v - i
        val p0 = n.variants[i]
        val p1 = n.variants[i + 1]
        val path = n.path
        path.rewind()
        var k = 0
        fun c(j: Int) = p0[j] + (p1[j] - p0[j]) * u
        for (op in n.ops) {
          when (op) {
            'M' -> { path.moveTo(c(k), c(k + 1)); k += 2 }
            'C' -> { path.cubicTo(c(k), c(k + 1), c(k + 2), c(k + 3), c(k + 4), c(k + 5)); k += 6 }
            else -> path.close()
          }
        }
        drawPath(path, pal[n.role], alpha = alpha, style = n.style)
      }
      is ArtRope -> {
        world(n.a, n.pa[0], n.pa[1], t)
        val ax = scratch[0]
        val ay = scratch[1]
        world(n.b, n.pb[0], n.pb[1], t)
        val bx = scratch[0]
        val by = scratch[1]
        val path = n.path
        path.rewind()
        path.moveTo(ax, ay)
        path.cubicTo(ax + n.pa[2], ay + n.pa[3], bx + n.pb[2], by + n.pb[3], bx, by)
        drawPath(path, pal[n.role], alpha = alpha, style = n.style)
      }
    }
  }

  /** نقطهٔ ([x], [y]) ِ گروهِ [g] در مختصاتِ صحنه در زمانِ [t] ⇒ `scratch` */
  private fun world(g: ArtGroup?, x: Float, y: Float, t: Float) {
    var px = x
    var py = y
    var n = g
    while (n != null) {
      val r = (n.r?.at(t, loop, 0f) ?: 0f) * PI.toFloat() / 180f
      val sx = n.sx?.at(t, loop, 1f) ?: 1f
      val sy = n.sy?.at(t, loop, 1f) ?: 1f
      val dx = (px - n.px) * sx
      val dy = (py - n.py) * sy
      val c = cos(r)
      val s = sin(r)
      px = c * dx - s * dy + n.px + (n.x?.at(t, loop, 0f) ?: 0f)
      py = s * dx + c * dy + n.py + (n.y?.at(t, loop, 0f) ?: 0f)
      n = n.parent
    }
    scratch[0] = px
    scratch[1] = py
  }

  companion object {
    /** دادهٔ صحنه (قالبِ خطیِ `tools/art/engine.mjs` → `sceneText`) */
    fun parse(text: String): ArtScene {
      var loop = 4f
      var rest = 0f
      var badge = floatArrayOf(ART_VIEW_X, ART_VIEW_Y, ART_VIEW_H)
      val stack = ArrayList<ArtGroup>()
      val named = HashMap<String, ArtGroup>()
      var root: ArtGroup? = null
      val ropes = ArrayList<ArtRope>()
      for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val kind = line[0]
        val rest0 = line.substring(1).trim()
        when (kind) {
          'S' -> {
            val p = rest0.split(' ').map { it.toFloat() }
            loop = p[0]
            rest = p[1]
            badge = floatArrayOf(p[2], p[3], p[4])
          }
          'G' -> {
            val p = rest0.split(' ')
            val tracks = HashMap<String, ArtTrack>()
            for (tok in p.drop(3)) tracks[tok.substringBefore(':')] = ArtTrack.parse(tok.substringAfter(':'))
            val g = ArtGroup(p[0].takeIf { it != "-" }, p[1].toFloat(), p[2].toFloat(), tracks["r"], tracks["x"], tracks["y"], tracks["sx"], tracks["sy"], tracks["a"])
            g.name?.let { named[it] = g }
            stack.lastOrNull()?.let { parent -> g.parent = parent; parent.kids += g }
            if (root == null) root = g
            stack += g
          }
          'E' -> stack.removeAt(stack.size - 1)
          'C' -> stack.last().clip = pathOf(rest0)
          'P' -> {
            val (role, w, d) = rest0.split(' ', limit = 3)
            stack.last().kids += ArtShape(ArtRole.of(role), w.toFloat(), pathOf(d))
          }
          'M' -> {
            val (role, w, tr, ds) = rest0.split(' ', limit = 4)
            val variants = ds.split(" | ")
            val ops = variants[0].filter { it == 'M' || it == 'C' || it == 'Z' }.toCharArray()
            stack.last().kids += ArtMorph(ArtRole.of(role), w.toFloat(), ArtTrack.parse(tr), ops, variants.map { numbers(it) }.toTypedArray())
          }
          'R' -> {
            val p = rest0.split(' ')
            val rope = ArtRope(
              ArtRole.of(p[0]), p[1].toFloat(),
              p[2], floatArrayOf(p[3].toFloat(), p[4].toFloat(), p[5].toFloat(), p[6].toFloat()),
              p[7], floatArrayOf(p[8].toFloat(), p[9].toFloat(), p[10].toFloat(), p[11].toFloat()),
            )
            stack.last().kids += rope
            ropes += rope
          }
        }
      }
      //  دو سرِ سیم پس از همهٔ گروه‌ها بسته می‌شود (ممکن است به گروهی بعد از خودش بسته باشد); «@» ⇒ مختصاتِ صحنه
      fun anchor(name: String) = name.takeIf { it != "@" }?.let { named[it] ?: error("rope anchor not found: $it") }
      for (rope in ropes) {
        rope.a = anchor(rope.aName)
        rope.b = anchor(rope.bName)
      }
      return ArtScene(loop, rest, badge, root ?: error("empty scene"))
    }

    private val NUM = Regex("""-?\d*\.?\d+(?:e-?\d+)?""")

    private fun numbers(d: String): FloatArray {
      val m = NUM.findAll(d).map { it.value.toFloat() }.toList()
      return FloatArray(m.size) { m[it] }
    }

    /** مسیرِ M/L/C/Z ِ مطلق */
    private fun pathOf(d: String): Path {
      val path = Path()
      var i = 0
      var cmd = 'M'
      val n = d.length
      fun num(): Float {
        while (i < n && (d[i] == ' ' || d[i] == ',')) i++
        val s = i
        if (i < n && d[i] == '-') i++
        while (i < n && (d[i].isDigit() || d[i] == '.' || d[i] == 'e' || (d[i] == '-' && d[i - 1] == 'e'))) i++
        return d.substring(s, i).toFloat()
      }
      while (i < n) {
        val ch = d[i]
        if (ch == ' ' || ch == ',') { i++; continue }
        if (ch.isLetter() && ch != 'e') { cmd = ch; i++; if (cmd == 'Z') path.close(); continue }
        when (cmd) {
          'M' -> { path.moveTo(num(), num()); cmd = 'L' }
          'L' -> path.lineTo(num(), num())
          'C' -> path.cubicTo(num(), num(), num(), num(), num(), num())
          else -> i++
        }
      }
      return path
    }
  }
}
