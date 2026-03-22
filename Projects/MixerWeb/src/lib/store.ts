"use client";

import {
  BrowserAudioDeck,
  BrowserMicrophoneTempoAnalyzer,
  detectBrowserAudioCapabilities,
  detectTempoFromBuffer,
  generateWaveformSamples,
  type AudioCapabilityReport
} from "@mixer/audio-core";
import {
  type BPMResult,
  type DeckId,
  type DeckRuntimeState,
  createDeckRuntimeState
} from "@mixer/domain";
import { create } from "zustand";

type MixerStore = {
  capabilities: AudioCapabilityReport;
  hydrated: boolean;
  decks: Record<DeckId, DeckRuntimeState>;
  microphoneAnalyzer: BrowserMicrophoneTempoAnalyzer | null;
  microphoneState: {
    bpmText: string;
    isRunning: boolean;
    status: string;
  };
  hydrate: () => void;
  syncDeck: (deckId: DeckId) => void;
  loadTrack: (deckId: DeckId, file: File) => Promise<void>;
  toggleDeckPlayback: (deckId: DeckId) => Promise<void>;
  stopDeck: (deckId: DeckId) => Promise<void>;
  seekDeckNormalized: (deckId: DeckId, progress: number) => Promise<void>;
  scratchDeckNormalized: (deckId: DeckId, progress: number) => Promise<void>;
  stopScratching: (deckId: DeckId) => void;
  setDeckRate: (deckId: DeckId, rate: number) => Promise<void>;
  setDeckVolume: (deckId: DeckId, volume: number) => Promise<void>;
  setDeckPan: (deckId: DeckId, pan: number) => Promise<void>;
  toggleMicrophone: () => Promise<void>;
};

function makeInitialDecks(): Record<DeckId, DeckRuntimeState> {
  return {
    left: createDeckRuntimeState("left"),
    right: createDeckRuntimeState("right")
  };
}

function formatBpm(result: BPMResult | null): string {
  if (!result) {
    return "-- BPM";
  }

  if (result.kind === "detected") {
    return `${result.bpm.toFixed(1)} BPM`;
  }

  return "-- BPM";
}

export const useMixerStore = create<MixerStore>((set, get) => ({
  capabilities: {
    supported: false,
    microphoneSupported: false,
    message: "Waiting for client runtime."
  },
  hydrated: false,
  decks: makeInitialDecks(),
  microphoneAnalyzer: null,
  microphoneState: {
    bpmText: "-- BPM",
    isRunning: false,
    status: "Microphone BPM stopped"
  },

  hydrate: () => {
    if (get().hydrated || typeof window === "undefined") {
      return;
    }

    const capabilities = detectBrowserAudioCapabilities();
    set((state) => ({
      capabilities,
      hydrated: true,
      decks: {
        left: {
          ...state.decks.left,
          engine: capabilities.supported ? new BrowserAudioDeck() : null,
          statusMessage: capabilities.message
        },
        right: {
          ...state.decks.right,
          engine: capabilities.supported ? new BrowserAudioDeck() : null,
          statusMessage: capabilities.message
        }
      }
    }));
  },

  syncDeck: (deckId) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    const snapshot = deck.engine.getSnapshot();
    set((state) => ({
      decks: {
        ...state.decks,
        [deckId]: {
          ...state.decks[deckId],
          currentTime: snapshot.currentTime,
          currentTimeText: snapshot.currentTimeText,
          duration: snapshot.duration,
          durationText: snapshot.durationText,
          progress: snapshot.progress,
          platterDegrees: snapshot.platterDegrees,
          isLoaded: snapshot.isLoaded,
          isPlaying: snapshot.isPlaying,
          rate: snapshot.rate,
          volume: snapshot.volume,
          pan: snapshot.pan
        }
      }
    }));
  },

  loadTrack: async (deckId, file) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    set((state) => ({
      decks: {
        ...state.decks,
        [deckId]: {
          ...state.decks[deckId],
          isAnalyzing: true,
          statusMessage: "Importing and analyzing track...",
          trackName: file.name
        }
      }
    }));

    const buffer = await deck.engine.loadFile(file);
    const waveform = generateWaveformSamples(buffer, 220);
    const bpmResult = detectTempoFromBuffer(buffer);
    const snapshot = deck.engine.getSnapshot();

    set((state) => ({
      decks: {
        ...state.decks,
        [deckId]: {
          ...state.decks[deckId],
          waveform,
          bpmResult,
          bpmText: formatBpm(bpmResult),
          isAnalyzing: false,
          statusMessage:
            bpmResult.kind === "detected"
              ? "Track ready for practice."
              : "Track loaded. BPM detector could not lock confidently.",
          currentTime: snapshot.currentTime,
          currentTimeText: snapshot.currentTimeText,
          duration: snapshot.duration,
          durationText: snapshot.durationText,
          progress: snapshot.progress,
          isLoaded: snapshot.isLoaded
        }
      }
    }));
  },

  toggleDeckPlayback: async (deckId) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    if (deck.engine.getSnapshot().isPlaying) {
      deck.engine.pause();
    } else {
      await deck.engine.play();
    }
    get().syncDeck(deckId);
  },

  stopDeck: async (deckId) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    deck.engine.pause();
    await deck.engine.seek(0);
    get().syncDeck(deckId);
  },

  seekDeckNormalized: async (deckId, progress) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    const snapshot = deck.engine.getSnapshot();
    await deck.engine.seek(snapshot.duration * progress);
    get().syncDeck(deckId);
  },

  scratchDeckNormalized: async (deckId, progress) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    const snapshot = deck.engine.getSnapshot();
    await deck.engine.scratch(snapshot.duration * progress);
    get().syncDeck(deckId);
  },

  stopScratching: (deckId) => {
    get().syncDeck(deckId);
  },

  setDeckRate: async (deckId, rate) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    deck.engine.setPlaybackRate(rate);
    get().syncDeck(deckId);
  },

  setDeckVolume: async (deckId, volume) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    deck.engine.setVolume(volume);
    get().syncDeck(deckId);
  },

  setDeckPan: async (deckId, pan) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    deck.engine.setPan(pan);
    get().syncDeck(deckId);
  },

  toggleMicrophone: async () => {
    const { microphoneAnalyzer, microphoneState, capabilities } = get();

    if (!capabilities.microphoneSupported) {
      set({
        microphoneState: {
          bpmText: "-- BPM",
          isRunning: false,
          status: "Microphone capture is not available in this browser."
        }
      });
      return;
    }

    if (microphoneAnalyzer && microphoneState.isRunning) {
      microphoneAnalyzer.stop();
      set({
        microphoneAnalyzer: null,
        microphoneState: {
          bpmText: "-- BPM",
          isRunning: false,
          status: "Microphone BPM stopped"
        }
      });
      return;
    }

    const analyzer = new BrowserMicrophoneTempoAnalyzer((result) => {
      set({
        microphoneState: {
          bpmText: formatBpm(result),
          isRunning: true,
          status:
            result.kind === "detected"
              ? `Listening live • confidence ${(result.confidence * 100).toFixed(0)}%`
              : "Listening live • insufficient confidence"
        }
      });
    });

    set({
      microphoneAnalyzer: analyzer,
      microphoneState: {
        bpmText: "-- BPM",
        isRunning: true,
        status: "Requesting microphone access..."
      }
    });

    try {
      await analyzer.start();
    } catch (error) {
      set({
        microphoneAnalyzer: null,
        microphoneState: {
          bpmText: "-- BPM",
          isRunning: false,
          status:
            error instanceof Error
              ? error.message
              : "Unable to start microphone capture."
        }
      });
    }
  }
}));
