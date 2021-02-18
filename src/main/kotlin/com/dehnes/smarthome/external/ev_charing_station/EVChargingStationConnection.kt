package com.dehnes.smarthome.external

import com.dehnes.smarthome.api.dtos.EvChargingStationClient
import com.dehnes.smarthome.api.dtos.Event
import com.dehnes.smarthome.api.dtos.EventType
import mu.KotlinLogging
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.*
import java.util.zip.CRC32

class EVChargingStationConnection(
    private val port: Int,
    private val executorService: ExecutorService
) {
    private val logger = KotlinLogging.logger { }
    private var serverSocket: ServerSocket? = null
    private val connectedClientsById = ConcurrentHashMap<Int, ClientConext>()
    private val timer = Executors.newSingleThreadScheduledExecutor()

    val listeners = ConcurrentHashMap<String, (Event) -> Unit>()

    fun start() {
        serverSocket = ServerSocket(port)

        val thread = Thread {
            while (true) {
                try {
                    while (true) {
                        val clientSocket = serverSocket!!.accept()
                        logger.info { "New connection from ${clientSocket.toAddressString()}" }
                        clientSocket.soTimeout = 2000
                        onNewClient(clientSocket)
                    }
                } catch (t: Throwable) {
                    logger.error("", t)
                }
                Thread.sleep(5000)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    fun getConnectedClients() = connectedClientsById.values.map { it.evChargingStationClient }

    fun ping(clientId: Int) = doWithClient(clientId) { socket ->
        send(socket, Ping())
        readResponse(socket) is PongResponse
    }

    fun uploadFirmwareAndReboot(clientId: Int, firmware: ByteArray) {
        doWithClient(clientId) { socket ->
            send(socket, Firmware(firmware))
        }
    }

    private fun readResponse(socket: Socket): InboundPacket {
        // type
        var read = socket.getInputStream().read()
        check(read >= 0) { "Client disconnected" }
        val responseType = ResponseType.fromValue(read)

        // len- 4 bytes
        val allocate = ByteBuffer.allocate(4)
        while (allocate.hasRemaining()) {
            read = socket.getInputStream().read()
            check(read >= 0) { "Client disconnected" }
            allocate.put(read.toByte())
        }
        val len = allocate.getInt(0)

        // msg
        val msg = ByteArray(len)
        var bytesRead = 0
        while (bytesRead < msg.size) {
            read = socket.getInputStream().read(msg, bytesRead, msg.size - bytesRead)
            check(read >= 0) { "Client disconnected" }
            bytesRead += read
        }

        return responseType.parser(msg)
    }

    private fun send(socket: Socket, outboundPacket: OutboundPacket) {
        val message = outboundPacket.serializedMessage()

        // type - 1 byte
        socket.getOutputStream().write(outboundPacket.type.value)

        // len - 4 byte
        socket.getOutputStream().write(
            ByteBuffer.allocate(4).putInt(message.size).array()
        )

        // msg
        socket.getOutputStream().write(message)
    }

    private fun <T> doWithClient(clientId: Int, fn: (socket: Socket) -> T) =
        connectedClientsById[clientId]?.let { clientContext ->
            synchronized(clientContext) {
                try {
                    fn(clientContext.socket)
                } catch (t: Throwable) {
                    logger.info(
                        "Got error against client=$clientId inet=${clientContext.socket.toAddressString()} - closing socket",
                        t
                    )
                    closeContext(clientContext)
                    connectedClientsById.remove(clientId)
                    null
                }
            }
        }

    private fun closeContext(clientContext: ClientConext) {
        clientContext.scheduledFuture.cancel(false)
        closeSocket(clientContext.socket)

        executorService.submit {
            listeners.values.forEach { fn ->
                try {
                    fn(Event(EventType.clientConnectionsChanged, getConnectedClients()))
                } catch (e: Exception) {
                    logger.error("", e)
                }
            }
        }
    }

    private fun closeSocket(socket: Socket) {
        try {
            socket.getInputStream().close()
        } catch (i: Exception) {
        }
        try {
            socket.getOutputStream().close()
        } catch (i: Exception) {
        }
        try {
            socket.close()
        } catch (i: Exception) {
        }
    }

    private fun onNewClient(socket: Socket) {
        try {

            val clientId = socket.getInputStream().read()
            if (clientId < 0) {
                closeSocket(socket)
                logger.info { "Timeout while reading client ID. Closing socket" }
                return
            }

            val firmwareVersion = socket.getInputStream().read()
            if (firmwareVersion < 0) {
                closeSocket(socket)
                logger.info { "Timeout while reading firmwareVersion. Closing socket" }
                return
            }

            val evChargingStationClient = EvChargingStationClient(
                clientId,
                socket.inetAddress.toString(),
                socket.port,
                firmwareVersion
            )
            logger.info { "New client connected $evChargingStationClient" }

            // start timer
            val timerRef = timer.scheduleAtFixedRate(
                {
                    executorService.submit {
                        ping(clientId)
                    }
                },
                5,
                5,
                TimeUnit.SECONDS
            )

            connectedClientsById[clientId] = ClientConext(socket, timerRef, evChargingStationClient)

            executorService.submit {
                listeners.values.forEach { fn ->
                    try {
                        fn(Event(EventType.clientConnectionsChanged, getConnectedClients()))
                    } catch (e: Exception) {
                        logger.error("", e)
                    }
                }
            }

        } catch (t: Throwable) {
            logger.info("", t)
        }
    }

}

fun Socket.toAddressString() = "${this.inetAddress}:${this.port}"

class ClientConext(
    val socket: Socket,
    val scheduledFuture: ScheduledFuture<*>,
    val evChargingStationClient: EvChargingStationClient
)

enum class RequestType(val value: Int) {
    pingRequest(1),
    firmwareUpdate(2)
}

enum class ResponseType(val value: Int, val parser: (ByteArray) -> InboundPacket) {
    pongResponse(1, PongResponse::parse);

    companion object {
        fun fromValue(value: Int) = values().first { it.value == value }
    }
}

sealed class InboundPacket(
    val type: ResponseType
)

class PongResponse(
) : InboundPacket(ResponseType.pongResponse) {
    companion object {
        fun parse(msg: ByteArray) = PongResponse()
    }
}

sealed class OutboundPacket(
    val type: RequestType
) {
    abstract fun serializedMessage(): ByteArray
}

class Ping : OutboundPacket(RequestType.pingRequest) {
    override fun serializedMessage() = byteArrayOf()
}

class Firmware(
    val firmware: ByteArray
) : OutboundPacket(RequestType.firmwareUpdate) {
    override fun serializedMessage(): ByteArray {
        val crc32 = CRC32()
        crc32.update(firmware)
        val crc32Value = crc32.value

        return ByteBuffer.allocate(firmware.size + 4)
            .put(crc32Value.to32Bit())
            .put(firmware)
            .array()
    }
}

fun Long.to32Bit(): ByteArray {
    val bytes = ByteArray(8)
    ByteBuffer.wrap(bytes).putLong(this)
    return bytes.copyOfRange(4, 8)
}
