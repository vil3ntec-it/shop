package ir.vil3ntec.tohid.ui.v3.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Stat
import ir.vil3ntec.tohid.ui.v3.V3StatGrid
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Toggle
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow

/** پنجرهٔ پیش‌نمایشِ کیت: کدام بخش‌ها (برای عکس‌های جدا) */
enum class KitPart { BASICS, FORMS, LISTS, STATES }

/**
 *  نمونهٔ زندهٔ کیتِ نسخهٔ ۳ — همهٔ اجزا در همهٔ حالت‌ها (برای عکس‌های خودکار و
 *  گروه‌ها). داده‌ها برچسبِ نمایشی‌اند، نه دادهٔ دکان. پشتِ پرچمِ `dev.kit` در برنامه.
 *  نوشته‌ها عمداً ثابت‌اند (صفحهٔ توسعه، نه کاربر).
 */
@Composable
fun KitGallery(part: KitPart = KitPart.BASICS, onBack: (() -> Unit)? = null) {
  var segment by remember { mutableIntStateOf(0) }
  var chip by remember { mutableStateOf("لبنیات") }
  var toggle by remember { mutableStateOf(true) }
  var search by remember { mutableStateOf("") }
  V3Scaffold(
    title = "کیتِ VILL3N",
    subtitle = "اجزای مشترکِ همهٔ صفحه‌ها",
    onBack = onBack,
    actions = listOf(V3Action(Icons.Outlined.Notifications, "هشدارها", dot = true) {}),
    overflow = listOf(V3MenuItem(Icons.Outlined.Share, "هم‌رسانی") {}, V3MenuItem(Icons.Outlined.DeleteOutline, "حذف", danger = true) {}),
    search = if (part == KitPart.LISTS) ({ V3SearchField(search, { search = it }, "جست‌وجوی کالا یا بارکد…", onScan = {}) }) else null,
    chips = if (part == KitPart.LISTS) ({
      V3ChipRow {
        listOf("همه", "لبنیات", "نوشیدنی", "شوینده").forEach { V3Chip(it, chip == it, { chip = it }, count = if (it == "همه") 24 else null) }
        V3AddChip("تازه") {}
      }
    }) else null,
    fab = if (part == KitPart.LISTS) ({ expanded -> V3Fab(Icons.Outlined.Add, "کالای تازه", expanded, {}) }) else null,
  ) { pad ->
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
    ) {
      when (part) {
        KitPart.BASICS -> Basics(segment) { segment = it }
        KitPart.FORMS -> Forms(toggle) { toggle = it }
        KitPart.LISTS -> Lists()
        KitPart.STATES -> States()
      }
    }
  }
}

