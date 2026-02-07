package com.sidekick.models

/**
 * Represents the current connection state to an LLM server.
 *
 * Used by the UI to show connection health in the status indicator.
 * Provider-agnostic — works for Ollama, LM Studio, or any backend.
 */
enum class ConnectionStatus {
    /**
     * No server URL has been configured in settings.
     */
    NOT_CONFIGURED,

    /**
     * Currently attempting to connect to the server.
     */
    CONNECTING,

    /**
     * Successfully connected and server is responding.
     */
    CONNECTED,

    /**
     * Connection failed or server is not responding.
     */
    DISCONNECTED
}
