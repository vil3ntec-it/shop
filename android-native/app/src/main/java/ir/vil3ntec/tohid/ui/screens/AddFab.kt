package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.Platform

/**
 *  دکمهٔ «تازه» هر صفحه.
 *
 *  گوشی: دایرهٔ + (جای انگشت). کامپیوتر: دکمهٔ با نوشته — «+ قرض‌دارِ تازه
 *  Ctrl+N» — چون آیکنِ تنها روی مانیتور معنایش را نمی‌گوید، و Ctrl+N
 *  در هر صفحه همان کارِ همین دکمه را می‌کند (`AppNav.addAction`).
 */
@Composable
fun AddFab(label: String, containerColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val latest = rememberUpdatedState(onClick)
  DisposableEffect(Unit) {
    val action = { latest.value() }
    AppNav.addAction.value = action
    onDispose { if (AppNav.addAction.value === action) AppNav.addAction.value = null }
  }
  if (Platform.DESKTOP) {
    ExtendedFloatingActionButton(
      onClick = onClick,
      containerColor = containerColor,
      contentColor = Color.White,
      modifier = modifier,
      icon = { Icon(Icons.Filled.Add, contentDescription = null) },
      text = { Text("$label    Ctrl+N", style = MaterialTheme.typography.labelLarge) },
    )
  } else {
    FloatingActionButton(onClick = onClick, containerColor = containerColor, contentColor = Color.White, modifier = modifier) {
      Icon(Icons.Filled.Add, contentDescription = label)
    }
  }
}
