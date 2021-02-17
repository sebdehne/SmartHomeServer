package com.dehnes.smarthome.external

import mu.KotlinLogging
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.*

class EVChargingStationConnection(
    private val port: Int,
    private val executorService: ExecutorService
) {
    private val logger = KotlinLogging.logger { }
    private var serverSocket: ServerSocket? = null
    private val connectedClientsById = ConcurrentHashMap<Int, ClientConext>()
    private val timer = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        serverSocket = ServerSocket(port)

        val thread = Thread {
            while (true) {
                try {
                    while (true) {
                        val clientSocket = serverSocket!!.accept()
                        logger.info { "New connection from ${clientSocket.toAddressString()}" }
                        clientSocket.soTimeout = 1000
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

    fun getFirmwareVersion(clientId: Int) = doWithClient(clientId) { socket ->
        send(socket, Ping())

        val pongResponse = readResponse(socket) as PongResponse
        pongResponse.firmwareVersion
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
            logger.info { "ClientId=$clientId for ${socket.toAddressString()}" }

            // start timer
            val timerRef = timer.scheduleAtFixedRate(
                {
                    executorService.submit {
                        getFirmwareVersion(clientId)
                    }
                },
                5,
                5,
                TimeUnit.SECONDS
            )

            connectedClientsById[clientId] = ClientConext(socket, timerRef)
        } catch (t: Throwable) {
            logger.info("", t)
        }
    }

}

fun Socket.toAddressString() = "${this.inetAddress}:${this.port}"

class ClientConext(
    val socket: Socket,
    val scheduledFuture: ScheduledFuture<*>
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
    val firmwareVersion: Int
) : InboundPacket(ResponseType.pongResponse) {
    companion object {
        fun parse(msg: ByteArray) = PongResponse(msg[0].toInt())
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

class Firmware : OutboundPacket(RequestType.firmwareUpdate) {
    override fun serializedMessage() = TODO()
}
