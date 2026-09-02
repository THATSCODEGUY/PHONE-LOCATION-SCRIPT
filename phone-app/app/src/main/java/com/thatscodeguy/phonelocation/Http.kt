package com.thatscodeguy.phonelocation

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Http {

    class Resp(val code: Int, val body: String?) {
        val ok: Boolean get() = code in 200..299
    }

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15000): Resp =
        run("GET", url, null, headers, timeoutMs)

    fun post(url: String, json: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20000): Resp =
        run("POST", url, json, headers, timeoutMs)

    private fun run(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Int,
    ): Resp {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
            Resp(code, stream?.bufferedReader()?.use { it.readText() })
        } catch (e: Exception) {
            Resp(-1, e.message ?: e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }
}
