package com.brendan.controlanything.domain.model

/** A typed control/output value. Its wire form is the plain-text primitive the device parses. */
sealed class TopicValue {
    abstract fun toWire(): String

    data class Bool(val value: Boolean) : TopicValue() {
        override fun toWire(): String = value.toString()
    }

    data class Number(val value: Float) : TopicValue() {
        override fun toWire(): String = value.toString()
    }
}
