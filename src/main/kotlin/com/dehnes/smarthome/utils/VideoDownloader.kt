package com.dehnes.smarthome.utils

import com.dehnes.smarthome.videoRootDir
import mu.KotlinLogging
import java.io.File
import java.io.RandomAccessFile
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class VideoDownloader : HttpServlet() {

    val logger = KotlinLogging.logger { }

    val classLoader = Thread.currentThread().contextClassLoader!!

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {

        val resourceName = pathToResource(req.pathInfo)
        val resource = File("$videoRootDir/$resourceName")


        if (!resource.exists() || resource.isDirectory) {
            resp.status = 404
        } else {
            val rangeValue = req.getHeader("Range")
            val range = if (rangeValue != null && rangeValue.startsWith("bytes=")) {
                val split = rangeValue.substring(6).split("-")
                val start = split[0].toLong()
                val end = if (split.size > 1 && split[1].isNotEmpty()) split[1].toLong() else resource.length()
                start..end
            } else {
                0..resource.length()
            }

            resp.status = 206
            val length = range.last - range.first
            resp.addHeader("Content-Range", "bytes ${range.first}-${range.last}/$length")
            resp.addHeader("Content-Length", length.toString())

            RandomAccessFile(resource, "r").use { inputStream ->
                var pos = range.first
                inputStream.seek(pos)
                val buf = ByteArray(1024)
                while (pos < range.last) {
                    val read = inputStream.read(buf)
                    if (read < 0) {
                        break
                    }
                    resp.outputStream.write(buf, 0, read)
                    pos += read
                }
            }
        }

        logger.info { "GET pathInfo=${req.pathInfo} status=${resp.status}" }
    }
}