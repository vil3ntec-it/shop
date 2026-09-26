package androidx.compose.ui.window

/**
 *  `DialogProperties`ِ اندروید با `decorFitsSystemWindows` — روی کامپیوتر
 *  نوارِ سیستمی نیست، پس همان ویژگی‌های مشترک.
 */
fun DialogProperties(
  dismissOnBackPress: Boolean = true,
  dismissOnClickOutside: Boolean = true,
  usePlatformDefaultWidth: Boolean = true,
  decorFitsSystemWindows: Boolean,
): DialogProperties = DialogProperties(
  dismissOnBackPress = dismissOnBackPress,
  dismissOnClickOutside = dismissOnClickOutside,
  usePlatformDefaultWidth = usePlatformDefaultWidth,
)
