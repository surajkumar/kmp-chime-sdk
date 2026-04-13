package com.wannaverse.chimesdk

data class MeetingInformation(
    val externalMeetingId: String = "778889999",
    val meetingId: String = "4476b83b-c8ae-4b96-a531-7c19e2473049",
    val audioHostURL: String = "a2cf2907ba19f464aca87e71e26f8239.k.m3.ew2.app.chime.aws:3478",
    val audioFallbackURL: String = "wss://wss.k.m3.ew2.app.chime.aws:443/calls/4476b83b-c8ae-4b96-a531-7c19e2473049",
    val turnControlURL: String = "https://3049.cell.eu-west-2.meetings.chime.aws/v2/turn_sessions",
    val signalingURL: String = "wss://signal.m3.ew2.app.chime.aws/control/4476b83b-c8ae-4b96-a531-7c19e2473049",
    val ingestionURL: String = "https://data.svc.ew2.ingest.chime.aws/v1/client-events",
    val attendeeId: String = "972a73c1-fd28-7a80-20a6-d313b015f4a5",
    val externalUserId: String = "778889999",
    val joinToken: String = "OTcyYTczYzEtZmQyOC03YTgwLTIwYTYtZDMxM2IwMTVmNGE1OmM0ZWY3ZjNlLTExZTgtNGZhZS04ZWViLWI1ZGQ4NzlhMjA2ZA"
)
