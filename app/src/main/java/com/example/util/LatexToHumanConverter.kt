package com.example.util

import java.util.regex.Pattern

object LatexToHumanConverter {

    /**
     * Converts raw LaTeX mathematical equations into clean, natural, human-readable math notation.
     * e.g.
     * "\text{Span} = I_{\text{max}} - I_{\text{min}}" -> "Span = I_max - I_min"
     * "e = A_m - A_t" -> "e = A_m - A_t"
     * "\% E_{\text{FS}} = \left( \frac{A_m - A_t}{\text{Span}} \right) \times 100" -> "% E_FS = ((A_m - A_t) / Span) × 100"
     * "S = \frac{dO}{dI} \approx \frac{\Delta O}{\Delta I}" -> "S = dO / dI ≈ ΔO / ΔI"
     */
    fun convert(rawLatex: String): String {
        if (rawLatex.isBlank()) return ""
        var s = rawLatex.trim()

        // Strip enclosing delimiters: $$, $, \[, \]
        s = s.replace(Regex("""^\$\$|\$\$$"""), "")
            .replace(Regex("""^\\\[|\\\]$"""), "")
            .replace(Regex("""^\$|\$$"""), "")
            .trim()

        // Remove \text{...}, \mathrm{...}, \mathbf{...}, \textbf{...}, \mathit{...}, \boldsymbol{...}
        s = removeTextCommandWrappers(s)

        // Parse \frac{...}{...} and \dfrac{...}{...} with nested balanced braces
        s = parseFractions(s)

        // Parse \sqrt[n]{...} and \sqrt{...}
        s = parseSquareRoots(s)

        // Delimiters
        s = s.replace(Regex("""\\left\("""), "(")
            .replace(Regex("""\\right\)"""), ")")
            .replace(Regex("""\\left\["""), "[")
            .replace(Regex("""\\right\]"""), "]")
            .replace(Regex("""\\left\\\{"""), "{")
            .replace(Regex("""\\right\\\}"""), "}")
            .replace(Regex("""\\left\|"""), "||")
            .replace(Regex("""\\right\|"""), "||")
            .replace("""\|""", "||")
            .replace("""\{""", "{")
            .replace("""\}""", "}")
            .replace("""\%""", "%")
            .replace("""\_""", "_")
            .replace("""\&""", "&")

        // Matrix / vectors
        s = s.replace(Regex("""\\begin\{[bB]matrix\}(.*?)\\end\{[bB]matrix\}""")) { match ->
            val content = match.groupValues[1]
                .replace("""\\\\""", ", ")
                .replace("&", ", ")
            "[ $content ]"
        }

        // Greek symbols
        val greeks = mapOf(
            """\Delta""" to "Δ", """\delta""" to "δ",
            """\Theta""" to "Θ", """\theta""" to "θ",
            """\nabla""" to "∇", """\eta""" to "η",
            """\alpha""" to "α", """\beta""" to "β",
            """\gamma""" to "γ", """\Gamma""" to "Γ",
            """\sigma""" to "σ", """\Sigma""" to "Σ",
            """\mu""" to "μ", """\lambda""" to "λ", """\Lambda""" to "Λ",
            """\pi""" to "π", """\Pi""" to "Π",
            """\omega""" to "ω", """\Omega""" to "Ω",
            """\tau""" to "τ", """\phi""" to "φ", """\Phi""" to "Φ",
            """\psi""" to "ψ", """\Psi""" to "Ψ",
            """\epsilon""" to "ε", """\rho""" to "ρ",
            """\zeta""" to "ζ", """\xi""" to "ξ", """\kappa""" to "κ"
        )
        for ((tex, sym) in greeks) {
            s = s.replace(Regex(Pattern.quote(tex) + """(?![a-zA-Z])"""), sym)
        }

        // Math Operators & Relations
        val operators = mapOf(
            """\approx""" to "≈",
            """\times""" to "×",
            """\cdot""" to "·",
            """\pm""" to "±",
            """\mp""" to "∓",
            """\leq""" to "≤",
            """\le""" to "≤",
            """\geq""" to "≥",
            """\ge""" to "≥",
            """\neq""" to "≠",
            """\ne""" to "≠",
            """\equiv""" to "≡",
            """\rightarrow""" to "→",
            """\to""" to "→",
            """\leftarrow""" to "←",
            """\partial""" to "∂",
            """\infty""" to "∞",
            """\sum""" to "∑",
            """\prod""" to "∏",
            """\int""" to "∫",
            """\in""" to "∈",
            """\circ""" to "°",
            """\vdots""" to "...",
            """\cdots""" to "...",
            """\ddots""" to "..."
        )
        for ((tex, sym) in operators) {
            s = s.replace(Regex(Pattern.quote(tex) + """(?![a-zA-Z])"""), sym)
        }

        // Common superscripts
        s = s.replace("^{2}", "²").replace("^2", "²")
            .replace("^{3}", "³").replace("^3", "³")
            .replace("^{0}", "⁰").replace("^0", "⁰")
            .replace("^{1}", "¹").replace("^1", "¹")
            .replace("^{T}", "ᵀ").replace("^T", "ᵀ")
            .replace("^{-1}", "⁻¹").replace("^-1", "⁻¹")

        // Generic superscripts ^{...}
        s = s.replace(Regex("""\^\{([^}]+)\}"""), "^($1)")

        // Common subscripts
        s = s.replace("_{max}", "_max")
            .replace("_{min}", "_min")
            .replace("_{FS}", "_FS")
            .replace("_{avg}", "_avg")
            .replace("_{rms}", "_rms")
            .replace("_{eff}", "_eff")
            .replace("_{out}", "_out")
            .replace("_{in}", "_in")
            .replace("_{0}", "₀").replace("_0", "₀")
            .replace("_{1}", "₁").replace("_1", "₁")
            .replace("_{2}", "₂").replace("_2", "₂")
            .replace("_{i}", "ᵢ").replace("_i", "ᵢ")
            .replace("_{n}", "ₙ").replace("_n", "ₙ")
            .replace("_{k}", "ₖ").replace("_k", "ₖ")

        // Generic subscripts _{...}
        s = s.replace(Regex("""_\{([^}]+)\}"""), "_$1")

        // Strip LaTeX whitespace tags
        s = s.replace(Regex("""\\[,;:!]|\\quad|\\qquad"""), " ")
            .replace("""\\left""", "")
            .replace("""\\right""", "")
            .replace("""\\""", "") // any remaining stray backslashes

        // Clean extra whitespace
        s = s.replace(Regex("""\s+"""), " ").trim()

        // Clean redundant double parentheses like ((X)) -> (X) if safe
        s = simplifyParentheses(s)

        return s
    }

