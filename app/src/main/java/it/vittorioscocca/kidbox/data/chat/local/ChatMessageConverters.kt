package it.vittorioscocca.kidbox.data.chat.local

import androidx.room.TypeConverter
import it.vittorioscocca.kidbox.data.chat.model.ContactPayload
import it.vittorioscocca.kidbox.data.chat.model.LabeledStringValue
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64

data class MediaGroupItem(
    val url: String,
    val type: String, // "photo" | "video"
)

class ChatMessageConverters {

    @TypeConverter
    fun reactionsToJson(value: Map<String, List<String>>?): String? {
        if (value.isNullOrEmpty()) return null
        val root = JSONObject()
        value.forEach { (emoji, users) ->
            val arr = JSONArray()
            users.forEach { arr.put(it) }
            root.put(emoji, arr)
        }
        return root.toString()
    }

    @TypeConverter
    fun reactionsFromJson(value: String?): Map<String, List<String>>? {
        if (value.isNullOrBlank()) return null
        val root = JSONObject(value)
        val out = linkedMapOf<String, List<String>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = root.optJSONArray(key) ?: JSONArray()
            val users = buildList {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }.filter { it.isNotBlank() }
            out[key] = users
        }
        return out
    }

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? {
        if (value.isNullOrEmpty()) return null
        return JSONArray(value).toString()
    }

    @TypeConverter
    fun stringListFromJson(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        val arr = JSONArray(value)
        return buildList {
            for (i in 0 until arr.length()) add(arr.optString(i))
        }.filter { it.isNotBlank() }
    }

    @TypeConverter
    fun mediaGroupToJson(value: List<MediaGroupItem>?): String? {
        if (value.isNullOrEmpty()) return null
        val arr = JSONArray()
        value.forEach { item ->
            arr.put(
                JSONObject()
                    .put("url", item.url)
                    .put("type", item.type),
            )
        }
        return arr.toString()
    }

    @TypeConverter
    fun mediaGroupFromJson(value: String?): List<MediaGroupItem>? {
        if (value.isNullOrBlank()) return null
        val arr = JSONArray(value)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url")
                val type = obj.optString("type")
                if (url.isNotBlank()) add(MediaGroupItem(url = url, type = type))
            }
        }
    }

    @TypeConverter
    fun contactToJson(value: ContactPayload?): String? {
        if (value == null) return null
        val root = JSONObject()
            .put("givenName", value.givenName)
            .put("familyName", value.familyName)
            .put("phoneNumbers", labeledValuesToJsonArray(value.phoneNumbers))
            .put("emailAddresses", labeledValuesToJsonArray(value.emailAddresses))
            .put(
                "avatarData",
                value.avatarData?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
            )
        return root.toString()
    }

    @TypeConverter
    fun contactFromJson(value: String?): ContactPayload? {
        if (value.isNullOrBlank()) return null
        val root = JSONObject(value)
        val avatarB64 = root.optString("avatarData").takeIf { it.isNotBlank() }
        return ContactPayload(
            givenName = root.optString("givenName"),
            familyName = root.optString("familyName"),
            phoneNumbers = labeledValuesFromJsonArray(root.optJSONArray("phoneNumbers")),
            emailAddresses = labeledValuesFromJsonArray(root.optJSONArray("emailAddresses")),
            avatarData = avatarB64?.let { Base64.decode(it, Base64.NO_WRAP) },
        )
    }

    private fun labeledValuesToJsonArray(values: List<LabeledStringValue>): JSONArray {
        val arr = JSONArray()
        values.forEach {
            arr.put(
                JSONObject()
                    .put("label", it.label)
                    .put("value", it.value),
            )
        }
        return arr
    }

    private fun labeledValuesFromJsonArray(arr: JSONArray?): List<LabeledStringValue> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val label = obj.optString("label")
                val value = obj.optString("value")
                add(LabeledStringValue(label = label, value = value))
            }
        }
    }
}
