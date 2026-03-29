package dev.manelix.mixer.feature.deck.model

data class DeckScreenState(
    val isInitializedForDevice: Boolean = false,
    val areControlsVisible: Boolean = false,
    val isRightDeckVisible: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val isEqualizerVisible: Boolean = false,
    val root: DeckUiState = DeckUiState(),
    val leftDeck: TurntableDeckUiState = TurntableDeckUiState(),
    val rightDeck: TurntableDeckUiState = TurntableDeckUiState(),
)