    /**
     * Strips markdown bold, italics, code delimiters and converts inline LaTeX math in standard text
     */
    fun cleanMarkdownAndMath(text: String): String {
        if (text.isBlank()) return ""
        var out = text

        // Convert inline math $...$
        out = out.replace(Regex("""\$([^$]+)\$""")) { match ->
            convert(match.groupValues[1])
        }

        // Strip markdown bold and italics
        out = out.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
            .replace(Regex("""\*([^*]+)\*"""), "$1")
            .replace(Regex("""__([^_]+)__"""), "$1")
            .replace(Regex("""_([^_]+)_"""), "$1")
            .replace(Regex("""`([^`]+)`"""), "$1")

        return out.trim()
    }

    private fun removeTextCommandWrappers(input: String): String {
        val pattern = Regex("""\\(text|mathrm|mathbf|textbf|mathit|boldsymbol)\{""")
        var s = input
        var match = pattern.find(s)
        while (match != null) {
            val start = match.range.first
            val braceStart = match.range.last
            val extracted = extractBracedContent(s, braceStart)
            if (extracted != null) {
                val (content, endIdx) = extracted
                s = s.substring(0, start) + content + s.substring(endIdx)
                match = pattern.find(s)
            } else {
                break
            }
        }
        return s
    }

    private fun parseFractions(input: String): String {
        var s = input
        var found = true
        var loopGuard = 0
        while (found && loopGuard++ < 20) {
            val fracIdx = s.indexOf("""\frac""").let { if (it == -1) s.indexOf("""\dfrac""") else it }
            if (fracIdx == -1) {
                found = false
                break
            }
            val tagLen = if (s.startsWith("""\dfrac""", fracIdx)) 6 else 5
            var cur = fracIdx + tagLen
            while (cur < s.length && s[cur].isWhitespace()) cur++

            val numRes = extractBracedContent(s, cur)
            if (numRes == null) {
                // If malformed, replace just tag to prevent infinite loop
                s = s.substring(0, fracIdx) + s.substring(fracIdx + tagLen)
                continue
            }

            var nextIdx = numRes.second
            while (nextIdx < s.length && s[nextIdx].isWhitespace()) nextIdx++

            val denRes = extractBracedContent(s, nextIdx)
            if (denRes == null) {
                s = s.substring(0, fracIdx) + numRes.first + s.substring(numRes.second)
                continue
            }

            val numClean = numRes.first.trim()
            val denClean = denRes.first.trim()

            val formattedFrac = if (isSingleToken(numClean) && isSingleToken(denClean)) {
                "$numClean / $denClean"
            } else {
                "($numClean) / ($denClean)"
            }

            s = s.substring(0, fracIdx) + formattedFrac + s.substring(denRes.second)
        }
        return s
    }

    private fun parseSquareRoots(input: String): String {
        var s = input
        var idx = s.indexOf("""\sqrt""")
        var loopGuard = 0
        while (idx != -1 && loopGuard++ < 20) {
            var cur = idx + 5
            // Check optional [n]
            var rootDegree = ""
            if (cur < s.length && s[cur] == '[') {
                val endBracket = s.indexOf(']', cur)
                if (endBracket != -1) {
                    rootDegree = s.substring(cur + 1, endBracket).trim()
                    cur = endBracket + 1
                }
            }
            while (cur < s.length && s[cur].isWhitespace()) cur++
            val contentRes = extractBracedContent(s, cur)
            if (contentRes != null) {
                val prefix = if (rootDegree.isNotEmpty()) "${rootDegree}√" else "√"
                val body = "($prefix(${contentRes.first}))"
                s = s.substring(0, idx) + body + s.substring(contentRes.second)
                idx = s.indexOf("""\sqrt""")
            } else {
                break
            }
        }
        return s
    }

    private fun extractBracedContent(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex >= text.length || text[startIndex] != '{') return null
        var depth = 0
        val sb = StringBuilder()
        for (i in startIndex until text.length) {
            when (val c = text[i]) {
                '{' -> {
                    depth++
                    if (depth > 1) sb.append(c)
                }
                '}' -> {
                    depth--
                    if (depth == 0) return Pair(sb.toString(), i + 1)
                    sb.append(c)
                }
                else -> sb.append(c)
            }
        }
        return null
    }

    private fun isSingleToken(str: String): Boolean {
        return !str.contains(" ") && !str.contains("+") && !str.contains("-") && !str.contains("/")
    }

    private fun simplifyParentheses(str: String): String {
        return str.replace("((", "(").replace("))", ")")
            .replace("( (", "(").replace(") )", ")")
    }
}
