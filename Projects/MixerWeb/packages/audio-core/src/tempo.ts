import type { BPMResult } from "@mixer/domain";

const MIN_BPM = 80;
const MAX_BPM = 180;
const DOWNSAMPLED_RATE = 200;

export function detectTempoFromBuffer(audioBuffer: AudioBuffer): BPMResult {
  const mono = downmixToMono(audioBuffer);
  if (mono.length === 0) {
    return { kind: "unavailable", reason: "empty-buffer" };
  }

  const envelope = buildEnvelope(mono, audioBuffer.sampleRate);
  if (envelope.length < 32) {
    return { kind: "unavailable", reason: "insufficient-signal" };
  }

  let bestLag = 0;
  let bestCorrelation = 0;
  const minLag = Math.floor((60 * DOWNSAMPLED_RATE) / MAX_BPM);
  const maxLag = Math.ceil((60 * DOWNSAMPLED_RATE) / MIN_BPM);

  for (let lag = minLag; lag <= maxLag; lag += 1) {
    let correlation = 0;
    for (let index = 0; index < envelope.length - lag; index += 1) {
      correlation += envelope[index] * envelope[index + lag];
    }
    if (correlation > bestCorrelation) {
      bestCorrelation = correlation;
      bestLag = lag;
    }
  }

  if (bestLag === 0 || bestCorrelation <= 0.01) {
    return { kind: "unavailable", reason: "low-confidence" };
  }

  const bpm = (60 * DOWNSAMPLED_RATE) / bestLag;
  return {
    kind: "detected",
    bpm,
    confidence: Math.min(Math.max(bestCorrelation / envelope.length, 0), 1)
  };
}

function downmixToMono(audioBuffer: AudioBuffer): Float32Array {
  const mono = new Float32Array(audioBuffer.length);
  const channelCount = audioBuffer.numberOfChannels;
  if (channelCount === 0) {
    return mono;
  }

  for (let channelIndex = 0; channelIndex < channelCount; channelIndex += 1) {
    const channel = audioBuffer.getChannelData(channelIndex);
    for (let index = 0; index < channel.length; index += 1) {
      mono[index] += channel[index] / channelCount;
    }
  }

  return mono;
}

function buildEnvelope(samples: Float32Array, sampleRate: number): Float32Array {
  const hop = Math.max(Math.floor(sampleRate / DOWNSAMPLED_RATE), 1);
  const bucketCount = Math.floor(samples.length / hop);
  const envelope = new Float32Array(bucketCount);

  for (let bucketIndex = 0; bucketIndex < bucketCount; bucketIndex += 1) {
    let sum = 0;
    for (let offset = 0; offset < hop; offset += 1) {
      const sample = samples[(bucketIndex * hop) + offset] ?? 0;
      sum += Math.abs(sample);
    }
    envelope[bucketIndex] = sum / hop;
  }

  // High-pass the envelope to emphasize transients.
  for (let index = envelope.length - 1; index > 0; index -= 1) {
    envelope[index] = Math.max(envelope[index] - envelope[index - 1], 0);
  }

  return envelope;
}
