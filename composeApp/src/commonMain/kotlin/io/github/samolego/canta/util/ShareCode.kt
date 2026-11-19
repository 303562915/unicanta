package io.github.samolego.canta.util

/**
 * The share code of one community plan: `CANTA-PLAN-<id>-<check>`.
 *
 * A plan can be handed to someone else without a link, an account or a server call: sharing copies
 * the code to the clipboard, and the receiver's app reads the clipboard at launch, recognises the
 * code and opens that plan. The last four characters are a checksum of the id, so a code copied
 * half-way, or a random sentence that happens to contain the prefix, is rejected instead of opening
 * plan number whatever-the-digits-were.
 *
 * The code is found *inside* a longer text on purpose: the share sheet sends a sentence with the
 * plan's name in it, and only the code part matters.
 */
object ShareCode {

    /** What every code starts with; the app looks for this before it parses anything. */
    const val PREFIX: String = "CANTA-PLAN-"

    /** `CANTA-PLAN-1234-0A1B`, with the checksum in upper-case hex. */
    private val PATTERN = Regex(
        pattern = PREFIX.replace("-", "\\-") + "(\\d{1,18})-([0-9A-F]{4})",
        option = RegexOption.IGNORE_CASE,
    )

    /** The code of plan [planId], e.g. `CANTA-PLAN-42-1A2B`. */
    fun of(planId: Long): String = "$PREFIX$planId-${checksumOf(planId)}"

    /**
     * The share message: the plan's name for the reader, the code for the app. Empty when [planId]
     * is not a plan id, so a caller never copies a code that would not resolve.
     */
    fun message(planId: Long, title: String = ""): String {
        if (planId <= 0) return ""
        val code = of(planId)
        val name = title.trim()
        return if (name.isEmpty()) {
            "$code\n打开 Canta 会自动识别这段分享码。"
        } else {
            "Canta 方案「$name」\n分享码：$code\n打开 Canta 会自动识别剪贴板里的分享码。"
        }
    }

    /** The plan id inside [text], or null when it holds no valid code. */
    fun parse(text: String?): Long? {
        val raw = text ?: return null
        if (!raw.contains(PREFIX, ignoreCase = true)) return null
        val match = PATTERN.find(raw) ?: return null
        val planId = match.groupValues[1].toLongOrNull() ?: return null
        if (planId <= 0) return null
        val check = match.groupValues[2].uppercase()
        return planId.takeIf { checksumOf(it) == check }
    }

    /** True when [text] carries a valid share code, i.e. [parse] would open something. */
    fun isCode(text: String?): Boolean = parse(text) != null

    /**
     * Four upper-case hex characters derived from [planId]: FNV-1a over its digits, folded to 16
     * bits. Written out by hand instead of using `hashCode()`, because that differs between the
     * platforms this app ships on and a code made on Android has to open on the desktop.
     */
    fun checksumOf(planId: Long): String {
        var hash = 0x811C9DC5.toInt()
        planId.toString().forEach { char ->
            hash = hash xor char.code
            hash *= 16777619
        }
        val folded = (hash ushr 16 xor hash) and 0xFFFF
        return folded.toString(16).uppercase().padStart(4, '0')
    }
}
