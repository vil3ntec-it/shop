package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  برگهٔ «تنظیمات» ِ ویترین: نشانی و نام (پسوندِ «.vill3n.top»)، رنگ با نمونهٔ کوچک، روشن/خاموش،
 *  ارسال/دریافت (دست‌کم یکی) و پول، ساعتِ کاریِ هر روز و تعطیلی‌ها. خطای هر کادر زیرِ خودش؛ «ذخیره»
 *  نوارِ چسبانِ پایینِ صفحه است (مسیر). حالتِ فرم بیرون از این‌جاست تا با عوضِ برگه گم نشود.
 */
@Composable
fun SettingsPane(
  form: StorefrontSettingsModel.Form,
  errors: Map<String, String>,
  onChange: (StorefrontSettingsModel.Form) -> Unit,
  pad: PaddingValues,
) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
  ) {
    V3Section(StrOnline.sfNameSection) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        V3TextField(
          form.slug, { onChange(form.copy(slug = it)) }, StrOnline.sfSlug, placeholder = "my-shop", suffix = ".vill3n.top",
          helper = StrOnline.sfSlugHelper(StorefrontSettingsModel.address(form.slug)), error = errors["slug"], modifier = Modifier.fillMaxWidth(),
        )
        V3TextField(form.title, { onChange(form.copy(title = it)) }, StrOnline.sfName, error = errors["title"], required = form.enabled, modifier = Modifier.fillMaxWidth())
        V3TextField(form.tagline, { onChange(form.copy(tagline = it)) }, StrOnline.sfTagline, modifier = Modifier.fillMaxWidth())
        V3TextField(form.phone, { onChange(form.copy(phone = it)) }, StrOnline.sfPhone, kind = V3FieldKind.PHONE, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.Top) {
          V3TextField(
            form.accent, { onChange(form.copy(accent = it)) }, StrOnline.sfAccent, placeholder = "#2c5ce6",
            error = errors["accent"], modifier = Modifier.weight(1f),
          )
          AccentSwatch(form.accent, Modifier.padding(start = 10.dp, top = 30.dp))
        }
        V3TextField(form.notice, { onChange(form.copy(notice = it)) }, StrOnline.sfNotice, modifier = Modifier.fillMaxWidth())
      }
      V3Divider(start = 64.dp)
      V3ToggleRow(StrOnline.sfEnabled, form.enabled, { onChange(form.copy(enabled = it)) }, icon = Icons.Outlined.Storefront, hue = V3Hue.BRAND)
    }

    V3Section(StrOnline.sfFulfilSection, footer = errors["fulfillment"]) {
      V3ToggleRow(StrOnline.sfDeliveryToggle, form.delivery, { onChange(form.copy(delivery = it)) }, icon = Icons.Outlined.TwoWheeler, hue = V3Hue.SKY)
      V3Divider(start = 64.dp)
      V3ToggleRow(StrOnline.sfPickupToggle, form.pickup, { onChange(form.copy(pickup = it)) }, icon = Icons.Outlined.Store, hue = V3Hue.AMBER)
      V3Divider()
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        V3TextField(form.fee, { onChange(form.copy(fee = it)) }, StrOnline.deliveryFee, kind = V3FieldKind.MONEY, error = errors["fee"], modifier = Modifier.fillMaxWidth())
        V3TextField(form.minOrder, { onChange(form.copy(minOrder = it)) }, StrOnline.sfMinOrder, kind = V3FieldKind.MONEY, error = errors["min"], modifier = Modifier.fillMaxWidth())
      }
    }

    V3Section(StrOnline.sfHours, footer = StrOnline.sfHoursHelp) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V3TextField(form.timezone, { onChange(form.copy(timezone = it)) }, StrOnline.sfTimezone, placeholder = "Asia/Kabul", modifier = Modifier.fillMaxWidth())
        StorefrontOwner.DISPLAY_DAYS.forEach { d ->
          V3TextField(
            form.days[d].orEmpty(), { onChange(form.copy(days = form.days + (d to it))) }, OnlineFmt.dayName(d),
            placeholder = "08:00-20:00", error = errors["day$d"], modifier = Modifier.fillMaxWidth(),
          )
        }
        V3TextField(form.closed, { onChange(form.copy(closed = it)) }, StrOnline.sfClosed, error = errors["closed"], singleLine = false, modifier = Modifier.fillMaxWidth())
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}

/** نمونهٔ رنگِ نوشته‌شده — فقط وقتی «#rrggbb» ِ درست است؛ رنگِ خودِ کاربر است، نه رنگِ برنامه */
@Composable
private fun AccentSwatch(hex: String, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val color = StorefrontAccent.parse(hex)
  val shape = RoundedCornerShape(12.dp)
  Box(modifier.size(44.dp).clip(shape).background(color ?: c.tile).border(1.dp, PhoneV3Roles.inputBorder(c), shape)) {
    if (color == null) Text("?", style = V3Type.headline, color = c.inkMuted, modifier = Modifier.align(Alignment.Center))
  }
}

/** رنگِ ویترینِ نوشته‌شدهٔ کاربر («#2c5ce6») برای نمونهٔ کنارِ کادر؛ نادرست ⇒ null */
internal object StorefrontAccent {
  private val HEX = Regex("^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})$")

  fun parse(raw: String): androidx.compose.ui.graphics.Color? {
    val m = HEX.matchEntire(raw.trim()) ?: return null
    val (r, g, b) = m.destructured
    return androidx.compose.ui.graphics.Color(red = r.toInt(16) / 255f, green = g.toInt(16) / 255f, blue = b.toInt(16) / 255f)
  }
}
