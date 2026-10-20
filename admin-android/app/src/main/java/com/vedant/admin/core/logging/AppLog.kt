package com.vedant.admin.core.logging

import android.util.Log

/**
 * Structured, **redacting** logging.
 *
 * Logging exists so a failed mutation can be diagnosed. It must never become a
 * second copy of the data it is describing, so every message passes through
 * [redact] before it is emitted: access tokens, publishable keys and email
 * addresses are replaced with placeholders (CLAUDE.md: "never log tokens, the
 * anon key, emails, or passwords").
 *
 * The sink is injectable. In production `VedantAdminApplication` installs an
 * `android.util.Log`-backed sink; in unit tests the default no-op sink is used,
 * so the core stays JVM-testable without the Android runtime.
 */
object AppLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun interface Sink {
        fun log(level: Level, tag: String, message: String, throwable: Throwable?)
    }

    private val NO_OP = Sink { _, _, _, _ -> }

    @Volatile
    private var sink: Sink = NO_OP

    /** Install the platform sink. Called once, from `Application.onCreate()`. */
    fun install(newSink: Sink) {
        sink = newSink
    }

    fun d(tag: String, message: String) = emit(Level.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = emit(Level.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        emit(Level.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        emit(Level.ERROR, tag, message, throwable)

    private fun emit(level: Level, tag: String, message: String, throwable: Throwable?) {
        sink.log(level, tag, redact(message), throwable)
    }

    // --- Redaction ---------------------------------------------------------

    private val JWT_PATTERN =
        Regex("""eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}""")
    private val API_KEY_PATTERN = Regex("""\bsb_[a-z]+_[A-Za-z0-9_\-]{6,}""")
    private val BEARER_PATTERN = Regex("""(?i)\bbearer\s+[A-Za-z0-9._\-]+""")
    private val EMAIL_PATTERN = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    /**
     * Removes secrets and personal data from a log line. Applied to every message
     * before it leaves [AppLog], so a caller cannot leak by accident.
     */
    fun redact(text: String): String = text
        .replace(JWT_PATTERN, "[redacted-jwt]")
        .replace(API_KEY_PATTERN, "[redacted-key]")
        .replace(BEARER_PATTERN, "Bearer [redacted-token]")
        .replace(EMAIL_PATTERN, "[redacted-email]")
}

/**
 * Production sink. Kept in the same file so `AppLog` has one obvious owner; it
 * is only ever instantiated from the `Application`, never in unit tests.
 */
class AndroidLogSink : AppLog.Sink {
    override fun log(level: AppLog.Level, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            AppLog.Level.DEBUG -> Log.d(tag, message, throwable)
            AppLog.Level.INFO -> Log.i(tag, message, throwable)
            AppLog.Level.WARN -> Log.w(tag, message, throwable)
            AppLog.Level.ERROR -> Log.e(tag, message, throwable)
        }
    }
}
