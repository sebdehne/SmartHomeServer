package com.dehnes.smarthome.api.dtos


data class VideoBrowserRequest(
    val minFileSize: Long?,
    val maxFileSize: Long?,
    val fromDate: Long?,
    val toDate: Long?
)

data class VideoBrowserResponse(
    val dirs: List<VideoDirectory>
)

data class VideoDirectory(
    val dirname: String,
    val files: List<VideoFile>
)

data class VideoFile(
    val filename: String,
    val size: Long,
    val timestampMillis: Long
)
