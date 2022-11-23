package com.dehnes.smarthome.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.charset.Charset
import java.util.*

class PersistenceService(
    private val objectMapper: ObjectMapper
) {
    private val filenameJson = System.getProperty("STORAGE_FILE_NAME", "properties.json")

    fun inDevMode() = this["devMode", "false"].toBoolean()

    @Synchronized
    operator fun get(key: String?, persistDefaultValue: String? = null): String? {
        val properties = loadJsonProperties(filenameJson)
        var value = properties.getProperty(key)
        if (value == null && persistDefaultValue != null) {
            value = persistDefaultValue
            set(key, persistDefaultValue)
        }
        return value
    }

    @Synchronized
    operator fun set(key: String?, value: String?) {
        val properties = loadJsonProperties(filenameJson)

        if (value == null) {
            properties.remove(key)
        } else {
            properties.setProperty(key, value)
        }
        writeAsJson(filenameJson, properties)
    }

    fun getAllFor(prefix: String): List<Pair<String, String>> {
        val properties = loadJsonProperties(filenameJson)
        return properties.keys().toList()
            .map { it as String }
            .filter { key -> key.startsWith(prefix) }
            .mapNotNull { key ->
                properties.getProperty(key)?.let {
                    key to it
                }
            }
    }

    private fun writeAsJson(filename: String, properties: Properties) {
        val json = mutableMapOf<String, Any>()
        properties.forEach { (k, value) ->
            val key = k as String
            setInJson(
                json,
                key.split("."),
                value as String
            )
        }

        File(filename).writeText(
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json),
            Charset.defaultCharset()
        )
    }

    private fun setInJson(obj: MutableMap<String, Any>, key: List<String>, value: String) {
        if (key.size > 1) {
            obj.putIfAbsent(key.first(), mutableMapOf<String, Any>())
            setInJson(
                obj[key.first()] as MutableMap<String, Any>,
                key.subList(1, key.size),
                value
            )
        } else {
            obj[key.first()] = value
        }
    }

    private fun loadJsonProperties(filename: String): Properties {
        val jsonStr = File(filename).readText(Charset.defaultCharset())
        val json = objectMapper.readValue<Map<String, Any>>(jsonStr)

        val p = Properties()
        jsonToProperties(
            p,
            emptyList(),
            json
        )
        return p
    }

    private fun jsonToProperties(dst: Properties, path: List<String>, source: Map<String, Any>) {
        source.forEach { (key, value) ->
            if (value is Map<*, *>) {
                jsonToProperties(
                    dst,
                    path + key,
                    value as Map<String, Any>
                )
            } else {
                dst[(path + key).joinToString(separator = ".")] = value
            }
        }
    }


}
