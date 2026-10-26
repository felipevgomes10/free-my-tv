package com.freemytv.home.webremote

import org.json.JSONArray
import org.json.JSONObject

/** An action the Home can't carry out; the page hears `failed` with this reason. */
class Refused(val reason: String) : Exception(reason)

/**
 * What the FMTV Controller's companion features ask the Home (free-my-tv-remote-controller's
 * ADR 0006): each feature registers its lists, the field of `open` it answers and any request of
 * its own. The bridge answers every request by its `id`, and only from connections that are ready.
 */
class CompanionFeatures {

    private val lists = mutableMapOf<String, suspend () -> JSONArray>()
    private val openers = mutableListOf<Pair<String, suspend (JSONObject) -> Unit>>()
    private val answers = mutableMapOf<String, suspend (JSONObject) -> JSONObject>()

    /** A list the page asks for by `of`, and which the Home pushes again when it changes. */
    fun list(of: String, items: suspend () -> JSONArray) {
        lists[of] = items
    }

    /** What `open` does when its message has this field (`package`, `input`, `shortcut`…); throw [Refused] to fail. */
    fun open(field: String, action: suspend (JSONObject) -> Unit) {
        openers += field to action
    }

    /** A request of a feature's own (`close-app`, `search`, `title`…), answered with this message and the request's `id`. */
    fun answer(type: String, reply: suspend (JSONObject) -> JSONObject) {
        answers[type] = reply
    }

    suspend fun items(of: String): JSONArray? = lists[of]?.invoke()

    /** The answer to one request, without its `id`. */
    internal suspend fun reply(request: JSONObject): JSONObject = try {
        when (val type = request.optString("type")) {
            "list" -> {
                val of = request.optString("of")
                val items = items(of) ?: throw Refused("unknown-list")
                JSONObject().put("type", "list").put("of", of).put("items", items)
            }
            "open" -> {
                val (_, action) = openers.firstOrNull { (field, _) -> request.has(field) } ?: throw Refused("unknown-target")
                action(request)
                JSONObject().put("type", "done")
            }
            else -> (answers[type] ?: throw Refused("unknown-request"))(request)
        }
    } catch (refused: Refused) {
        JSONObject().put("type", "failed").put("reason", refused.reason)
    } catch (_: Exception) {
        JSONObject().put("type", "failed").put("reason", "error")
    }
}
