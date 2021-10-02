package com.dehnes.smarthome

import com.dehnes.smarthome.api.dtos.VideoBrowserRequest
import com.dehnes.smarthome.api.dtos.VideoBrowserResponse
import com.dehnes.smarthome.api.dtos.VideoDirectory
import com.dehnes.smarthome.api.dtos.VideoFile
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

//val videoRootDir = "testdir"
val videoRootDir = "/mnt/motion"

class VideoBrowser {

    fun rpc(request: VideoBrowserRequest): VideoBrowserResponse {
        val minFileSize = request.minFileSize ?: 0
        val maxFileSize = request.maxFileSize?.let { if (it >= 200000000) Long.MAX_VALUE else it } ?: Long.MAX_VALUE
        val fromDate = request.fromDate?.let { Instant.ofEpochMilli(it) } ?: Instant.MIN
        val toDate = request.toDate?.let { Instant.ofEpochMilli(it) } ?: Instant.MAX

        val root = File(videoRootDir)
        val result = mutableListOf<VideoDirectory>()
        root.listFiles().forEach { file ->
            if (file.isDirectory && file.name.matches("\\d\\d\\d\\d-\\d\\d-\\d\\d".toRegex())) {
                val files = getFiles(file, minFileSize, maxFileSize, fromDate, toDate)
                if (files.isNotEmpty()) {
                    result.add(
                        VideoDirectory(
                            file.name,
                            files
                        )
                    )
                }
            }
        }

        return VideoBrowserResponse(result)
    }

    fun getFiles(dir: File, minFileSize: Long, maxFileSize: Long, fromDate: Instant, toDate: Instant): List<VideoFile> {
        val result = mutableListOf<VideoFile>()
        dir.listFiles().forEach { file ->
            if (!file.isDirectory && file.name.matches("\\d\\d_\\d\\d_\\d\\d\\.mp4".toRegex())) {
                val fileDate = toInstant(dir.name, file.name)
                val fileSize = file.length()

                if (fileDate in fromDate..toDate) {
                    if (fileSize in minFileSize..maxFileSize) {
                        result.add(
                            VideoFile(
                                file.name,
                                fileSize,
                                fileDate.toEpochMilli()
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun toInstant(dirname: String, filename: String): Instant {
        val date = LocalDate.parse(dirname)
        val time = LocalTime.parse(filename.substring(0..7).replace("_", ":"))
        return date.atTime(time.hour, time.minute, time.second).atZone(ZoneId.of("Europe/Oslo")).toInstant()
    }

}
