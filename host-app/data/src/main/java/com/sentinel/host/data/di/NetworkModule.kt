package com.sentinel.host.data.di

import android.content.Context
import com.sentinel.host.data.audio.AndroidAudioRecorder
import com.sentinel.host.data.audio.NativeOpusEncoder
import com.sentinel.host.data.location.FusedLocationProviderImpl
import com.sentinel.host.data.remote.AndroidNetworkObserver
import com.sentinel.host.data.remote.ReconnectPolicy
import com.sentinel.host.data.remote.SequenceGenerator
import com.sentinel.host.data.remote.protocol.MessageSerializer
import com.sentinel.host.data.remote.websocket.WebSocketDataSource
import com.sentinel.host.data.repository.AuthRepositoryImpl
import com.sentinel.host.data.repository.ConnectionRepositoryImpl
import com.sentinel.host.data.repository.DeviceRepositoryImpl
import com.sentinel.host.data.repository.FileRepositoryImpl
import com.sentinel.host.data.repository.LocationRepositoryImpl
import com.sentinel.host.data.session.SessionManagerImpl
import com.sentinel.host.domain.audio.AudioRecorder
import com.sentinel.host.domain.audio.OpusEncoder
import com.sentinel.host.domain.location.LocationProvider
import com.sentinel.host.domain.network.NetworkObserver
import com.sentinel.host.domain.repository.AuthRepository
import com.sentinel.host.domain.repository.ConnectionRepository
import com.sentinel.host.domain.repository.DeviceRepository
import com.sentinel.host.domain.repository.FileRepository
import com.sentinel.host.domain.repository.LocationRepository
import com.sentinel.host.domain.session.SessionManager
import com.sentinel.host.domain.usecase.ConnectUseCase
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder().build()
    }

    @Provides
    @Singleton
    fun provideWebSocketDataSource(client: OkHttpClient): WebSocketDataSource {
        return WebSocketDataSource(client)
    }

    @Provides
    @Singleton
    fun provideMessageSerializer(moshi: Moshi): MessageSerializer {
        return MessageSerializer(moshi)
    }

    @Provides
    @Singleton
    fun provideSequenceGenerator(): SequenceGenerator {
        return SequenceGenerator()
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManagerImpl(context)
    }

    @Provides
    @Singleton
    fun provideConnectionRepository(
        webSocketDataSource: WebSocketDataSource,
        messageSerializer: MessageSerializer,
        @ApplicationScope scope: CoroutineScope
    ): ConnectionRepository {
        return ConnectionRepositoryImpl(webSocketDataSource, messageSerializer, scope)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        connectionRepository: ConnectionRepository,
        messageSerializer: MessageSerializer,
        sequenceGenerator: SequenceGenerator
    ): AuthRepository {
        return AuthRepositoryImpl(connectionRepository, messageSerializer, sequenceGenerator)
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(
        connectionRepository: ConnectionRepository,
        messageSerializer: MessageSerializer,
        sequenceGenerator: SequenceGenerator,
        sessionManager: SessionManager
    ): DeviceRepository {
        return DeviceRepositoryImpl(
            connectionRepository, messageSerializer, sequenceGenerator,
            sessionManager,
            appVersion = "1.0.0"
        )
    }

    @Provides
    @Singleton
    fun provideConnectUseCase(
        connectionRepository: ConnectionRepository,
        authRepository: AuthRepository,
        deviceRepository: DeviceRepository,
        sessionManager: SessionManager
    ): ConnectUseCase {
        return ConnectUseCase(connectionRepository, authRepository, deviceRepository, sessionManager)
    }

    @Provides
    @Singleton
    fun provideNetworkObserver(@ApplicationContext context: Context): NetworkObserver {
        return AndroidNetworkObserver(context)
    }

    @Provides
    @Singleton
    fun provideReconnectPolicy(): ReconnectPolicy {
        return ReconnectPolicy()
    }

    @Provides
    @Singleton
    fun provideLocationProvider(@ApplicationContext context: Context): LocationProvider {
        return FusedLocationProviderImpl(context)
    }

    @Provides
    @Singleton
    fun provideLocationRepository(
        connectionRepository: ConnectionRepository,
        messageSerializer: MessageSerializer,
        sequenceGenerator: SequenceGenerator
    ): LocationRepository {
        return LocationRepositoryImpl(connectionRepository, messageSerializer, sequenceGenerator)
    }

    @Provides
    @Singleton
    fun provideFileRepository(): FileRepository {
        return FileRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideAudioRecorder(): AudioRecorder {
        return AndroidAudioRecorder()
    }

    @Provides
    @Singleton
    fun provideOpusEncoder(): OpusEncoder {
        val encoder = NativeOpusEncoder()
        encoder.initialize()
        return encoder
    }
}
