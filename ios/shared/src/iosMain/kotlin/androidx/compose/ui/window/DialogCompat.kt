package androidx.compose.ui.window

/** `DialogProperties(decorFitsSystemWindows = …)` ِ فقط-اندروید — همان shimِ کامپیوتر؛ iOS این پرچم را ندارد */
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
