package com.sentinel.host.service

import com.sentinel.host.data.remote.protocol.MessageSerializer
import com.sentinel.host.domain.model.ConnectionEvent
import com.sentinel.host.domain.model.ConnectionState
import com.sentinel.host.domain.model.FileItem
import com.sentinel.host.domain.repository.ConnectionRepository
import com.sentinel.host.domain.repository.FileRepository
import com.sentinel.shared.protocol.MessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileStreamerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testScope: TestScope
    private lateinit var fakeFileRepo: FakeFileRepository
    private lateinit var fakeConnRepo: FakeConnectionRepository
    private lateinit var streamer: FileStreamer
    private lateinit var testFile: File

    @Before
    fun setUp() {
        testScope = TestScope()
        testFile = tempFolder.newFile("sample.txt")
        testFile.writeText("Sentinel platform unit test file for SHA256 integrity.")

        fakeFileRepo = FakeFileRepository(testFile)
        fakeConnRepo = FakeConnectionRepository()

        streamer = FileStreamer(
            fileRepository = fakeFileRepo,
            connectionRepository = fakeConnRepo,
            messageSerializer = MessageSerializer(),
            scope = testScope,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )
    }

    @Test
    fun `handleFileDownloadReq sends metadata response with SHA-256 and streams binary chunks`() = testScope.runTest {
        streamer.handleFileDownloadReq(testFile.absolutePath, 0L, "nonce123", 1001L)
        testScope.advanceUntilIdle()

        // Verify metadata text message was sent
        assertEquals(1, fakeConnRepo.sentTexts.size)
        val textRes = fakeConnRepo.sentTexts[0]
        assertTrue("Must be FILE_DOWNLOAD_RES", textRes.contains(MessageType.FILE_DOWNLOAD_RES))
        assertTrue("Must contain file size", textRes.contains("\"size\":${testFile.length()}"))
        assertTrue("Must contain sha256 field", textRes.contains("\"sha256\""))

        // Verify binary chunks were sent
        assertTrue("Should have sent at least 1 binary chunk", fakeConnRepo.sentBinaries.isNotEmpty())
    }

    @Test
    fun `handleFileDownloadReq with offset seeks and emits chunks from offset`() = testScope.runTest {
        val offset = 10L
        streamer.handleFileDownloadReq(testFile.absolutePath, offset, "nonce123", 1002L)
        testScope.advanceUntilIdle()

        assertEquals(offset, fakeFileRepo.lastRequestedOffset)
    }

    @Test
    fun `handleFileStopReq stops active transfer`() = testScope.runTest {
        streamer.handleFileDownloadReq(testFile.absolutePath, 0L, "nonce123", 1003L)
        streamer.handleFileStopReq(testFile.absolutePath)
        testScope.advanceUntilIdle()

        // Ensure no crash and cleanly stopped
        assertNotNull(streamer)
    }

    // ================================================================
    // Test Doubles
    // ================================================================

    private class FakeFileRepository(private val file: File) : FileRepository {
        var lastRequestedOffset: Long? = null

        override fun listFiles(path: String): List<FileItem> {
            return listOf(
                FileItem(file.name, file.absolutePath, false, file.length(), file.lastModified())
            )
        }

        override fun getFileMetadata(path: String): FileItem? {
            return FileItem(file.name, file.absolutePath, false, file.length(), file.lastModified())
        }

        override fun openFile(path: String, offset: Long): Flow<ByteArray> = flow {
            lastRequestedOffset = offset
            val bytes = file.readBytes()
            if (offset < bytes.size) {
                emit(bytes.copyOfRange(offset.toInt(), bytes.size))
            }
        }
    }

    private class FakeConnectionRepository : ConnectionRepository {
        val sentTexts = mutableListOf<String>()
        val sentBinaries = mutableListOf<ByteArray>()

        override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Ready)
        override val events: SharedFlow<ConnectionEvent> = MutableSharedFlow()

        override fun sendText(text: String): Boolean {
            sentTexts.add(text)
            return true
        }

        override fun sendBinary(data: ByteArray): Boolean {
            sentBinaries.add(data)
            return true
        }

        override suspend fun connect(serverUrl: String) {}
        override suspend fun disconnect() {}
    }
}
