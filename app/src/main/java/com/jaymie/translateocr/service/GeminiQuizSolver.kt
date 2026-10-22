package com.jaymie.translateocr.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiQuizSolver(private val apiKey: String) {

    suspend fun solveQuizQuestion(ocrText: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Error: Gemini API Key missing!"

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val systemPrompt = """
            You are a real-time quiz and survey solver. 
            Read the extracted screen text below, identify the question and choices, and give a direct answer in this format:
            👉 ANSWER: [Exact correct choice]
            💡 REASON: [One simple sentence explaining why]
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", "$systemPrompt\n\nScreen Text:\n$ocrText"))
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        return@withContext try {
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }
            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(responseStr)
                responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            } else {
                "API Error: HTTP ${conn.responseCode}"
            }
        } catch (e: Exception) {
            "Connection Error: ${e.localizedMessage}"
        } finally {
            conn.disconnect()
        }
    }
}
