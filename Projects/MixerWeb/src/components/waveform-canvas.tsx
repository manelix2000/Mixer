"use client";

import { useEffect, useRef } from "react";

type WaveformCanvasProps = {
  samples: number[];
  progress: number;
  zoom: number;
  heightClassName?: string;
  onSeek?: (progress: number) => void;
};

export function WaveformCanvas({
  samples,
  progress,
  zoom,
  heightClassName = "h-[122px]",
  onSeek
}: WaveformCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }

    const devicePixelRatio = window.devicePixelRatio || 1;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    canvas.width = Math.floor(width * devicePixelRatio);
    canvas.height = Math.floor(height * devicePixelRatio);

    const context = canvas.getContext("2d");
    if (!context) {
      return;
    }

    context.scale(devicePixelRatio, devicePixelRatio);
    context.clearRect(0, 0, width, height);

    context.fillStyle = "#1a1d20";
    context.fillRect(0, 0, width, height);

    context.strokeStyle = "rgba(255,255,255,0.08)";
    context.beginPath();
    context.moveTo(0, height / 2);
    context.lineTo(width, height / 2);
    context.stroke();

    const playedWidth = width * Math.min(Math.max(progress, 0), 1);
    const columns = Math.max(Math.floor(width), 2);
    const centerSample = Math.min(Math.max(progress, 0), 1) * Math.max(samples.length - 1, 0);
    const zoomLevel = Math.min(Math.max(zoom, 0.4), 3.5);
    const sampleSpacing = Math.max((1.45 * Math.pow(zoomLevel, 1.2)), 0.8);

    for (let index = 0; index < columns; index += 1) {
      const x = index;
      const samplePosition = centerSample + ((x - (width / 2)) / sampleSpacing);
      const sample = interpolatedSample(samples, samplePosition);
      const halfHeight = Math.max(2, sample * height * 0.38);

      context.fillStyle = x <= playedWidth ? "rgba(249,115,22,0.82)" : "rgba(148, 152, 156, 0.52)";
      context.fillRect(x, (height / 2) - halfHeight, 1.1, halfHeight * 2);

      context.fillStyle = x <= playedWidth ? "rgba(255,255,255,0.16)" : "rgba(255,255,255,0.06)";
      context.fillRect(x, (height / 2) - (halfHeight * 0.48), 1.1, halfHeight * 0.96);
    }

    context.fillStyle = "rgba(255,255,255,0.92)";
    context.fillRect(playedWidth, 0, 2, height);
  }, [progress, samples, zoom]);

  return (
    <canvas
      ref={canvasRef}
      className={`${heightClassName} w-full cursor-pointer rounded-xl border border-black/15 bg-[#1a1d20]`}
      onClick={(event) => {
        if (!onSeek) {
          return;
        }
        const rect = event.currentTarget.getBoundingClientRect();
        const clickProgress = (event.clientX - rect.left) / rect.width;
        onSeek(Math.min(Math.max(clickProgress, 0), 1));
      }}
    />
  );
}

function interpolatedSample(samples: number[], position: number): number {
  if (samples.length === 0) {
    return 0;
  }

  if (position < 0 || position > (samples.length - 1)) {
    return 0;
  }

  const lowerIndex = Math.floor(position);
  const upperIndex = Math.min(lowerIndex + 1, samples.length - 1);
  const fraction = position - lowerIndex;
  const lower = samples[lowerIndex] ?? 0;
  const upper = samples[upperIndex] ?? 0;
  return Math.min(Math.max(lower + ((upper - lower) * fraction), 0), 1);
}
