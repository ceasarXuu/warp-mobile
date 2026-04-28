package dev.warp.mobile.observability

import android.util.Log
import org.json.JSONObject

class MobileEventLogger(private val tag: String) {
    fun event(name: String, fields: Map<String, String> = emptyMap()) {
        val payload = JSONObject()
        payload.put("event", name)
        fields.toSortedMap().forEach { (key, value) -> payload.put(key, value) }
        Log.i(tag, payload.toString())
    }

    fun warn(name: String, fields: Map<String, String> = emptyMap()) {
        val payload = JSONObject()
        payload.put("event", name)
        fields.toSortedMap().forEach { (key, value) -> payload.put(key, value) }
        Log.w(tag, payload.toString())
    }
}
