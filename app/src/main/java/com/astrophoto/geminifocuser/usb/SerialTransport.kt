package com.astrophoto.geminifocuser.usb

/** One-command-at-a-time byte transport; implementation details stay outside protocol code. */
interface SerialTransport {
    suspend fun open()
    suspend fun send(command: String)
    suspend fun exchange(command: String, expectedPrefix: String): String
    suspend fun close()
}
