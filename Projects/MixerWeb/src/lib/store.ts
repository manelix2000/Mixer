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
    level: number;
    peak: number;
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
  setDeckEq: (deckId: DeckId, low: number, mid: number, high: number) => Promise<void>;
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

async function extractArtworkDataUrl(file: File): Promise<string | null> {
  try {
    const header = new Uint8Array(await file.slice(0, 10).arrayBuffer());
    if (header.length < 10 || header[0] !== 0x49 || header[1] !== 0x44 || header[2] !== 0x33) {
      return null;
    }

    const versionMajor = header[3];
    if (versionMajor < 2 || versionMajor > 4) {
      return null;
    }

    const tagSize = decodeSynchsafeInteger(header, 6);
    const tagBuffer = new Uint8Array(await file.slice(10, 10 + tagSize).arrayBuffer());

    if (versionMajor === 2) {
      return extractId3v22Picture(tagBuffer);
    }

    return extractId3v23OrV24Picture(tagBuffer, versionMajor);
  } catch {
    return null;
  }
}

function decodeSynchsafeInteger(bytes: Uint8Array, offset: number): number {
  return (
    ((bytes[offset] ?? 0) << 21) |
    ((bytes[offset + 1] ?? 0) << 14) |
    ((bytes[offset + 2] ?? 0) << 7) |
    (bytes[offset + 3] ?? 0)
  );
}

function decodeBigEndianInteger(bytes: Uint8Array, offset: number): number {
  return (
    ((bytes[offset] ?? 0) << 24) |
    ((bytes[offset + 1] ?? 0) << 16) |
    ((bytes[offset + 2] ?? 0) << 8) |
    (bytes[offset + 3] ?? 0)
  );
}

function extractId3v23OrV24Picture(tagData: Uint8Array, versionMajor: number): string | null {
  let offset = 0;
  while (offset + 10 <= tagData.length) {
    const id = String.fromCharCode(
      tagData[offset] ?? 0,
      tagData[offset + 1] ?? 0,
      tagData[offset + 2] ?? 0,
      tagData[offset + 3] ?? 0
    );
    if (!id.trim()) {
      break;
    }

    const frameSize =
      versionMajor === 4
        ? decodeSynchsafeInteger(tagData, offset + 4)
        : decodeBigEndianInteger(tagData, offset + 4);
    if (frameSize <= 0) {
      break;
    }

    const frameStart = offset + 10;
    const frameEnd = frameStart + frameSize;
    if (frameEnd > tagData.length) {
      break;
    }

    if (id === "APIC") {
      return parseApicFrame(tagData.subarray(frameStart, frameEnd));
    }

    offset = frameEnd;
  }

  return null;
}

function extractId3v22Picture(tagData: Uint8Array): string | null {
  let offset = 0;
  while (offset + 6 <= tagData.length) {
    const id = String.fromCharCode(
      tagData[offset] ?? 0,
      tagData[offset + 1] ?? 0,
      tagData[offset + 2] ?? 0
    );
    if (!id.trim()) {
      break;
    }

    const frameSize =
      ((tagData[offset + 3] ?? 0) << 16) |
      ((tagData[offset + 4] ?? 0) << 8) |
      (tagData[offset + 5] ?? 0);
    if (frameSize <= 0) {
      break;
    }

    const frameStart = offset + 6;
    const frameEnd = frameStart + frameSize;
    if (frameEnd > tagData.length) {
      break;
    }

    if (id === "PIC") {
      return parsePicFrame(tagData.subarray(frameStart, frameEnd));
    }

    offset = frameEnd;
  }

  return null;
}

function parseApicFrame(frameData: Uint8Array): string | null {
  if (frameData.length < 4) {
    return null;
  }

  const encoding = frameData[0] ?? 0;
  let index = 1;
  let mimeEnd = index;
  while (mimeEnd < frameData.length && frameData[mimeEnd] !== 0) {
    mimeEnd += 1;
  }
  if (mimeEnd >= frameData.length) {
    return null;
  }

  const mimeType = new TextDecoder("latin1").decode(frameData.subarray(index, mimeEnd)) || "image/jpeg";
  index = mimeEnd + 1;
  if (index >= frameData.length) {
    return null;
  }

  index += 1; // picture type
  index = skipId3Description(frameData, index, encoding);
  if (index >= frameData.length) {
    return null;
  }

  const imageData = frameData.subarray(index);
  if (imageData.length === 0) {
    return null;
  }

  return URL.createObjectURL(new Blob([imageData], { type: mimeType }));
}

