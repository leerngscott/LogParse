import Utils.Companion.info
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.internal.bind.ObjectTypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import java.util.*


data class DAOS<K, V>(val data: MutableList<Dao<K, V>> = mutableListOf(), private val outputFilePath: String) {
    @Synchronized
    fun addDao(k: K, v: V) {
        data.find { it.key == k }?.let {
            this.data.remove(it)
            it.value.add(v)
            this.data.add(it)
        } ?: data.add(Dao.fromPair(k, v))
    }

    @Synchronized
    fun removeDao(k: K): Dao<K, V>? {
        return data.find { it.key == k }?.also {
            data.remove(it)
        }
    }

    fun filterDao(k: K): MutableSet<V> = filterDao { it.key == k }.map { it.value }.flatten().toMutableSet()

    fun filterDao(predicate: (Dao<K, V>) -> Boolean): MutableSet<Dao<K, V>> {
        return data.filter { predicate(it) }.toMutableSet()
    }

    fun saveFile(path: String = outputFilePath) {
        val content = GlobalGson.toJson(this, DAOS::class.java)
        val fd = File(path)
        createNewFile(file = fd)
        fd.appendText(content)
    }

    fun fromFile(path: String = outputFilePath) {
        val fd = File(path)
        check(fd.exists())
        fd.readLines().first().let {
            (GlobalGson.fromJson(it, DAOS::class.java) as DAOS<K, V>).data.let {
                this.data.addAll(it)
            }
        }
    }

    fun dump() {
        info(data.joinToString("\n") { "${it.key.toString()} ${it.value.map { it.toString() }}" })
    }

    class Dao<K, V>(val key: K, val value: MutableSet<V> = mutableSetOf()) {
        companion object {
            fun <K, V> fromPair(key: K, value: V): Dao<K, V> = Dao<K, V>(key).apply { this.value.add(value) }
        }
    }
}

val GlobalGson: Gson =
    getGson()!!

private fun getGson(): Gson? {
    val gson = GsonBuilder().create()
    try {
        val factories: Field = Gson::class.java.getDeclaredField("factories")
        factories.setAccessible(true)
        val o: Any = factories.get(gson)
        val declaredClasses = Collections::class.java.declaredClasses
        for (c in declaredClasses) {
            if ("java.util.Collections\$UnmodifiableList" == c.name) {
                val listField: Field = c.getDeclaredField("list")
                listField.setAccessible(true)
                val list = listField.get(o) as MutableList<TypeAdapterFactory>
                val i = list.indexOf(ObjectTypeAdapter.FACTORY)
                list[i] = MapTypeAdapter.FACTORY
                break
            }
        }
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
    }
    return gson
}

private class MapTypeAdapter private constructor(private val gson: Gson) : TypeAdapter<Any?>() {
    @Throws(IOException::class)
    override fun read(`in`: JsonReader): Any? {
        val token = `in`.peek()
        return when (token) {
            JsonToken.BEGIN_ARRAY -> {
                val list: MutableList<Any?> = ArrayList()
                `in`.beginArray()
                while (`in`.hasNext()) {
                    list.add(read(`in`))
                }
                `in`.endArray()
                list
            }
            JsonToken.BEGIN_OBJECT -> {
                val map: MutableMap<String, Any?> = LinkedTreeMap()
                `in`.beginObject()
                while (`in`.hasNext()) {
                    map[`in`.nextName()] = read(`in`)
                }
                `in`.endObject()
                map
            }
            JsonToken.STRING -> `in`.nextString()
            JsonToken.NUMBER -> {
                val s = `in`.nextString()
                if (s.contains(".")) {
                    java.lang.Double.valueOf(s)
                } else {
                    try {
                        Integer.valueOf(s)
                    } catch (e: Exception) {
                        java.lang.Long.valueOf(s)
                    }
                }
            }
            JsonToken.BOOLEAN -> `in`.nextBoolean()
            JsonToken.NULL -> {
                `in`.nextNull()
                null
            }
            else -> throw IllegalStateException()
        }
    }

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: Any?) {
        if (value == null) {
            out.nullValue()
            return
        }
        val typeAdapter = gson.getAdapter(value.javaClass) as TypeAdapter<Any>
        if (typeAdapter is ObjectTypeAdapter) {
            out.beginObject()
            out.endObject()
            return
        }
        typeAdapter.write(out, value)
    }

    companion object {
        val FACTORY: TypeAdapterFactory = object : TypeAdapterFactory {
            override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
                return if (type.rawType == Any::class.java) {
                    MapTypeAdapter(gson) as TypeAdapter<T>
                } else null
            }
        }
    }
}
