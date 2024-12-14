package com.dehnes.smarthome.utils

import java.io.File
import java.nio.file.Files
import kotlin.io.path.name

object SerialPortFinder {

    private val searchPath = "/dev/serial/by-id"


    fun findSerialPort(usbDeviceName: String): String {
        val files = File(searchPath).listFiles().map { it.name }
        val candidate = files.filter { it.contains(usbDeviceName) }
        if (candidate.size != 1) {
            error("Did not find single file with name $usbDeviceName in $searchPath - found: $files")
        }

        val targetFile = candidate.single()
        val file = File(searchPath, targetFile)
        val deviceFile = Files.readSymbolicLink(file.toPath())

        return "/dev/${deviceFile.name}"
    }

}