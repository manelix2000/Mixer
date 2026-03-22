import { detectTempoFromBuffer } from "./tempo";
import type { BPMResult } from "@mixer/domain";

export class BrowserMicrophoneTempoAnalyzer {
  private stream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private analyserNode: AnalyserNode | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  private intervalId: number | null = null;
  private historyBuffer: Float32Array | null = null;
  private historyWriteIndex = 0;
  private historyFilledSamples = 0;
  private analysisTick = 0;

  constructor(
    private readonly onTempo: (result: BPMResult) => void,
    private readonly onLevel?: (level: { rms: number; peak: number }) => void
  ) {}

  async start(): Promise<void> {
    if (
      typeof navigator.mediaDevices === "undefined" ||
      typeof navigator.mediaDevices.getUserMedia !== "function"
    ) {
      throw new Error("Microphone capture is not available.");
    }

    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: false,
        noiseSuppression: false,
        autoGainControl: false
      }
    });

    this.audioContext = new AudioContext();
    await this.audioContext.resume();
    this.sourceNode = this.audioContext.createMediaStreamSource(this.stream);
    this.analyserNode = this.audioContext.createAnalyser();
    this.analyserNode.fftSize = 16384;
    this.sourceNode.connect(this.analyserNode);

    const sampleWindow = new Float32Array(this.analyserNode.fftSize);
    const historySeconds = 8;
    const minAnalysisSeconds = 4;
    const historyLength = Math.floor(this.audioContext.sampleRate * historySeconds);
    this.historyBuffer = new Float32Array(historyLength);
    this.historyWriteIndex = 0;
    this.historyFilledSamples = 0;
    this.analysisTick = 0;

    this.intervalId = window.setInterval(() => {
      if (!this.analyserNode || !this.audioContext || !this.historyBuffer) {
        return;
      }

      this.analyserNode.getFloatTimeDomainData(sampleWindow);
      writeIntoCircularBuffer(this.historyBuffer, sampleWindow, () => this.historyWriteIndex, (next) => {
        this.historyWriteIndex = next;
      });
      this.historyFilledSamples = Math.min(
        this.historyFilledSamples + sampleWindow.length,
        this.historyBuffer.length
      );

      this.analysisTick += 1;
      if (this.analysisTick % 2 !== 0) {
        return;
      }

      const minAnalysisSamples = Math.floor(this.audioContext.sampleRate * minAnalysisSeconds);
      if (this.historyFilledSamples < minAnalysisSamples) {
        this.onTempo({ kind: "unavailable", reason: "warming-up" });
        return;
      }

      const analysisWindow = extractRecentSamples(
        this.historyBuffer,
        this.historyWriteIndex,
        this.historyFilledSamples
      );
      const signal = signalStats(analysisWindow);
      this.onLevel?.(signal);
      if (signal.rms < 0.006) {
        this.onTempo({ kind: "unavailable", reason: `low-signal rms=${signal.rms.toFixed(4)} peak=${signal.peak.toFixed(3)}` });
        return;
      }

      const buffer = new AudioBuffer({
        length: analysisWindow.length,
        numberOfChannels: 1,
        sampleRate: this.audioContext.sampleRate
      });
      buffer.getChannelData(0).set(analysisWindow);
      const primary = detectTempoFromBuffer(buffer);
      if (primary.kind === "detected") {
        this.onTempo(primary);
        return;
      }

      const fallback = detectTempoFromMicrophoneSamples(analysisWindow, this.audioContext.sampleRate);
      if (fallback.kind === "detected") {
        this.onTempo(fallback);
        return;
      }

      this.onTempo({
        kind: "unavailable",
        reason: `${primary.reason}; ${fallback.reason}; rms=${signal.rms.toFixed(4)} peak=${signal.peak.toFixed(3)}`
      });
    }, 250);
  }

  stop(): void {
    if (this.intervalId !== null) {
      window.clearInterval(this.intervalId);
      this.intervalId = null;
    }
    this.sourceNode?.disconnect();
    this.analyserNode?.disconnect();
    void this.audioContext?.close();
    this.stream?.getTracks().forEach((track) => track.stop());
    this.sourceNode = null;
    this.analyserNode = null;
    this.audioContext = null;
    this.stream = null;
    this.historyBuffer = null;
    this.historyWriteIndex = 0;
    this.historyFilledSamples = 0;
    this.analysisTick = 0;
  }
}

