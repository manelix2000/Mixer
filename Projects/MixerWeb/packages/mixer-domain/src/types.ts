export type DeckId = "left" | "right";

export type BPMResult =
  | {
      kind: "detected";
      bpm: number;
      confidence: number;
    }
  | {
      kind: "unavailable";
      reason: string;
    };

export type AudioDeckLike = {
  getSnapshot: () => {
    currentTime: number;
    currentTimeText: string;
    duration: number;
    durationText: string;
    progress: number;
    platterDegrees: number;
    isLoaded: boolean;
    isPlaying: boolean;
    rate: number;
    volume: number;
    pan: number;
  };
  loadFile: (file: File) => Promise<AudioBuffer>;
  play: () => Promise<void>;
  pause: () => void;
  seek: (time: number) => Promise<void>;
  scratch: (time: number) => Promise<void>;
  setPlaybackRate: (rate: number) => void;
  setVolume: (volume: number) => void;
  setPan: (pan: number) => void;
};

export type DeckRuntimeState = {
  deckId: DeckId;
  engine: AudioDeckLike | null;
  trackName: string | null;
  artworkDataUrl: string | null;
  waveform: number[];
  bpmResult: BPMResult | null;
  bpmText: string;
  statusMessage: string;
  isAnalyzing: boolean;
  isLoaded: boolean;
  isPlaying: boolean;
  currentTime: number;
  currentTimeText: string;
  duration: number;
  durationText: string;
  progress: number;
  platterDegrees: number;
  rate: number;
  volume: number;
  pan: number;
};

export function createDeckRuntimeState(deckId: DeckId): DeckRuntimeState {
  return {
    deckId,
    engine: null,
    trackName: null,
    artworkDataUrl: null,
    waveform: new Array(220).fill(0.08),
    bpmResult: null,
    bpmText: "-- BPM",
    statusMessage: "Load a local audio file to begin.",
    isAnalyzing: false,
    isLoaded: false,
    isPlaying: false,
    currentTime: 0,
    currentTimeText: "00:00 / 00:00",
    duration: 0,
    durationText: "00:00",
    progress: 0,
    platterDegrees: 0,
    rate: 1,
    volume: 0.82,
    pan: 0
  };
}
