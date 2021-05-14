package com.dehnes.smarthome.datalogging

import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Request
import org.apache.http.client.fluent.Response
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class InfluxDBClient(
    private val persistenceService: PersistenceService,
    private val objectMapper: ObjectMapper,
    host: String = "192.168.1.1",
    port: Int = 8086
) {
    private val logger = KotlinLogging.logger { }

    private val bucketName = "sensor_data"
    private val baseUrl = "http://$host:$port"

    fun recordSensorData(type: String, fields: List<Pair<String, Any>>, vararg tags: Pair<String, String>) {
        try {
            val sb = StringBuilder()
            sb.append(type)
            tags.toList().forEach { tag ->
                sb.append(",").append(tag.first).append("=").append(tag.second)
            }
            sb.append(" ").append(fields.joinToString(",") { v -> v.first + "=" + v.second })
            logger.debug("About to send {}", sb)
            val result: Response = Request.Post("$baseUrl/api/v2/write?bucket=$bucketName&org=dehnes.com")
                .addHeader("Authorization", "Token " + persistenceService["influxDbAuthToken", null]!!)
                .bodyString(sb.toString(), ContentType.TEXT_PLAIN)
                .execute()
            val httpResponse: HttpResponse? = result.returnResponse()
            if (httpResponse?.statusLine != null && httpResponse.statusLine.statusCode > 299) {
                throw RuntimeException("Could not write to InFluxDb $httpResponse ${httpResponse.entity.content.readAllBytes().toString(
                    Charset.defaultCharset())}")
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun queryRaw(
        type: String,
        targetTag: Pair<String, String>?,
        timeMin: Optional<Long>,
        timeMax: Optional<Long>,
        limit: Int
    ): Map<String, Any> {
        var query = "SELECT * FROM $type WHERE 1 = 1"
        if (targetTag != null) {
            query += " AND " + targetTag.first + "=" + targetTag.second
        }
        if (timeMin.isPresent) {
            query += " AND time > " + timeMin.get() / 1000 + "s"
        }
        if (timeMax.isPresent) {
            query += " AND time < " + timeMax.get() / 1000 + "s"
        }
        query += " order by time desc LIMIT $limit"

        val b = URIBuilder("$baseUrl/query")
        b.addParameter("db", bucketName)
        b.addParameter("q", query)
        val execute: Response = Request.Get(b.build()).execute()
        return objectMapper.readValue(execute.returnContent().asString())
    }

    fun avgTempDuringLastHour(room: String): Map<String, Any> {
        val b = URIBuilder("$baseUrl/query")
        b.addParameter("db", bucketName)
        b.addParameter(
            "q",
            "SELECT mean(temperature) from sensor where room = '" + room + "' and time > " + (System.currentTimeMillis() - 3600 * 1000) / 1000 + "s"
        )
        val execute: Response = Request.Get(b.build()).execute()
        return objectMapper.readValue(execute.returnContent().asString())
    }
}