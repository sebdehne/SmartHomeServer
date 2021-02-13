package com.dehnes.smarthome.utils

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class StaticFilesServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {

        val resourceName = pathToResource(req.pathInfo)
        val resource = Thread.currentThread().getContextClassLoader().getResource("webapp/$resourceName")

        if (resource == null) {
            resp.status = 404
        } else {
            resource.openStream().use { inputStream ->
                inputStream.copyTo(resp.outputStream)
            }
        }
    }
}

val fileNamePattern = "^[\\w]+(\\.[\\w]+)+\$".toRegex()
val dirNamePattern = "^[\\w]+\$".toRegex()
val allowedDirs = listOf(
    listOf("static", "css"),
    listOf("static", "js")
)
val defaultFile = "index.html"

fun pathToResource(path: String): String? {

    val pathNoFrontSlash = if (path.startsWith("/")) {
        path.substring(1)
    } else path

    var resultResource = mutableListOf<String>()
    if (pathNoFrontSlash.isNotEmpty()) {
        val pathParts = pathNoFrontSlash.split("/")
        pathParts.forEachIndexed { index, s ->
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
        if (allowedDirs.any { it == dirs }) {
            return resultResource.joinToString("/")
        }
        resultResource = resultResource.subList(1, resultResource.size)
    }

    return resultResource.firstOrNull() ?: defaultFile
}