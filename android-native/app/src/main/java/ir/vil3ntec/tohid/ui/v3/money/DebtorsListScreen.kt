package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import ir.vil3ntec.tohid.ui.screens.DebtorsAddButton
import ir.vil3ntec.tohid.ui.screens.DebtorsBackdrop
import ir.vil3ntec.tohid.ui.screens.DebtorsSortRow
import ir.vil3ntec.tohid.ui.screens.DebtorsStatsCard
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.PhoneDebtorsTheme
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** کارهای فهرستِ قرض‌داران — هر کدام یک مسیر */
internal class DebtorsListActions(
  val onBack: (() -> Unit)?,
  val onOpen: (String) -> Unit,
  val onEdit: (String) -> Unit,
  val onDelete: (String) -> Unit,
  val onAdd: () -> Unit,
  /** «ادغامِ تکراری‌ها» (`debtors/merge`) — فقط با «گزارش‌های مشتری» روشن؛ وگرنه null و ردیفی نیست */
  val onMerge: (() -> Unit)? = null,
)

/**
 *  قرض‌داران (`debtors?sort=&q=`) — سربرگِ r9 همان (کارتِ سه‌عددی، جستجو با میکروفون، تراشه‌های ترتیب و برچسب)؛ ردیف‌ها
 *  سادهٔ `claude/e-rows-63q` (`DebtorListRow`): نام، «آخرین حرکت · شماره»، «۳۰۰ ؋ • قرض‌دار» به متنِ آرام، درونِ یک کارتِ
 *  پیوسته به‌جای شبکهٔ کارت‌های سرخ/سبزِ قاب‌دار. زدن ⇐ `debtors/{id}`؛ نگه‌داشتن ⇐ برگهٔ «باز کردن/ویرایش/حذف»
 *  (`debtors/{id}/edit`، `debtors/{id}/delete`)؛ «+» ⇐ `debtors/new`. ترتیب و جستجو از پرسشِ مسیر (`?sort=oldest` ِ هشدارها).
 */
