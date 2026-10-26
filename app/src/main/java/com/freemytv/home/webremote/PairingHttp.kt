package com.freemytv.home.webremote

import org.json.JSONException
import org.json.JSONObject

/** `POST /pair` as HTTP: the request's JSON in, a status and JSON out (free-my-tv-remote-controller's protocol/README.md). */
suspend fun WebRemotePairing.answer(body: String): Pair<Int, String> {
    val request = try {
        JSONObject(body)
    } catch (_: JSONException) {
        return 400 to JSONObject().put("error", "bad-request").toString()
    }
    return when (val answer = pair(request.optString("code"), request.optString("name"))) {
        is PairingAnswer.Paired -> 200 to JSONObject().put("credential", answer.credential).toString()
        PairingAnswer.WrongCode -> 401 to JSONObject().put("error", "wrong-code").toString()
        PairingAnswer.Closed -> 409 to JSONObject().put("error", "pairing-closed").toString()
        PairingAnswer.TooManyTries -> 429 to JSONObject().put("error", "too-many-tries").toString()
    }
}
