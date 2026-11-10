package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrAppData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStream

/**
 *  خروجیِ کاملِ داده برای مالک — JSON و Excel (OPS-08).
 *
 *  ── چه چیزی را می‌بندد ──────────────────────────────────────────────
 *  دادهٔ دکان مالِ صاحبِ دکان است. تا امروز تنها راهِ بیرون بردنش
 *  «پشتیبان» بود: فایلی برای برگرداندن به همین برنامه، نه برای خواندن.
 *  گزارش‌های اکسل هم هر کدام فقط یک برش بودند (فروشِ این بازه، کالاهای
 *  کم…)، نه همهٔ داده.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── شکلِ JSON (قالبِ `tohid-owner-export`، نسخهٔ ۱) ─────────────────
 *      format, formatVersion, exportedAt, source{app, appVersion}, counts
 *      …همهٔ مجموعه‌های دفتر با همان کلیدهای `shop-data.json`…
 *      settings{storeName}
 *      archive{stockMovements, priceHistory, auditLog}   ← فقط اگر بایگانی دارد
 *
 *  سرآیند جلوی همه است تا هر خواننده‌ای پیش از داده، قالب و نسخه را
 *  ببیند. کلیدهای داده همان کلیدهای پشتیبان‌اند، پس همین فایل در «بازیابی
 *  از فایل» هم باز می‌شود (کلیدهای اضافه نادیده گرفته می‌شوند).
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── شکلِ اکسل ──────────────────────────────────────────────────────
 *  برگهٔ اول «اطلاعات خروجی»، بعد یک برگه برای هر مجموعه با سرستونِ
 *  فارسی، بعد برگه‌های بایگانی. ستون‌ها از خودِ داده می‌آیند: فیلدی که
 *  فردا اضافه شود بی‌صدا نمی‌افتد، فقط تا وقتی نامِ فارسی بگیرد با نامِ
 *  خودش می‌آید. رشته رشته می‌ماند (تلفنِ «0700» صفرش را از دست نمی‌دهد).
 *
 *  نسخهٔ وب همین را با همین نام‌ها در `license/owner-export.js` دارد؛
 *  یکی بودنِ دو فهرستِ نام‌ها را `server/test/owner-export.test.js` می‌سنجد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ⛔ **این‌جا فقط خوانده می‌شود.** مُهرِ پشتیبان نمی‌خورد — خروجی گرفتن
 *  پشتیبان گرفتن نیست. تنها نوشتن، کارِ صفحهٔ تنظیمات است: یک ردیفِ
 *  `data_export` در سابقهٔ عملیات (مشخصات ۲۱.۲: خروجی فقط برای مالک/نقشِ
 *  مجاز و با ثبتِ audit؛ شاگرد تنظیمات را اصلاً باز نمی‌کند).
 *
 *  قالبِ امن: هر رشته `inlineStr` است و هیچ خانه‌ای `<f>` ندارد، پس متنی
 *  مثلِ «=HYPERLINK(…)» در اکسل فرمول نمی‌شود.
 */
object OwnerExport {

  const val FORMAT = "tohid-owner-export"
  const val FORMAT_VERSION = 1

  /** سقفِ ردیف‌های یک برگهٔ اکسل (بی سرستون)؛ بیشتر ⇐ برگهٔ دنباله */
  const val MAX_ROWS = 1_048_575

  data class Meta(
    val exportedAt: Long,
    /** android | desktop | web */
    val app: String,
    val appVersion: String,
    val storeName: String,
  )

  data class Parsed(val formatVersion: Int, val data: ShopData, val archive: LedgerArchive.Archive)

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
  private val pretty = Json(json) { prettyPrint = true }

