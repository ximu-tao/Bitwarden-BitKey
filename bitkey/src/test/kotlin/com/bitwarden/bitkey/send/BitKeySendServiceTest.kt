package com.bitwarden.bitkey.send

import com.bitwarden.bitkey.connection.FakeBitKeyConnectionManager
import com.bitwarden.bitkey.model.BitKeyAck
import com.bitwarden.bitkey.protocol.BitKeyCommands
import com.bitwarden.bitkey.protocol.BitKeyError
import com.bitwarden.bitkey.protocol.BitKeyFlags
import com.bitwarden.bitkey.protocol.BitKeyFrame
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class BitKeySendServiceTest {

    private val deviceAddress = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `sendPassword returns EmptyText for an empty string`() = runTest {
        val manager = FakeBitKeyConnectionManager()
        val service = BitKeySendService(manager)

        val result = service.sendPassword(deviceAddress, "")

        assertEquals(BitKeySendResult.EmptyText, result)
        assertEquals(0, manager.sentFrames().size)
    }

    @Test
    fun `sendPassword returns UnsupportedCharacters when the text contains non-ASCII`() = runTest {
        val manager = FakeBitKeyConnectionManager()
        val service = BitKeySendService(manager)

        val result = service.sendPassword(deviceAddress, "密码123")

        assertEquals(BitKeySendResult.UnsupportedCharacters, result)
        assertEquals(0, manager.sentFrames().size)
    }

    @Test
    fun `sendPassword reports ConnectionFailed when the adapter cannot connect`() = runTest {
        val manager = FakeBitKeyConnectionManager().apply { connectShouldFail = true }
        val service = BitKeySendService(manager)

        val result = service.sendPassword(deviceAddress, "P@ssw0rd!")

        assertTrue(result is BitKeySendResult.ConnectionFailed)
    }

    @Test
    fun `sendPassword orchestrates session start type and session end on success`() = runTest {
        val manager = FakeBitKeyConnectionManager()
        val service = BitKeySendService(manager)

        val result = service.sendPassword(deviceAddress, "P@ssw0rd!")

        assertEquals(BitKeySendResult.Success, result)

        val sent = manager.sentFrames()
        // Session start, one type_text fragment, session end.
        assertEquals(3, sent.size)
        assertEquals(BitKeyCommands.CMD_SESSION_START, sent[0].cmd)
        assertEquals(BitKeyCommands.CMD_TYPE_TEXT, sent[1].cmd)
        assertEquals("P@ssw0rd!".toByteArray(Charsets.UTF_8).toList(), sent[1].payload.toList())
        assertEquals(BitKeyCommands.CMD_SESSION_END, sent[2].cmd)
    }

    @Test
    fun `sendPassword fragments long payloads across multiple type_text frames`() = runTest {
        val manager = FakeBitKeyConnectionManager()
        val service = BitKeySendService(manager)
        val longText = "a".repeat(com.bitwarden.bitkey.protocol.BitKeyFragmenter.MAX_CHUNK + 16)

        val result = service.sendPassword(deviceAddress, longText)

        assertEquals(BitKeySendResult.Success, result)
        val sent = manager.sentFrames()
        val typeFrames = sent.filter { it.cmd == BitKeyCommands.CMD_TYPE_TEXT }
        assertEquals(2, typeFrames.size)
        assertTrue(BitKeyFlags.has(typeFrames.first().flags, BitKeyFlags.FLAG_START))
        assertTrue(BitKeyFlags.has(typeFrames.last().flags, BitKeyFlags.FLAG_END))
    }

    @Test
    fun `sendPassword returns DeviceError when the session start is refused`() = runTest {
        val manager = FakeBitKeyConnectionManager().apply {
            enqueueAck(Result.success(BitKeyAck(seq = 0x10, error = BitKeyError.BadSession, extra = ByteArray(0))))
        }
        val service = BitKeySendService(manager)

        val result = service.sendPassword(deviceAddress, "P@ssw0rd!")

        assertEquals(BitKeySendResult.DeviceError(BitKeyError.BadSession), result)
    }

    @Test
    fun `sendPassword returns DeviceError when a type_text fragment is refused`() = runTest {
        val manager = FakeBitKeyConnectionManager().apply {
            enqueueAck(BitKeyAck(seq = 0x10, error = BitKeyError.None, extra = ByteArray(0)).toResult())
            enqueueAck(BitKeyAck(seq = 0x11, error = BitKeyError.BufferFull, extra = ByteArray(0)).toResult())
        }
        val service = BitKeySendService(manager)

        val result = service.sendPassword(deviceAddress, "hi")

        assertEquals(BitKeySendResult.DeviceError(BitKeyError.BufferFull), result)
    }

    @Test
    fun `sendPassword returns TimedOut when the link drops mid-send`() = runTest {
        val manager = FakeBitKeyConnectionManager().apply {
            enqueueFailure(IllegalStateException("BitKey link disconnected"))
        }
        val service = BitKeySendService(manager)

        val result = service.sendPassword(deviceAddress, "P@ssw0rd!")

        assertEquals(BitKeySendResult.TimedOut, result)
    }

    @Test
    fun `sendPassword surfaces an arbitrary connection failure as ConnectionFailed`() = runTest {
        val manager = FakeBitKeyConnectionManager().apply {
            enqueueFailure(IOException("IO error"))
        }
        val service = BitKeySendService(manager)

        val result = service.sendPassword(deviceAddress, "P@ssw0rd!")

        assertTrue(result is BitKeySendResult.ConnectionFailed)
        assertTrue((result as BitKeySendResult.ConnectionFailed).cause is IOException)
    }

    @Test
    fun `sendPassword calls disconnect at least once after a successful send`() = runTest {
        val manager = FakeBitKeyConnectionManager()
        val service = BitKeySendService(manager)

        service.sendPassword(deviceAddress, "P@ssw0rd!")

        assertTrue(manager.disconnectCount >= 1)
    }

    @Test
    fun `sendPassword increments sequence numbers across the session frames`() = runTest {
        val manager = FakeBitKeyConnectionManager()
        val service = BitKeySendService(manager)

        service.sendPassword(deviceAddress, "hi")

        val sent = manager.sentFrames()
        assertEquals(0x10, sent[0].seq)
        assertEquals(0x11, sent[1].seq)
        assertEquals(0x12, sent[2].seq)
    }
}

private fun <T> T.toResult(): Result<T> = Result.success(this)