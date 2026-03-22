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
      <div className="absolute inset-[1.2%] rounded-full border border-black/60 bg-[radial-gradient(circle,_#111_0%,_#07090c_64%,_#050608_100%)]" />
      <div className="absolute inset-[2.9%] rounded-full border border-[#5a5a5a] bg-black" />
      <div className="absolute inset-[5.2%] rounded-full border border-[#1b1b1b] bg-[radial-gradient(circle,_#151515_0%,_#070707_76%,_#030303_100%)]" />

      <div className="absolute inset-[6.4%] rounded-full border-[8px] border-transparent bg-[radial-gradient(circle,_transparent_58%,_rgba(255,255,255,0.02)_59%,_transparent_61%),repeating-conic-gradient(rgba(230,230,230,0.62)_0_1.2deg,rgba(0,0,0,0)_1.2deg_6deg)] [mask:radial-gradient(circle,transparent_57%,black_58%,black_70%,transparent_71%)]" />
      <div className="absolute inset-[9.5%] rounded-full border-[6px] border-transparent bg-[repeating-conic-gradient(rgba(212,212,212,0.58)_0_1deg,rgba(0,0,0,0)_1deg_5.4deg)] [mask:radial-gradient(circle,transparent_66%,black_67%,black_77%,transparent_78%)]" />

      <div className="absolute inset-[12%] rounded-full border border-white/10 bg-[#07090b]" />
      <div
        className="absolute inset-[12.8%] rounded-full"
        style={{
          ...ringStyle,
          background: "repeating-radial-gradient(circle, rgba(255,255,255,0.12) 0 2px, transparent 2px 10px)"
        }}
      />
      <div
        className="absolute inset-[17%] rounded-full border border-white/10 bg-[radial-gradient(circle_at_top,_rgba(255,255,255,0.14),_rgba(15,21,30,1)_52%,_rgba(0,0,0,1)_100%)]"
        style={ringStyle}
      >
        <div className="absolute inset-[12%] rounded-full border border-accent/30 bg-[radial-gradient(circle,_rgba(249,115,22,0.46),_rgba(20,23,27,0.9)_58%,_rgba(10,10,10,1)_100%)]" />
        <div className="absolute inset-[28%] rounded-full border border-white/20 bg-[#efefef]/10" />
        <div className="absolute left-1/2 top-1/2 h-5 w-5 -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/20 bg-white/80" />
      </div>

      <div className="absolute inset-x-0 top-[22%] z-20 text-center text-[15%] font-bold italic tracking-tight text-[#6d7c96]/92 [text-shadow:0_1px_0_rgba(0,0,0,0.35)]">
        Techniks
      </div>
      <div className="absolute inset-x-0 bottom-[19%] z-20 rotate-180 text-center text-[15%] font-bold italic tracking-tight text-[#6d7c96]/80 [text-shadow:0_1px_0_rgba(0,0,0,0.35)]">
        Techniks
      </div>

      <div className="absolute right-[5.6%] top-[4.6%] z-30 h-[16.5%] w-[16.5%] rounded-full border border-black/35 bg-[radial-gradient(circle_at_36%_28%,_#dfe4ea_0%,_#98a1ab_38%,_#5d6570_72%,_#4f5761_100%)] shadow-[0_8px_16px_rgba(0,0,0,0.28)]" />
      <div className="absolute right-[8.2%] top-[7.2%] z-30 h-[11.2%] w-[11.2%] rounded-full border border-black/30 bg-[radial-gradient(circle_at_32%_24%,_#f0f3f6_0%,_#c7ced6_40%,_#8f98a3_76%,_#7c858f_100%)]" />
      <div className="absolute right-[11.35%] top-[10.55%] z-30 h-[4%] w-[4%] rounded-full border border-black/25 bg-[#5d6672]" />
      <div className="absolute right-[13.2%] top-[5%] z-30 h-[5%] w-[4.6%] rotate-[42deg] rounded-[6px] border border-black/25 bg-[linear-gradient(180deg,_#e9edf1_0%,_#adb5be_100%)]" />
      <div className="absolute right-[14.25%] top-[17%] z-30 h-[35%] w-[2.2%] origin-top rotate-[7deg] rounded-full border border-black/20 bg-[linear-gradient(180deg,_#e8edf1_0%,_#7e8793_100%)]" />
      <div className="absolute right-[17.8%] top-[45.5%] z-30 h-[22%] w-[2.05%] origin-top rotate-[18deg] rounded-full border border-black/20 bg-[linear-gradient(180deg,_#dde2e7_0%,_#6f7885_100%)]" />
      <div className="absolute right-[20.35%] top-[63.4%] z-30 h-[6%] w-[5.5%] rotate-[31deg] rounded-[6px] border border-black/35 bg-[linear-gradient(180deg,_#cfd5da_0%,_#939ba6_100%)] shadow-[0_3px_7px_rgba(0,0,0,0.28)]" />
      <div className="absolute right-[19.95%] top-[66.1%] z-30 h-[2.5%] w-[2.8%] rotate-[30deg] rounded-[2px] border border-black/35 bg-[#f1cf43]" />
    </div>
  );
}
