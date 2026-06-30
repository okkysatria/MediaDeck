package com.gojektracker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "orders")
data class OrderRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tanggal: String,          // e.g., "2026-06-19"
    val hari: String,             // e.g., "Jumat"
    val jamMulai: String,         // e.g., "17:00"
    val jamPickup: String?,       // e.g., "17:15"
    val jamSelesai: String?,      // e.g., "17:40"
    val jenisOrder: String,       // "Penumpang", "Food", "Paket"
    val pendapatan: Double,
    val durasi: Long,             // in minutes
    val jarakTempuh: Double,      // in km
    val jarakKePickup: Double = 0.0, // in km (dari awal ke pickup)
    val jarakKeTujuan: Double = 0.0, // in km (dari pickup ke akhir)
    val latitudeAwal: Double,
    val longitudeAwal: Double,
    val alamatAwal: String,
    val latitudePickup: Double?,
    val longitudePickup: Double?,
    val alamatPickup: String?,
    val latitudeAkhir: Double?,
    val longitudeAkhir: Double?,
    val alamatAkhir: String?,
    val catatan: String?,
    val trackGps: String          // JSON array string of {"lat": x, "lng": y, "time": t}
) {
    fun getTrackPoints(): List<TrackPoint> {
        val list = mutableListOf<TrackPoint>()
        try {
            if (trackGps.isNotEmpty() && trackGps != "[]") {
                val jsonArray = JSONArray(trackGps)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        TrackPoint(
                            lat = obj.getDouble("lat"),
                            lng = obj.getDouble("lng"),
                            timestamp = obj.getLong("time")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Fallback to start -> pickup -> end if detailed track points are empty
        if (list.isEmpty()) {
            list.add(TrackPoint(latitudeAwal, longitudeAwal, 0L))
            if (latitudePickup != null && longitudePickup != null && latitudePickup != 0.0 && longitudePickup != 0.0) {
                list.add(TrackPoint(latitudePickup, longitudePickup, 0L))
            }
            if (latitudeAkhir != null && longitudeAkhir != null && latitudeAkhir != 0.0 && longitudeAkhir != 0.0) {
                list.add(TrackPoint(latitudeAkhir, longitudeAkhir, 0L))
            }
        }
        return list
    }
}

data class TrackPoint(val lat: Double, val lng: Double, val timestamp: Long)

fun List<TrackPoint>.toJsonString(): String {
    val jsonArray = JSONArray()
    for (pt in this) {
        val obj = JSONObject()
        obj.put("lat", pt.lat)
        obj.put("lng", pt.lng)
        obj.put("time", pt.timestamp)
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}