function parsePicFrame(frameData: Uint8Array): string | null {
  if (frameData.length < 6) {
    return null;
  }

  const encoding = frameData[0] ?? 0;
  const format = new TextDecoder("latin1").decode(frameData.subarray(1, 4)).toLowerCase();
  const mimeType =
    format === "png" ? "image/png" : format === "gif" ? "image/gif" : "image/jpeg";

  let index = 4;
  index += 1; // picture type
  index = skipId3Description(frameData, index, encoding);
  if (index >= frameData.length) {
    return null;
  }

  const imageData = frameData.subarray(index);
  if (imageData.length === 0) {
    return null;
  }

  return URL.createObjectURL(new Blob([imageData], { type: mimeType }));
}

function skipId3Description(frameData: Uint8Array, start: number, encoding: number): number {
  let index = start;
  if (encoding === 1 || encoding === 2) {
    while (index + 1 < frameData.length) {
      if (frameData[index] === 0 && frameData[index + 1] === 0) {
        return index + 2;
      }
      index += 2;
    }
    return frameData.length;
  }

  while (index < frameData.length) {
    if (frameData[index] === 0) {
      return index + 1;
    }
    index += 1;
  }

  return frameData.length;
}

function revokeBlobUrl(url: string | null): void {
  if (url && url.startsWith("blob:")) {
    URL.revokeObjectURL(url);
  }
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
    level: 0,
    peak: 0,
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
          pan: snapshot.pan,
          eqLow: snapshot.eqLow,
          eqMid: snapshot.eqMid,
          eqHigh: snapshot.eqHigh
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

    const previousArtwork = deck.artworkDataUrl;
    const [buffer, artworkDataUrl] = await Promise.all([
      deck.engine.loadFile(file),
      extractArtworkDataUrl(file)
    ]);
    const waveform = generateWaveformSamples(buffer, 220);
    const bpmResult = detectTempoFromBuffer(buffer);
    const snapshot = deck.engine.getSnapshot();

    if (previousArtwork !== artworkDataUrl) {
      revokeBlobUrl(previousArtwork);
    }

    set((state) => ({
      decks: {
        ...state.decks,
        [deckId]: {
          ...state.decks[deckId],
          waveform,
          bpmResult,
          bpmText: formatBpm(bpmResult),
          artworkDataUrl,
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

  setDeckEq: async (deckId, low, mid, high) => {
    const deck = get().decks[deckId];
    if (!deck.engine) {
      return;
    }

    deck.engine.setEqualizer(low, mid, high);
    get().syncDeck(deckId);
  },

  toggleMicrophone: async () => {
    const { microphoneAnalyzer, microphoneState, capabilities } = get();

    if (!capabilities.microphoneSupported) {
      set({
        microphoneState: {
          bpmText: "-- BPM",
          level: 0,
          peak: 0,
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
          level: 0,
          peak: 0,
          isRunning: false,
          status: "Microphone BPM stopped"
        }
      });
      return;
    }

    const analyzer = new BrowserMicrophoneTempoAnalyzer(
      (result) => {
        const unavailableStatus = (() => {
          if (result.kind === "detected") {
            return "";
          }

          switch (result.reason) {
            case "warming-up":
              return "Listening live • warming up audio window...";
            case "insufficient-signal":
              return "Listening live • signal too low";
            case "low-confidence":
              return "Listening live • beat not stable enough yet";
            default:
              return `Listening live • ${result.reason}`;
          }
        })();

        set((state) => ({
          microphoneState: {
            ...state.microphoneState,
            bpmText: formatBpm(result),
            isRunning: true,
            status:
              result.kind === "detected"
                ? `Listening live • ${result.bpm.toFixed(1)} BPM • confidence ${(result.confidence * 100).toFixed(0)}%`
                : unavailableStatus
          }
        }));
      },
      ({ rms, peak }) => {
        set((state) => ({
          microphoneState: {
            ...state.microphoneState,
            level: Math.min(Math.max(rms * 22, 0), 1),
            peak: Math.min(Math.max(peak, 0), 1)
          }
        }));
      }
    );

    set({
      microphoneAnalyzer: analyzer,
      microphoneState: {
        bpmText: "-- BPM",
        level: 0,
        peak: 0,
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
          level: 0,
          peak: 0,
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
