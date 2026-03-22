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

const IOS_PRO_WAVEFORM_STYLE = {
  horizontalSpacingMultiplier: 1.45,
  amplitudeExponent: 1.35,
  smoothingFactor: 1.0,
  minimumHalfHeight: 0.0,
  maxWaveHeightRatio: 0.46,
  innerLayerScale: 0.46,
  outerIdleFill: "rgba(174,178,184,0.54)",
  outerPlayedFill: "rgba(251,146,60,0.82)",
  innerIdleFill: "rgba(255,255,255,0.06)",
  innerPlayedFill: "rgba(251,146,60,0.18)",
  edgeStroke: "rgba(255,255,255,0.22)",
  baselineStroke: "rgba(255,255,255,0.10)",
  playheadStroke: "rgba(255,59,48,0.85)"
} as const;

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

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.scale(devicePixelRatio, devicePixelRatio);
    context.clearRect(0, 0, width, height);

    context.fillStyle = "#1a1d20";
    context.fillRect(0, 0, width, height);

    context.strokeStyle = IOS_PRO_WAVEFORM_STYLE.baselineStroke;
    context.beginPath();
    context.moveTo(0, height / 2);
    context.lineTo(width, height / 2);
    context.stroke();

    if (samples.length > 0) {
      const clampedProgress = Math.min(Math.max(progress, 0), 1);
      const centerX = width / 2;
      const midY = height / 2;
      const centerSample = clampedProgress * Math.max(samples.length - 1, 0);
      const zoomLevel = Math.min(Math.max(zoom, 0.2), 8);

      const baseSampleSpacing = 1.8;
      const horizontalSpacingMultiplier = IOS_PRO_WAVEFORM_STYLE.horizontalSpacingMultiplier;
      const amplitudeExponent = IOS_PRO_WAVEFORM_STYLE.amplitudeExponent;
      const smoothingFactor = IOS_PRO_WAVEFORM_STYLE.smoothingFactor;
      const minimumHalfHeight = IOS_PRO_WAVEFORM_STYLE.minimumHalfHeight;
      const maxWaveHeightRatio = IOS_PRO_WAVEFORM_STYLE.maxWaveHeightRatio;
      const innerLayerScale = IOS_PRO_WAVEFORM_STYLE.innerLayerScale;

      const sampleSpacing =
        baseSampleSpacing *
        Math.max(horizontalSpacingMultiplier, 0.1) *
        Math.pow(zoomLevel, 1.35);
      const columns = Math.max(Math.ceil(width), 2);
      const maxHalfHeight = height * Math.min(Math.max(maxWaveHeightRatio, 0), 0.48);
      const points: Array<{ x: number; y: number }> = [];
      points.length = 0;

      let smoothedAmplitude = 0;
      for (let column = 0; column < columns; column += 1) {
        const x = column;
        const samplePosition = centerSample + ((x - centerX) / sampleSpacing);
        const amplitude = interpolatedSample(samples, samplePosition);
        const clampedAmplitude = Math.min(Math.max(amplitude, 0), 1);
        const shapedAmplitude = Math.pow(clampedAmplitude, Math.max(amplitudeExponent, 0.01));
        if (smoothingFactor >= 0.999) {
          smoothedAmplitude = shapedAmplitude;
        } else {
          smoothedAmplitude += (shapedAmplitude - smoothedAmplitude) * smoothingFactor;
        }

        const halfHeight = Math.max(minimumHalfHeight, smoothedAmplitude * maxHalfHeight);
        points.push({ x, y: halfHeight });
      }

      if (points.length > 4) {
        const innerPoints = points.map((point) => ({
          x: point.x,
          y: point.y * Math.min(Math.max(innerLayerScale, 0), 1)
        }));

        drawWaveformBody(context, points, midY, IOS_PRO_WAVEFORM_STYLE.outerIdleFill);
        clipLeft(context, centerX, width, height, () => {
          drawWaveformBody(context, points, midY, IOS_PRO_WAVEFORM_STYLE.outerPlayedFill);
        });

        drawWaveformBody(context, innerPoints, midY, IOS_PRO_WAVEFORM_STYLE.innerIdleFill);
        clipLeft(context, centerX, width, height, () => {
          drawWaveformBody(context, innerPoints, midY, IOS_PRO_WAVEFORM_STYLE.innerPlayedFill);
        });

        context.beginPath();
        context.moveTo(points[0]?.x ?? 0, midY - (points[0]?.y ?? 0));
        for (let index = 1; index < points.length; index += 1) {
          const point = points[index];
          if (!point) {
            continue;
          }
          context.lineTo(point.x, midY - point.y);
        }
        context.strokeStyle = IOS_PRO_WAVEFORM_STYLE.edgeStroke;
        context.lineWidth = 1;
        context.stroke();
      }
    }

    context.fillStyle = IOS_PRO_WAVEFORM_STYLE.playheadStroke;
    context.fillRect((width / 2) - 1, 0, 2, height);
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

function drawWaveformBody(
  context: CanvasRenderingContext2D,
  points: Array<{ x: number; y: number }>,
  midY: number,
  fillStyle: string
) {
  const first = points[0];
  if (!first) {
    return;
  }

  context.beginPath();
  context.moveTo(first.x, midY - first.y);

  for (let index = 1; index < points.length; index += 1) {
    const point = points[index];
    if (!point) {
      continue;
    }
    context.lineTo(point.x, midY - point.y);
  }

  for (let index = points.length - 1; index >= 0; index -= 1) {
    const point = points[index];
    if (!point) {
      continue;
    }
    context.lineTo(point.x, midY + point.y);
  }

  context.closePath();
  context.fillStyle = fillStyle;
  context.fill();
}

function clipLeft(
  context: CanvasRenderingContext2D,
  centerX: number,
  width: number,
  height: number,
  draw: () => void
) {
  context.save();
  context.beginPath();
  context.rect(0, 0, centerX, height);
  context.clip();
  draw();
  context.restore();
}
