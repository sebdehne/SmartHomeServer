package com.dehnes.smarthome

import com.dehnes.smarthome.api.ApiRequest
import com.dehnes.smarthome.api.ApiResponse
import com.dehnes.smarthome.api.GarageStatus
import com.dehnes.smarthome.api.Notify
import com.dehnes.smarthome.api.RequestType.*
import com.dehnes.smarthome.service.GarageDoorService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer
import java.io.Closeable
import javax.websocket.*
import javax.websocket.server.ServerEndpoint

val configuration = Configuration()

fun main(args: Array<String>) {
    val logger = KotlinLogging.logger { }

    System.setProperty("DST_HOST", "192.168.1.1")
    configuration.init()

    val server = Server()
    val connector = ServerConnector(server)
    connector.port = 8080
    server.addConnector(connector)

    // Setup the basic application "context" for this application at "/"
    // This is also known as the handler tree (in jetty speak)
    // Setup the basic application "context" for this application at "/"
    // This is also known as the handler tree (in jetty speak)
    val context = ServletContextHandler(ServletContextHandler.SESSIONS)
    context.contextPath = "/"
    server.handler = context

    try {
        val wscontainer = WebSocketServerContainerInitializer.configureContext(context)
        wscontainer.addEndpoint(WebSocketServer::class.java)
        server.start()
        server.join()
    } catch (t: Throwable) {
        logger.error("", t)
    }
}

// one instance per sessions
@ServerEndpoint(value = "/api")
class WebSocketServer {

    private val objectMapper = configuration.getBean<ObjectMapper>(ObjectMapper::class)
    private val logger = KotlinLogging.logger { }
    private val garageDoorService = configuration.getBean<GarageDoorService>(GarageDoorService::class)
    private val subscriptions = mutableMapOf<String, Subscription<*>>()

    @OnOpen
    fun onWebSocketConnect(sess: Session) {
        logger.info("Socket connected: $sess")
    }

    @OnMessage
    fun onWebSocketText(argSession: Session, argMessage: String) {
        val apiRequest: ApiRequest = objectMapper.readValue(argMessage)

        val response: Any? = when (apiRequest.type) {
            getGarageStatus -> garageDoorService.getCurrentState()
            subscribeGarageStatus -> {
                val subscriptionId = apiRequest.subscriptionId!!

                val sub = object : Subscription<GarageStatus>(subscriptionId, argSession) {
                    override fun onEvent(e: GarageStatus) {
                        argSession.basicRemote.sendText(objectMapper.writeValueAsString(Notify(subscriptionId, e)))
                    }

                    override fun close() {
                        garageDoorService.listeners.remove(subscriptionId)
                        subscriptions.remove(subscriptionId)
                    }
                }
                garageDoorService.listeners[subscriptionId] = sub::onEvent
                subscriptions.put(subscriptionId, sub)?.close()
                Notify(subscriptionId, garageDoorService.getCurrentState())
                logger.info { "New subscribeGarageStatus id=$subscriptionId" }
            }
            unsubscribeGarageStatus -> {
                val subscriptionId = apiRequest.subscriptionId!!
                subscriptions[subscriptionId]!!.close()
                Notify(subscriptionId, garageDoorService.getCurrentState())
                logger.info { "Removed subscribeGarageStatus id=$subscriptionId" }
            }
        }

        argSession.basicRemote.sendText(objectMapper.writeValueAsString(ApiResponse(response)))
    }

    @OnClose
    fun onWebSocketClose(reason: CloseReason) {
        subscriptions.forEach { (_, u) ->
            u.close()
        }
        logger.info("Socket Closed: $reason")
    }

    @OnError
    fun onWebSocketError(cause: Throwable) {
        logger.warn("", cause)
    }
}

abstract class Subscription<E>(
    val id: String,
    val sess: Session
) : Closeable {

    abstract fun onEvent(e: E)

}
