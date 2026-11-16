package app.cl0udly.ui

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import app.cl0udly.ui.theme.OcColors

// The same highlighting as on the phone (ui/components/Code.kt there), so code looks alike on both.

/** Colors for code, picked from the theme so it fits every look, light and dark. */
private class CodePalette(c: OcColors) {
    val keyword = c.accent
    val string = lerp(c.ok, c.text, 0.25f)
    val number = lerp(c.warn, c.text, 0.2f)
    val comment = c.textDim.copy(alpha = 0.75f)
    val call = lerp(c.link, c.text, 0.35f)
    val type = lerp(c.accent, c.text, 0.45f)
}

private val KEYWORDS = setOf(
    // shared by most languages people ask about
    "if", "else", "for", "while", "do", "return", "break", "continue", "switch", "case", "default", "try", "catch", "finally", "throw",
    "class", "interface", "enum", "struct", "new", "this", "self", "super", "import", "from", "export", "package", "as", "in", "is",
    "true", "false", "null", "nil", "None", "True", "False", "undefined", "void",
    // JS/TS
    "const", "let", "var", "function", "async", "await", "yield", "typeof", "instanceof", "extends", "implements", "static", "public", "private", "protected", "readonly", "type",
    // Kotlin/Java/Swift
    "fun", "val", "when", "object", "data", "sealed", "override", "open", "internal", "companion", "lateinit", "final", "abstract", "func", "guard", "init", "where",
    // Python/Ruby/Rust/Go
    "def", "lambda", "pass", "with", "elif", "and", "or", "not", "end", "fn", "mut", "impl", "pub", "use", "mod", "match", "loop", "go", "defer", "chan", "range", "map",
    // shell
    "then", "fi", "esac", "done", "echo", "export", "local", "sudo",
)

private val TOKENS = Regex(
    """(//[^\n]*|#(?!include)[^\n]*|/\*[\s\S]*?\*/|--[^\n]*)""" +
        """|("(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)""" +
        """|(\b\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?\b|\b0x[0-9a-fA-F]+\b)""" +
        """|([A-Za-z_][A-Za-z0-9_]*)""",
)

/** Light highlighting by token, good enough for reading on a phone; plain text and logs stay plain. */
fun highlight(code: String, lang: String, c: OcColors): AnnotatedString {
    val l = lang.lowercase()
    if (l in setOf("text", "txt", "output", "log", "error", "plain")) return AnnotatedString(code)
    val p = CodePalette(c)
    // "#" starts a comment only where it does (shell, Python, Ruby, YAML...), not in C or CSS colors
    val hashComments = l in setOf("sh", "bash", "shell", "zsh", "py", "python", "rb", "ruby", "yaml", "yml", "toml", "r", "perl", "dockerfile", "makefile", "conf", "ini")
    val dashComments = l in setOf("sql", "lua", "haskell", "hs")
    return buildAnnotatedString {
        var at = 0
        for (m in TOKENS.findAll(code)) {
            val v = m.value
            val style = when {
                m.groups[1] != null -> when {
                    v.startsWith("#") && !hashComments -> null
                    v.startsWith("--") && !dashComments -> null
                    else -> SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
                }
                m.groups[2] != null -> SpanStyle(color = p.string)
                m.groups[3] != null -> SpanStyle(color = p.number)
                v in KEYWORDS -> SpanStyle(color = p.keyword)
                // a name followed by "(" is a call; a capitalized name is a type
                code.getOrNull(m.range.last + 1) == '(' -> SpanStyle(color = p.call)
                v.first().isUpperCase() && v.length > 1 && v.any { it.isLowerCase() } -> SpanStyle(color = p.type)
                else -> null
            }
            if (style == null) continue
            append(code.substring(at, m.range.first))
            withStyle(style) { append(v) }
            at = m.range.last + 1
        }
        append(code.substring(at))
    }
}

