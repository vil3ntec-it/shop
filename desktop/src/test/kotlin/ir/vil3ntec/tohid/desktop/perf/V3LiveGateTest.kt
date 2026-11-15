package ir.vil3ntec.tohid.desktop.perf

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3Covered
import ir.vil3ntec.tohid.ui.v3.ProvideV3Foreground
import ir.vil3ntec.tohid.ui.v3.V3LiveEffect
import ir.vil3ntec.tohid.ui.v3.V3LiveProbe
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.rememberV3Live
import ir.vil3ntec.tohid.ui.v3.v3LiveFloat
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  PERF-SCREEN — «هیچ چیزِ زنده تا کسی در آن بخش نیست» (`V3Live`): صفحهٔ زیرِ برگه، صفحهٔ زیرِ دروازه، برنامهٔ پس‌زمینه و تکهٔ
 *  بیرون از قاب **هیچ قابی** نمی‌خواهند (شمارندهٔ [V3LiveProbe]، ساعتِ ترکیبِ آزمون دستی جلو می‌رود)، و تیکِ صفحه
 *  ([V3LiveEffect]) نمی‌دود؛ دوباره دیده شد ⇒ همان دم ادامه.
 */
class V3LiveGateTest {

  @get:Rule val rule = createComposeRule()

  private val ticks = intArrayOf(0)

  /** یک چشمکِ بی‌پایان که هر قابش را می‌خواند (همان کارِ شیمر/چشمکِ کیت) و یک تیکِ ۱۰۰ میلی‌ثانیه‌ای */
  @Composable
  private fun Live(area: Boolean = false) {
    val live = rememberV3Live()
    val v by v3LiveFloat(0f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), "probe", live)
    V3LiveEffect(live = live) { while (true) { ticks[0]++; delay(100) } }
    Box(Modifier.size(20.dp).then(if (area) live.area else Modifier).drawBehind { v.let { } })
  }

  private fun frames(ms: Long): Long {
    val before = V3LiveProbe.frames
    val t0 = ticks[0]
    rule.mainClock.advanceTimeBy(ms)
    rule.waitForIdle()
    return (V3LiveProbe.frames - before).also { lastTicks = ticks[0] - t0 }
  }

  private var lastTicks = 0

  private fun host(content: @Composable () -> Unit) {
    rule.mainClock.autoAdvance = false
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) { TohidTheme(ThemeChoice.LIGHT) { content() } }
    }
  }

  @Test fun `صفحهٔ زیرِ برگه هیچ قابی نمی‌خواهد و با بستنِ برگه ادامه می‌دهد`() {
    val sheet = mutableStateOf(false)
    host {
      Live()
      if (sheet.value) V3Sheet(onDismiss = { sheet.value = false }) { Spacer(Modifier.height(40.dp)) }
    }
    assertTrue("صفحهٔ دیده‌شده باید زنده باشد", frames(1_000) > 10 && lastTicks > 5)
    rule.runOnIdle { sheet.value = true }
    frames(500) // برگه بالا می‌آید
    assertEquals("زیرِ برگه", 0L, frames(1_000))
    assertEquals("تیکِ زیرِ برگه", 0, lastTicks)
    rule.runOnIdle { sheet.value = false }
    frames(500)
    assertTrue("پس از بستنِ برگه دوباره زنده", frames(1_000) > 10 && lastTicks > 5)
  }

  @Test fun `درونِ برگهٔ بالا زنده است`() {
    host { V3Sheet(onDismiss = {}) { Live() } }
    frames(500)
    assertTrue(frames(1_000) > 10 && lastTicks > 5)
  }

  @Test fun `صفحهٔ زیرِ دروازه هیچ قابی نمی‌خواهد`() {
    val covered = mutableStateOf(true)
    host { CompositionLocalProvider(LocalV3Covered provides covered.value) { Live() } }
    assertEquals(0L, frames(1_000))
    assertEquals(0, lastTicks)
    rule.runOnIdle { covered.value = false }
    assertTrue(frames(1_000) > 10 && lastTicks > 5)
  }

  private class Owner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
  }

  @Test fun `برنامهٔ پس‌زمینه هیچ قابی نمی‌خواهد`() {
    val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
    host { CompositionLocalProvider(LocalLifecycleOwner provides owner) { ProvideV3Foreground { Live() } } }
    assertTrue("پیش‌رو", frames(1_000) > 10)
    rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
    frames(100)
    assertEquals("پس‌زمینه (STARTED)", 0L, frames(1_000))
    assertEquals(0, lastTicks)
    rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
    assertEquals("صفحهٔ خاموش (CREATED)", 0L, frames(1_000))
    rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
    assertTrue("برگشت به پیش‌رو", frames(1_000) > 10 && lastTicks > 5)
  }

  @Test fun `تکهٔ بیرون از قاب هیچ قابی نمی‌خواهد تا پیمایش به آن برسد`() {
    val scroll = mutableStateOf<androidx.compose.foundation.ScrollState?>(null)
    host {
      val s = rememberScrollState().also { scroll.value = it }
      Column(Modifier.height(100.dp).verticalScroll(s)) {
        Spacer(Modifier.height(600.dp))
        Live(area = true)
      }
    }
    frames(100)
    assertEquals("بیرون از قاب", 0L, frames(1_000))
    rule.runOnIdle { scroll.value!!.dispatchRawDelta(10_000f) }
    frames(100)
    assertTrue("در قاب", frames(1_000) > 10)
  }
}
