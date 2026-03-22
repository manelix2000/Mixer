import { detectTempoFromBuffer } from "./tempo";
import type { BPMResult } from "@mixer/domain";

export class BrowserMicrophoneTempoAnalyzer {
  private stream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private analyserNode: AnalyserNode | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  private intervalId: number | null = null;

  constructor(private readonly onTempo: (result: BPMResult) => void) {}

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
    this.sourceNode = this.audioContext.createMediaStreamSource(this.stream);
    this.analyserNode = this.audioContext.createAnalyser();
    this.analyserNode.fftSize = 2048;
    this.sourceNode.connect(this.analyserNode);

    const sampleWindow = new Float32Array(this.analyserNode.fftSize);
    this.intervalId = window.setInterval(() => {
      if (!this.analyserNode || !this.audioContext) {
        return;
      }

      this.analyserNode.getFloatTimeDomainData(sampleWindow);
      const buffer = new AudioBuffer({
        length: sampleWindow.length,
        numberOfChannels: 1,
        sampleRate: this.audioContext.sampleRate
      });
      buffer.getChannelData(0).set(sampleWindow);
      this.onTempo(detectTempoFromBuffer(buffer));
    }, 1200);
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
  }
}
