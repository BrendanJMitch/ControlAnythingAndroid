package com.brendan.controlanything.data.pubsub

/** One message on the wire. [topic] is the full topic (e.g. "outputs/battery_voltage"), never just a leaf. */
data class TopicMessage(
    val topic: String,
    val value: String,
)

/**
 * The text framing shared by the device and the app: `topic:value`, split on the *first* colon so
 * values may themselves contain colons (the `info` JSON always does). Topics therefore can't.
 */
object WireCodec {

    fun encode(message: TopicMessage): String = "${message.topic}:${message.value}"

    /** Malformed frames (no colon, or an empty topic) yield null and are dropped by the caller. */
    fun decode(frame: String): TopicMessage? {
        val delimiter = frame.indexOf(':')
        if (delimiter <= 0) return null
        return TopicMessage(frame.substring(0, delimiter), frame.substring(delimiter + 1))
    }
}
