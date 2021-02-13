package com.dehnes.smarthome.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class PathMapperTest {

    @Test
    fun test() {
        assertEquals("file.js", pathToResource("/file.js"))
        assertEquals("index.html", pathToResource("/"))
        assertEquals("index.html", pathToResource("/garage"))
        assertEquals("static/css/file.js", pathToResource("/static/css/file.js"))
        assertEquals("static/js/file.js", pathToResource("/static/js/file.js"))
        assertEquals("static/js/file.js", pathToResource("/garage/static/js/file.js"))
        assertEquals("file.js", pathToResource("/static/js2/file.js"))
    }
}