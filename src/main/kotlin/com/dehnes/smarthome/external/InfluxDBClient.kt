package com.dehnes.smarthome.external

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Request
import org.apache.http.client.fluent.Response
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

class InfluxDBClient(
    private val objectMapper: ObjectMapper,
    host: String = "localhost",
    port: Int = 8086
) {
    private val logger = KotlinLogging.logger { }

    private val dbName = "sensor_data"
    private val baseUrl = "http://$host:$port"

    @Volatile
    private var dbCreated = false


    fun InfluxDBConnector() {
        createDb()
    }

    private fun createDb() {
        if (dbCreated) {
            return
        }
        logger.info("About  to (re-)create DB in influxDb")
        val createDbQuery = "CREATE DATABASE $dbName"
        val retentionPolicy = "alter RETENTION POLICY default ON sensor_data DURATION 260w" // 5 years
        //
        try {
            Request.Get(baseUrl + "/query?q=" + URLEncoder.encode(createDbQuery, StandardCharsets.UTF_8)).execute()
            Request.Get(baseUrl + "/query?q=" + URLEncoder.encode(retentionPolicy, StandardCharsets.UTF_8)).execute()
            dbCreated = true
        } catch (e: IOException) {
            logger.warn("", e)
        }
    }

    fun recordSensorData(
        name: String,
        temp: String?,
        humidity: String?,
        counter: String?,
        light: String?,
        batVolt: String?
    ) {
        val values: MutableList<Pair<String, String>> = LinkedList()
        if (temp != null) {
            values.add("temperature" to temp)
        }
        if (humidity != null) {
            values.add("humidity" to humidity)
        }
        if (counter != null) {
            values.add("counter" to counter)
        }
        if (light != null) {
            values.add("light" to light)
        }
        if (batVolt != null) {
            values.add("battery_volt" to batVolt)
        }
        if (values.size > 0) {
            recordSensorData("sensor", values, "room" to name)
        }
    }

    fun recordSensorData(type: String, fields: List<Pair<String, String>>, vararg tags: Pair<String, String>) {
        try {
            val sb = StringBuilder()
            sb.append(type)
            tags.toList().forEach { tag ->
                sb.append(",").append(tag.first).append("=").append(tag.second)
            }
            sb.append(" ").append(fields.joinToString(",") { v -> v.first + "=" + v.second })
            logger.debug("About to send {}", sb)
            val result: Response = Request.Post("$baseUrl/write?db=$dbName")
                .bodyString(sb.toString(), ContentType.TEXT_PLAIN)
                .execute()
            val httpResponse: HttpResponse? = result.returnResponse()
            if (httpResponse?.statusLine != null && httpResponse.statusLine.statusCode > 299) {
                throw RuntimeException("Could not write to InFluxDb$httpResponse")
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
        b.addParameter("db", dbName)
        b.addParameter("q", query)
        val execute: Response = Request.Get(b.build()).execute()
        return objectMapper.readValue(execute.returnContent().asString())
    }

    fun avgTempDuringLastHour(room: String): Map<String, Any> {
        val b = URIBuilder("$baseUrl/query")
        b.addParameter("db", dbName)
        b.addParameter(
            "q",
            "SELECT mean(temperature) from sensor where room = '" + room + "' and time > " + (System.currentTimeMillis() - 3600 * 1000) / 1000 + "s"
        )
        val execute: Response = Request.Get(b.build()).execute()
        return objectMapper.readValue(execute.returnContent().asString())
    }
}