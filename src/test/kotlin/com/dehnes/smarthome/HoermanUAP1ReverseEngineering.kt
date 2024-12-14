package com.dehnes.smarthome

import com.dehnes.smarthome.garage.HoermannE4Broadcast
import com.dehnes.smarthome.garage.SupramatiDoorState
import com.dehnes.smarthome.utils.CRC16
import com.dehnes.smarthome.utils.parse16UInt
import com.dehnes.smarthome.utils.toUnsignedInt
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*

@Disabled
class HoermanUAP1ReverseEngineering {
    val basePath = "src/test/resources/hoermann_files"


    @Test
    fun single() {
        parseAndReport("02_light_on_light_off.txt")
        //parseAndReport("12_arduino_first_try_goes_error.txt")
    }

    @Test
    fun single2() {
        //parseAndReport("02_light_on_light_off.txt")
        parseAndReport("12_arduino_first_try_goes_error.txt")
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun compare() {
        val left = parseFile("01_bus_scan_and_wait_until_light_off.txt")
        val right = parseFile("12_arduino_02_try_goes_error.txt")

        var leftIndex = 0
        var rightIndex = 0
        var leftCnt = -1
        var rightCnt = -1

        while (true) {
            val l = left[leftIndex]
            val r = right[rightIndex]

            check(l.addr == r.addr) {
                println()
            }

            if (l.hexLine == "02179CB900029C4100020402041701B84D") {
                // skip other req + resp on left side
                leftIndex++
                leftIndex++
                rightIndex--
                leftCnt++
                continue
            }

            if (leftCnt == -1 && l.hexLine.startsWith("02179CB900089C41000204")) {
                // TX
                leftCnt = l.hexLine.drop(22).substring(0,2).hexToInt()
                rightCnt = leftCnt
            }

            val matchViaPattern = listOf(
                "**179CB900059C410003060002210E010*****", // bus-scan
                "00109D31000912****00004060**0000000000001000010000****", // broadcast
            ).any {
                if (l.macthes(it)) {
                    r.macthes(it).apply {
                        if (!this) {
                            println()
                        }
                    }
                } else false
            }

            val isSame = l.hexLine == r.hexLine

            val tx = l.hexLine.matches("02179CB900089C41000204${leftCnt.toHexString().drop(6)}030000\\w\\w\\w\\w".toRegex())
                    && r.hexLine.matches("02179CB900089C41000204${rightCnt.toHexString().drop(6)}030000\\w\\w\\w\\w".toRegex())

            if (tx) {
                leftCnt++
                rightCnt++
            }

            if (!isSame && !matchViaPattern) {
                println()
                // 02179CB900089C4100020403030000 02171003000301000000000000000000000000A49A
                // 02179CB900089C4100020401030000 0217100100030100000000000000000000000025FB

                error("")
            }
            leftIndex++
            rightIndex++
        }
    }

    @Test
    fun test() {
        File(basePath).listFiles()
            .filter { it.name.endsWith(".txt") }
            .map { it.name }.forEach { f ->
                println("======================================================")
                println("$f:")
                parseAndReport(f)
                println()
                println()
                println()
            }
    }

    fun parseFile(file: String) = File("$basePath/$file").readLines()
        .map { it.trim() }
        .filter { it.contains("Received:") || it.contains("Sending:") }
        .map {
            val strings = it.split(": ")
            strings[0].trim().toLong() to strings[2].trim()
        }.flatMap {
            val parts = it.second.split("0217")
            if (false && parts.size > 2) {
                listOf(
                    it.first to ("0217" + parts[1]),
                    it.first to ("0217" + parts[2]),
                )
            } else {
                listOf(it)
            }
        }.map { (t, line) -> parse(line, t) }

    fun parseAndReport(file: String) {
        val ignoredRequests = LinkedList<ModBusMsg>()
        val txes = mutableListOf<Pair<ModBusMsg, ModBusMsg>>()

        File("$basePath/$file").readLines()
            .map { it.trim() }
            .filter { it.contains("Received:") || it.contains("Sending:") }
            .map {
                val strings = it.split(": ")
                strings[0].trim().toLong() to strings[2].trim()
            }.flatMap {
                val parts = it.second.split("0217")
                if (false && parts.size > 2) {
                    listOf(
                        it.first to ("0217" + parts[1]),
                        it.first to ("0217" + parts[2]),
                    )
                } else {
                    listOf(it)
                }
            }
            .forEach { (t, line) ->
                val modBusMsg = parse(line, t)
                if (modBusMsg is ReadWriteMultipleResponse) {
                    if (ignoredRequests.isEmpty()) {
                        return@forEach
                    }
                    val req = ignoredRequests.removeLast()
                    txes.add(req to modBusMsg)
                } else {
                    ignoredRequests.add(modBusMsg)
                }
            }


        val ignoredBusScans = mutableListOf<ModBusMsg>()
        val ignoredOther = mutableListOf<ModBusMsg>()
        val broadcasts = mutableListOf<ModBusMsg>()
        val commandRequests = mutableListOf<Pair<ModBusMsg, ModBusMsg>>()
        val busScan = mutableListOf<Pair<ModBusMsg, ModBusMsg>>()
        val other = mutableListOf<Pair<ModBusMsg, ModBusMsg>>()

        ignoredRequests.forEach {
            if (it is ReadWriteMultiple && it.writeWords == listOf(0, 2, 33, 14, 1, 2)) {
                ignoredBusScans.add(it)
            } else if (it.addr == 0) {
                broadcasts.add(it)
            } else {
                ignoredOther.add(it)
            }
        }

        txes.forEach { (req, resp) ->
            val readWriteMultiple = req as ReadWriteMultiple
            if (readWriteMultiple.readOffset == 0x9cb9 && readWriteMultiple.readLength == 8
                && readWriteMultiple.writeOffset == 0x9c41 && readWriteMultiple.writeLength == 2
                && readWriteMultiple.byteCount == 4
            ) {
                commandRequests.add(req to resp)
            } else if (readWriteMultiple.readOffset == 0x9cb9 && readWriteMultiple.readLength == 5 && readWriteMultiple.writeOffset == 0x9c41 && readWriteMultiple.writeLength == 3 && readWriteMultiple.byteCount == 6) {
                busScan.add(req to resp)
            } else {
                other.add(req to resp)
            }
        }

        println("ignoredOther: ${ignoredOther.size}")
        println("ignoredBusScans: ${ignoredBusScans.size}")
        println("broadcasts: ${broadcasts.size}")

        println("commandRequests: ${commandRequests.size}")
        println("busScan: ${busScan.size}")
        println("other: ${other.size}")

        analizeCommandTxes(commandRequests)
        analizeBroadcasts(broadcasts)
        val broadscasts = LinkedList<HoermannE4Broadcast>()
        broadcasts.forEach {
            val b = parse(it as WriteMultiple)
            if (broadscasts.isEmpty() || broadscasts.last() != b) {
                broadscasts.add(b)
            }
        }
        println()

        if (other.isNotEmpty()) {
            analyzeOthers(other)
        }

        // analyze commandRequests


        // TODO
        // improve parser and parse/record responses correctly
        // record more data to collect more of the seldom messages
        // record data for all scenarios:

        // via bus-device:
        // 1) bus-scan + wait until light off
        // 2) closed: trigger light + wait + trigger light (off)
        // 3) closed: close (light goes on)
        // 4) closed: open -> opening -> opened -> !close -> closing -> closed (light stil on at the end)
        // 5) closed: open -> open while opening (stops) -> open -> open (stops again) -> open -> opened -> close
        // 6) closed: open -> stop (paused) -> stop (closes) -> stop (paused) -> stop (closes)
        // 7) closed: halv open (opens a little, not halv but more than vent) -> halv open again -> closes
        // 8) closed: vent -> (goes into vent-mode) -> vent (closes)

        // via remote:
        // 9) closed: open -> wait -> close
        // 10) closed & light off: toggle light

        // 11) 10min idle

    }

    private fun analyzeOthers(other: List<Pair<ModBusMsg, ModBusMsg>>) {
        other.forEach { (req, resp) ->
            req as ReadWriteMultiple
            resp as ReadWriteMultipleResponse

            check(req.readOffset == 0x9cb9 && req.readLength == 2 && req.writeOffset == 0x9c41 && req.writeLength == 2 && req.byteCount == 4)

            val cnt = req.writeWords[0]
            val cmd = req.writeWords[1]

            check(
                resp.readWords[0] == cnt
                        && resp.readWords[1] == 0
                        && resp.readWords[2] == cmd
                        && resp.readWords[3] == 0xfd
                        && resp.byteCount == 4
            )

        }
        println()
    }

    private fun analizeBroadcasts(broadcasts: List<ModBusMsg>) {
        // 00109D31000912 \d\d0000004060080000000000001000010000 96CB

        val q = LinkedList<Pair<Int, ModBusMsg>>()

        broadcasts.forEachIndexed { index, brdcast ->
            brdcast as WriteMultiple

            /*
             * 0: target pos (0 == closed, c8 == open)
             * 1: current pos (0 == closed, c8 == open)
             * 2: current state (40=closed,01=opening,20=open,02=closing,05=halv opening,80=halv open,09=venting)
             * 3: 60=standard,61=vented
             * 8: motor speed ?
             * 11: 00=light off, motor off; 10=light on, motor off; 14=light & motor ON
             */

            val payloadHex = brdcast.payloadHex()
            val middle = payloadHex.drop(4).dropLast(8)
            val i = listOf(
                "\\w\\w\\w\\w(40|20|02|01|00|05|80|09)(60|61)08000000\\w\\w0000(00)".toRegex(),

//                "000040600800000000000000".toRegex(), // index=0, time=919959   - light off - base line
//                "000040600800000000000010".toRegex(), // index=289, time=940489 - light on
//                "000040600800000000000014".toRegex(), // index=290, time=940560 // opening?
//                "c80001600800000000000014".toRegex(), // index=295, time=940912 // opening with position?
//                "c8\\w\\w0160080000000c000014".toRegex(), // index=295, time=940912 // opening with position?
//                "c8\\w\\w0160080000000a000014".toRegex(), // index=323, time=942903
//                "c8\\w\\w01600800000009000014".toRegex(), // index=339
//                "c8\\w\\w01600800000008000014".toRegex(), // index=354
//                "c8\\w\\w01600800000007000014".toRegex(), // index=370
//                "c8\\w\\w01600800000006000014".toRegex(), // index=385
//                "c8\\w\\w01600800000005000014".toRegex(), // index=399
//                "c8\\w\\w01600800000004000014".toRegex(), // index=413
//                "c8\\w\\w01600800000003000014".toRegex(), // index=427
//                "c8\\w\\w01600800000002000014".toRegex(), // index=442
//                "c8c820600800000000000010".toRegex(), // index=490 // motor stopped, open, light on
//                "c8c820600800000000000014".toRegex(), // index=583 // start closing
//                "00c802600800000000000014".toRegex(), // index=587 //
//                "00\\w\\w02600800000016000014".toRegex(), // index=592
//                "00\\w\\w02600800000015000014".toRegex(), // index=607
//                "00\\w\\w02600800000014000014".toRegex(), //
//                "00\\w\\w02600800000013000014".toRegex(), //
//                "00\\w\\w02600800000012000014".toRegex(), // index=648
//                "00\\w\\w02600800000011000014".toRegex(), // index=661
//                "00\\w\\w02600800000010000014".toRegex(), // index=674
//                "00\\w\\w0260080000000f000014".toRegex(), // index=688
//                "00\\w\\w0260080000000e000014".toRegex(), // index=701
//                "00\\w\\w0260080000000d000014".toRegex(), // index=715
//                "00\\w\\w0260080000000c000014".toRegex(), // index=728
//                "00\\w\\w0260080000000b000014".toRegex(), // index=741
//                "00\\w\\w0260080000000a000014".toRegex(), // index=766
//                "00\\w\\w02600800000009000014".toRegex(), // index=779
//                "00\\w\\w02600800000008000014".toRegex(), // index=792
//                "00\\w\\w02600800000007000014".toRegex(), // index=806
//                "00\\w\\w02600800000006000014".toRegex(), // index=826
//                "00\\w\\w02600800000005000014".toRegex(), // index=847
//                "00\\w\\w02600800000003000014".toRegex(), // index=
//
//                "4e4e00600800000008000014".toRegex(), // scenario 05: stopped? index=
//                "4e4e00600800000007000014".toRegex(), // scenario 05: stopped? index=178
//                "4e4e00600800000000000014".toRegex(), // scenario 05: stopped? index=182
//                "4e4e00600800000000000010".toRegex(), // scenario 05: stopped? index=183
//                "c84e01600800000000000014".toRegex(), // scenario 05: continue open
//                "969600600800000006000014".toRegex(), // scenario 05: stopped again
//                "969600600800000000000014".toRegex(), // scenario 05: stopped again
//                "969600600800000000000010".toRegex(), // scenario 05: stopped again
//                "009602600800000000000014".toRegex(), // scenario 05: close command given
//
//                "c8\\w\\w0160080000000e000014".toRegex(), // scenario 06: opening (different speed)
//                "c8\\w\\w0160080000000b000014".toRegex(), // scenario 06: opening (different speed)
//                "666600600800000008000014".toRegex(), // scenario 06: stopping
//                "666600600800000007000014".toRegex(), // scenario 06: stopping
//                "666600600800000000000014".toRegex(), // scenario 06: stopping
//                "666600600800000000000010".toRegex(), // scenario 06: stopping
            ).indexOfFirst {
                middle.matches(it)
            }

            if (i >= 0) {
                if (q.isNotEmpty() && q.last().first == i) {

                } else {
                    q.add(i to brdcast)
                }
            } else {
                error("")
            }

        }

        println()
    }

    private fun analizeCommandTxes(commandRequests: List<Pair<ModBusMsg, ModBusMsg>>) {

        val q = LinkedList<Pair<Int, Pair<ModBusMsg, ModBusMsg>>>()

        commandRequests.forEachIndexed { index, (req, resp) ->
            val r = req as ReadWriteMultiple
            val response = resp as ReadWriteMultipleResponse

            val isEmptyReq = r.payloadHex().matches("\\w\\w\\w\\w0000".toRegex())

            val knownBytes = listOf(
                "000000", // idle
                "080002", "100002", // light
                "012000", "022000", // close
                "011000", "021000", // open
                "014000", "024000", // toggle
                "010004", "020004", // halv open
                "010040", "020040", // vent
            ).indexOfFirst { b ->
                response.payloadHex().matches("^\\w\\w00\\w\\w01${b}000000000000000000$".toRegex())
            }

            when {
                isEmptyReq && knownBytes >= 0 -> {
                    if (q.isNotEmpty() && q.last().first == knownBytes) {
                        //
                    } else {
                        q.add(knownBytes to (req to resp))
                    }
                }

                else -> {
                    error("")
                }
            }

        }

        println()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun blabla() {
        val parse = parse("021710620003010000000000000000000000004D22", 1)
        println(
            CRC16.crc16("02171001000301000000000000000000000000".hexToByteArray()).toHexString()
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parse(line: String, time: Long): ModBusMsg {
        // 01179CB900059C410003060002210E0102633A
        val bytes = line.windowed(2, 2).map {
            it.hexToByte()
        }
        val addr = bytes[0].toUnsignedInt()
        val func = bytes[1].toUnsignedInt()

        val crc = CRC16.crc16(line.dropLast(4).hexToByteArray())
        return when (func) {
            0x10 -> {
                val byteCount = bytes[6].toUnsignedInt()
                WriteMultiple(
                    timestamp = time,
                    addr = addr,
                    writeOffset = (bytes[2] to bytes[3]).parse16UInt(),
                    writeLength = (bytes[4] to bytes[5]).parse16UInt(),
                    byteCount = byteCount,
                    writeWords = (0..<byteCount).map { i ->
                        bytes[7 + i].toUnsignedInt()
                    },
                    crc = (bytes[7 + byteCount + 1] to bytes[7 + byteCount]).parse16UInt(),
                    hexLine = line,
                )
            }

            0x17 -> {
                var byteCount = bytes[2].toUnsignedInt()
                if (bytes.size == (1 + 1 + 1 + byteCount + 2)) {
                    ReadWriteMultipleResponse(
                        timestamp = time,
                        addr = addr,
                        byteCount = byteCount,
                        readWords = (0..<byteCount).map { i ->
                            bytes[3 + i].toUnsignedInt()
                        },
                        crc = (bytes[3 + byteCount + 1] to bytes[3 + byteCount]).parse16UInt(),
                        hexLine = line,
                    )
                } else {
                    byteCount = bytes[10].toUnsignedInt()
                    ReadWriteMultiple(
                        timestamp = time,
                        addr = addr,
                        readOffset = (bytes[2] to bytes[3]).parse16UInt(),
                        readLength = (bytes[4] to bytes[5]).parse16UInt(),
                        writeOffset = (bytes[6] to bytes[7]).parse16UInt(),
                        writeLength = (bytes[8] to bytes[9]).parse16UInt(),
                        byteCount = byteCount,
                        writeWords = (0..<byteCount).map { i ->
                            bytes[11 + i].toUnsignedInt()
                        },
                        crc = (bytes[11 + byteCount + 1] to bytes[11 + byteCount]).parse16UInt(),
                        hexLine = line,
                    )
                }

            }

            else -> error("unknown func $func")
        }.apply {
            check(this.crc == crc)
        }
    }
}


sealed class ModBusMsg {
    abstract val hexLine: String
    abstract val addr: Int
    abstract val crc: Int
    abstract val timestamp: Long

    fun macthes(pattern: String): Boolean = pattern.replace("*", "\\w").toRegex().matches(hexLine)
}

data class WriteMultiple(
    override val timestamp: Long,
    override val hexLine: String,
    override val addr: Int,
    val writeOffset: Int,
    val writeLength: Int,
    val byteCount: Int,
    val writeWords: List<Int>,
    override val crc: Int,
) : ModBusMsg() {
    @OptIn(ExperimentalStdlibApi::class)
    fun payloadHex() = writeWords.map { it.toByte().toHexString() }.joinToString("")
}

data class ReadWriteMultiple(
    override val timestamp: Long,
    override val hexLine: String,
    override val addr: Int,
    val readOffset: Int,
    val readLength: Int,
    val writeOffset: Int,
    val writeLength: Int,
    val byteCount: Int,
    val writeWords: List<Int>,
    override val crc: Int,
) : ModBusMsg() {
    @OptIn(ExperimentalStdlibApi::class)
    fun payloadHex() = writeWords.map { it.toByte().toHexString() }.joinToString("")
}

data class ReadWriteMultipleResponse(
    override val timestamp: Long,
    override val hexLine: String,
    override val addr: Int,
    val byteCount: Int,
    val readWords: List<Int>,
    override val crc: Int,
) : ModBusMsg() {
    @OptIn(ExperimentalStdlibApi::class)
    fun payloadHex() = readWords.map { it.toByte().toHexString() }.joinToString("")
}




fun parse(msg: WriteMultiple): HoermannE4Broadcast = HoermannE4Broadcast(
    //count = msg.writeWords[0],
    targetPos = msg.writeWords[2],
    currentPos = msg.writeWords[3],
    doorState = SupramatiDoorState.entries.single { it.value == msg.writeWords[4] },
    isVented = msg.writeWords[5] == 61,
    motorSpeed = msg.writeWords[10],
    light = msg.writeWords[13] and 0x10 > 0,
    motorRunning = msg.writeWords[13] and 0x04 > 0,
    0L
)