@Composable
internal fun DebtorsListScreen(d: ShopData, sortArg: String?, queryArg: String?, actions: DebtorsListActions, duplicates: Int = 0) {
  var search by rememberSaveable { mutableStateOf(queryArg.orEmpty()) }
  var sortName by rememberSaveable { mutableStateOf(DebtorListModel.Sort.parse(sortArg?.uppercase()).name) }
  //  پیوندِ تازه (هشدار، جستجو) با همان تبِ باز ⇐ همان ترتیب/جستجو
  LaunchedEffect(sortArg) { if (sortArg != null) sortName = DebtorListModel.Sort.parse(sortArg.uppercase()).name }
  LaunchedEffect(queryArg) { if (queryArg != null) search = queryArg }
  val sort = DebtorListModel.Sort.parse(sortName)
  val list = rememberLazyListState()
  val stagger = rememberV3Stagger()
  val hidden = MoneyUndo.hiddenDebtors
  val term = search.trim()
  //  CRM-11: صافیِ برچسب (همان صافیِ پروندهٔ کامپیوتر)؛ برچسبی که دیگر نیست ⇒ همه
  var tagPick by rememberSaveable { mutableStateOf<String?>(null) }
  val tag = remember(d, tagPick) { DebtorListModel.keptTag(d, tagPick) }
  val tags = remember(d) { DebtorListModel.tagChips(d) }
  //  ردیف‌ها یک بار حساب می‌شوند، نه با هر کشیدنِ فهرست (`DebtorListModel`)
  val allRows = remember(d, term, sort, tag) { DebtorListModel.rows(d, term, sort, System.currentTimeMillis(), tag) }
  val rows = if (hidden.isEmpty()) allRows else allRows.filterNot { it.debtor.id in hidden }
  val summary = remember(d) { DebtorListModel.summary(d) }
  val motion = Motion.enabled
  val contacts = ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts() // CRM-15
  val today = remember(d) { todayIso() }
  //  متنِ هر ردیف (مانده، وضعیت، آخرین حرکت) یک بار برای همین دفتر و زبان
  val texts = remember(d, allRows, today, contacts, I18n.lang) {
    allRows.associate { it.debtor.id to DebtorRowModel.of(d, it, today, SaleTime.zoneOffset, contacts) }
  }
  var menuFor by rememberSaveable { mutableStateOf<String?>(null) }

  //  جست‌وجو (با صدا) از دکمهٔ نوار؛ برچسب‌ها یک ردیفِ فشرده زیرِ عنوان؛ «+» تنها دکمهٔ پایین — بقیهٔ r9 همان
  val voice = ir.vil3ntec.tohid.ui.screens.rememberVoiceSearch { search = it }
  V3Scaffold(
    title = Str.debtors,
    onBack = actions.onBack,
    largeTitle = false,
    search = ir.vil3ntec.tohid.ui.v3.V3Search(search, { search = it }, ir.vil3ntec.tohid.core.i18n.StrMoney.receivePickHint, onVoice = voice),
    chips = if (tags.isEmpty()) null else ({
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        V3Chip(StrMoney.allTags, tag == null, { tagPick = null })
        tags.forEach { (t, n) -> V3Chip("#$t", tag == t, { tagPick = if (tag == t) null else t }, count = n) }
      }
    }),
  ) { pad ->
    PhoneDebtorsTheme {
      DebtorsBackdrop {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          state = list,
          //  پایین: جای دکمهٔ گردِ «+» (۱۶ + ۵۶ + ۱۶) بالای پایینِ صفحه
          contentPadding = PaddingValues(PhoneV3Debt.gutter, 8.dp, PhoneV3Debt.gutter, pad.calculateBottomPadding() + 72.dp),
        ) {
          item(key = "debtors-head") {
            Column {
              DebtorsStatsCard(summary)
              Spacer(Modifier.height(12.dp))
              val onMerge = actions.onMerge
              if (onMerge != null && duplicates > 0) {
                V3ListRow(
                  title = StrMoney.mergeEntry(duplicates),
                  subtitle = StrMoney.mergeEntrySub,
                  icon = Icons.Outlined.PeopleOutline,
                  hue = V3Hue.ORANGE,
                  onClick = onMerge,
                )
                Spacer(Modifier.height(10.dp))
              }
              if (CustomerCrm.active(d).isNotEmpty()) {
                DebtorsSortRow(current = sort, count = rows.size) { sortName = it.name }
                Spacer(Modifier.height(8.dp))
              }
            }
          }
          if (rows.isEmpty()) {
            item(key = "debtors-empty") {
              //  خالیِ هر دو حالت گامِ بعدیِ خودش را دارد: دفترِ خالی «قرض‌دار تازه»، جستجوی بی نتیجه «نمایشِ همه»
              if (CustomerCrm.active(d).isEmpty()) {
                V3EmptyState(Icons.Filled.Groups, StrMoney.emptyDebtorsTitle, body = StrMoney.emptyDebtorsBody, action = StrMoney.newDebtor, onAction = actions.onAdd)
              } else {
                V3EmptyState(
                  Icons.Filled.Search, StrMoney.noDebtorMatchTitle, body = StrMoney.noDebtorMatchBody,
                  action = StrMoney.showAll, onAction = { search = ""; tagPick = null },
                )
              }
            }
          } else {
            itemsIndexed(rows, key = { _, row -> row.debtor.id }) { index, row ->
              val text = texts[row.debtor.id] ?: return@itemsIndexed
              //  ورودِ پله‌پله در نخستین نمایش؛ با عوضِ ترتیب هر ردیف به جای تازه‌اش سُر می‌خورد
              DebtorListRow(
                text = text,
                first = index == 0,
                last = index == rows.lastIndex,
                onOpen = { actions.onOpen(row.debtor.id) },
                onMenu = { menuFor = row.debtor.id },
                modifier = (if (motion) Modifier.animateItem() else Modifier).v3Stagger(stagger, row.debtor.id, index),
              )
            }
          }
        }
        //  دفترِ خالی دکمهٔ «قرض‌دار تازه» ِ خودش را دارد؛ آن‌جا دایره کشیده نمی‌شود (Ctrl+N می‌ماند)
        DebtorsAddButton(
          onClick = actions.onAdd,
          modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = pad.calculateBottomPadding()),
          visible = CustomerCrm.active(d).isNotEmpty(),
        )
      }
    }
  }

  //  نگه‌داشتنِ ردیف ⇐ همان سه کارِ کارتِ پیشین (× و مداد دیگر روی ردیف نیستند)
  val menuText = menuFor?.let(texts::get)
  if (menuText != null) {
    val id = menuText.id
    DebtorRowMenu(
      title = menuText.title,
      onOpen = { actions.onOpen(id) },
      onEdit = { actions.onEdit(id) },
      onDelete = { actions.onDelete(id) },
      onDismiss = { menuFor = null },
    )
  }
}
