"use client";

import { type ReactNode, useEffect, useState } from "react";
import { useMixerStore } from "@/lib/store";
import { DeckPanel } from "@/components/deck-panel";

export function MixerApp() {
  const hydrate = useMixerStore((state) => state.hydrate);
  const capabilities = useMixerStore((state) => state.capabilities);
  const micState = useMixerStore((state) => state.microphoneState);
  const toggleMicrophone = useMixerStore((state) => state.toggleMicrophone);
  const [isEqMode, setIsEqMode] = useState(false);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  return (
    <main className="min-h-screen bg-black">
      <section className="mx-auto flex min-h-screen max-w-[1660px] gap-3 p-3">
        <aside className="relative flex w-[50px] flex-col items-center rounded-2xl bg-black/90 pt-1.5">
          <RailButton
            label="toggle mic"
            icon={<MicIcon muted={!micState.isRunning} />}
            onClick={() => {
              void toggleMicrophone();
            }}
            tone="active"
          />
          <RailButton
            disabled
            icon={<HeadphoneIcon />}
            label="monitor"
            tone="inactive"
          />
          <RailButton
            icon={<BarsIcon />}
            label="levels"
            onClick={() => {
              setIsEqMode((value) => !value);
            }}
            tone={isEqMode ? "active" : "inactive"}
            className="mb-2 mt-auto"
          />
        </aside>

        <div className="flex min-w-0 flex-1 flex-col gap-3">
          <section className="grid flex-1 gap-3 xl:grid-cols-2">
            <DeckPanel deckId="left" eqActive={isEqMode} />
            <DeckPanel deckId="right" eqActive={isEqMode} />
          </section>
        </div>
      </section>
    </main>
  );
}

function RailButton({
  className,
  disabled = false,
  icon,
  label,
  onClick,
  tone
}: {
  className?: string;
  disabled?: boolean;
  icon: ReactNode;
  label: string;
  onClick?: () => void;
  tone: "active" | "inactive";
}) {
  const toneClass =
    tone === "active"
      ? "border-[#0a66d9] bg-[#0a84ff] text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.25),0_5px_10px_rgba(10,132,255,0.34)] hover:bg-[#2b95ff]"
      : "border-white/10 bg-[#101114] text-[#2a69c7] shadow-[inset_0_1px_0_rgba(255,255,255,0.04)] hover:border-white/20";

  return (
    <button
      aria-label={label}
      className={`${className ?? "mt-1"} flex h-[36px] w-[36px] items-center justify-center rounded-[9px] border transition disabled:cursor-not-allowed disabled:opacity-55 ${toneClass}`}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {icon}
    </button>
  );
}

function MicIcon({ muted }: { muted: boolean }) {
  return (
    <svg aria-hidden="true" height="18" viewBox="0 0 24 24" width="18">
      <rect
        fill="none"
        height="8"
        rx="3.6"
        stroke="currentColor"
        strokeWidth="1.6"
        width="7"
        x="8.5"
        y="4.5"
      />
      <path d="M6.5 11.8a5.5 5.5 0 1 0 11 0" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <line stroke="currentColor" strokeLinecap="round" strokeWidth="1.6" x1="12" x2="12" y1="17.6" y2="20.2" />
      <line stroke="currentColor" strokeLinecap="round" strokeWidth="1.6" x1="9" x2="15" y1="20.2" y2="20.2" />
      {muted ? (
        <line stroke="currentColor" strokeLinecap="round" strokeWidth="2" x1="5" x2="19" y1="5" y2="19" />
      ) : null}
    </svg>
  );
}

function HeadphoneIcon() {
  return (
    <svg aria-hidden="true" height="18" viewBox="0 0 24 24" width="18">
      <path d="M5.2 12a6.8 6.8 0 1 1 13.6 0" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <rect fill="currentColor" height="6.2" rx="1.6" width="2.6" x="4.2" y="11.4" />
      <rect fill="currentColor" height="6.2" rx="1.6" width="2.6" x="17.2" y="11.4" />
    </svg>
  );
}

function BarsIcon() {
  return (
    <svg aria-hidden="true" height="18" viewBox="0 0 24 24" width="18">
      <rect fill="currentColor" height="5.5" rx="0.8" width="2.8" x="4" y="14.5" />
      <rect fill="currentColor" height="8.5" rx="0.8" width="2.8" x="9.2" y="11.5" />
      <rect fill="currentColor" height="12" rx="0.8" width="2.8" x="14.4" y="8" />
      <rect fill="currentColor" height="4" rx="0.8" width="2.8" x="19.6" y="16" />
    </svg>
  );
}
