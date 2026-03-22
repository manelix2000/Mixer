export function generateWaveformSamples(audioBuffer: AudioBuffer, sampleCount = 4096): number[] {
  if (sampleCount <= 0) {
    return [];
  }

  const channelCount = audioBuffer.numberOfChannels;
  const frameCount = audioBuffer.length;
  const framesPerBucket = Math.max(Math.ceil(frameCount / sampleCount), 1);
  const buckets: number[] = [];
  const channels = Array.from({ length: channelCount }, (_, channelIndex) => (
    audioBuffer.getChannelData(channelIndex)
  ));

  for (let bucketIndex = 0; bucketIndex < sampleCount; bucketIndex += 1) {
    const start = bucketIndex * framesPerBucket;
    const end = Math.min(start + framesPerBucket, frameCount);

    let peak = 0;
    let squareSum = 0;
    let sampleFrames = 0;

    for (let frameIndex = start; frameIndex < end; frameIndex += 1) {
      let mixed = 0;
      for (let channelIndex = 0; channelIndex < channelCount; channelIndex += 1) {
        mixed += Math.abs(channels[channelIndex]?.[frameIndex] ?? 0);
      }
      const amplitude = mixed / Math.max(channelCount, 1);
      peak = Math.max(peak, amplitude);
      squareSum += amplitude * amplitude;
      sampleFrames += 1;
    }

    const rms = sampleFrames > 0 ? Math.sqrt(squareSum / sampleFrames) : 0;
    const blended = Math.pow(Math.max((rms * 0.82) + (peak * 0.18), 0), 0.95);
    buckets.push(blended);
  }

  const sorted = [...buckets].sort((a, b) => a - b);
  const floorIndex = Math.min(Math.max(Math.floor((sorted.length - 1) * 0.10), 0), sorted.length - 1);
  const ceilingIndex = Math.min(
    Math.max(Math.floor((sorted.length - 1) * 0.985), floorIndex),
    sorted.length - 1
  );

  const floor = sorted[floorIndex] ?? 0;
  const ceiling = sorted[ceilingIndex] ?? floor;
  const scale = Math.max(ceiling - floor, 0.000001);

  return buckets.map((value) => {
    const normalized = (value - floor) / scale;
    return Math.min(Math.max(normalized, 0), 1);
  });
}
