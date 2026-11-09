package ir.vil3ntec.tohid.compat

import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.system.exitProcess

/*
 *  نمونه‌سازِ سازگاریِ دفتر — با مدلِ **همان نسخه‌ای** که `regen.sh` کنارش گذاشت (`git show <ref>:`).
 *
 *    maximal <out>     دفتری که هر فیلدِ این مدل در آن پر است (از روی توصیف‌گرِ خودِ `ShopData`)، همان‌طور که
 *                      `ShopStore` می‌نویسد: رمزگشایی و رمزگذاری با همان تنظیم‌ها.
 *    resave <in> <out> «این نسخه دفترِ [in] را باز کرد و ذخیره کرد» — همان `LedgerRecovery.decode` و
 *                      `json.encodeToString` ِ `ShopStore.writeToDisk`. نخواند ⇒ خروجِ ۲ (خطا در stderr).
 */

/** همان `ShopStore.json` (و `LedgerRecovery.decode`) */
private val STORE = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

private fun num(path: String): Long = (path.hashCode().toLong() and 0x7fff) % 997 + 2

@OptIn(ExperimentalSerializationApi::class)
private fun gen(d: SerialDescriptor, path: String, depth: Int): JsonElement {
  val name = d.serialName.removeSuffix("?")
  if (name == "kotlinx.serialization.json.JsonObject") return JsonObject(mapOf("k" to JsonPrimitive(path)))
  if (name == "kotlinx.serialization.json.JsonArray") return JsonArray(listOf(JsonPrimitive(path)))
  if (name.startsWith("kotlinx.serialization.json.Json")) return JsonPrimitive(path)
  if (depth > 8) return JsonNull
  return when (d.kind) {
    PrimitiveKind.STRING -> JsonPrimitive(path)
    PrimitiveKind.BOOLEAN -> JsonPrimitive(true)
    PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> JsonPrimitive(num(path))
    PrimitiveKind.DOUBLE, PrimitiveKind.FLOAT -> JsonPrimitive(num(path) + 0.25)
    PrimitiveKind.CHAR -> JsonPrimitive("c")
    SerialKind.ENUM -> JsonPrimitive(d.getElementName(0))
    StructureKind.LIST -> JsonArray(listOf(gen(d.getElementDescriptor(0), "$path.0", depth + 1)))
    StructureKind.MAP -> JsonObject(mapOf("k" to gen(d.getElementDescriptor(1), "$path.k", depth + 1)))
    StructureKind.CLASS, StructureKind.OBJECT -> JsonObject(
      (0 until d.elementsCount).associate { i -> d.getElementName(i) to gen(d.getElementDescriptor(i), "$path.${d.getElementName(i)}", depth + 1) },
    )
    is PolymorphicKind, SerialKind.CONTEXTUAL -> JsonNull
    else -> JsonNull
  }
}

fun main(args: Array<String>) {
  when (args.firstOrNull()) {
    "maximal" -> {
      val tree = gen(ShopData.serializer().descriptor, "", 0)
      val d = STORE.decodeFromJsonElement(ShopData.serializer(), tree)
      File(args[1]).writeText(STORE.encodeToString(ShopData.serializer(), d))
    }
    "resave" -> {
      val d = try {
        STORE.decodeFromString(ShopData.serializer(), File(args[1]).readText())
      } catch (e: Exception) {
        System.err.println("NOT READ: ${e::class.simpleName}: ${e.message}")
        exitProcess(2)
      }
      File(args[2]).writeText(STORE.encodeToString(ShopData.serializer(), d))
    }
    else -> { System.err.println("usage: maximal <out> | resave <in> <out>"); exitProcess(1) }
  }
}
