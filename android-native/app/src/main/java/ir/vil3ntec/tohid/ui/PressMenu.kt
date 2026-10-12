package ir.vil3ntec.tohid.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

/**
 *  «زدن» و «منوی کارها» روی یک ردیف.
 *
 *  گوشی: لمسِ طولانی منو را باز می‌کند. کامپیوتر (`PressMenuDesk.kt`):
 *  کلیکِ راست هم — همان رفتارِ هر برنامهٔ میزکار. یک API، دو دستگاه.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressMenu(onClick: () -> Unit, onMenu: () -> Unit): Modifier =
  this.combinedClickable(onClick = onClick, onLongClick = onMenu)
