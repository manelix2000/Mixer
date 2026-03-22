"use client";

import { useMemo, useRef } from "react";

type PlatterViewProps = {
  angleDegrees: number;
  isPlaying: boolean;
  onScrub: (normalizedPosition: number) => void;
  onScrubEnd: () => void;
};

export function PlatterView({
  angleDegrees,
  isPlaying,
  onScrub,
  onScrubEnd
}: PlatterViewProps) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const pointerIdRef = useRef<number | null>(null);

  const ringStyle = useMemo(
    () => ({
      transform: `rotate(${angleDegrees}deg)`
    }),
    [angleDegrees]
  );

  return (
    <div
      ref={rootRef}
      className="relative aspect-square w-full max-w-[420px] touch-none select-none rounded-full border border-black/20 bg-[#090d11] shadow-platter"
      onPointerDown={(event) => {
        pointerIdRef.current = event.pointerId;
        event.currentTarget.setPointerCapture(event.pointerId);
      }}
      onPointerMove={(event) => {
        if (pointerIdRef.current !== event.pointerId || !rootRef.current) {
          return;
        }

        const rect = rootRef.current.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const normalized = Math.min(Math.max(x / rect.width, 0), 1);
        onScrub(normalized);
      }}
      onPointerUp={(event) => {
        if (pointerIdRef.current === event.pointerId) {
          pointerIdRef.current = null;
          onScrubEnd();
        }
      }}
      onPointerCancel={() => {
        pointerIdRef.current = null;
        onScrubEnd();
      }}
      role="presentation"
    >
      <div className="absolute right-[7%] top-[4%] z-30 h-[16%] w-[4.8%] rounded-full border border-black/25 bg-[linear-gradient(180deg,_#ebedf0_0%,_#a6adb3_100%)] shadow-[0_5px_12px_rgba(0,0,0,0.28)]" />
      <div className="absolute right-[12%] top-[16%] z-30 h-[35%] w-[2.4%] origin-top rotate-[20deg] rounded-full border border-black/20 bg-[linear-gradient(180deg,_#e5e8eb_0%,_#7d858c_100%)]" />
      <div className="absolute right-[19%] top-[45%] z-30 h-[20%] w-[2.2%] origin-top rotate-[18deg] rounded-full border border-black/20 bg-[linear-gradient(180deg,_#d7dade_0%,_#747c84_100%)]" />
      <div className="absolute right-[21%] top-[62%] z-30 h-[6%] w-[5.2%] rounded-full border border-black/35 bg-[linear-gradient(180deg,_#d9dde1_0%,_#90979e_100%)] shadow-[0_4px_8px_rgba(0,0,0,0.25)]" />
      <div className="absolute right-[20.8%] top-[66.2%] z-30 h-[2.4%] w-[2.2%] rounded-sm border border-black/30 bg-[#f4d44e]" />
      <div className="absolute inset-[4%] rounded-full border border-white/10 bg-[#10161b]" />
      <div className="absolute inset-[8%] rounded-full border border-white/5 bg-[#050709]" />
      <div
        className="absolute inset-[3.5%] rounded-full"
        style={{
          ...ringStyle,
          background:
            "repeating-radial-gradient(circle, rgba(255,255,255,0.1) 0 2px, transparent 2px 12px)"
        }}
      />
      <div
        className="absolute inset-[20%] rounded-full border border-white/10 bg-[radial-gradient(circle_at_top,_rgba(255,255,255,0.16),_rgba(17,24,39,1)_55%,_rgba(0,0,0,1)_100%)]"
        style={ringStyle}
      >
        <div className="absolute inset-[12%] rounded-full border border-accent/30 bg-[radial-gradient(circle,_rgba(249,115,22,0.45),_rgba(35,20,7,0.92)_55%,_rgba(10,10,10,1)_100%)]" />
        <div className="absolute inset-[28%] rounded-full border border-white/20 bg-[#efefef]/10" />
        <div className="absolute left-1/2 top-1/2 h-5 w-5 -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/20 bg-white/80" />
      </div>
    </div>
  );
}
