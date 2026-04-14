package com.wannaverse.chimesdk

data class TextMessage(
    val topic: String,
    val senderId: String,
    val content: String,
    val timestamp: Long
)