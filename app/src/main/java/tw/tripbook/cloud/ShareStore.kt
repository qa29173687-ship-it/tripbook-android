package tw.tripbook.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Remembers which local trips are shared (owner) and which cloud trips this phone joined (companion). */
data class JoinedTrip(val cloudId: String, val memberId: String, val memberName: String, val tripName: String)

class ShareStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("share", Context.MODE_PRIVATE)

    fun ownedCloudId(localTripId: Long): String? = sp.getString("own_$localTripId", null)
    fun setOwned(localTripId: Long, cloudId: String) = sp.edit().putString("own_$localTripId", cloudId).apply()
    fun ownedAll(): Map<Long, String> = sp.all.filterKeys { it.startsWith("own_") }
        .mapNotNull { (k, v) -> k.removePrefix("own_").toLongOrNull()?.let { id -> (v as? String)?.let { id to it } } }
        .toMap()

    /** After restoring on a new phone, local member ids differ from the cloud ids companions bound to. */
    fun memberMap(localTripId: Long): Map<Long, String> {
        val o = runCatching { JSONObject(sp.getString("map_$localTripId", "{}")!!) }.getOrDefault(JSONObject())
        return o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to o.getString(k) } }.toMap()
    }
    fun setMemberMap(localTripId: Long, map: Map<Long, String>) {
        val o = JSONObject(); map.forEach { (k, v) -> o.put(k.toString(), v) }
        sp.edit().putString("map_$localTripId", o.toString()).apply()
    }

    fun joined(): List<JoinedTrip> {
        val arr = runCatching { JSONArray(sp.getString("joined", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            JoinedTrip(o.getString("c"), o.getString("m"), o.getString("n"), o.optString("t"))
        }
    }

    fun addJoined(j: JoinedTrip) {
        val list = joined().filter { it.cloudId != j.cloudId } + j
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("c", it.cloudId).put("m", it.memberId).put("n", it.memberName).put("t", it.tripName)) }
        sp.edit().putString("joined", arr.toString()).apply()
    }

    fun removeJoined(cloudId: String) {
        val list = joined().filter { it.cloudId != cloudId }
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("c", it.cloudId).put("m", it.memberId).put("n", it.memberName).put("t", it.tripName)) }
        sp.edit().putString("joined", arr.toString()).apply()
    }
}
