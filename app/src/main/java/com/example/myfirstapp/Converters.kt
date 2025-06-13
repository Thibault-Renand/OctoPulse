// app/src/main/java/com/example/myfirstapp/Converters.kt
package com.example.myfirstapp

import androidx.room.TypeConverter
import com.google.gson.Gson                     // ← Import ajouté
import com.google.gson.reflect.TypeToken       // ← Import ajouté

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun listToJson(list: List<String>): String =
        gson.toJson(list)

    @TypeConverter
    fun jsonToList(json: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    fun textureToString(texture: TextureRepas): String =
        texture.name

    @TypeConverter
    fun stringToTexture(name: String): TextureRepas =
        TextureRepas.valueOf(name)

    @TypeConverter
    fun regimeToString(regime: RegimeType): String =
        regime.name

    @TypeConverter
    fun stringToRegime(name: String): RegimeType =
        RegimeType.valueOf(name)
}
