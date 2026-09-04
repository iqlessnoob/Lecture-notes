package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "lecture_notes",
    foreignKeys = [
        ForeignKey(
            entity = SubjectSlot::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("slotId")]
)
data class LectureNote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val slotId: Long,
    val title: String,
    val sourceType: String, // "UPLOAD" or "YOUTUBE"
    val sourceUri: String,
    val summary: String,
    val keyConceptsJson: String,
    val formulasJson: String,
    val diagramsJson: String,
    val timestampsJson: String,
    val videoDurationFormatted: String = "45:00",
    val capturedFrameUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getKeyConcepts(): List<String> {
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(keyConceptsJson)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    fun getFormulas(): List<FormulaItem> {
        val list = mutableListOf<FormulaItem>()
        try {
            val jsonArray = JSONArray(formulasJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    FormulaItem(
                        name = obj.optString("name", "Formula"),
                        latex = obj.optString("latex", ""),
                        explanation = obj.optString("explanation", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun getDiagrams(): List<DiagramItem> {
        val list = mutableListOf<DiagramItem>()
        try {
            val jsonArray = JSONArray(diagramsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DiagramItem(
                        label = obj.optString("label", "Diagram"),
                        timestamp = obj.optString("timestamp", "00:00"),
                        description = obj.optString("description", ""),
                        diagramType = obj.optString("diagram_type", "flowchart"),
                        mermaidCode = obj.optString("mermaid_code", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun getTimestamps(): List<TimestampMarker> {
        val list = mutableListOf<TimestampMarker>()
        try {
            val jsonArray = JSONArray(timestampsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TimestampMarker(
                        time = obj.optString("time", "00:00"),
                        topic = obj.optString("topic", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}

data class FormulaItem(
    val name: String,
    val latex: String,
    val explanation: String
)

data class DiagramItem(
    val label: String,
    val timestamp: String,
    val description: String,
    val diagramType: String,
    val mermaidCode: String = ""
)

data class TimestampMarker(
    val time: String,
    val topic: String
)

data class GeneratedLectureResult(
    val title: String,
    val summary: String,
    val keyConcepts: List<String>,
    val formulas: List<FormulaItem>,
    val diagrams: List<DiagramItem>,
    val timestamps: List<TimestampMarker>
) {
    fun toKeyConceptsJson(): String {
        val array = JSONArray()
        keyConcepts.forEach { array.put(it) }
        return array.toString()
    }

    fun toFormulasJson(): String {
        val array = JSONArray()
        formulas.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("latex", it.latex)
            obj.put("explanation", it.explanation)
            array.put(obj)
        }
        return array.toString()
    }

    fun toDiagramsJson(): String {
        val array = JSONArray()
        diagrams.forEach {
            val obj = JSONObject()
            obj.put("label", it.label)
            obj.put("timestamp", it.timestamp)
            obj.put("description", it.description)
            obj.put("diagram_type", it.diagramType)
            obj.put("mermaid_code", it.mermaidCode)
            array.put(obj)
        }
        return array.toString()
    }

    fun toTimestampsJson(): String {
        val array = JSONArray()
        timestamps.forEach {
            val obj = JSONObject()
            obj.put("time", it.time)
            obj.put("topic", it.topic)
            array.put(obj)
        }
        return array.toString()
    }
}
