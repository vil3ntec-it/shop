package ir.vil3ntec.tohid.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  کادرِ جستجو با میکروفون.
 *
 *  فروشنده‌ای که وسطِ کار است، نامِ قرض‌دار یا کالا را سریع‌تر می‌گوید تا
 *  اینکه با یک دست تایپش کند — و نامِ فارسی روی صفحه‌کلید انگلیسی، خودش
 *  یک گرفتاریِ جداست.
 *
 *  کار را به خودِ گوشی می‌سپاریم: `RecognizerIntent` برنامهٔ تشخیصِ گفتارِ
 *  دستگاه را باز می‌کند. پس نه اجازهٔ میکروفون لازم است، نه اینترنتِ
 *  همیشگی، نه کتابخانهٔ اضافه — و اگر گوشی چنین برنامه‌ای نداشته باشد،
 *  نشانِ میکروفون اصلاً نشان داده نمی‌شود تا دکمه‌ای نباشد که کار نمی‌کند.
 */
@Composable
fun VoiceSearchField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier.fillMaxWidth(),
) {
  val context = LocalContext.current

  // هست یا نیست؟ یک بار پرسیده می‌شود، نه در هر بازچینش
  val canListen = remember {
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
      .resolveActivity(context.packageManager) != null ||
      context.packageManager.queryIntentActivities(
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), PackageManager.MATCH_DEFAULT_ONLY
      ).isNotEmpty()
  }

  val listen = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val said = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
        ?.trim()
      if (!said.isNullOrBlank()) onValueChange(said)
    }
  }

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    trailingIcon = {
      // تا وقتی چیزی نوشته شده، جای میکروفون دکمهٔ پاک کردن است: در
      // یک کادر دو دکمه، هیچ‌کدام پیدا نمی‌شود
      when {
        value.isNotBlank() -> Box(
          Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .clickable { onValueChange("") },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.Close,
            contentDescription = "پاک کردن جستجو",
            tint = Shop.colors.muted,
            modifier = Modifier.size(18.dp),
          )
        }
        canListen -> Box(
          Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Shop.colors.primary.copy(alpha = 0.14f))
            .clickable {
              val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                  RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                  RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                // فارسی، و اگر دستگاه نداشت خودش سراغِ زبانِ خودش می‌رود
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "بگویید دنبالِ چه می‌گردید")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
              }
              runCatching { listen.launch(intent) }
            },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.Mic,
            contentDescription = "جستجوی صوتی",
            tint = Shop.colors.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    },
    singleLine = true,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    modifier = modifier,
  )
}
