package com.example.canchasdeportivas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson

/** Caché de consulta por cuenta. Nunca almacena contraseñas, tokens ni comprobantes. */
class LocalCache(context: Context) : SQLiteOpenHelper(context, "canchas_cache.db", null, 1) {
    private val gson = Gson()
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE cache(owner TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY(owner,kind))") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun write(owner: String, kind: String, value: Any) {
        writableDatabase.insertWithOnConflict("cache", null, ContentValues().apply {
            put("owner", owner); put("kind", kind); put("payload", gson.toJson(value)); put("updated", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    @Synchronized fun <T> read(owner: String, kind: String, type: Class<T>): T? =
        readableDatabase.query("cache", arrayOf("payload"), "owner=? AND kind=?", arrayOf(owner, kind), null, null, null).use {
            if (it.moveToFirst()) runCatching { gson.fromJson(it.getString(0), type) }.getOrNull() else null
        }
    @Synchronized fun clear() { writableDatabase.delete("cache", null, null) }
}