function signalStats(samples: Float32Array): { rms: number; peak: number } {
  if (samples.length === 0) {
    return { rms: 0, peak: 0 };
  }

  let sumSquares = 0;
  let peak = 0;
  for (let i = 0; i < samples.length; i += 1) {
    const v = samples[i] ?? 0;
    sumSquares += v * v;
    const abs = Math.abs(v);
    if (abs > peak) {
      peak = abs;
    }
  }

  return {
    rms: Math.sqrt(sumSquares / samples.length),
    peak
  };
}

function detectTempoFromMicrophoneSamples(samples: Float32Array, sampleRate: number): BPMResult {
  const targetRate = 200;
  const minBpm = 60;
  const maxBpm = 200;
  const hop = Math.max(Math.floor(sampleRate / targetRate), 1);
  const bucketCount = Math.floor(samples.length / hop);
  if (bucketCount < 128) {
    return { kind: "unavailable", reason: "mic-window-too-short" };
  }

  const energy = new Float32Array(bucketCount);
  for (let b = 0; b < bucketCount; b += 1) {
    let sum = 0;
    for (let o = 0; o < hop; o += 1) {
      const s = samples[(b * hop) + o] ?? 0;
      sum += Math.abs(s);
    }
    energy[b] = sum / hop;
  }

  // Spectral-flux-like envelope from positive differences.
  for (let i = energy.length - 1; i > 0; i -= 1) {
    const delta = energy[i] - energy[i - 1];
    energy[i] = delta > 0 ? delta : 0;
  }
  energy[0] = 0;

  let mean = 0;
  for (let i = 0; i < energy.length; i += 1) {
    mean += energy[i];
  }
  mean /= Math.max(energy.length, 1);
  if (mean <= 1e-6) {
    return { kind: "unavailable", reason: "mic-no-transients" };
  }
  for (let i = 0; i < energy.length; i += 1) {
    energy[i] = Math.max(energy[i] - mean, 0);
  }

  const minLag = Math.floor((60 * targetRate) / maxBpm);
  const maxLag = Math.ceil((60 * targetRate) / minBpm);
  let bestLag = 0;
  let best = 0;

  for (let lag = minLag; lag <= maxLag; lag += 1) {
    let corr = 0;
    for (let i = 0; i < energy.length - lag; i += 1) {
      corr += energy[i] * energy[i + lag];
    }
    if (corr > best) {
      best = corr;
      bestLag = lag;
    }
  }

  if (bestLag === 0 || best <= 1e-6) {
    return { kind: "unavailable", reason: "mic-no-tempo-peak" };
  }

  let zeroLag = 0;
  for (let i = 0; i < energy.length; i += 1) {
    zeroLag += energy[i] * energy[i];
  }
  const confidence = Math.min(Math.max(best / Math.max(zeroLag, 1e-6), 0), 1);
  if (confidence < 0.14) {
    return { kind: "unavailable", reason: `mic-low-confidence=${confidence.toFixed(2)}` };
  }

  const bpm = (60 * targetRate) / bestLag;
  return { kind: "detected", bpm, confidence };
}

function writeIntoCircularBuffer(
  history: Float32Array,
  chunk: Float32Array,
  getWriteIndex: () => number,
  setWriteIndex: (next: number) => void
): void {
  if (history.length === 0 || chunk.length === 0) {
    return;
  }

  const writeIndex = getWriteIndex();
  const firstPart = Math.min(chunk.length, history.length - writeIndex);
  history.set(chunk.subarray(0, firstPart), writeIndex);

  const remaining = chunk.length - firstPart;
  if (remaining > 0) {
    history.set(chunk.subarray(firstPart, firstPart + remaining), 0);
  }

  setWriteIndex((writeIndex + chunk.length) % history.length);
}

function extractRecentSamples(
  history: Float32Array,
  writeIndex: number,
  filledSamples: number
): Float32Array {
  const available = Math.min(filledSamples, history.length);
  const output = new Float32Array(available);
  if (available === 0) {
    return output;
  }

  const start = (writeIndex - available + history.length) % history.length;
  const firstPart = Math.min(available, history.length - start);
  output.set(history.subarray(start, start + firstPart), 0);
  if (firstPart < available) {
    output.set(history.subarray(0, available - firstPart), firstPart);
  }

  return output;
}
