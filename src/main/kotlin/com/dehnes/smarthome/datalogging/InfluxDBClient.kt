package com.dehnes.smarthome.datalogging

import com.dehnes.smarthome.config.ConfigService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Request
import org.apache.http.client.fluent.Response
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

data class InfluxDBRecord(
    val timestamp: Instant,
    val type: String,
    val fields: Map<String, Any>,
    val tags: Map<String, String>
) {
    fun toLine(): String {
        val tags = this.tags.entries.joinToString(separator = ",") { "${it.key}=${it.value}" }.let {
            if (it.isNotBlank()) {
                ",$it"
            } else {
                it
            }
        }
        val fields = this.fields.entries.joinToString(separator = ",") { "${it.key}=${it.value}" }
        return "$type$tags $fields ${TimeUnit.NANOSECONDS.convert(Duration.ofMillis(timestamp.toEpochMilli()))}"
    }
}

class InfluxDBClient(
    private val configService: ConfigService,
    host: String = "192.168.1.1",
    port: Int = 8086
) {
    private val logger = KotlinLogging.logger { }

    private val bucketName = "sensor_data"
    private val baseUrl = "http://$host:$port"

    fun recordSensorData(vararg record: InfluxDBRecord) {
        recordSensorData(record.toList())
    }

    fun recordSensorData(records: List<InfluxDBRecord>) {
        if (configService.isDevMode()) {
            logger.warn { "Not writing to influxDb because of devMode=true" }
            return
        }
        try {
            val body = records.joinToString(separator = "\n") { it.toLine() }
            logger.debug("About to send {}", body)
            val result: Response = Request.Post("$baseUrl/api/v2/write?bucket=$bucketName&org=dehnes.com")
                .addHeader("Authorization", "Token " + configService.getInfluxDbAuthToken())
                .bodyString(body, ContentType.TEXT_PLAIN)
                .execute()
            val httpResponse: HttpResponse? = result.returnResponse()
            if (httpResponse?.statusLine != null && httpResponse.statusLine.statusCode > 299) {
                throw RuntimeException(
                    "Could not write to InFluxDb $httpResponse ${
                        httpResponse.entity.content.readAllBytes().toString(
                            Charset.defaultCharset()
                        )
                    }"
                )
            }

        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun query(
        query: String
    ): List<InfluxDbQueryRecord> {
        val execute: Response = Request
            .Post(
                URIBuilder("$baseUrl/api/v2/query")
                    .addParameter("orgID", "b351a5d22d7de2aa")
                    .build()
            )
            .addHeader("Authorization", "Token " + configService.getInfluxDbAuthToken())
            .body(StringEntity(query, ContentType.create("application/vnd.flux", StandardCharsets.UTF_8)))
            .execute()

        val responseBody = execute.returnContent().asString()

        val lines = responseBody.split("\r\n")
            .map { it.trim() }
            .filterNot { it.isBlank() }
            .filterNot { it.startsWith("#") }

        val headerLine = lines.firstOrNull() ?: return emptyList()
        val dataLines = lines.subList(1, lines.size)

        val headerFields = headerLine.split(",")

        val data = dataLines.map { line ->
            val dataFields = line.split(",")
            dataFields.mapIndexed { index, s ->
                headerFields[index] to s
            }.toMap().let { d ->
                InfluxDbQueryRecord(
                    time = d["_time"]?.let { Instant.parse(it) },
                    value = d["_value"]!!.toDouble(),
                    field = d["_field"]!!,
                    measurement = d["_measurement"]!!,
                    tags = d.entries
                        .filterNot { it.key.startsWith("_") }
                        .filterNot { it.key == "" }
                        .filterNot { it.key == "result" }
                        .filterNot { it.key == "table" }
                        .associate { it.key to it.value })
            }
        }.sortedBy { it.time }

        return data
    }

}

data class InfluxDbQueryRecord(
    val time: Instant?,
    val value: Double,
    val field: String,
    val measurement: String,
    val tags: Map<String, String>
)