  /** مجموعه ⇐ نامِ برگه؛ ترتیبِ برگه‌ها همین است */
  val SHEETS: Map<String, String> = linkedMapOf(
    "products" to "کالاها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "productCategories" to "دسته‌های کالا",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "productUnits" to "واحدهای کالا",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "warehouseEntries" to "ورودی‌های انبار",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "sales" to "فاکتورهای فروش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "saleItems" to "اقلام فاکتورها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "returns" to "برگشتی‌های فروش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "debtors" to "قرض‌داران",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "transactions" to "حساب قرض‌داران",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "expenses" to "مصارف",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "expenseCategories" to "دسته‌های مصارف",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "suppliers" to "تأمین‌کنندگان",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "purchases" to "خریدها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "supplierPayments" to "پرداخت به تأمین‌کنندگان",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "stockMovements" to "حرکات انبار",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "priceHistory" to "سابقهٔ قیمت‌ها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "auditLog" to "سابقهٔ عملیات",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "safeEntries" to "گاوصندوق",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "setAside" to "سندهای کنارگذاشته",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
  )

  /** فیلد ⇐ سرستونِ فارسی */
  val HEADERS: Map<String, String> = mapOf(
    "id" to "شناسه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "name" to "نام",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "phone" to "تلفن",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "dueDate" to "سررسید",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "creditLimit" to "سقف اعتبار",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "phone2" to "تلفن دوم",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "tags" to "برچسب‌ها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "group" to "گروه مشتری",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "priceList" to "لیست قیمت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "marketingConsent" to "رضایت پیام تبلیغاتی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "mergedInto" to "ادغام‌شده در",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  کارتِ مشتری (CRM-16) — همان `license/owner-export.js`
    "cardCode" to "کارت مشتری",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  نشانیِ تحویلِ مشتری (CRM-09) — همان `license/owner-export.js`
    "deliveryAddress" to "نشانی تحویل",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "deliveryPin" to "پین تحویل",  // داده: CRM-09، همان `license/owner-export.js`
    "specialPrices" to "قیمت‌های خاص مشتری",  // داده: POS-18، همان `license/owner-export.js`
    "notes" to "یادداشت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "createdAt" to "زمان ثبت (UTC)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "date" to "تاریخ",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "amount" to "مبلغ",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "type" to "نوع",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "title" to "عنوان",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "category" to "دسته",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "subcategory" to "زیردسته",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "unit" to "واحد",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "debtorId" to "شناسهٔ قرض‌دار",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "productId" to "شناسهٔ کالا",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "supplierId" to "شناسهٔ تأمین‌کننده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "saleId" to "شناسهٔ فاکتور",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "saleItemId" to "شناسهٔ قلم فاکتور",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "purchaseId" to "شناسهٔ خرید",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "warehouseEntryId" to "شناسهٔ ورودی انبار",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "refId" to "شناسهٔ مرجع",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "refIds" to "ردیف‌های مرتبط",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "kind" to "نوعِ ردیفِ سیستمی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "purchasePrice" to "قیمت خرید",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "salePrice" to "قیمت فروش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "wholesalePrice" to "قیمت عمده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "minStock" to "حداقل موجودی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "barcodes" to "بارکدها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "photo" to "عکس دارد",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "active" to "فعال",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "saleUnit" to "واحد فروش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "unitFactor" to "ضریب واحد",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  PRD-02/PRD-26
    "namePs" to "نام پشتو",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "nameEn" to "نام انگلیسی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "synonyms" to "مترادف‌ها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "keywords" to "واژه‌های جست‌وجو",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "aisle" to "راهرو",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "shelf" to "قفسه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "shelfLevel" to "طبقهٔ قفسه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  PRD-24/PRD-25
    "alternatives" to "کالاهای جایگزین",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "customFields" to "فیلدهای سفارشی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  PRD-03/PRD-09
    "sku" to "کد کالا (SKU)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "stockless" to "خدماتی یا بی موجودی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  PRD-10/PRD-13
    "kit" to "اجزای کیت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "priceWindows" to "قیمت‌های زمان‌بندی‌شده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "parts" to "اجزای کیت هنگام فروش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  PRD-11
    "variantAxes" to "ویژگی‌های تنوع",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "variantOf" to "شناسهٔ کالای مدل",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "variant" to "ترکیبِ تنوع",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  POS-28
    "serialTracked" to "سریال‌دار",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "serials" to "سریال‌ها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "brand" to "برند",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "reorderPoint" to "نقطهٔ سفارش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "maxStock" to "حداکثر موجودی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "consumptionDays" to "چرخهٔ مصرف (روز)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)، MKT-06
    "brand" to "برند",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "packs" to "بسته‌ها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "purchaseUnits" to "واحدهای خرید",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)، PRD-07
    "costMinor" to "ارزش دقیق کالا (صدم افغانی)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)، PRD-07
    "unitName" to "واحد خرید",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)، PRD-07
    "unitQtyMilli" to "مقدار به واحد خرید (هزارم)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)، PRD-07
    "unitFactorMilli" to "ضریب واحد خرید (هزارم)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)، PRD-07
    "cartons" to "تعداد کارتن",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "perCarton" to "تعداد در هر کارتن",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "units" to "تعداد واحد",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "price" to "قیمت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "isAdjustment" to "اصلاح موجودی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "expiryDate" to "تاریخ انقضا",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "batch" to "شمارهٔ بچ",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "productionDate" to "تاریخ تولید",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "invoiceNumber" to "شمارهٔ فاکتور",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "series" to "سری سند",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "channel" to "کانال فروش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "orderId" to "شناسهٔ سفارش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "orderNo" to "شمارهٔ سفارش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "total" to "جمع",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "discountType" to "نوع تخفیف",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "discountValue" to "مقدار تخفیف",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "discount" to "تخفیف",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "finalTotal" to "مبلغ نهایی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "paymentMethod" to "روش پرداخت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "paidAmount" to "پرداخت‌شده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "remaining" to "باقی‌مانده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "status" to "وضعیت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "debtGiven" to "قرض داده‌شده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "debtSettled" to "قرض تسویه‌شده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "syncStatus" to "وضعیت همگام‌سازی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "cancelReason" to "دلیل لغو",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "seller" to "فروشنده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "correctedAt" to "تاریخِ تصحیح‌شده (UTC)", //  S28b: از سرور، وقتی ساعتِ دستگاه کج بود  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "approval" to "تأییدِ مدیر",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "cancelApproval" to "تأییدِ لغو",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "creditApproval" to "تأییدِ قرض",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)، STF-03
    "priceApproval" to "تأییدِ قیمت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)، STF-03
    "payments" to "ردیف‌های پرداخت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "tenderedMinor" to "دریافتی (پول)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "changeMinor" to "پول پس (پول)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "roundingMinor" to "گردکردن نقدی (پول)", // PAY-09  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  فاکتورِ نسخهٔ ۲ — توکنِ QRِ فاکتور (نشانیِ عمومیِ همان فروش)
    "receiptToken" to "توکنِ QRِ فاکتور",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "by" to "کاربر",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "approvedBy" to "تأییدکننده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "note" to "توضیح",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "z" to "گزارش Z",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  POS-40 اصلاحِ روشِ پرداخت (`pay_fix`)
    "fromMethod" to "روش پیشین",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "toMethod" to "روش تازه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "payRef" to "مرجع روش تازه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  PAY-07 وضعیتِ پرداخت (`pay_status`)
    "payRow" to "ردیف پرداخت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "payMethod" to "روش ردیف",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "fromStatus" to "وضعیت پیشین",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "toStatus" to "وضعیت تازه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  سفارشِ ویژه (POS-26): سند و وضعیتِ تازه روی ردیفِ سابقه
    "specialOrder" to "سفارش ویژه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  فروشِ امانی (POS-27): سند و وضعیتِ تازه روی ردیفِ سابقه
    "layaway" to "فروش امانی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  امتیازِ وفاداری (PRM-13): شمارِ امتیازِ ردیفِ سابقه
    "points" to "امتیاز",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "promoDiscountMinor" to "تخفیف خودکار (یک‌صدم افغانی)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "coupons" to "کدهای تخفیف",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "quantity" to "تعداد",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "unitPrice" to "قیمت واحد",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "totalPrice" to "جمع قلم",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "returnedQty" to "تعداد برگشتی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "listPriceMinor" to "قیمت فهرست (یک‌صدم افغانی)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "promo" to "تخفیف‌های خودکار قلم",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "priceSource" to "منبع قیمت",  // داده: POS-18 («special» = قیمتِ خاصِ مشتری)، همان `license/owner-export.js`
    "reason" to "دلیل",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  برگشت و تعویض (RET، فقط با تنظیمِ روشن)
    "docId" to "سند برگشت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "disposition" to "مقصد کالا",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "refund" to "روش بازپرداخت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "toAccount" to "کسر از بدهی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "toCredit" to "اعتبار فروشگاه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "creditTo" to "صاحب اعتبار",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "toMethods" to "به روش‌های دیگر",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "exchangeSaleId" to "فاکتور تعویض",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "adjKind" to "نوعِ اصلاح",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "address" to "نشانی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "totalAmount" to "مبلغ کل",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "debt" to "بدهی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "invoiceNo" to "شمارهٔ فاکتور خرید",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "qty" to "مقدار",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "oldPrice" to "قیمت قبلی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "newPrice" to "قیمت تازه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "nextInvoiceNo" to "شمارهٔ فاکتور بعدی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  موتورِ تازهٔ موجودی و بها (افزودنی، فقط وقتی تنظیمِ دکان روشن است)
    "landedCost" to "هزینهٔ جانبی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "landedCostMinor" to "هزینهٔ جانبی (صدم افغانی)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "locationId" to "محل",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "toLocationId" to "محلِ مقصد",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "userId" to "کاربر",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "lotId" to "سری",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  INV-10 — ردیفِ «انبارگردانی»: لحظهٔ شمردنِ کالا و دانستهٔ شمارنده (افزودنی)
    "countedAt" to "لحظهٔ شمارش (UTC)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "seenMilli" to "دانستهٔ شمارنده (هزارم)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  PUR-10 — زمانِ تأمینِ دستیِ تأمین‌کننده (افزودنی)
    "leadDays" to "زمان تأمین (روز)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  `adjKind` و `by` بالاتر (RET/PAY) آمده‌اند — کلیدِ تکراری در `mapOf` برچسبِ آن‌ها را عوض می‌کرد
    //  گاوصندوق (PAY-18)
    "amountMinor" to "مبلغ (پول)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "bagNo" to "شمارهٔ کیسه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "expectedMinor" to "ماندهٔ دفتر هنگامِ شمارش (پول)",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "shiftId" to "شناسهٔ صندوق",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "auditId" to "شناسهٔ ردیفِ صندوق",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  چند کشو (PAY-17): نامِ کشوی نام‌دار روی بازکردنِ صندوق (`shiftId` ِ فروش، برگشت و قرض همان «شناسهٔ صندوق» است)
    "drawer" to "کشو",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  انتقالِ موجودیِ سرور (INV-11): گامِ ارسال/دریافت/رد روی ردیفِ `stock_transfer`
    "xfer" to "گامِ انتقالِ موجودی",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  سندِ ردشده‌ای که صاحب کنار گذاشت/اصلاح کرد (`ShopData.setAside`، فقط دستگاهِ سازنده)
    "table" to "جدولِ سند",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "rowId" to "شناسهٔ ردیفِ سند",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "fixedDoc" to "فاکتورِ اصلاح",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "resolvedAt" to "زمانِ تصمیم",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "settledAt" to "زمانِ کنار گذاشتن",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "ops" to "تغییرهای ردشده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "rows" to "ردیف‌های کنارگذاشته",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "undo" to "برگرداندن‌ها",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "kept" to "برگردانده‌نشده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  سابقهٔ دسترسی (CRM-15): بیننده، نقش، دستگاه و نوعِ دیدن روی ردیفِ `customer_view` (نامِ بیننده همان «کاربر»)
    "viewer" to "شناسهٔ بیننده",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "role" to "نقش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "device" to "دستگاه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "view" to "نوعِ دیدن",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  STF-09: مُهر و زنجیرهٔ هشِ سابقهٔ عملیات (`userId` بالاتر «کاربر» است) — خروجی خودش سنجیدنی است
    "userRole" to "نقشِ کاربر",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "deviceId" to "دستگاهِ ثبت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "chain" to "زنجیرهٔ هش",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "chainSeq" to "شمارهٔ حلقه",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "prev" to "هشِ پیشین",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "hash" to "هشِ ردیف",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    "chainV" to "سطحِ زنجیره",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    //  بسته‌بندی و تولید (INV-21): دستورِ ساختِ ثبت‌شده (بهای دقیقِ ردیفِ خروجی همان `costMinor` ِ بالا، PRD-07)
    "bom" to "دستورِ ساخت",  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
  )

  /** کلیدهای سرآیند — داده نیستند و برگه نمی‌شوند */
  private val HEAD = setOf("format", "formatVersion", "exportedAt", "source", "counts", "settings", "archive")

  const val INFO_SHEET = "اطلاعات خروجی"  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
  const val ARCHIVE_PREFIX = "بایگانی — "  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)

  fun tree(d: ShopData, meta: Meta, archive: LedgerArchive.Archive = LedgerArchive.Archive()): JsonObject {
    val data = json.encodeToJsonElement(ShopData.serializer(), d).jsonObject
    val arch = json.encodeToJsonElement(LedgerArchive.Archive.serializer(), archive).jsonObject
    return buildJsonObject {
      put("format", FORMAT)
      put("formatVersion", FORMAT_VERSION)
      put("exportedAt", iso(meta.exportedAt))
      putJsonObject("source") {
        put("app", meta.app)
        put("appVersion", meta.appVersion)
      }
      putJsonObject("counts") { data.forEach { (k, v) -> if (v is JsonArray) put(k, v.size) } }
      data.forEach { (k, v) -> put(k, v) }
      putJsonObject("settings") { put("storeName", meta.storeName) }
      if (arch.values.any { it is JsonArray && it.isNotEmpty() }) put("archive", arch)
    }
  }

  fun json(d: ShopData, meta: Meta, archive: LedgerArchive.Archive = LedgerArchive.Archive()): String =
    pretty.encodeToString(JsonObject.serializer(), tree(d, meta, archive))

  /** همان JSON، مستقیم روی جریان — دفترِ بزرگ یک بارِ دیگر رشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class)
  fun writeJson(d: ShopData, meta: Meta, archive: LedgerArchive.Archive, out: OutputStream) =
    pretty.encodeToStream(JsonObject.serializer(), tree(d, meta, archive), out)

  /**
   *  خواندنِ همین قالب. نسخهٔ تازه‌تر رد می‌شود (نه نیمه‌خوانده)؛ فایلِ
   *  دیگری که سرآیندِ این قالب را ندارد هم.
   */
  fun parse(raw: String): Parsed {
    val tree = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
      ?: error(StrAppData.exportBad)
    if ((tree["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) error(StrAppData.exportNotOurs)
    val v = (tree["formatVersion"] as? JsonPrimitive)?.intOrNull ?: error(StrAppData.exportNoVersion)
    if (v < 1 || v > FORMAT_VERSION) error(StrAppData.exportNewer(v))
    val data = json.decodeFromJsonElement(ShopData.serializer(), tree)
    val archive = (tree["archive"] as? JsonObject)
      ?.let { json.decodeFromJsonElement(LedgerArchive.Archive.serializer(), it) }
      ?: LedgerArchive.Archive()
    return Parsed(v, data, archive)
  }

  fun xlsx(d: ShopData, meta: Meta, archive: LedgerArchive.Archive = LedgerArchive.Archive()): ByteArray =
    Xlsx.book(sheets(tree(d, meta, archive)))

  fun writeXlsx(d: ShopData, meta: Meta, archive: LedgerArchive.Archive, out: OutputStream) =
    Xlsx.book(sheets(tree(d, meta, archive)), out)

  /** برگه‌ها از روی درختِ JSON — همان الگوریتمِ نسخهٔ وب */
  fun sheets(tree: JsonObject): List<Xlsx.Sheet> {
    val tables = ArrayList<Pair<String, List<List<Xlsx.Cell?>>>>()
    val keys = SHEETS.keys.filter { tree[it] is JsonArray } +
      tree.keys.filter { it !in SHEETS && it !in HEAD && tree[it] is JsonArray }
    for (k in keys) tables += split(SHEETS[k] ?: k, table(tree[k] as JsonArray))
    (tree["archive"] as? JsonObject)?.forEach { (k, v) ->
      if (v is JsonArray) tables += split(ARCHIVE_PREFIX + (SHEETS[k] ?: k), table(v))
    }
    return listOf(Xlsx.Sheet(INFO_SHEET, info(tree, tables))) + tables.map { Xlsx.Sheet(it.first, it.second) }
  }

  private fun info(tree: JsonObject, tables: List<Pair<String, List<List<Xlsx.Cell?>>>>): List<List<Xlsx.Cell?>> {
    fun t(s: String) = Xlsx.Cell.Text(s)
    fun str(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""
    val source = tree["source"] as? JsonObject
    val settings = tree["settings"] as? JsonObject
    val rows = ArrayList<List<Xlsx.Cell?>>()
    rows += listOf(t("عنوان"), t("مقدار"))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    rows += listOf(t("قالب"), t(str(tree["format"])))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    rows += listOf(t("نسخهٔ قالب"), cell("formatVersion", tree["formatVersion"]))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    rows += listOf(t("زمان خروجی (UTC)"), t(str(tree["exportedAt"])))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    rows += listOf(t("برنامه"), t(str(source?.get("app"))))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    rows += listOf(t("نسخهٔ برنامه"), t(str(source?.get("appVersion"))))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    rows += listOf(t("نام فروشگاه"), t(str(settings?.get("storeName"))))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    tree.forEach { (k, v) -> if (k !in HEAD && v !is JsonArray) rows += listOf(t(HEADERS[k] ?: k), cell(k, v)) }
    settings?.forEach { (k, v) -> if (k != "storeName") rows += listOf(t("تنظیمات — $k"), cell(k, v)) }  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    for ((name, table) in tables) rows += listOf(t("ردیف‌های «$name»"), Xlsx.Cell.Num((table.size - 1).toDouble()))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    rows += listOf(t("عکس‌ها"), t("در این خروجی نیستند؛ «پشتیبان کامل» آن‌ها را دارد"))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    return rows
  }

  private fun table(arr: JsonArray): List<List<Xlsx.Cell?>> {
    if (arr.isEmpty()) return listOf(listOf(Xlsx.Cell.Text("هیچ ردیفی ثبت نشده")))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    if (!arr.all { it is JsonObject }) {
      return listOf(listOf<Xlsx.Cell?>(Xlsx.Cell.Text("مقدار"))) + arr.map { listOf(cell("", it)) }  // داده: برگه و ستونِ خروجیِ کاملِ صاحب (هم‌سانِ `license/owner-export.js`)
    }
    val cols = LinkedHashSet<String>()
    arr.forEach { cols.addAll((it as JsonObject).keys) }
    val head = cols.map<String, Xlsx.Cell?> { Xlsx.Cell.Text(HEADERS[it] ?: it) }
    return listOf(head) + arr.map { o -> cols.map { k -> cell(k, (o as JsonObject)[k]) } }
  }

  /** برگهٔ بیش از سقفِ اکسل ⇐ چند برگه، هر کدام با همان سرستون */
  private fun split(name: String, rows: List<List<Xlsx.Cell?>>): List<Pair<String, List<List<Xlsx.Cell?>>>> {
    if (rows.size - 1 <= MAX_ROWS) return listOf(name to rows)
    return rows.drop(1).chunked(MAX_ROWS).map { name to (listOf(rows[0]) + it) }
  }

  private fun cell(key: String, e: JsonElement?): Xlsx.Cell? = when (e) {
    null, is JsonNull -> null
    is JsonPrimitive -> when {
      e.isString -> Xlsx.Cell.Text(e.content)
      e.booleanOrNull != null -> Xlsx.Cell.Bool(e.booleanOrNull!!)
      else -> {
        val n = e.doubleOrNull
        val l = e.longOrNull
        when {
          n == null -> Xlsx.Cell.Text(e.content)
          //  زمانِ میلی‌ثانیه‌ای (`createdAt` و…) ⇐ تاریخ و ساعتِ خوانا
          key.endsWith("At") && l != null && l > 100_000_000_000L -> Xlsx.Cell.Time(l)
          else -> Xlsx.Cell.Num(n)
        }
      }
    }
    is JsonArray ->
      if (e.all { it is JsonPrimitive && it !is JsonNull }) Xlsx.Cell.Text(e.joinToString("، ") { (it as JsonPrimitive).content })
      else Xlsx.Cell.Text(e.toString())
    is JsonObject -> Xlsx.Cell.Text(e.toString())
  }

  private fun iso(ms: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
      timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(ms))
}
