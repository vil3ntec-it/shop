package ir.vil3ntec.tohid.ui.v3.nav

import ir.vil3ntec.tohid.core.nav.Move
import ir.vil3ntec.tohid.ui.v3.v3BehindSheet
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExploreOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Transitions

/**
 *  نمایشِ مسیرِ باز — جای `when (current) { … }`ِ پیشینِ `AppRoot`.
 *
 *  • صفحه‌ها با حرکتِ shared-axis جابه‌جا می‌شوند (رفتن/برگشت/تب، آینه در
 *    راست‌به‌چپ) و حالتِ ذخیره‌پذیرِ هر صفحه (جای پیمایش، فیلتر) با برگشت
 *    می‌ماند (`SaveableStateHolder`).
 *  • برگه (`SHEET`) روی صفحهٔ زیرش کشیده می‌شود، نه جای آن.
 *  • دروازه (`GATE`) تمام‌صفحه روی همه‌چیز.
 *  • صفحهٔ امروزِ بی‌سربرگ (`Chrome.SHELL`) روی گوشی در `V3Scaffold` با عنوان و
 *    برگشت پیچیده می‌شود؛ کامپیوتر سربرگِ خودش را دارد.
 *  • بی‌دسترسی (نقش/پرچم) یا مسیرِ ناشناس ⇐ صفحهٔ آرامِ «دسترسی ندارید» با برگشت —
 *    هرگز پرشِ بی‌صدا به «بیشتر».
 */
@Composable
fun V3RouteHost(nav: V3Nav, shell: ShellContext, modifier: Modifier = Modifier) {
  val dir = LocalLayoutDirection.current
  val saved = rememberSaveableStateHolder()
  val stack = nav.stack
  val top = stack.current
  val topIsSheet = remember(top) { nav.resolve(top)?.route?.kind == RouteKind.SHEET } && stack.canPop
  val base = if (!topIsSheet) top
  else stack.entries.dropLast(1).lastOrNull { nav.resolve(it)?.route?.kind != RouteKind.SHEET } ?: top

  CompositionLocalProvider(LocalV3Nav provides nav) {
    AnimatedContent(
      targetState = base,
      //  صفحهٔ زیرِ برگه تا ۰٫۹۶ کوچک می‌شود (`V3Sheet` خودش را می‌شمارد)
      modifier = modifier.v3BehindSheet(),
      //  کامپیوتر/پهن: صفحه‌ها هم‌سطح‌اند ⇐ محو، نه لغزشِ تمام‌عرض
      transitionSpec = { V3Transitions.page(if (nav.wide) Move.SWITCH else nav.move, dir) },
      label = "route",
    ) { path ->
      saved.SaveableStateProvider(path) { RouteContent(path, nav, shell) }
    }
    if (topIsSheet) RouteContent(top, nav, shell)
    nav.gate?.let { RouteContent(it, nav, shell) }
  }
}

@Composable
private fun RouteContent(path: String, nav: V3Nav, shell: ShellContext) {
  val r = remember(path) { nav.resolve(path) }
  when {
    r == null -> Blocked(StrKit.unknownRouteTitle, null, Icons.Outlined.ExploreOff, nav)
    !r.allowed(shell.access) -> {
      val serverFlag = (r.module.needs + r.route.needs).any { it != Need.MANAGE && it != Need.AUDIT && it !in shell.access.granted }
      val moduleOff = (r.module.flag != null && r.module.flag !in shell.access.flags) || (r.route.flag != null && r.route.flag !in shell.access.flags)
      if (serverFlag || moduleOff) Blocked(StrKit.notOnTitle, r.route.title(), Icons.Outlined.ToggleOff, nav)
      else Blocked(StrKit.noAccessTitle, r.route.title(), Icons.Outlined.Lock, nav, r.route.deniedBody?.invoke() ?: StrKit.noAccessBody)
    }
    else -> {
      val scope = RouteScope(r.args, nav, shell)
      //  هر جا که سربرگِ کامپیوتر (DeskHeader) نیست — گوشی و تبلت، باریک یا پهن (AUDIT-NAV N9) —
      //  صفحهٔ قدیم سربرگِ نسخهٔ ۳ می‌گیرد؛ برگشت فقط وقتی چیزی زیرش است (N8)
      if (r.route.chrome == Chrome.SHELL && !LocalDeskLayout.current && r.route.kind != RouteKind.SHEET) {
        V3Scaffold(title = r.route.title(), onBack = scope.backOrNull) { pad ->
          Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) { r.route.content(scope) }
        }
      } else r.route.content(scope)
    }
  }
}

/** «دسترسی ندارید» / «روشن نیست» / «پیدا نشد» — با برگشت */
@Composable
private fun Blocked(title: String, page: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, nav: V3Nav, body: String? = null) {
  V3Scaffold(title = page ?: title, onBack = { nav.back() }, largeTitle = false) { pad ->
    Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
      V3EmptyState(icon, title, body = body, action = StrKit.back, onAction = { nav.back() })
    }
  }
}
