package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Radius

/**
 *  فیلترِ دسته‌بندی در بالای فهرست‌ها.
 *
 *  با `CategoryPicker` فرق دارد و عمداً نامش جداست: آن یکی برای
 *  **انتخابِ** دستهٔ یک کالا در فرم است و مقدارِ خالی نمی‌پذیرد؛ این یکی
 *  فیلتر است و «همه» هم یک حالتِ درست است.
 *
 *  تا چهار دسته، تراشه‌ها کنارِ هم می‌نشینند — یک نگاه و یک لمس. از آن
 *  بیشتر، ردیف از صفحه بیرون می‌زند و کاربر نمی‌داند چند تای دیگر مانده؛
 *  آنجا کادرِ کشویی می‌آید که همه را یک‌جا نشان می‌دهد و جای ثابتی
 *  می‌گیرد.
 *
 *  یک جا نوشته شده تا هر صفحه‌ای که دسته‌بندی دارد همین رفتار را داشته
 *  باشد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilter(
  categories: List<String>,
  selected: String?,
  onSelect: (String?) -> Unit,
  modifier: Modifier = Modifier,
  allLabel: String = "همه",
) {
  if (categories.isEmpty()) return

  if (categories.size <= 4) {
    Row(
      modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FilterChip(
        selected = selected == null,
        onClick = { onSelect(null) },
        label = { Text(allLabel) },
      )
      categories.forEach { c ->
        FilterChip(
          selected = selected == c,
          onClick = { onSelect(if (selected == c) null else c) },
          label = { Text(c) },
        )
      }
    }
    return
  }

  var open by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(
    expanded = open,
    onExpandedChange = { open = it },
    modifier = modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
      value = selected ?: allLabel,
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text("دسته‌بندی") },
      shape = RoundedCornerShape(Radius.sm),
      trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
      modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); open = false })
      categories.forEach { c ->
        DropdownMenuItem(text = { Text(c) }, onClick = { onSelect(c); open = false })
      }
    }
  }
}
