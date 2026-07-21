package com.sentinel.admin.service.di

import com.sentinel.admin.data.audio.AudioOutput
import com.sentinel.admin.data.audio.AudioTrackOutput
import com.sentinel.admin.data.audio.NativeOpusDecoder
import com.sentinel.admin.data.remote.ReconnectPolicy
import com.sentinel.admin.data.remote.SequenceGenerator
import com.sentinel.admin.data.remote.protocol.MessageSerializer
import com.sentinel.admin.domain.repository.AuthRepository
import com.sentinel.admin.domain.repository.ConnectionRepository
import com.sentinel.admin.domain.supervisor.ConnectionSupervisor
import com.sentinel.admin.domain.time.Clock
import com.sentinel.admin.domain.time.SystemClock
import com.sentinel.admin.service.AdminSupervisor
import com.sentinel.admin.service.AudioMonitor
import com.sentinel.admin.service.HeartbeatScheduler
import com.sentinel.admin.service.files.FileDownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.os.Environment
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import java.io.File
import javax.inject.Singleton

/**
 * Hilt module providing service-layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideClock(): Clock {
        return SystemClock()
    }

    @Provides
    @Singleton
    fun provideHeartbeatScheduler(
        connectionRepository: ConnectionRepository,
        messageSerializer: MessageSerializer,
        sequenceGenerator: SequenceGenerator,
        scope: CoroutineScope,
        clock: Clock
    ): HeartbeatScheduler {
        return HeartbeatScheduler(
            connectionRepository = connectionRepository,
            messageSerializer = messageSerializer,
            sequenceGenerator = sequenceGenerator,
            scope = scope,
            clock = clock
        )
    }

    @Provides
    @Singleton
    fun provideNativeOpusDecoder(): NativeOpusDecoder {
        return NativeOpusDecoder()
    }

    @Provides
    @Singleton
    fun provideAudioOutput(): AudioOutput {
        return AudioTrackOutput()
    }

    @Provides
    @Singleton
    fun provideAudioMonitor(
        decoder: NativeOpusDecoder,
        audioOutput: AudioOutput,
        scope: CoroutineScope
    ): AudioMonitor {
        return AudioMonitor(
            decoder = decoder,
            audioOutput = audioOutput,
            scope = scope
        )
    }

    @Provides
    @Singleton
    fun provideFileDownloadManager(
        connectionRepository: ConnectionRepository,
        messageSerializer: MessageSerializer,
        scope: CoroutineScope,
        @ApplicationContext context: Context
    ): FileDownloadManager {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        
        return FileDownloadManager(
            connectionRepository = connectionRepository,
            messageSerializer = messageSerializer,
            scope = scope,
            downloadDir = downloadDir
        )
    }

    @Provides
    @Singleton
    fun provideAdminSupervisor(
        connectionRepository: ConnectionRepository,
        authRepository: AuthRepository,
        heartbeatScheduler: HeartbeatScheduler,
        messageSerializer: MessageSerializer,
        sequenceGenerator: SequenceGenerator,
        reconnectPolicy: ReconnectPolicy,
        scope: CoroutineScope,
        clock: Clock,
        audioMonitor: AudioMonitor
    ): ConnectionSupervisor {
        return AdminSupervisor(
            connectionRepository = connectionRepository,
            authRepository = authRepository,
            heartbeatScheduler = heartbeatScheduler,
            messageSerializer = messageSerializer,
            sequenceGenerator = sequenceGenerator,
            reconnectPolicy = reconnectPolicy,
            scope = scope,
            clock = clock,
            audioMonitor = audioMonitor
        )
    }
}
