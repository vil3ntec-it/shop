package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import ir.vil3ntec.tohid.core.nav.DeepLink
import ir.vil3ntec.tohid.core.nav.Move
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.nav.RoutePaths

/**
 *  ناوبریِ برنامه — یک پشتهٔ نوع‌دار به‌جای `tab`/`sub` و دهها `var openX`ِ پیشین.
 *
 *  • `open(path)`: تبِ نوار ⇐ همان تب از نو؛ برگه و صفحه ⇐ رفتن به عمق (روی
 *    کامپیوتر صفحه‌ها هم‌سطح‌اند: تب + همین صفحه، مثلِ منوی کناری). نامِ قدیمی
 *    (`warehouse`) خودکار به مسیرِ امروز (`stock`) می‌رود.
 *  • `back()`: یک پله؛ در ریشهٔ تبی جز خانه ⇐ خانه؛ در خانه ⇐ false (برنامه بسته شود).
 *  • `openDeep(path)`: پیوندِ سرد (اعلان، `vill3n://app/…`) — پشته از زنجیرهٔ پدرها
 *    ساخته می‌شود تا «برگشت» به جای درست برود؛ وقتی پوسته پشتِ دروازه است (قفل،
 *    ساختِ دکان)، پیوند نگه داشته و بعد از عبور باز می‌شود.
 *  • `openForResult(path) { نتیجه -> }` و در مقصد `finish(نتیجه)` (مثلاً
 *    `debtors/new` ⇐ برگشت به صندوق با شناسهٔ قرض‌دارِ تازه). برگشتِ بی‌نتیجه ⇐ null.
 */
@Stable
class V3Nav(private val table: RouteTable, val home: String, initial: NavStack) {
  var stack: NavStack by mutableStateOf(initial)
    private set

  /** مسیرِ (پایهٔ) تب‌های نوارِ همین کاربر — پوسته می‌گذارد */
  var isTab: (String) -> Boolean = { it == home }

  /** چیدمانِ پهن (کامپیوتر/تبلتِ افقی): صفحه‌ها هم‌سطح */
  var wide: Boolean = false

  /** پوسته پشتِ دروازه است؟ (پیوندها نگه داشته می‌شوند) */
  var held: Boolean = false
    set(value) {
      field = value
      if (!value) pending?.let { p -> pending = null; openDeep(p) }
    }
  private var pending: String? = null
  private val results = HashMap<String, (String?) -> Unit>()

  /** دروازهٔ باز (`auth`، `lock`، …): تمام‌صفحه روی همه‌چیز، بیرونِ پشته */
  var gate: String? by mutableStateOf(null)
    private set

  val current: String get() = stack.current

  /** صفحهٔ زیرِ برگه‌ها (برگه روی صفحهٔ پیشین است و جایش را نمی‌گیرد) */
  val page: String get() = stack.entries.lastOrNull { table.resolve(it)?.route?.kind != RouteKind.SHEET } ?: stack.current
  val move: Move get() = stack.move
  val canBack: Boolean get() = stack.canPop || stack.root != home

  fun resolve(path: String): Resolved? = table.resolve(path)

  fun open(path: String) {
    val p = table.canonical(path)
    val kind = table.resolve(p)?.route?.kind
    if (kind == RouteKind.GATE) { gate = p; return }
    stack = when {
      isTab(RoutePaths.normalize(p.substringBefore('?'))) -> stack.switchTab(p)
      wide && kind != RouteKind.SHEET -> stack.jump(p)
      else -> stack.push(p)
    }
  }

  /** برگشت از **همین** مسیر؛ اگر دیگر بالا نیست (نتیجه داده و برداشته شده)، هیچ کاری نمی‌کند */
  fun backFrom(path: String): Boolean {
    val g = gate
    if (g != null) return if (g == RoutePaths.normalize(path)) back() else false
    return if (stack.isTop(path)) back() else false
  }

  fun back(): Boolean {
    if (gate != null) { gate = null; return true }
    if (stack.canPop) {
      val top = stack.current
      stack = stack.pop()
      results.remove(top)?.invoke(null)
      return true
    }
    if (stack.root != home) {
      stack = stack.switchTab(home)
      return true
    }
    return false
  }

  fun replace(path: String) {
    stack = stack.replaceTop(table.canonical(path))
  }

  /**
   *  مسیری که هنوز صفحهٔ خودش را ندارد به نزدیک‌ترین صفحهٔ امروز می‌رود: اگر همان
   *  صفحه زیرش است برمی‌گردد (تکرار نمی‌شود)، وگرنه جای همین مسیر می‌نشیند.
   */
  fun redirect(path: String) {
    val target = table.canonical(path)
    val below = stack.entries.getOrNull(stack.entries.size - 2)
    stack = if (below == target) stack.pop() else stack.replaceTop(target)
  }

  /** دروازه گذشت ⇒ بسته و پیوندِ نگه‌داشته (اگر بود) باز می‌شود */
  fun passGate() {
    gate = null
  }

  fun switchTab(path: String) {
    stack = stack.switchTab(table.canonical(path))
  }

  fun openForResult(path: String, onResult: (String?) -> Unit) {
    open(path)
    results[stack.current] = onResult
  }

  fun finish(result: String?) {
    val top = stack.current
    if (stack.canPop) stack = stack.pop()
    results.remove(top)?.invoke(result)
  }

  fun openDeep(path: String) {
    if (held) { pending = path; return }
    val chain = table.chain(path)
    if (chain.isEmpty()) return
    val root = chain.first()
    val entries = if (isTab(RoutePaths.normalize(root.substringBefore('?'))) || root == home) chain else listOf(home) + chain
    stack = NavStack(entries.takeLast(NavStack.MAX_DEPTH), Move.PUSH)
  }

  fun openLink(link: DeepLink) = openDeep(link.path)

  companion object {
    fun saver(table: RouteTable, home: String) = listSaver<V3Nav, String>(
      save = { it.stack.entries },
      restore = { V3Nav(table, home, NavStack.restore(it, home)) },
    )
  }
}

@Composable
fun rememberV3Nav(table: RouteTable, home: String): V3Nav =
  rememberSaveable(table, home, saver = V3Nav.saver(table, home)) { V3Nav(table, home, NavStack.of(home)) }

/** ناوبری از هر جای صفحه (`LocalV3Nav.current?.open(...)`) */
val LocalV3Nav = staticCompositionLocalOf<V3Nav?> { null }
