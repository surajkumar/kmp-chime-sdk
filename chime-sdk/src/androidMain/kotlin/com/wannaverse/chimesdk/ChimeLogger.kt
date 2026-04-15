package com.wannaverse.chimesdk

import android.util.Log
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.amazonaws.services.chime.sdk.meetings.utils.logger.Logger

class ChimeLogger : Logger {
    private var currentLogLevel: LogLevel = LogLevel.INFO

    override fun debug(tag: String, msg: String) {
        if (currentLogLevel.ordinal >= LogLevel.DEBUG.ordinal) Log.d(tag, msg)
    }

    override fun error(tag: String, msg: String) {
        if (currentLogLevel.ordinal >= LogLevel.ERROR.ordinal) Log.e(tag, msg)
    }

    override fun getLogLevel(): LogLevel = currentLogLevel

    override fun info(tag: String, msg: String) {
        if (currentLogLevel.ordinal >= LogLevel.INFO.ordinal) Log.i(tag, msg)
    }

    override fun setLogLevel(level: LogLevel) {
        currentLogLevel = level
    }

    override fun verbose(tag: String, msg: String) {
        if (currentLogLevel.ordinal == LogLevel.VERBOSE.ordinal) Log.v(tag, msg)
    }

    override fun warn(tag: String, msg: String) {
        if (currentLogLevel.ordinal >= LogLevel.WARN.ordinal) Log.w(tag, msg)
    }
}
