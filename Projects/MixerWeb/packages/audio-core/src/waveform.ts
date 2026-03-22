export function generateWaveformSamples(audioBuffer: AudioBuffer, sampleCount = 220): number[] {
  if (sampleCount <= 0) {
    return [];
  }

  const channelCount = audioBuffer.numberOfChannels;
  const frameCount = audioBuffer.length;
  const framesPerBucket = Math.max(Math.ceil(frameCount / sampleCount), 1);
  const buckets: number[] = [];
  let runningMax = 0;

  for (let bucketIndex = 0; bucketIndex < sampleCount; bucketIndex += 1) {
    const start = bucketIndex * framesPerBucket;
    const end = Math.min(start + framesPerBucket, frameCount);

    let peak = 0;
    let squareSum = 0;
    let sampleFrames = 0;

    for (let frameIndex = start; frameIndex < end; frameIndex += 1) {
      let mixed = 0;
      for (let channelIndex = 0; channelIndex < channelCount; channelIndex += 1) {
        mixed += Math.abs(audioBuffer.getChannelData(channelIndex)[frameIndex] ?? 0);
      }
      const amplitude = mixed / Math.max(channelCount, 1);
      peak = Math.max(peak, amplitude);
      squareSum += amplitude * amplitude;
      sampleFrames += 1;
    }

    const rms = sampleFrames > 0 ? Math.sqrt(squareSum / sampleFrames) : 0;
    const blended = Math.pow(Math.max((rms * 0.82) + (peak * 0.18), 0), 0.95);
    buckets.push(blended);
    runningMax = Math.max(runningMax, blended);
  }

  const normalizedMax = Math.max(runningMax, 0.000001);
  return buckets.map((value) => Math.min(Math.max(value / normalizedMax, 0), 1));
}
