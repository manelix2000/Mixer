package dev.manelix.mixer.core.audio.model

sealed interface AudioEngineError {
    data class StartFailed(val reason: String) : AudioEngineError
    data class FileLoadFailed(val reason: String) : AudioEngineError
    data object NoFileLoaded : AudioEngineError
    data object ScratchNotActive : AudioEngineError
    data object MicrophoneUnavailable : AudioEngineError
    data object MicrophonePermissionDenied : AudioEngineError
    data class SessionConfigurationFailed(val reason: String) : AudioEngineError
}
