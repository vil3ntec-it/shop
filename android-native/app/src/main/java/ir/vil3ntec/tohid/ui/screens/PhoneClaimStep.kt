package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.account.PhoneClaim
import ir.vil3ntec.tohid.core.account.PhoneClaim.Step
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.AccountRepository
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  AUTH-08 — افزودنِ شماره به حساب فقط با کدِ همان شماره، در «ویرایشِ پروفایل».
 *
 *  تصمیمِ گامِ بعد در هسته است (`core/account/PhoneClaim`، commonMain)؛ این‌جا فقط کادر و دو درخواست.
 *  پرچمِ سروریِ `auth.phone_add_code` خاموش ⇒ سرور هرگز `phone_code_required` نمی‌دهد، کادرِ کد هرگز
 *  باز نمی‌شود و `phoneCode` هرگز فرستاده نمی‌شود — همان درخواست و همان پیامِ پیشین.
 */

/**
 *  کادرِ شمارهٔ تازه (یک بار ثبت می‌شود)، و فقط وقتی سرور کد خواست، کادرِ کد با «فرستادنِ دوباره».
 *  شماره و کد به هم بسته‌اند: صفحه با هر تغییرِ شماره کادرِ کد را می‌بندد.
 */
@Composable
internal fun NewPhoneField(
  phone: String,
  onPhone: (String) -> Unit,
  codeShown: Boolean,
  code: String,
  onCode: (String) -> Unit,
  busy: Boolean,
  onResend: () -> Unit,
) {
  /*
   *  شماره ندارد — یک بار می‌شود ثبتش کرد.
   *
   *  چرا مهم است: بازیابیِ رمز و خبرهای دکان از راهِ شماره می‌روند،
   *  و کسی که با ایمیل آمده هیچ‌کدام را ندارد.
   */
  Column {
    OutlinedTextField(
      value = phone,
      onValueChange = onPhone,
      label = { Text("شماره موبایل — یک بار ثبت می‌شود") },
      placeholder = { Text("07XXXXXXXX") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(
      "شماره پس از ثبت قفل می‌شود و از برنامه عوض نمی‌شود.",
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.warning,
    )
    if (codeShown) {
      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
        value = code,
        onValueChange = onCode,
        label = { Text("کدی که به همین شماره پیامک شد") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
      )
      TextButton(onClick = onResend, enabled = !busy) { Text("فرستادنِ دوبارهٔ کد") }
    }
  }
}

/**
 *  کدِ همان شماره را می‌خواهد. خروجی: کادرِ کد باز (`true`)، بسته (`false`، سرور دیگر کد نمی‌خواهد)، یا
 *  `null` (دست نزن؛ پیامِ سرور نشان داده شد).
 */
internal suspend fun sendPhoneCode(account: AccountRepository, phone: String, toast: (String) -> Unit): Boolean? {
  var open: Boolean? = null
  account.requestPhoneCode(phone)
    .onSuccess {
      open = true
      toast("کدی به همین شماره پیامک شد؛ آن را بنویسید و «ذخیره» را بزنید")
    }
    .onFailure { f ->
      open = when (PhoneClaim.afterSend(f.code)) {
        Step.ENTER_CODE -> true
        Step.WITHOUT_CODE -> false
        else -> null
      }
      toast(f.userMessage)
    }
  return open
}

/**
 *  پس از ذخیرهٔ ناموفق: اگر سرور کدِ همان شماره را خواست (یا کدِ پیشین مرده است) کد را می‌خواهد؛ وگرنه
 *  همان پیامِ سرور. خروجی همان `sendPhoneCode`؛ `null` ⇒ کادر همان‌طور که بود.
 */
internal suspend fun afterProfileFailure(
  account: AccountRepository,
  phone: String,
  failure: ApiFailure,
  toast: (String) -> Unit,
): Boolean? {
  val step = PhoneClaim.afterSave(failure.code)
  if ((step == Step.SEND_CODE || step == Step.NEW_CODE) && phone.isNotBlank()) return sendPhoneCode(account, phone, toast)
  toast(failure.userMessage)
  return null
}
