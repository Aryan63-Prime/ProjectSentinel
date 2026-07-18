package com.sentinel.host.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.host.domain.model.ConnectionState
import com.sentinel.host.domain.session.SessionManager
import com.sentinel.host.domain.usecase.ConnectUseCase
import com.sentinel.host.service.ConnectionSupervisor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val connectUseCase: ConnectUseCase,
    private val connectionSupervisor: ConnectionSupervisor,
    private val sessionManager: SessionManager
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = connectionSupervisor.state

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    init {
        // Auto-fill saved session if available
        sessionManager.getServerUrl()?.let { _serverUrl.value = it }
        sessionManager.getToken()?.let { _token.value = it }
    }

    fun onServerUrlChanged(url: String) {
        _serverUrl.value = url
        _errorMessage.value = null
    }

    fun onTokenChanged(newToken: String) {
        _token.value = newToken
        _errorMessage.value = null
    }

    fun connect() {
        val url = _serverUrl.value.trim()
        val jwt = _token.value.trim()

        if (url.isBlank()) {
            _errorMessage.value = "Server URL is required"
            return
        }
        if (jwt.isBlank()) {
            _errorMessage.value = "JWT token is required"
            return
        }

        _isConnecting.value = true
        _errorMessage.value = null

        // Start supervisor to observe events
        connectionSupervisor.start()

        viewModelScope.launch {
            val result = connectUseCase.execute(url, jwt)
            _isConnecting.value = false

            result.onFailure { error ->
                _errorMessage.value = error.message ?: "Connection failed"
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionSupervisor.stop()
        }
    }
}
