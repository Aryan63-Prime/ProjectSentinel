package com.sentinel.admin.service.files

import com.sentinel.admin.data.remote.protocol.MessageSerializer
import com.sentinel.admin.domain.model.ConnectionEvent
import com.sentinel.admin.domain.model.ConnectionState
import com.sentinel.admin.domain.repository.ConnectionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class FileDownloadManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testScope: TestScope
    private lateinit var fakeConnRepo: FakeConnectionRepository
    private lateinit var downloadDir: File
    private lateinit var downloadManager: FileDownloadManager
    private val serializer = MessageSerializer()

    @Before
    fun setUp() {
        testScope = TestScope()
        downloadDir = tempFolder.newFolder("downloads")
        fakeConnRepo = FakeConnectionRepository()

        downloadManager = FileDownloadManager(
            connectionRepository = fakeConnRepo,
            messageSerializer = serializer,
            scope = testScope,
            downloadDir = downloadDir,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )
    }

    @Test
    fun `fresh download requests from offset 0 and completes when chunks received`() = testScope.runTest {
        val deviceId = "HOST-0001"
        val remotePath = "/sdcard/test.txt"
        val content = "Sentinel resilient file download verification payload."
        val contentBytes = content.toByteArray()
        val expectedSha256 = sha256(contentBytes)

        downloadManager.startDownload(deviceId, remotePath)
        testScope.advanceUntilIdle()

        // 1. Verify download request sent with offset 0
        assertEquals(1, fakeConnRepo.sentTexts.size)
        val reqJson = fakeConnRepo.sentTexts[0]
        assertTrue(reqJson.contains("\"offset\":0"))

        // Extract sequence from request
        val seqRegex = "\"sequence\":(\\d+)".toRegex()
        val match = seqRegex.find(reqJson)!!
        val sequence = match.groupValues[1].toLong()
        val transferId = sequence.toInt()

        // 2. Host responds with FILE_DOWNLOAD_RES
        val resJson = """
            {
                "type": "FILE_DOWNLOAD_RES",
                "version": 1,
                "timestamp": ${System.currentTimeMillis()},
                "sequence": $sequence,
                "data": {
                    "path": "$remotePath",
                    "success": true,
                    "size": ${contentBytes.size},
                    "sha256": "$expectedSha256"
                }
            }
        """.trimIndent()
        fakeConnRepo.emitEvent(ConnectionEvent.FileDownloadReceived(resJson))
        testScope.advanceUntilIdle()

        // 3. Host sends binary chunk
        val chunkPacket = buildChunkPacket(transferId, 0, contentBytes)
        fakeConnRepo.emitEvent(ConnectionEvent.FileChunkReceived(chunkPacket))
        testScope.advanceUntilIdle()

        // 4. Verify completed state and file contents
        val state = downloadManager.state.value
        assertTrue("State should be Completed, was: $state", state is FileDownloadManager.DownloadState.Completed)
        val completedFile = (state as FileDownloadManager.DownloadState.Completed).file
        assertTrue("Completed file must exist", completedFile.exists())
        assertEquals(content, completedFile.readText())

        // Ensure temporary .part file was cleaned up
        val partFile = File(downloadDir, "test.txt.part")
        assertFalse(".part file should be renamed/removed", partFile.exists())
    }

    @Test
    fun `resumes from existing partial part file offset`() = testScope.runTest {
        val deviceId = "HOST-0001"
        val remotePath = "/sdcard/movie.mp4"
        val part1 = "Initial downloaded bytes portion."
        val part2 = " Remaining downloaded bytes after resume."
        val fullContent = part1 + part2
        val fullBytes = fullContent.toByteArray()
        val fullSha256 = sha256(fullBytes)

        // Simulate interrupted download: .part file already has part1
        val partFile = File(downloadDir, "movie.mp4.part")
        partFile.writeBytes(part1.toByteArray())
        val existingOffset = partFile.length()

        downloadManager.startDownload(deviceId, remotePath)
        testScope.advanceUntilIdle()

        // Verify request offset matches partial file length
        assertEquals(1, fakeConnRepo.sentTexts.size)
        val reqJson = fakeConnRepo.sentTexts[0]
        assertTrue("Request must seek to offset $existingOffset: $reqJson", reqJson.contains("\"offset\":$existingOffset"))

        val seqRegex = "\"sequence\":(\\d+)".toRegex()
        val match = seqRegex.find(reqJson)!!
        val sequence = match.groupValues[1].toLong()
        val transferId = sequence.toInt()

        // Host metadata response
        val resJson = """
            {
                "type": "FILE_DOWNLOAD_RES",
                "version": 1,
                "timestamp": ${System.currentTimeMillis()},
                "sequence": $sequence,
                "data": {
                    "path": "$remotePath",
                    "success": true,
                    "size": ${fullBytes.size},
                    "sha256": "$fullSha256"
                }
            }
        """.trimIndent()
        fakeConnRepo.emitEvent(ConnectionEvent.FileDownloadReceived(resJson))
        testScope.advanceUntilIdle()

        // Host sends only the remaining chunk
        val remainingBytes = part2.toByteArray()
        val chunkPacket = buildChunkPacket(transferId, 1, remainingBytes)
        fakeConnRepo.emitEvent(ConnectionEvent.FileChunkReceived(chunkPacket))
        testScope.advanceUntilIdle()

        // Verify completed state and file assembly
        val state = downloadManager.state.value
        assertTrue("State should be Completed, was: $state", state is FileDownloadManager.DownloadState.Completed)
        val completedFile = (state as FileDownloadManager.DownloadState.Completed).file
        assertEquals(fullContent, completedFile.readText())
    }

    @Test
    fun `checksum mismatch emits Error and removes corrupted file`() = testScope.runTest {
        val deviceId = "HOST-0001"
        val remotePath = "/sdcard/secure.doc"
        val corruptedBytes = "Tampered or corrupted binary content".toByteArray()
        val expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

        downloadManager.startDownload(deviceId, remotePath)
        testScope.advanceUntilIdle()

        val reqJson = fakeConnRepo.sentTexts[0]
        val seqRegex = "\"sequence\":(\\d+)".toRegex()
        val match = seqRegex.find(reqJson)!!
        val sequence = match.groupValues[1].toLong()
        val transferId = sequence.toInt()

        val resJson = """
            {
                "type": "FILE_DOWNLOAD_RES",
                "version": 1,
                "timestamp": ${System.currentTimeMillis()},
                "sequence": $sequence,
                "data": {
                    "path": "$remotePath",
                    "success": true,
                    "size": ${corruptedBytes.size},
                    "sha256": "$expectedSha256"
                }
            }
        """.trimIndent()
        fakeConnRepo.emitEvent(ConnectionEvent.FileDownloadReceived(resJson))
        testScope.advanceUntilIdle()

        val chunkPacket = buildChunkPacket(transferId, 0, corruptedBytes)
        fakeConnRepo.emitEvent(ConnectionEvent.FileChunkReceived(chunkPacket))
        testScope.advanceUntilIdle()

        val state = downloadManager.state.value
        assertTrue("State should be Error on mismatch, was: $state", state is FileDownloadManager.DownloadState.Error)
        val finalFile = File(downloadDir, "secure.doc")
        val partFile = File(downloadDir, "secure.doc.part")
        assertFalse("Corrupted file must not be kept", finalFile.exists())
        assertFalse("Corrupted part file must be deleted", partFile.exists())
    }

    // ================================================================
    // Helper Functions
    // ================================================================

    private fun buildChunkPacket(transferId: Int, chunkSeq: Int, payload: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(9 + payload.size)
        bb.put(com.sentinel.shared.protocol.PacketType.FILE_CHUNK)
        bb.putInt(transferId)
        bb.putInt(chunkSeq)
        bb.put(payload)
        return bb.array()
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private class FakeConnectionRepository : ConnectionRepository {
        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Ready)
        override val state: StateFlow<ConnectionState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
        override val events: SharedFlow<ConnectionEvent> = _events.asSharedFlow()

        val sentTexts = mutableListOf<String>()

        fun emitEvent(event: ConnectionEvent) {
            _events.tryEmit(event)
        }

        override suspend fun connect(serverUrl: String) {}
        override suspend fun disconnect() {}
        override fun sendText(text: String): Boolean {
            sentTexts.add(text)
            return true
        }
        override fun sendBinary(data: ByteArray): Boolean = true
    }
}
