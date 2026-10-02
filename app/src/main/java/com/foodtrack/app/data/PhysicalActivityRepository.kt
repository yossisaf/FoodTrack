package com.foodtrack.app.data

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object PhysicalActivityRepository {
    @Volatile private var cached: List<PhysicalActivity>? = null

    fun all(context: Context): List<PhysicalActivity> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val result = mutableListOf<PhysicalActivity>()
            context.assets.open("physical_activities_2024.tsv").use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        val p = line.split('\t', limit = 4)
                        if (p.size == 4) {
                            val met = p[1].toDoubleOrNull()
                            if (met != null) result += PhysicalActivity(p[0], met, p[2], p[3])
                        }
                    }
                }
            }
            return result.also { cached = it }
        }
    }

    fun search(context: Context, query: String): List<PhysicalActivity> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return all(context).take(80)
        return all(context).filter {
            it.description.lowercase().contains(q) || it.category.lowercase().contains(q) || it.code.contains(q)
        }.take(80)
    }
}
