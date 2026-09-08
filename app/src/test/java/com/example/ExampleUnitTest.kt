package com.example

import com.example.util.LatexToHumanConverter
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testLatexToHumanConverter_fractionsAndPowers() {
    val input = "E = mc^2"
    val result = LatexToHumanConverter.convert(input)
    assertEquals("E = mc²", result)

    val fracInput = "\\frac{a + b}{c}"
    val fracResult = LatexToHumanConverter.convert(fracInput)
    assertTrue(fracResult.contains("(a + b) / (c)") || fracResult.contains("/"))

    val sqrtInput = "\\sqrt{x^2 + y^2}"
    val sqrtResult = LatexToHumanConverter.convert(sqrtInput)
    assertTrue(sqrtResult.contains("√(x² + y²)") || sqrtResult.contains("√"))
  }

  @Test
  fun testLatexToHumanConverter_greekLetters() {
    val input = "\\alpha + \\beta = \\theta"
    val result = LatexToHumanConverter.convert(input)
    assertEquals("α + β = θ", result)
  }

  @Test
  fun testCleanMarkdownAndMath() {
    val input = "Here is **bold** text and `code`."
    val cleaned = LatexToHumanConverter.cleanMarkdownAndMath(input)
    assertEquals("Here is bold text and code.", cleaned)
  }
}
