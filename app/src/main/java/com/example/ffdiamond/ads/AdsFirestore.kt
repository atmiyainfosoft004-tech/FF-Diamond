package com.example.ffdiamond.ads

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

object AdsFirestore {

    const val COLLECTION = "New Update"
    const val DOCUMENT = "mycollection"
    const val FIELD_ADS_CONFIG = "ads_config"

    private const val TAG = "AdsFirestore"

    fun db() = FirebaseFirestore.getInstance()

    fun document() = db().collection(COLLECTION).document(DOCUMENT)

    fun adsJson(snap: DocumentSnapshot): String? = adsJsonFromData(snap.data)

    internal fun adsJsonFromData(data: Map<String, Any?>?): String? {
        if (data.isNullOrEmpty()) return null
        val fromField = jsonFromValue(data[FIELD_ADS_CONFIG])
        if (!fromField.isNullOrBlank()) return fromField
        if (data.containsKey("ads_flag") || data.containsKey("google_ads") || data.containsKey("units")) {
            return jsonObject(data).toString()
        }
        Log.w(TAG, "no $FIELD_ADS_CONFIG on doc keys=${data.keys}")
        return null
    }

    private fun jsonFromValue(value: Any?): String? = when (value) {
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is Map<*, *> -> {
            val nested = LinkedHashMap<String, Any?>()
            value.forEach { (key, item) ->
                if (key != null) nested[key.toString()] = item
            }
            jsonObject(nested).toString().takeIf { it.isNotBlank() && it != "{}" }
        }
        else -> null
    }

    fun jsonObject(data: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        data.forEach { (key, value) ->
            if (key == FIELD_ADS_CONFIG) return@forEach
            obj.put(key, wrap(value))
        }
        return obj
    }

    internal fun wrap(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> {
            val nested = JSONObject()
            value.forEach { (k, v) ->
                if (k != null) nested.put(k.toString(), wrap(v))
            }
            nested
        }
        is List<*> -> JSONArray().also { array ->
            value.forEach { item -> array.put(wrap(item)) }
        }
        else -> value
    }

    suspend fun fetch(): DocumentSnapshot? {
        val hits = listOf(
            getDoc("New Update", "mycollection"),
            getDoc("mycollection", "All Video Downloader & Launcher"),
            getDoc("New Update", "All Video Downloader & Launcher"),
            firstIn("mycollection"),
            firstIn("New Update")
        )
        for (snap in hits) {
            if (snap != null && snap.exists() && adsJson(snap) != null) {
                Log.i(TAG, "loaded ${snap.reference.path}")
                return snap
            }
        }
        Log.w(TAG, "no ads_config on tried paths")
        return hits.firstOrNull { it != null && it.exists() }
    }

    private suspend fun getDoc(collection: String, document: String): DocumentSnapshot? =
        suspendCancellableCoroutine { cont ->
            db().collection(collection).document(document).get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) Log.w(TAG, "missing $collection/$document")
                    if (cont.isActive) cont.resume(snap.takeIf { it.exists() })
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "get $collection/$document failed: ${error.message}")
                    if (cont.isActive) cont.resume(null)
                }
            cont.invokeOnCancellation { }
        }

    private suspend fun firstIn(collection: String): DocumentSnapshot? =
        suspendCancellableCoroutine { cont ->
            db().collection(collection).limit(8).get()
                .addOnSuccessListener { query ->
                    val ids = query.documents.map { it.id }
                    Log.i(TAG, "collection '$collection' ids=$ids")
                    val hit = query.documents.firstOrNull { it.exists() && adsJson(it) != null }
                        ?: query.documents.firstOrNull { it.exists() }
                    if (cont.isActive) cont.resume(hit)
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "query $collection failed: ${error.message}")
                    if (cont.isActive) cont.resume(null)
                }
            cont.invokeOnCancellation { }
        }
}
