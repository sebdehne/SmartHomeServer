package com.dehnes.smarthome

import com.dehnes.smarthome.api.WebSocketServer
import com.dehnes.smarthome.utils.StaticFilesServlet
import mu.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer

val configuration = Configuration()

fun main() {
    val logger = KotlinLogging.logger { }

    System.setProperty("DST_HOST", "192.168.1.1")
    configuration.init()

    val server = Server()
    val connector = ServerConnector(server)
    connector.port = 9090
    server.addConnector(connector)

    val context = ServletContextHandler(ServletContextHandler.SESSIONS)
    context.contextPath = "/"
    server.handler = context

    context.addServlet(ServletHolder(StaticFilesServlet()), "/*")

    try {
        val wscontainer = WebSocketServerContainerInitializer.configureContext(context)
        wscontainer.addEndpoint(WebSocketServer::class.java)
        server.start()
        server.join()
    } catch (t: Throwable) {
        logger.error("", t)
    }
}

