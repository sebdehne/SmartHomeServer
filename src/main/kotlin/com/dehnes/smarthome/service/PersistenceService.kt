package com.dehnes.smarthome.service

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class PersistenceService {
    private val filename = System.getProperty("STORAGE_FILE_NAME", "storage.properties")
    private val properties = Properties()

    init {
        load()
    }

    @Synchronized
    operator fun get(key: String?, persistDefaultValue: String? = null): String? {
        var value = properties.getProperty(key)
        if (value == null && persistDefaultValue != null) {
            value = persistDefaultValue
            set(key, persistDefaultValue)
        }
        return value
    }

    @Synchronized
    operator fun set(key: String?, value: String?) {
        if (value == null) {
            properties.remove(key)
        } else {
            properties.setProperty(key, value)
        }
        write()
    }

    private fun load() {
        val file = File(filename)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
        try {
            FileInputStream(filename).use { fis -> properties.load(fis) }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun write() {
        try {
            FileOutputStream(filename, false).use { fos -> properties.store(fos, "") }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

}
