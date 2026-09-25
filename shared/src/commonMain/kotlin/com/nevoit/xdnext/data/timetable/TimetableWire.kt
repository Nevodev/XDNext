package com.nevoit.xdnext.data.timetable

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reading the registrar's JSON without trusting its types.
 *
 * eHall answers these endpoints with a shape that is stable in its *names* and not in its *types*:
 * `KSJC` is a string in one semester and a number in the next, a field that is "not set" arrives as
 * `null` or as the key simply being absent, and an empty result set is `[]` on one endpoint and a
 * missing `rows` on another. The original read these as Dart's `dynamic`, where `int.parse(i["KSJC"])`
 * works only because the value happened to be a `String` that semester.
 *
 * These helpers are deliberately total: none of them throws, so a row with a field spelled
 * differently produces a *skipped row* the parser can log rather than a crash on the timetable's first
 * frame. [text] goes through `JsonPrimitive.content` rather than an `int`/`boolean` accessor, because
 * that is the one reading that works whatever the value's JSON type turns out to be.
 */

/** The object at [key], or null when it is absent, `null`, or not an object. */
internal fun JsonObject.obj(key: String): JsonObject? =
    (this[key] as? JsonObject)?.takeIf { it.isNotEmpty() }

/** The objects in the array at [key]; an absent or non-array value reads as no rows. */
internal fun JsonObject.rows(key: String = "rows"): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

/**
 * The text at [key].
 *
 * A JSON `null` and an absent key both answer null, and a number or boolean answers its literal text —
 * which is why a week flag string reads the same whether the registrar sent `"0110"` or `110`.
 */
internal fun JsonObject.text(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.contentOrNull
}

/** The whole number at [key], read from whatever type the registrar used. */
internal fun JsonObject.int(key: String): Int? = text(key)?.trim()?.toIntOrNull()

/**
 * The `datas.<name>` envelope every `jwapp` endpoint answers inside.
 *
 * Taken as a whole object rather than as its rows, because the endpoints put their own verdict next to
 * the rows: `xskcb` reports "this semester is not published" in `extParams`, and that verdict has to
 * be read from the same object the rows would have come from.
 */
internal fun JsonObject.datas(name: String): JsonObject? = obj("datas")?.obj(name)

/** The first object of `datas.<name>.rows`, which several endpoints answer with a single row. */
internal fun JsonObject.firstRow(name: String): JsonObject? = datas(name)?.rows()?.firstOrNull()
