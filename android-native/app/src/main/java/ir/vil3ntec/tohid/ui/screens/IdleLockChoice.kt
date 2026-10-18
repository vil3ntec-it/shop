package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.perm.IdleLock
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  «قفلِ خودکار پس از بی‌کاری» — STF-05، زیرِ بخشِ «قفل برنامه»ِ تنظیمات.
 *  فقط وقتی دیده می‌شود که رمز گذاشته شده باشد؛ پیش‌فرض «هرگز» (رفتارِ پیشین).
 */
@Composable
fun IdleLockChoice(lockStore: LockStore) {
  var minutes by remember { mutableIntStateOf(lockStore.idleMinutes) }
  Column {
    Spacer(Modifier.height(12.dp))
    Text(
      "قفلِ خودکار پس از بی‌کاری",
      style = MaterialTheme.typography.labelLarge,
      color = Shop.colors.text,
    )
    Text(
      "اگر دستگاه روی پیشخوان بماند، پس از این مدت بی‌کاری دوباره رمز می‌خواهد.",
      style = MaterialTheme.typography.bodySmall,
      color = Shop.colors.muted,
    )
    Spacer(Modifier.height(6.dp))
    Row(
      Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      IdleLock.CHOICES.forEach { m ->
        FilterChip(
          selected = minutes == m,
          onClick = { lockStore.idleMinutes = m; minutes = m },
          label = { Text(if (m == 0) "هرگز" else "$m دقیقه") },
        )
      }
    }
  }
}
