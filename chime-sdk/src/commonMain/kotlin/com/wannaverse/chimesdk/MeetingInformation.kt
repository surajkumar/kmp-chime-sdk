package com.wannaverse.chimesdk

data class MeetingInformation(
    val externalMeetingId: String = "",
    val meetingId: String = "",
    val audioHostURL: String = "",
    val audioFallbackURL: String = "",
    val turnControlURL: String = "",
    val signalingURL: String = "",
    val ingestionURL: String = "",
    val attendeeId: String = "",
    val externalUserId: String = "",
    val joinToken: String = ""
)
