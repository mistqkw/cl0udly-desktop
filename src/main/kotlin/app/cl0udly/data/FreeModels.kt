package app.cl0udly.data

import app.cl0udly.Graph
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Free models without an account, as on the phone. OpenCode Zen's free tier is capped per IP
 * ("Free usage exceeded"), so when it hits the cap the chat moves to Pollinations (GPT-OSS 20B,
 * no key). The text then goes to a third-party service; the toast says so.
 */
object FreeModels {
    const val POLLINATIONS_ID = "pollinations"
    const val POLLINATIONS_DEFAULT = "openai-fast"

    /** Applied through the server: it writes its global config and picks it up at once. */
    suspend fun enablePollinations() {
        val patch = buildJsonObject {
            putJsonArray("disabled_providers") { }
            putJsonObject("provider") {
                putJsonObject(POLLINATIONS_ID) {
                    put("name", "Pollinations (no account)")
                    put("npm", "@ai-sdk/openai-compatible")
                    putJsonObject("options") { put("baseURL", "https://text.pollinations.ai/openai"); put("apiKey", "anonymous") }
                    putJsonObject("models") {
                        putJsonObject(POLLINATIONS_DEFAULT) {
                            put("name", "GPT-OSS 20B"); put("tool_call", true); put("reasoning", true); put("attachment", false)
                            putJsonObject("limit") { put("context", 131072); put("output", 8192) }
                        }
                    }
                }
            }
        }
        Graph.api.patchGlobalConfig(patch)
        runCatching { Graph.api.disposeAll() }
        Graph.prefs.providerId = POLLINATIONS_ID; Graph.prefs.modelId = POLLINATIONS_DEFAULT; Graph.prefs.variant = ""
        Graph.hub.loadModels()
    }

}
