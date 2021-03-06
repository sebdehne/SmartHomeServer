package com.dehnes.smarthome.utils

import mu.KotlinLogging
import org.apache.http.client.fluent.Request
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class WebRTCServlet : HttpServlet() {
    val webrtcServer = "http://192.168.1.1:8083"
    val logger = KotlinLogging.logger { }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        logger.info { "POST pathInfo=${req.pathInfo} status=${resp.status}" }

        // forward signalling negotiation to https://github.com/deepch/RTSPtoWebRTC server

        val pathParts = req.pathInfo.split("/")
        val readAllBytes = req.inputStream.readAllBytes()

        Request.Post("$webrtcServer/stream/receiver/${pathParts.last()}")
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .bodyByteArray(readAllBytes).execute()
            .handleResponse { clientResponse ->
                resp.status = clientResponse.statusLine.statusCode
                clientResponse.entity.writeTo(resp.outputStream)
                resp.outputStream.flush()
            }

    }
}

