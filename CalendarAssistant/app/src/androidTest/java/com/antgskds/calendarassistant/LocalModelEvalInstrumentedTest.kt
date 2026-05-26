package com.antgskds.calendarassistant

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antgskds.calendarassistant.aiengine.AiEngineException
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.provider.LocalSemanticProvider
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalModelEvalInstrumentedTest {
    @Test
    fun runEngineSmoke() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as App
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            instrumentation.startActivitySync(intent)
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
        }
        val selectedModelId = app.settingsQueryApi.settings.value.selectedLocalModelId
        val model = app.localModelManager.getModel(selectedModelId)
            ?: app.localModelManager.models.value.firstOrNull()
            ?: error("no local model imported")
        val prompt = InstrumentationRegistry.getArguments().getString("prompt")
            ?: "只输出 JSON：{\"events\":[]}"
        val output = File(context.filesDir, SMOKE_RESULT_FILE)
        val started = System.currentTimeMillis()
        val record = try {
            val result = kotlinx.coroutines.runBlocking {
                app.liteRtAiEngineClient.generate(
                    model = model,
                    prompt = prompt,
                    enableVision = false,
                    maxTokens = 256
                )
            }
            JSONObject()
                .put("status", "SUCCESS")
                .put("elapsedMillis", System.currentTimeMillis() - started)
                .put("loadElapsedMillis", result.loadElapsedMillis)
                .put("inferenceElapsedMillis", result.inferenceElapsedMillis)
                .put("totalElapsedMillis", result.totalElapsedMillis)
                .put("text", result.text)
        } catch (e: Throwable) {
            JSONObject()
                .put("status", (e as? AiEngineException)?.status?.name ?: "EXCEPTION")
                .put("elapsedMillis", System.currentTimeMillis() - started)
                .put("message", e.message.orEmpty())
                .put("stack", android.util.Log.getStackTraceString(e))
        }
        output.writeText(record.toString() + "\n")
    }

    @Test
    fun runEngineSmokeTwice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as App
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            instrumentation.startActivitySync(intent)
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
        }
        val selectedModelId = app.settingsQueryApi.settings.value.selectedLocalModelId
        val model = app.localModelManager.getModel(selectedModelId)
            ?: app.localModelManager.models.value.firstOrNull()
            ?: error("no local model imported")
        val output = File(context.filesDir, SMOKE_TWICE_RESULT_FILE)
        output.writeText("")
        repeat(2) { index ->
            val started = System.currentTimeMillis()
            val record = try {
                val result = kotlinx.coroutines.runBlocking {
                    app.liteRtAiEngineClient.generate(
                        model = model,
                        prompt = "只输出 JSON：{\"events\":[]}",
                        enableVision = false,
                        maxTokens = 256
                    )
                }
                JSONObject()
                    .put("index", index)
                    .put("status", "SUCCESS")
                    .put("elapsedMillis", System.currentTimeMillis() - started)
                    .put("loadElapsedMillis", result.loadElapsedMillis)
                    .put("inferenceElapsedMillis", result.inferenceElapsedMillis)
                    .put("totalElapsedMillis", result.totalElapsedMillis)
                    .put("text", result.text)
            } catch (e: Throwable) {
                JSONObject()
                    .put("index", index)
                    .put("status", (e as? AiEngineException)?.status?.name ?: "EXCEPTION")
                    .put("elapsedMillis", System.currentTimeMillis() - started)
                    .put("message", e.message.orEmpty())
                    .put("stack", android.util.Log.getStackTraceString(e))
            }
            output.appendText(record.toString() + "\n")
            if (index == 0) Thread.sleep(5000)
        }
    }

    @Test
    fun runTextEvalFromExternalFile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as App
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            instrumentation.startActivitySync(intent)
            instrumentation.waitForIdleSync()
        }

        val dataset = File(context.getExternalFilesDir(null), DATASET_FILE)
        require(dataset.exists()) { "dataset not found: ${dataset.absolutePath}" }

        val selectedModelId = app.settingsQueryApi.settings.value.selectedLocalModelId
        val model = app.localModelManager.getModel(selectedModelId)
            ?: app.localModelManager.models.value.firstOrNull()
            ?: error("no local model imported")
        val settings = app.settingsQueryApi.settings.value.copy(
            isLocalSemanticEnabled = true,
            selectedLocalModelId = model.id,
            useMultimodalAi = false,
            defaultEventDurationMinutes = 120
        )

        val results = File(context.filesDir, RESULT_FILE)
        results.writeText("")
        val targetId = InstrumentationRegistry.getArguments().getString("caseId").orEmpty()
        val maxCases = InstrumentationRegistry.getArguments().getString("maxCases")?.toIntOrNull() ?: 1
        val cases = dataset.readLines()
            .filter { it.isNotBlank() }
            .filter { line -> targetId.isBlank() || JSONObject(line).getString("id") == targetId }
            .take(maxCases)
        require(cases.isNotEmpty()) { "no eval case matched caseId=$targetId" }

        cases.forEachIndexed { index, line ->
            val case = JSONObject(line)
            val id = case.getString("id")
            val messages = case.getJSONArray("messages")
            var userText = ""
            var expectedJson = ""
            for (i in 0 until messages.length()) {
                val message = messages.getJSONObject(i)
                when (message.getString("role")) {
                    "user" -> userText = message.getString("content")
                    "assistant" -> expectedJson = message.getString("content")
                }
            }

            val started = System.currentTimeMillis()
            val record = try {
                when (val result = kotlinx.coroutines.runBlocking {
                    LocalSemanticProvider.parseUserText(userText, settings, context)
                }) {
                    is AnalysisResult.Success -> buildRecord(
                        id = id,
                        index = index,
                        status = "SUCCESS",
                        elapsedMillis = System.currentTimeMillis() - started,
                        expectedJson = expectedJson,
                        actual = listOf(result.data),
                        error = ""
                    )
                    is AnalysisResult.Empty -> buildRecord(
                        id = id,
                        index = index,
                        status = result.message,
                        elapsedMillis = System.currentTimeMillis() - started,
                        expectedJson = expectedJson,
                        actual = emptyList(),
                        error = ""
                    )
                    is AnalysisResult.Failure -> buildRecord(
                        id = id,
                        index = index,
                        status = result.failure.detail,
                        elapsedMillis = System.currentTimeMillis() - started,
                        expectedJson = expectedJson,
                        actual = emptyList(),
                        error = result.failure.fullMessage()
                    )
                }
            } catch (e: Throwable) {
                JSONObject()
                    .put("id", id)
                    .put("index", index)
                    .put("status", "EXCEPTION")
                    .put("elapsedMillis", System.currentTimeMillis() - started)
                    .put("error", e.message.orEmpty())
                    .put("stack", android.util.Log.getStackTraceString(e))
            }
            results.appendText(record.toString() + "\n")
        }
    }

    private fun buildRecord(
        id: String,
        index: Int,
        status: String,
        elapsedMillis: Long,
        expectedJson: String,
        actual: List<com.antgskds.calendarassistant.core.model.RecognitionDraft>,
        error: String
    ): JSONObject {
        val expected = JSONObject(expectedJson).getJSONArray("events")
        val expectedTags = mutableListOf<String>()
        for (i in 0 until expected.length()) {
            expectedTags += expected.getJSONObject(i).optString("tag")
        }
        val actualTags = actual.map { it.tag }
        return JSONObject()
            .put("id", id)
            .put("index", index)
            .put("status", status)
            .put("elapsedMillis", elapsedMillis)
            .put("expectedCount", expected.length())
            .put("actualCount", actual.size)
            .put("expectedTags", expectedTags.joinToString(","))
            .put("actualTags", actualTags.joinToString(","))
            .put("countMatch", expected.length() == actual.size)
            .put("tagMatch", expectedTags.sorted() == actualTags.sorted())
            .put("titles", actual.joinToString(" | ") { it.title })
            .put("error", error)
    }

    companion object {
        private const val DATASET_FILE = "local_model_eval.jsonl"
        private const val RESULT_FILE = "local-model-eval-results.jsonl"
        private const val SMOKE_RESULT_FILE = "local-model-engine-smoke.jsonl"
        private const val SMOKE_TWICE_RESULT_FILE = "local-model-engine-smoke-twice.jsonl"
    }
}
