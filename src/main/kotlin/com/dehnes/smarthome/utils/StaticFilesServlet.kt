package com.dehnes.smarthome.utils

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream

class StaticFilesServlet : HttpServlet() {
    val logger = KotlinLogging.logger { }

    val classLoader = Thread.currentThread().contextClassLoader!!

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {

        val resourceName = pathToResource(req.pathInfo)
        val resource = classLoader.getResource("webapp/$resourceName")

        if (resource == null) {
            logger.info { "GET pathInfo=${req.pathInfo} resourceName=$resourceName status=404" }
            resp.status = 404
        } else {
            logger.info { "GET pathInfo=${req.pathInfo} resourceName=$resourceName status=200" }
            resource.openStream().use { inputStream ->
                val bs = ByteArrayOutputStream().apply {
                    inputStream.copyTo(this)
                }
                val toByteArray = bs.toByteArray()
                resp.status = 200
                if (resourceName?.endsWith(".js") == true) {
                    resp.contentType = "application/javascript;charset=UTF-8"
                }
                resp.addHeader("Content-Length", toByteArray.size.toString())
                resp.outputStream.write(toByteArray)
                resp.outputStream.flush()
            }
        }
    }
}

val fileNamePattern = "^[\\w-]+(\\.[\\w]+)+\$".toRegex()
val dirNamePattern = "^[\\w-]+\$".toRegex()
val allowedDirs = listOf(
    "assets".toRegex(),
    "^\\d\\d\\d\\d-\\d\\d-\\d\\d".toRegex()
)
val defaultFile = "index.html"
val contextPath = "smarthome"

fun pathToResource(path: String): String? {

    val pathNoFrontSlash = (if (path.startsWith("/")) {
        path.substring(1)
    } else {
        path
    }).let { if (it.endsWith("/")) it.substring(0, it.length - 1) else it }

    var resultResource = mutableListOf<String>()
    if (pathNoFrontSlash.isNotEmpty()) {
        val pathParts = pathNoFrontSlash.split("/")
        pathParts.forEachIndexed { index, s ->
            if (s == contextPath) {
                return@forEachIndexed
            }

            if (index == pathParts.size - 1) {
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
        val dirs = resultResource.dropLast(1)
        val dirsString = dirs.joinToString(separator = "/")
        if (allowedDirs.any { it.matches(dirsString) }) {
            return resultResource.joinToString("/")
        }
        resultResource = resultResource.drop(1).toMutableList()
    }

    return defaultFile
}