@Composable
private fun Basics(segment: Int, onSegment: (Int) -> Unit) {
  V3Section("آمار") {
    V3StatGrid(
      Modifier.padding(12.dp),
      items = listOf(
        { m -> V3Stat("فروشِ امروز", 1_284_500.0, { "${money(it)} ؋" }, m, hue = V3Hue.BRAND) },
        { m -> V3Stat("فاکتور", 37.0, { money(it) }, m, hue = V3Hue.BLUE) },
        { m -> V3Stat("ارزشِ انبار", 98_450_000.0, { "${money(it)} ؋" }, m, hue = V3Hue.TEAL, caption = "۲۴۰ قلم") },
        { m -> V3Stat("کم‌موجود", 6.0, { money(it) }, m, hue = V3Hue.ORANGE) },
      ),
    )
  }
  V3Section("دکمه‌ها") {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button("ثبتِ فروش", {}, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Button("دوم", {}, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
        V3Button("خنثی", {}, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Button("حذف", {}, Modifier.weight(1f), kind = V3ButtonKind.DANGER, icon = Icons.Outlined.DeleteOutline)
        V3Button("غیرفعال", {}, Modifier.weight(1f), enabled = false)
      }
      V3Button("در حالِ ثبت…", {}, Modifier.fillMaxWidth(), loading = true)
    }
  }
  V3Section("تب‌ها و تراشه‌ها") {
    Column(Modifier.padding(12.dp)) {
      V3Segmented(listOf("روز", "هفته", "ماه"), segment, onSegment)
      Spacer(Modifier.height(12.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V3Chip("عدد", true, {})
        V3Chip("کیلوگرم", false, {})
        V3Chip("لیتر", false, {})
        V3AddChip("واحدِ تازه") {}
      }
    }
  }
  V3Section("وضعیتِ سفارش (§۸)") {
    FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      V3StatusPill(V3Status.AWAITING, "منتظرِ تأیید")
      V3StatusPill(V3Status.PREPARING, "در حالِ آماده‌سازی")
      V3StatusPill(V3Status.IN_DELIVERY, "در راه")
      V3StatusPill(V3Status.DELIVERED, "تحویل شد")
      V3StatusPill(V3Status.CANCELLED, "لغو شد")
      V3StatusPill(V3Status.REVIEW, "نیاز به بررسی")
      V3StatusPill(V3Status.INACTIVE, "غیرفعال")
    }
  }
}

@Composable
private fun Forms(toggle: Boolean, onToggle: (Boolean) -> Unit) {
  var name by remember { mutableStateOf("بیسکویت شکلاتی") }
  var price by remember { mutableStateOf("125000") }
  var barcode by remember { mutableStateOf("") }
  V3Section("کالا", footer = "نام و قیمت لازم است؛ بارکد را اسکن کنید یا بنویسید.") {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      V3TextField(name, { name = it }, "نامِ کالا", required = true)
      V3TextField(price, { price = it }, "قیمتِ فروش", kind = V3FieldKind.MONEY, helper = "سود هر عدد: ۲۵٬۰۰۰ ؋")
      V3TextField(barcode, { barcode = it }, "بارکد", kind = V3FieldKind.BARCODE, placeholder = "اسکن یا تایپ", onScan = {})
      V3TextField("", {}, "قیمتِ خرید", kind = V3FieldKind.MONEY, error = "قیمتِ خرید را بنویسید")
      V3TextField("کارتن", {}, "واحد", enabled = false)
    }
  }
  V3Section("گزینه‌ها") {
    V3ToggleRow("فعال در فروش", toggle, onToggle, icon = Icons.Outlined.Inventory2, hue = V3Hue.BRAND)
    V3Divider(start = 64.dp)
    V3ListRow("قفلِ برنامه", icon = Icons.Outlined.Lock, hue = V3Hue.BRAND, trailing = { V3Toggle(true, "قفل", null) }, chevron = false)
  }
}

@Composable
private fun Lists() {
  V3Section("ردیف‌ها", action = "همه", onAction = {}) {
    V3ListRow("ظاهر", icon = Icons.Outlined.Palette, hue = V3Hue.BLUE, value = "روشن", onClick = {})
    V3Divider(start = 64.dp)
    V3ListRow(
      "شیرِ پاکتی ۱ لیتری", subtitle = "۱۲ کارتن · ۱۴۴ عدد", icon = Icons.Outlined.Inventory2, hue = V3Hue.ORANGE, value = "۶۵٬۰۰۰ ؋",
      endActions = listOf(V3SwipeAction(Icons.Outlined.Edit, "ویرایش") {}, V3SwipeAction(Icons.Outlined.DeleteOutline, "حذف", V3ButtonKind.DANGER) {}),
      onClick = {},
    )
    V3Divider(start = 64.dp)
    V3ListRow("نوشابه ۱٫۵ لیتری", subtitle = "تمام شده", icon = Icons.Outlined.Inventory2, hue = V3Hue.PURPLE, trailing = { V3StatusPill(V3Status.CANCELLED, "تمام‌شده") }, onClick = {})
    V3Divider(start = 64.dp)
    V3ListRow("انتخاب‌شده", subtitle = "حالتِ انتخابِ گروهی", icon = Icons.Outlined.Search, selected = true, onClick = {})
  }
  V3Skeleton(rows = 3, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun States() {
  V3Message(V3Tone.SUCCESS, "پشتیبان گرفته شد", body = "امروز ساعتِ ۹:۴۰")
  Spacer(Modifier.height(8.dp))
  V3Message(V3Tone.INFO, "نسخهٔ تازه آماده است", action = "دیدن", onAction = {})
  Spacer(Modifier.height(8.dp))
  V3Message(V3Tone.WARNING, "۳ روز تا پایانِ اشتراک", body = "پیش از قفل شدن تمدید کنید")
  Spacer(Modifier.height(8.dp))
  V3Message(V3Tone.ERROR, "همگام‌سازی خطا دارد", body = "کارهای این دستگاه نگه داشته شده‌اند")
  V3Section(null) { V3EmptyState(Icons.Outlined.Inventory2, "انبار خالی است", body = "کالاهای دکان را ثبت کنید تا موجودی و هشدارِ کم‌بودن کار کند.", action = "ثبتِ کالای تازه", onAction = {}) }
  Spacer(Modifier.height(16.dp))
  V3SuccessCheck(visible = true)
}
