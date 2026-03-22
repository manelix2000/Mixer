"use client";

import { useEffect, useRef } from "react";
import { useState } from "react";

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
  const [isDragging, setIsDragging] = useState(false);
  const dragStateRef = useRef<{
    pointerId: number;
    startX: number;
    startProgress: number;
    moved: boolean;
  } | null>(null);

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

    const clampedProgress = Math.min(Math.max(progress, 0), 1);
    const indicatorX = width / 2;
    const columns = Math.max(Math.floor(width), 2);
    const centerSample = clampedProgress * Math.max(samples.length - 1, 0);
    const zoomLevel = Math.min(Math.max(zoom, 0.4), 3.5);
    const sampleSpacing = Math.max((1.45 * Math.pow(zoomLevel, 1.2)), 0.8);

    for (let index = 0; index < columns; index += 1) {
      const x = index;
      const samplePosition = centerSample + ((x - (width / 2)) / sampleSpacing);
      const sample = interpolatedSample(samples, samplePosition);
      const halfHeight = Math.max(2, sample * height * 0.38);

      context.fillStyle = x <= indicatorX ? "rgba(249,115,22,0.82)" : "rgba(148, 152, 156, 0.52)";
      context.fillRect(x, (height / 2) - halfHeight, 1.1, halfHeight * 2);

      context.fillStyle = x <= indicatorX ? "rgba(255,255,255,0.16)" : "rgba(255,255,255,0.06)";
      context.fillRect(x, (height / 2) - (halfHeight * 0.48), 1.1, halfHeight * 0.96);
    }

    context.fillStyle = "rgba(255,255,255,0.92)";
    context.fillRect(indicatorX, 0, 2, height);
  }, [progress, samples, zoom]);

  return (
    <div
      className={`${heightClassName} relative w-full touch-none ${onSeek ? (isDragging ? "cursor-grabbing" : "cursor-grab") : "cursor-default"}`}
      onPointerDown={(event) => {
        if (!onSeek) {
          return;
        }
        dragStateRef.current = {
          pointerId: event.pointerId,
          startX: event.clientX,
          startProgress: Math.min(Math.max(progress, 0), 1),
          moved: false
        };
        setIsDragging(true);
        event.currentTarget.setPointerCapture(event.pointerId);
      }}
      onPointerMove={(event) => {
        if (!onSeek || !dragStateRef.current || dragStateRef.current.pointerId !== event.pointerId) {
          return;
        }
        const rect = event.currentTarget.getBoundingClientRect();
        const deltaX = event.clientX - dragStateRef.current.startX;
        const normalizedDelta = deltaX / Math.max(rect.width, 1);
        if (Math.abs(deltaX) > 2) {
          dragStateRef.current.moved = true;
        }
        onSeek(Math.min(Math.max(dragStateRef.current.startProgress + normalizedDelta, 0), 1));
      }}
      onPointerUp={(event) => {
        if (!onSeek || !dragStateRef.current || dragStateRef.current.pointerId !== event.pointerId) {
          return;
        }
        const rect = event.currentTarget.getBoundingClientRect();
        if (!dragStateRef.current.moved) {
          const localX = event.clientX - rect.left;
          const direction = localX < rect.width / 2 ? -1 : 1;
          const tapStep = 0.03;
          onSeek(
            Math.min(Math.max(dragStateRef.current.startProgress + (direction * tapStep), 0), 1)
          );
        }
        if (event.currentTarget.hasPointerCapture(event.pointerId)) {
          event.currentTarget.releasePointerCapture(event.pointerId);
        }
        dragStateRef.current = null;
        setIsDragging(false);
      }}
      onPointerCancel={(event) => {
        if (dragStateRef.current?.pointerId === event.pointerId) {
          if (event.currentTarget.hasPointerCapture(event.pointerId)) {
            event.currentTarget.releasePointerCapture(event.pointerId);
          }
          dragStateRef.current = null;
          setIsDragging(false);
        }
      }}
      role="presentation"
    >
      <canvas
        ref={canvasRef}
        className="h-full w-full rounded-xl border border-black/15 bg-[#1a1d20]"
      />
    </div>
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
