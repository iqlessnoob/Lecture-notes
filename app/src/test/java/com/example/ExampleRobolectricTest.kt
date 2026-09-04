package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LectureScribe AI", appName)
  }

  @Test
  fun `verify lecture note serialization and deserialization`() {
    val note = com.example.data.model.LectureNote(
      id = 1L,
      slotId = 1L,
      title = "Quantum Physics: Wave-Particle Duality",
      sourceType = "YOUTUBE",
      sourceUri = "https://youtube.com/watch?v=quantum_01",
      summary = "Foundational lecture exploring photon interference.",
      keyConceptsJson = "[\"De Broglie wavelength\", \"Heisenberg uncertainty\"]",
      formulasJson = "[{\"name\":\"De Broglie\",\"latex\":\"\\\\lambda = \\\\frac{h}{p}\",\"explanation\":\"Wavelength of matter wave\"}]",
      diagramsJson = "[{\"label\":\"Double Slit Experiment\",\"timestamp\":\"04:15\",\"description\":\"Electron beam passes through two slits\",\"diagram_type\":\"flowchart\",\"mermaid_code\":\"graph LR; A[Beam]-->B[Slits]; B-->C[Detector];\"}]",
      timestampsJson = "[{\"time\":\"00:00\",\"topic\":\"Introduction\"},{\"time\":\"04:15\",\"topic\":\"Interference\"}]",
      videoDurationFormatted = "32:10"
    )

    val concepts = note.getKeyConcepts()
    assertEquals(2, concepts.size)
    assertEquals("De Broglie wavelength", concepts[0])

    val formulas = note.getFormulas()
    assertEquals(1, formulas.size)
    assertEquals("De Broglie", formulas[0].name)
    assertEquals("\\lambda = \\frac{h}{p}", formulas[0].latex)

    val diagrams = note.getDiagrams()
    assertEquals(1, diagrams.size)
    assertEquals("04:15", diagrams[0].timestamp)

    val timestamps = note.getTimestamps()
    assertEquals(2, timestamps.size)
    assertEquals("04:15", timestamps[1].time)
  }
}
