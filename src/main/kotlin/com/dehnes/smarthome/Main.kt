package com.dehnes.smarthome

import com.dehnes.smarthome.api.WebSocketServer
import com.dehnes.smarthome.utils.StaticFilesServlet
import com.dehnes.smarthome.utils.VideoDownloader
import com.dehnes.smarthome.utils.WebRTCServlet
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
    context.contextPath = System.getProperty("CONTEXTPATH", "/").apply {
        logger.info { "Using contextPath=$this" }
    }
    server.handler = context

    context.addServlet(ServletHolder(WebRTCServlet()), "/webrtc/*")
    context.addServlet(ServletHolder(VideoDownloader()), "/video/*")
    context.addServlet(ServletHolder(StaticFilesServlet()), "/*")

    try {
        WebSocketServerContainerInitializer.configure(
            context
        ) { servletContext, serverContainer ->
            serverContainer.addEndpoint(WebSocketServer::class.java)
            serverContainer.policy.idleTimeout = 3600 * 1000
        }
        server.start()
        server.join()
    } catch (t: Throwable) {
        logger.error("", t)
    }
}

