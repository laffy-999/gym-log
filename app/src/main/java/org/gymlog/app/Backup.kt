package org.gymlog.app

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * A complete, versioned JSON copy of all records, the training plan, and settings.
 * Import replaces everything in one transaction: it either restores the whole backup or changes nothing.
 */
object Backup {
    private const val FORMAT = "gym-log-backup"
    private const val VERSION = 1
    private const val INVALID = "This file isn't a Gym Log backup this version can read. Nothing was changed."

    fun export(store: WorkoutStore, settings: Settings, out: OutputStream) {
        val db = store.readableDatabase
        JsonWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { json ->
            json.setIndent(" ")
            // One read transaction so the copy is consistent even if written to concurrently.
            db.beginTransaction()
            try {
                json.beginObject()
                json.name("format").value(FORMAT)
                json.name("version").value(VERSION.toLong())
                json.name("schemaVersion").value(db.version.toLong())
                json.name("exportedAt").value(System.currentTimeMillis())
                json.name("settings").beginObject()
                for ((key, value) in SettingsStore.toMap(settings)) when (value) {
                    is String -> json.name(key).value(value)
                    is Int -> json.name(key).value(value.toLong())
                    is Boolean -> json.name(key).value(value)
                }
                json.endObject()
                json.name("tables").beginObject()
                for (table in WorkoutStore.tables) {
                    json.name(table).beginArray()
                    db.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { cursor -> while (cursor.moveToNext()) writeRow(json, cursor) }
                    json.endArray()
                }
                json.endObject()
                json.endObject()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun writeRow(json: JsonWriter, cursor: Cursor) {
        json.beginObject()
        for (index in 0 until cursor.columnCount) {
            json.name(cursor.getColumnName(index))
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> json.nullValue()
                Cursor.FIELD_TYPE_INTEGER -> json.value(cursor.getLong(index))
                Cursor.FIELD_TYPE_STRING -> json.value(cursor.getString(index))
                else -> throw IOException("Unexpected column type")
            }
        }
        json.endObject()
    }

    /** Replaces all data with the backup and returns its settings. Throws [InputConflict] if the file is unusable. */
    fun import(store: WorkoutStore, input: InputStream): Settings {
        val db = store.writableDatabase
        val parsed = try { parse(input) } catch (_: IOException) { throw InputConflict(INVALID) } catch (_: IllegalStateException) { throw InputConflict(INVALID) }
            catch (_: NumberFormatException) { throw InputConflict(INVALID) }
        if (parsed.format != FORMAT || parsed.version !in 1..VERSION || parsed.schemaVersion !in 1..db.version) throw InputConflict(INVALID)
        if (!parsed.tables.keys.all { it in WorkoutStore.tables }) throw InputConflict(INVALID)
        val columns = WorkoutStore.tables.associateWith { table ->
            db.rawQuery("PRAGMA table_info($table)", null).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) } }
        }
        try {
            store.transaction { tx ->
                for (table in WorkoutStore.tables.asReversed()) tx.delete(table, null, null)
                for (table in WorkoutStore.tables) {
                    val rows = parsed.tables[table].orEmpty()
                    // Parents before children: drop segments always have larger ids than their sets.
                    val ordered = if (table == "logged_set") rows.sortedBy { (it["id"] as? Long) ?: Long.MAX_VALUE } else rows
                    for (row in ordered) {
                        if (!columns.getValue(table).containsAll(row.keys)) throw InputConflict(INVALID)
                        tx.insertOrThrow(table, null, ContentValues(row.size).apply {
                            for ((column, value) in row) when (value) {
                                null -> putNull(column)
                                is Long -> put(column, value)
                                is String -> put(column, value)
                                else -> throw InputConflict(INVALID)
                            }
                        })
                    }
                }
                tx.rawQuery("PRAGMA foreign_key_check", null).use { if (it.moveToFirst()) throw InputConflict(INVALID) }
            }
        } catch (_: SQLiteException) {
            throw InputConflict(INVALID)
        }
        return SettingsStore.fromMap(parsed.settings)
    }

    private class Parsed(
        var format: String? = null,
        var version: Long = -1,
        var schemaVersion: Long = -1,
        var settings: Map<String, Any?> = emptyMap(),
        var tables: Map<String, List<Map<String, Any?>>> = emptyMap(),
    )

    private fun parse(input: InputStream): Parsed {
        val result = Parsed()
        JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { json ->
            json.beginObject()
            while (json.hasNext()) when (json.nextName()) {
                "format" -> result.format = json.nextString()
                "version" -> result.version = json.nextLong()
                "schemaVersion" -> result.schemaVersion = json.nextLong()
                "settings" -> result.settings = readObject(json)
                "tables" -> result.tables = buildMap {
                    json.beginObject()
                    while (json.hasNext()) {
                        val table = json.nextName()
                        put(table, buildList { json.beginArray(); while (json.hasNext()) add(readObject(json)); json.endArray() })
                    }
                    json.endObject()
                }
                else -> json.skipValue()
            }
            json.endObject()
        }
        return result
    }

    /** A flat object of strings, integers, booleans, and nulls; nested values are rejected. */
    private fun readObject(json: JsonReader): Map<String, Any?> = buildMap {
        json.beginObject()
        while (json.hasNext()) {
            val name = json.nextName()
            put(name, when (json.peek()) {
                JsonToken.NULL -> { json.nextNull(); null }
                JsonToken.BOOLEAN -> json.nextBoolean()
                JsonToken.NUMBER -> json.nextLong()
                JsonToken.STRING -> json.nextString()
                else -> throw IOException("Unexpected value")
            })
        }
        json.endObject()
    }
}
