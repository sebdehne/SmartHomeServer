package com.dehnes.smarthome.utils

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import io.github.oshai.kotlinlogging.KotlinLogging

class StaticFilesServlet : HttpServlet() {
    val logger = KotlinLogging.logger { }

    val classLoader = Thread.currentThread().contextClassLoader!!

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {

        val resourceName = pathToResource(req.pathInfo)
        val resource = classLoader.getResource("webapp/$resourceName")

        if (resource == null) {
            resp.status = 404
        } else {
            resource.openStream().use { inputStream ->
                inputStream.copyTo(resp.outputStream)
            }
        }

        logger.info { "GET pathInfo=${req.pathInfo} status=${resp.status}" }
    }
}

val fileNamePattern = "^[\\w]+(\\.[\\w]+)+\$".toRegex()
val dirNamePattern = "^[\\w-]+\$".toRegex()
val allowedDirs = listOf(
    "static/css".toRegex(),
    "static/js".toRegex(),
    "^\\d\\d\\d\\d-\\d\\d-\\d\\d".toRegex()
)
val defaultFile = "index.html"
val contextPath = "smarthome"

fun pathToResource(path: String): String? {

    val pathNoFrontSlash = (if (path.startsWith("/")) {
        path.substring(1)
    } else path).let { if (it.endsWith("/")) it.substring(0, it.length - 1) else it }

    var resultResource = mutableListOf<String>()
    if (pathNoFrontSlash.isNotEmpty()) {
        val pathParts = pathNoFrontSlash.split("/")
        pathParts.forEachIndexed { index, s ->
            if (s == contextPath) {
                return@forEachIndexed
            }

            if (index + 1 >= pathParts.size) {
                if (s.matches(fileNamePattern)) {
                    resultResource.add(s)
                }
            } else {
                if (s.matches(dirNamePattern)) {
                    resultResource.add(s)
                } else {
                    return null
                }
            }
        }
    }


    while (resultResource.size > 1) {
        val dirs = resultResource.subList(0, resultResource.size - 1)
        val dirsString = dirs.joinToString(separator = "/")
        if (allowedDirs.any {
                it.matches(dirsString)
            }) {
            return resultResource.joinToString("/")
        }
        resultResource = resultResource.subList(1, resultResource.size)
    }

    return resultResource.firstOrNull() ?: defaultFile
}