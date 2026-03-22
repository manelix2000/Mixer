"use client";

import { useEffect, useState } from "react";
import { useMixerStore } from "@/lib/store";
import { DeckPanel } from "@/components/deck-panel";

export function MixerApp() {
  const hydrate = useMixerStore((state) => state.hydrate);
  const capabilities = useMixerStore((state) => state.capabilities);
  const micState = useMixerStore((state) => state.microphoneState);
  const toggleMicrophone = useMixerStore((state) => state.toggleMicrophone);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  return (
    <main className="min-h-screen bg-black">
      <section className="mx-auto flex min-h-screen max-w-[1660px] gap-3 p-3">
        <aside className="relative flex w-[46px] flex-col items-center rounded-2xl bg-black/90 pt-1">
          <RailButton
            label="gear"
            onClick={() => {
              setIsSettingsOpen((value) => !value);
            }}
            text="G"
          />
          <RailButton label="toggle mic" text={micState.isRunning ? "M" : "m"} />
          <button
            className="mt-1 flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-[#0f0f10] text-[11px] font-semibold text-white transition hover:border-white/20 disabled:text-white/30"
            disabled={!capabilities.microphoneSupported}
            onClick={() => {
              void toggleMicrophone();
            }}
            type="button"
          >
            {micState.isRunning ? "■" : "●"}
          </button>
          {isSettingsOpen ? (
            <div className="absolute left-[52px] top-1 z-20 w-[220px] rounded-xl border border-white/10 bg-[#0f1012] p-3 shadow-[0_20px_36px_rgba(0,0,0,0.35)]">
              <div className="text-[11px] font-semibold uppercase tracking-[0.18em] text-white/65">
                Settings
              </div>
              <div className="mt-3 rounded-lg border border-white/10 bg-black/20 px-2.5 py-2 text-[11px] text-white/85">
                Next dev tools are pinned under this settings rail in development mode.
              </div>
              <div className="mt-2 text-[11px] font-semibold text-white/60">{capabilities.message}</div>
            </div>
          ) : null}
          <div className="mt-auto mb-2 flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-[#0f0f10] text-[11px] font-semibold text-white">
            EQ
          </div>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-black px-1 py-0.5">
            <div className="flex flex-wrap gap-2">
              <StatusPill text={capabilities.supported ? "WEB AUDIO READY" : "WEB AUDIO OFFLINE"} />
              <StatusPill text={micState.status.toUpperCase()} muted={!micState.isRunning} />
            </div>
            <div className="rounded-full border border-white/10 bg-[#101112] px-4 py-2 text-sm font-semibold tracking-[0.12em] text-white">
              {micState.bpmText}
            </div>
          </div>

          <section className="grid flex-1 gap-3 xl:grid-cols-2">
            <DeckPanel deckId="left" />
            <DeckPanel deckId="right" />
          </section>
        </div>
      </section>
    </main>
  );
}

function RailButton({
  label,
  onClick,
  text
}: {
  label: string;
  onClick?: () => void;
  text: string;
}) {
  return (
    <button
      aria-label={label}
      className="mt-1 flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-[#0f0f10] text-[11px] font-semibold text-white transition hover:border-white/20"
      onClick={onClick}
      type="button"
    >
      {text}
    </button>
  );
}

function StatusPill({ text, muted = false }: { text: string; muted?: boolean }) {
  return (
    <div
      className={`rounded-full border px-3 py-1.5 text-[11px] font-semibold tracking-[0.18em] ${
        muted
          ? "border-white/10 bg-[#101112] text-white/55"
          : "border-white/15 bg-[#121417] text-white"
      }`}
    >
      {text}
    </div>
  );
}
