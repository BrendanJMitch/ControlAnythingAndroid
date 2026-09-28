package com.brendan.controlanything.data.pubsub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireCodecTest {

    @Test
    fun `encodes topic and value separated by a colon`() {
        assertEquals("controls/red_led:true", WireCodec.encode(TopicMessage("controls/red_led", "true")))
    }

    @Test
    fun `decodes a primitive message`() {
        assertEquals(TopicMessage("outputs/battery_voltage", "7.21"), WireCodec.decode("outputs/battery_voltage:7.21"))
    }

    @Test
    fun `splits on the first colon so values may contain colons`() {
        val decoded = WireCodec.decode("""info:{"device_name":"Rover","project_id":"p"}""")
        assertEquals(TopicMessage("info", """{"device_name":"Rover","project_id":"p"}"""), decoded)
    }

    @Test
    fun `an empty value is still a message`() {
        assertEquals(TopicMessage("log/info", ""), WireCodec.decode("log/info:"))
    }

    @Test
    fun `frames without a colon or with an empty topic are rejected`() {
        assertNull(WireCodec.decode("no delimiter here"))
        assertNull(WireCodec.decode(":value"))
        assertNull(WireCodec.decode(""))
    }
}
