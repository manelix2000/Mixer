import { createTurntablePhysicsState, stepTurntablePhysics } from "@mixer/domain";

type AudioSnapshot = {
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
  eqLow: number;
  eqMid: number;
  eqHigh: number;
};

function formatSeconds(seconds: number): string {
  const safeSeconds = Math.max(seconds, 0);
  const minutes = Math.floor(safeSeconds / 60);
  const remainder = Math.floor(safeSeconds % 60);
  return `${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

export class BrowserAudioDeck {
  private audioContext: AudioContext | null = null;
  private gainNode: GainNode | null = null;
  private pannerNode: StereoPannerNode | null = null;
  private eqLowNode: BiquadFilterNode | null = null;
  private eqMidNode: BiquadFilterNode | null = null;
  private eqHighNode: BiquadFilterNode | null = null;
  private sourceNode: AudioBufferSourceNode | null = null;
  private audioBuffer: AudioBuffer | null = null;
  private playbackOffset = 0;
  private playbackStartedAt = 0;
  private isPlaying = false;
  private rate = 1;
  private volume = 0.82;
  private pan = 0;
  private eqLow = 0.5;
  private eqMid = 0.5;
  private eqHigh = 0.5;
  private physics = createTurntablePhysicsState();
  private lastSnapshotTimestamp = 0;

  async loadFile(file: File): Promise<AudioBuffer> {
    const context = this.ensureAudioContext();
    const arrayBuffer = await file.arrayBuffer();
    const decoded = await context.decodeAudioData(arrayBuffer.slice(0));

    this.stopSource();
    this.audioBuffer = decoded;
    this.playbackOffset = 0;
    this.playbackStartedAt = context.currentTime;
    this.isPlaying = false;
    this.physics = createTurntablePhysicsState();
    this.lastSnapshotTimestamp = performance.now();

    return decoded;
  }

  async play(): Promise<void> {
    const context = this.ensureAudioContext();
    if (!this.audioBuffer) {
      return;
    }

    await context.resume();
    if (this.isPlaying) {
      return;
    }

    this.startSource(this.playbackOffset);
  }

  pause(): void {
    if (!this.isPlaying) {
      return;
    }

    this.playbackOffset = this.getCurrentTime();
    this.stopSource();
    this.isPlaying = false;
  }

  async seek(time: number): Promise<void> {
    if (!this.audioBuffer) {
      return;
    }

    this.playbackOffset = clamp(time, 0, this.audioBuffer.duration);
    if (this.isPlaying) {
      this.startSource(this.playbackOffset);
    }
  }

  async scratch(time: number): Promise<void> {
    await this.seek(time);
  }

  setPlaybackRate(rate: number): void {
    const clampedRate = clamp(rate, 0.84, 1.16);
    const currentTime = this.getCurrentTime();
    this.rate = clampedRate;
    if (this.sourceNode) {
      this.sourceNode.playbackRate.value = clampedRate;
    }
    if (this.isPlaying) {
      this.startSource(currentTime);
    }
  }

  setVolume(volume: number): void {
    this.volume = clamp(volume, 0, 1);
    this.ensureNodeGraph();
    if (this.gainNode) {
      this.gainNode.gain.value = this.volume;
    }
  }

  setPan(pan: number): void {
    this.pan = clamp(pan, -1, 1);
    this.ensureNodeGraph();
    if (this.pannerNode) {
      this.pannerNode.pan.value = this.pan;
    }
  }

  setEqualizer(low: number, mid: number, high: number): void {
    this.eqLow = clamp(low, 0, 1);
    this.eqMid = clamp(mid, 0, 1);
    this.eqHigh = clamp(high, 0, 1);
    this.ensureNodeGraph();
    this.applyEqualizerGains();
  }

  getSnapshot(): AudioSnapshot {
    const now = performance.now();
    const deltaSeconds =
      this.lastSnapshotTimestamp === 0 ? 0 : (now - this.lastSnapshotTimestamp) / 1000;
    this.lastSnapshotTimestamp = now;

    if (this.isPlaying) {
      this.physics = stepTurntablePhysics(this.physics, deltaSeconds, this.rate * 2.1);
    } else {
      this.physics = stepTurntablePhysics(this.physics, deltaSeconds);
    }

    const duration = this.audioBuffer?.duration ?? 0;
    const currentTime = this.getCurrentTime();
    return {
      currentTime,
      currentTimeText: `${formatSeconds(currentTime)} / ${formatSeconds(duration)}`,
      duration,
      durationText: formatSeconds(duration),
      progress: duration > 0 ? clamp(currentTime / duration, 0, 1) : 0,
      platterDegrees: (this.physics.platterPositionRadians * 180) / Math.PI,
      isLoaded: Boolean(this.audioBuffer),
      isPlaying: this.isPlaying,
      rate: this.rate,
      volume: this.volume,
      pan: this.pan,
      eqLow: this.eqLow,
      eqMid: this.eqMid,
      eqHigh: this.eqHigh
    };
  }

  private getCurrentTime(): number {
    if (!this.audioBuffer) {
      return 0;
    }

    if (!this.isPlaying || !this.audioContext) {
      return clamp(this.playbackOffset, 0, this.audioBuffer.duration);
    }

    const elapsed = (this.audioContext.currentTime - this.playbackStartedAt) * this.rate;
    const position = this.playbackOffset + elapsed;
    if (position >= this.audioBuffer.duration) {
      this.isPlaying = false;
      this.stopSource();
      this.playbackOffset = this.audioBuffer.duration;
      return this.audioBuffer.duration;
    }
    return position;
  }

  private startSource(offset: number): void {
    if (!this.audioBuffer) {
      return;
    }

    const context = this.ensureAudioContext();
    this.stopSource();
    this.ensureNodeGraph();

    const source = context.createBufferSource();
    source.buffer = this.audioBuffer;
    source.playbackRate.value = this.rate;
    source.connect(this.eqLowNode!);
    source.onended = () => {
      if (!this.audioBuffer) {
        return;
      }
      const current = this.getCurrentTime();
      if (current >= this.audioBuffer.duration - 0.01) {
        this.isPlaying = false;
        this.playbackOffset = this.audioBuffer.duration;
      }
    };

    this.playbackOffset = clamp(offset, 0, this.audioBuffer.duration);
    this.playbackStartedAt = context.currentTime;
    source.start(0, this.playbackOffset);
    this.sourceNode = source;
    this.isPlaying = true;
  }

  private stopSource(): void {
    if (!this.sourceNode) {
      return;
    }

    this.sourceNode.onended = null;
    this.sourceNode.stop();
    this.sourceNode.disconnect();
    this.sourceNode = null;
  }

  private ensureNodeGraph(): void {
    const context = this.ensureAudioContext();
    if (this.gainNode && this.pannerNode && this.eqLowNode && this.eqMidNode && this.eqHighNode) {
      return;
    }

    this.gainNode = context.createGain();
    this.pannerNode = context.createStereoPanner();
    this.eqLowNode = context.createBiquadFilter();
    this.eqMidNode = context.createBiquadFilter();
    this.eqHighNode = context.createBiquadFilter();

    this.eqLowNode.type = "lowshelf";
    this.eqLowNode.frequency.value = 220;
    this.eqMidNode.type = "peaking";
    this.eqMidNode.frequency.value = 1100;
    this.eqMidNode.Q.value = 0.9;
    this.eqHighNode.type = "highshelf";
    this.eqHighNode.frequency.value = 4600;

    this.gainNode.gain.value = this.volume;
    this.pannerNode.pan.value = this.pan;

    this.applyEqualizerGains();

    this.eqLowNode.connect(this.eqMidNode);
    this.eqMidNode.connect(this.eqHighNode);
    this.eqHighNode.connect(this.gainNode);
    this.gainNode.connect(this.pannerNode);
    this.pannerNode.connect(context.destination);
  }

  private applyEqualizerGains(): void {
    if (!this.eqLowNode || !this.eqMidNode || !this.eqHighNode) {
      return;
    }

    const gainFromNormalized = (value: number): number => ((clamp(value, 0, 1) - 0.5) * 24);
    this.eqLowNode.gain.value = gainFromNormalized(this.eqLow);
    this.eqMidNode.gain.value = gainFromNormalized(this.eqMid);
    this.eqHighNode.gain.value = gainFromNormalized(this.eqHigh);
  }

  private ensureAudioContext(): AudioContext {
    if (this.audioContext) {
      return this.audioContext;
    }

    const AudioContextConstructor =
      window.AudioContext ||
      (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextConstructor) {
      throw new Error("Web Audio is unavailable in this browser.");
    }

    this.audioContext = new AudioContextConstructor();
    this.ensureNodeGraph();
    return this.audioContext;
  }
}
