package io.github.samolego.canta.ui.lock

/**
 * The questions the start-up lock can ask, and the date that answers each of them.
 *
 * Every answer is a date written `YYYY-MM-DD`, which is why one input serves all of them: the
 * question only decides what the person is asked to remember, not what they type. A birthday is
 * the one date nobody has to look up, and "whose birthday" is what splits the plausible answers
 * apart — your own, your father's, your child's.
 *
 * The labels are the question as it reads on the lock screen, so they are written the way a
 * person asks it, not the way a form labels it.
 */
data class LaunchQuestion(val id: String, val label: String)

/** A lock in force: the question it asks and the date that answers it. */
data class LaunchGate(val question: LaunchQuestion, val answer: String)

/**
 * Five birthdays, which is enough for the phone in a household without turning the picker into a
 * form. Stored by [LaunchQuestion.id], never by index: adding a question in the middle must not
 * silently change what an existing install is being asked.
 */
val LAUNCH_QUESTIONS: List<LaunchQuestion> = listOf(
    LaunchQuestion("self_birthday", "您的生日"),
    LaunchQuestion("father_birthday", "您父亲的生日"),
    LaunchQuestion("mother_birthday", "您母亲的生日"),
    LaunchQuestion("spouse_birthday", "您伴侣的生日"),
    LaunchQuestion("child_birthday", "您孩子的生日"),
)

/** The question with [id], or null when a stored id names one this build no longer has. */
fun launchQuestionOf(id: String): LaunchQuestion? = LAUNCH_QUESTIONS.firstOrNull { it.id == id }

/** Years the field accepts: before 1900 no living birthday is being typed, after 2100 it is a typo. */
private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2100

/** Digits in a date without its separators. */
private const val BIRTHDAY_DIGITS = 8

/**
 * Turns eight digits (`20001101`) into the stored form `2000-11-01`, or answers null when the
 * digits are not a date at all — a month of 13, a 31st of February's shape, a year out of range.
 *
 * The day check is deliberately shallow: it rules out the typos (14 as a month, 45 as a day) and
 * leaves the rest alone, because this is a lock somebody has to still be able to answer, not a
 * calendar.
 */
fun normalizeBirthday(digits: String): String? {
    if (digits.length != BIRTHDAY_DIGITS || digits.any { it !in '0'..'9' }) return null
    val year = digits.substring(0, 4).toIntOrNull() ?: return null
    val month = digits.substring(4, 6).toIntOrNull() ?: return null
    val day = digits.substring(6, 8).toIntOrNull() ?: return null
    if (year !in MIN_YEAR..MAX_YEAR) return null
    if (month !in 1..12) return null
    if (day !in 1..31) return null
    return "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
}

/**
 * True when [answer] is a stored date this lock can be answered with.
 *
 * Everything that reads the stored answer goes through this. A value that failed it — a file
 * edited by hand, an old four-digit lock, a format from some other build — must be treated as
 * "no lock" rather than as a question nobody can answer, because the one mistake this feature
 * cannot afford is locking the owner out of their own app for good.
 */
fun isUsableBirthday(answer: String): Boolean =
    answer.length == BIRTHDAY_DIGITS + 2 && normalizeBirthday(answer.filter { it in '0'..'9' }) == answer

/**
 * The lock a stored question and answer describe, or null when the pair is not usable.
 *
 * A question whose id this build does not know, or an answer that is not a date, both mean "no
 * lock": the pair is only a lock while it can actually be shown and actually be answered.
 */
fun launchGateOf(questionId: String, answer: String): LaunchGate? {
    val question = launchQuestionOf(questionId) ?: return null
    if (!isUsableBirthday(answer)) return null
    return LaunchGate(question, answer)
}

/** True when the eight digits typed on the lock screen answer [expected]. */
fun answersGate(digits: String, expected: String): Boolean =
    normalizeBirthday(digits) != null && normalizeBirthday(digits) == expected
