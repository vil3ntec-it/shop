package ir.vil3ntec.tohid.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent

/**
 *  جایگزینِ کامپیوتریِ `ui/PressMenu.kt`: کلیکِ راست = منوی کارها
 *  (و نگه‌داشتنِ دکمهٔ چپ هم، مثلِ گوشی).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.pressMenu(onClick: () -> Unit, onMenu: () -> Unit): Modifier =
  this
    .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) onMenu() }
    .combinedClickable(onClick = onClick, onLongClick = onMenu)
