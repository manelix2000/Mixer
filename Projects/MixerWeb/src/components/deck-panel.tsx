"use client";

import { useEffect, useRef, useState } from "react";
import type { DeckId } from "@mixer/domain";
import { useMixerStore } from "@/lib/store";
import { PlatterView } from "@/components/platter-view";
import { WaveformCanvas } from "@/components/waveform-canvas";

type DeckPanelProps = {
  deckId: DeckId;
  eqActive?: boolean;
};

export function DeckPanel({ deckId, eqActive = false }: DeckPanelProps) {
  const deck = useMixerStore((state) => state.decks[deckId]);
  const micState = useMixerStore((state) => state.microphoneState);
  const syncDeck = useMixerStore((state) => state.syncDeck);
  const loadTrack = useMixerStore((state) => state.loadTrack);
  const toggleDeckPlayback = useMixerStore((state) => state.toggleDeckPlayback);
  const stopDeck = useMixerStore((state) => state.stopDeck);
  const setDeckRate = useMixerStore((state) => state.setDeckRate);
  const setDeckVolume = useMixerStore((state) => state.setDeckVolume);
  const setDeckPan = useMixerStore((state) => state.setDeckPan);
  const setDeckEq = useMixerStore((state) => state.setDeckEq);
  const seekDeckNormalized = useMixerStore((state) => state.seekDeckNormalized);
  const [waveformZoom, setWaveformZoom] = useState(1);
  const [isPitchDragging, setIsPitchDragging] = useState(false);
  const [pitchSensitivityPercent, setPitchSensitivityPercent] = useState(8);
  const [isEqOverlayRendered, setIsEqOverlayRendered] = useState(eqActive);
  const [isEqOverlayVisible, setIsEqOverlayVisible] = useState(eqActive);
  const pressureBendRef = useRef<{
    active: boolean;
    direction: -1 | 1;
    startRate: number;
    startTimestampMs: number;
    rafId: number | null;
  }>({
    active: false,
    direction: 1,
    startRate: 1,
    startTimestampMs: 0,
    rafId: null
  });

  const detectedBpm = deck.bpmResult?.kind === "detected" ? deck.bpmResult.bpm : null;
  const targetBpmText = detectedBpm ? (detectedBpm * deck.rate).toFixed(1) : "--.-";
  const pitchMinRate = 1 - (pitchSensitivityPercent / 100);
  const pitchMaxRate = 1 + (pitchSensitivityPercent / 100);
  const absolutePitchMinRate = 0.84;
  const absolutePitchMaxRate = 1.16;
  const micOverlayText = micState.isRunning
    ? (micState.bpmText !== "-- BPM" ? `Listening to MIC... ${micState.bpmText}` : "Listening to MIC...")
    : null;

  useEffect(() => {
    let frame = 0;

    const loop = () => {
      syncDeck(deckId);
      frame = window.requestAnimationFrame(loop);
    };

    frame = window.requestAnimationFrame(loop);
    return () => window.cancelAnimationFrame(frame);
  }, [deckId, syncDeck]);

  useEffect(() => {
    return () => {
      const interaction = pressureBendRef.current;
      if (interaction.rafId !== null) {
        window.cancelAnimationFrame(interaction.rafId);
        interaction.rafId = null;
      }
    };
  }, []);

  useEffect(() => {
    let hideTimer: number | null = null;
    if (eqActive) {
      setIsEqOverlayRendered(true);
      const frameId = window.requestAnimationFrame(() => {
        setIsEqOverlayVisible(true);
      });
      return () => window.cancelAnimationFrame(frameId);
    }

    setIsEqOverlayVisible(false);
    hideTimer = window.setTimeout(() => {
      setIsEqOverlayRendered(false);
    }, 190);

    return () => {
      if (hideTimer !== null) {
        window.clearTimeout(hideTimer);
      }
    };
  }, [eqActive]);

  useEffect(() => {
    if (pressureBendRef.current.active) {
      return;
    }
    const clamped = Math.min(Math.max(deck.rate, pitchMinRate), pitchMaxRate);
    if (Math.abs(clamped - deck.rate) > 0.0001) {
      void setDeckRate(deckId, clamped);
    }
  }, [deck.rate, deckId, pitchMaxRate, pitchMinRate, setDeckRate]);

  const applyPressureBendFrame = () => {
    const interaction = pressureBendRef.current;
    if (!interaction.active) {
      interaction.rafId = null;
      return;
    }

    const elapsedMs = performance.now() - interaction.startTimestampMs;
    const normalizedPressure = Math.min(Math.max(elapsedMs / 460, 0.12), 1);
    const pressureCurve = Math.pow(normalizedPressure, 1.6);
    const multiplier =
      interaction.direction < 0
        ? Math.max(1.0 - (pressureCurve * 0.9), 0.08)
        : Math.min(1.0 + (pressureCurve * 0.9), 1.92);

    const adjustedRate = Math.min(
      Math.max(interaction.startRate * multiplier, absolutePitchMinRate),
      absolutePitchMaxRate
    );
    void setDeckRate(deckId, adjustedRate);
    interaction.rafId = window.requestAnimationFrame(applyPressureBendFrame);
  };

  const beginPressureBend = (direction: -1 | 1) => {
    const interaction = pressureBendRef.current;
    if (interaction.active) {
      interaction.direction = direction;
      return;
    }

    interaction.active = true;
    interaction.direction = direction;
    interaction.startRate = deck.rate;
    interaction.startTimestampMs = performance.now();

    const tapNudgeRate = Math.min(
      Math.max(deck.rate + (direction * 0.01), absolutePitchMinRate),
      absolutePitchMaxRate
    );
    void setDeckRate(deckId, tapNudgeRate);

    interaction.rafId = window.requestAnimationFrame(applyPressureBendFrame);
  };

  const updatePressureBend = (direction: -1 | 1) => {
    pressureBendRef.current.direction = direction;
  };

  const endPressureBend = () => {
    const interaction = pressureBendRef.current;
    if (!interaction.active) {
      return;
    }

    interaction.active = false;
    if (interaction.rafId !== null) {
      window.cancelAnimationFrame(interaction.rafId);
      interaction.rafId = null;
    }
    void setDeckRate(deckId, interaction.startRate);
  };

  return (
    <article className="relative grid min-h-0 grid-rows-[auto_auto_minmax(0,1fr)] gap-3 rounded-2xl bg-[#111214] p-0">
      <div className="flex items-center gap-2 rounded-md border border-black/12 bg-[#b4babf] px-2 py-1.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.45)]">
        <div className="h-10 w-10 shrink-0 overflow-hidden rounded-md border border-black/30 bg-[linear-gradient(180deg,_#dfe4e8_0%,_#a6adb4_100%)] shadow-[inset_0_1px_0_rgba(255,255,255,0.45)]">
          {deck.artworkDataUrl ? (
            <img
              alt=""
              className="h-full w-full object-cover"
              draggable={false}
              src={deck.artworkDataUrl}
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center text-[10px] font-bold uppercase tracking-[0.08em] text-black/55">
              ART
            </div>
          )}
        </div>
        <PanFader
          onChange={(value) => {
            void setDeckPan(deckId, value);
          }}
          value={deck.pan}
        />
      </div>

      <div className="h-fit rounded-xl bg-[linear-gradient(180deg,_#d8dde0_0%,_#a7afb6_46%,_#8b939a_100%)] p-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.38)]">
        <div className="mb-2 flex items-center justify-between gap-3 text-black/80">
          <div className="min-w-0">
            <div className="truncate text-[27px] font-semibold tracking-tight text-black/85">
              {deck.trackName ?? "No Track"}
            </div>
          </div>
          <div className="text-sm font-semibold tabular-nums">{deck.currentTimeText}</div>
        </div>

        <div className="grid grid-cols-[42px_minmax(0,1fr)] items-stretch gap-2">
          <div className="h-[60px]">
            <label className="inline-flex h-full w-full cursor-pointer items-center justify-center rounded-[11px] border border-[#0a66d9] bg-[#0a84ff] text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.28),0_6px_12px_rgba(10,132,255,0.34)]">
              <FolderIcon />
              <input
                accept="audio/*"
                className="hidden"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) {
                    void loadTrack(deckId, file);
                  }
                  event.currentTarget.value = "";
                }}
                type="file"
              />
            </label>
          </div>

          <div className="relative h-[60px]">
            <WaveformCanvas
              progress={deck.progress}
              samples={deck.waveform}
              zoom={waveformZoom}
              heightClassName="h-[60px]"
              onSeek={deck.duration > 0 ? (progress) => void seekDeckNormalized(deckId, progress) : undefined}
            />
            {isPitchDragging ? (
              <div className="pointer-events-none absolute inset-0 z-30">
                <div className="flex h-full w-full items-center justify-center rounded-md border border-white/12 bg-[#666]/95 text-[50px] font-bold leading-none tracking-tight text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.18)]">
                  {targetBpmText} BPM
                </div>
              </div>
            ) : null}

            <div className="pointer-events-none absolute inset-x-2 top-1/2 z-10 flex -translate-y-1/2 items-center justify-between">
              <button
                aria-label="Zoom in"
                className="pointer-events-auto flex h-7 w-7 items-center justify-center rounded-md border border-white/25 bg-[linear-gradient(180deg,_rgba(255,255,255,0.24),_rgba(255,255,255,0.08))] text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.25)] backdrop-blur"
                onClick={() => {
                  setWaveformZoom((value) => Math.min(value + 0.2, 3.5));
                }}
                type="button"
              >
                <MagnifierIcon withPlus />
              </button>
              <button
                aria-label="Zoom out"
                className="pointer-events-auto flex h-7 w-7 items-center justify-center rounded-md border border-white/25 bg-[linear-gradient(180deg,_rgba(255,255,255,0.24),_rgba(255,255,255,0.08))] text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.25)] backdrop-blur"
                onClick={() => {
                  setWaveformZoom((value) => Math.max(value - 0.2, 0.4));
                }}
                type="button"
              >
                <MagnifierIcon withPlus={false} />
              </button>
            </div>
          </div>
        </div>

        <div className="mt-1 flex items-center justify-between gap-3 text-[11px] font-semibold text-black/60">
          <div className="tabular-nums">
            {targetBpmText} BPM {deck.rate.toFixed(3)}x
          </div>
          <div className="text-right tabular-nums">
            {deck.bpmResult?.kind === "detected"
              ? `Detected ${deck.bpmResult.bpm.toFixed(1)} BPM (acc. ${deck.bpmResult.confidence.toFixed(2)})`
              : "Detected --.- BPM (acc. --.--)"}
          </div>
        </div>

      </div>

      <div className="relative h-full min-h-0 rounded-xl bg-[linear-gradient(180deg,_#d8dde0_0%,_#9aa1a7_45%,_#848b92_100%)] p-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.35),0_22px_40px_rgba(0,0,0,0.35)]">
        <div className="grid h-full min-h-0 items-stretch gap-2 lg:grid-cols-[max-content_minmax(0,1fr)_max-content]">
          <div className="flex h-full min-h-0 w-fit justify-self-start flex-row gap-3 lg:flex-col lg:items-start">
            <VerticalFader
              label="VOL"
              mode="volume"
              max={1}
              min={0}
              onChange={(value) => {
                void setDeckVolume(deckId, value);
              }}
              thumbPopoverSide="none"
              value={deck.volume}
            />
          </div>

          <div className="relative flex w-full min-w-0 justify-self-stretch items-center justify-center rounded-xl border border-black/15 bg-[linear-gradient(180deg,_rgba(255,255,255,0.1),_rgba(0,0,0,0.04))] p-2">
            <PlatterView
              angleDegrees={deck.platterDegrees}
              artworkDataUrl={deck.artworkDataUrl}
              onPressureBendEnd={endPressureBend}
              onPressureBendMove={updatePressureBend}
              onPressureBendStart={beginPressureBend}
            />
            {deck.statusMessage ? (
              <div className="pointer-events-none absolute left-3 top-3 rounded-full bg-white/50 px-2.5 py-1 text-[10px] font-semibold text-black/70 backdrop-blur">
                {deck.statusMessage}
              </div>
            ) : null}
            <div className="absolute bottom-3 left-3">
              <ChromeButton
                disabled={!deck.isLoaded}
                glowState={deck.isPlaying ? "playing" : (deck.isLoaded ? "ready" : "none")}
                onClick={() => {
                  void toggleDeckPlayback(deckId);
                }}
                text={deck.isPlaying ? "PAUSE" : "START"}
              />
            </div>
            {micOverlayText ? (
              <div className="mixer-mic-glow pointer-events-none absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-white/50 px-3 py-1 text-[10px] font-semibold text-black/75 backdrop-blur">
                {micOverlayText}
              </div>
            ) : null}
            <div className="absolute bottom-3 right-3">
              <ChromeButton
                disabled={!deck.isLoaded}
                glowState="none"
                onClick={() => {
                  void stopDeck(deckId);
                }}
                text="STOP"
              />
            </div>
          </div>

          <div className="flex h-full min-h-0 w-fit justify-self-end flex-row gap-3 lg:flex-col lg:items-end">
            <div className="flex h-full min-h-0 flex-col items-center">
              <VerticalFader
                label="PITCH"
                mode="pitch"
                max={pitchMaxRate}
                min={pitchMinRate}
                onInteractionChange={setIsPitchDragging}
                onChange={(value) => {
                  void setDeckRate(deckId, value);
                }}
                thumbPopoverSize="large"
                thumbPopoverSide="left"
                value={deck.rate}
              />
              <div className="mt-2 flex items-center gap-1">
                <button
                  aria-label="Increase pitch sensitivity"
                  className="flex h-6 w-8 items-center justify-center rounded-[3px] border border-black/55 bg-[linear-gradient(180deg,_#efefef_0%,_#d7d7d7_100%)] text-[18px] font-bold leading-none text-black/75 shadow-[inset_0_1px_0_rgba(255,255,255,0.75)]"
                  onClick={() => {
                    setPitchSensitivityPercent((value) => {
                      if (value >= 16) {
                        return value;
                      }
                      return value * 2;
                    });
                  }}
                  type="button"
                >
                  +
                </button>
                <button
                  aria-label="Decrease pitch sensitivity"
                  className="flex h-6 w-8 items-center justify-center rounded-[3px] border border-black/55 bg-[linear-gradient(180deg,_#efefef_0%,_#d7d7d7_100%)] text-[18px] font-bold leading-none text-black/75 shadow-[inset_0_1px_0_rgba(255,255,255,0.75)]"
                  onClick={() => {
                    setPitchSensitivityPercent((value) => {
                      if (value <= 2) {
                        return value;
                      }
                      return Math.round(value * 0.5);
                    });
                  }}
                  type="button"
                >
                  -
                </button>
              </div>
              <div className="mt-1 text-[11px] font-semibold tracking-tight text-black/65">
                ±{pitchSensitivityPercent}%
              </div>
            </div>
          </div>
        </div>
        {isEqOverlayRendered ? (
          <EQOverlay
            eqHigh={deck.eqHigh}
            eqLow={deck.eqLow}
            eqMid={deck.eqMid}
            isVisible={isEqOverlayVisible}
            onHighChange={(value) => {
              void setDeckEq(deckId, deck.eqLow, deck.eqMid, value);
            }}
            onLowChange={(value) => {
              void setDeckEq(deckId, value, deck.eqMid, deck.eqHigh);
            }}
            onMidChange={(value) => {
              void setDeckEq(deckId, deck.eqLow, value, deck.eqHigh);
            }}
          />
        ) : null}
      </div>
    </article>
  );
}

function EQOverlay({
  eqHigh,
  eqLow,
  eqMid,
  isVisible,
  onHighChange,
  onLowChange,
  onMidChange
}: {
  eqHigh: number;
  eqLow: number;
  eqMid: number;
  isVisible: boolean;
  onHighChange: (value: number) => void;
  onLowChange: (value: number) => void;
  onMidChange: (value: number) => void;
}) {
  return (
    <div
      className={`absolute inset-0 z-[80] overflow-hidden rounded-xl border border-white/30 bg-white/42 backdrop-blur-[7px] transition-opacity duration-200 ease-out ${
        isVisible ? "pointer-events-auto opacity-100" : "pointer-events-none opacity-0"
      }`}
    >
      <div className="h-full w-full bg-[rgba(255,255,255,0.5)]" />
      <div className="absolute left-3 top-3 z-10 flex items-center gap-1.5 text-[31px] font-bold text-black/85">
        <MiniBarsIcon />
        <span className="text-[31px] leading-none tracking-tight">EQ</span>
      </div>
      <div className="absolute inset-0 flex items-center justify-center px-8">
        <div className="flex items-end justify-center gap-4">
          <div className="h-[270px] w-[82px]">
            <VerticalFader
              label="LOW"
              labelPosition="bottom"
              mode="volume"
              max={1}
              min={0}
              onChange={onLowChange}
              thumbPopoverSide="none"
              value={eqLow}
            />
          </div>
          <div className="h-[270px] w-[82px]">
            <VerticalFader
              label="MID"
              labelPosition="bottom"
              mode="volume"
              max={1}
              min={0}
              onChange={onMidChange}
              thumbPopoverSide="none"
              value={eqMid}
            />
          </div>
          <div className="h-[270px] w-[82px]">
            <VerticalFader
              label="HIGH"
              labelPosition="bottom"
              mode="volume"
              max={1}
              min={0}
              onChange={onHighChange}
              thumbPopoverSide="none"
              value={eqHigh}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function MiniBarsIcon() {
  return (
    <svg aria-hidden="true" height="20" viewBox="0 0 24 24" width="20">
      <rect fill="currentColor" height="7.5" rx="0.9" width="3.2" x="3.3" y="13.5" />
      <rect fill="currentColor" height="11" rx="0.9" width="3.2" x="9.1" y="10" />
      <rect fill="currentColor" height="15" rx="0.9" width="3.2" x="14.9" y="6" />
    </svg>
  );
}

function FolderIcon() {
  return (
    <svg aria-hidden="true" height="23" viewBox="0 0 24 24" width="23">
      <path
        d="M3.2 7.3a2.1 2.1 0 0 1 2.1-2.1h4.7l1.9 1.9h6.8a2.1 2.1 0 0 1 2.1 2.1v6.9a2.9 2.9 0 0 1-2.9 2.9H6.1a2.9 2.9 0 0 1-2.9-2.9V7.3z"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.9"
      />
      <circle cx="17.9" cy="8.1" fill="currentColor" r="3.1" />
      <line stroke="#0a84ff" strokeLinecap="round" strokeWidth="1.7" x1="17.9" x2="17.9" y1="6.8" y2="9.4" />
      <line stroke="#0a84ff" strokeLinecap="round" strokeWidth="1.7" x1="16.6" x2="19.2" y1="8.1" y2="8.1" />
    </svg>
  );
}

function MagnifierIcon({ withPlus }: { withPlus: boolean }) {
  return (
    <svg aria-hidden="true" height="15" viewBox="0 0 24 24" width="15">
      <circle cx="10" cy="10" fill="none" r="6" stroke="currentColor" strokeWidth="2" />
      <line stroke="currentColor" strokeLinecap="round" strokeWidth="2" x1="14.5" x2="20" y1="14.5" y2="20" />
      <line stroke="currentColor" strokeLinecap="round" strokeWidth="2" x1="7.2" x2="12.8" y1="10" y2="10" />
      {withPlus ? (
        <line stroke="currentColor" strokeLinecap="round" strokeWidth="2" x1="10" x2="10" y1="7.2" y2="12.8" />
      ) : null}
    </svg>
  );
}

function PanFader({
  onChange,
  value
}: {
  onChange: (value: number) => void;
  value: number;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [isDraggingFromThumb, setIsDraggingFromThumb] = useState(false);

  const rangeMin = -1;
  const rangeMax = 1;
  const thumbWidth = 34;
  const faderWidth = Math.max((containerRef.current?.clientWidth ?? 156), 1);
  const usableWidth = Math.max(faderWidth - thumbWidth, 1);
  const progress = normalizedProgress(value, rangeMin, rangeMax);
  const thumbX = progress * usableWidth;
  const baselineProgress = normalizedProgress(0, rangeMin, rangeMax);
  const selectedWidth = Math.max(Math.abs(progress - baselineProgress) * faderWidth, 2);
  const selectedMidpoint = (progress + baselineProgress) * 0.5;

  return (
    <div
      ref={containerRef}
      className="relative h-7 w-full touch-none"
      onPointerDown={(event) => {
        if (!containerRef.current) {
          return;
        }

        const rect = containerRef.current.getBoundingClientRect();
        const x = Math.min(Math.max(event.clientX - rect.left, 0), faderWidth);
        const thumbCenterX = thumbX + (thumbWidth * 0.5);
        const startedOnThumb = Math.abs(x - thumbCenterX) <= (thumbWidth * 0.8);
        if (!startedOnThumb) {
          return;
        }

        setIsDraggingFromThumb(true);
        event.currentTarget.setPointerCapture(event.pointerId);
      }}
      onPointerMove={(event) => {
        if (!isDraggingFromThumb || !containerRef.current) {
          return;
        }

        const rect = containerRef.current.getBoundingClientRect();
        const x = Math.min(Math.max(event.clientX - rect.left, 0), faderWidth);
        const mappedProgress = x / faderWidth;
        onChange(mappedValue(mappedProgress, rangeMin, rangeMax));
      }}
      onPointerUp={(event) => {
        if (event.currentTarget.hasPointerCapture(event.pointerId)) {
          event.currentTarget.releasePointerCapture(event.pointerId);
        }
        setIsDraggingFromThumb(false);
      }}
      onPointerCancel={() => {
        setIsDraggingFromThumb(false);
      }}
      role="presentation"
    >
      <div className="absolute inset-x-0 top-1/2 h-[10px] -translate-y-1/2 rounded-full border border-black/20 bg-[linear-gradient(180deg,_#eceff1_0%,_#c8ced3_100%)]" />
      <div
        className="absolute top-1/2 h-[10px] -translate-y-1/2 rounded-full bg-sky-400/35"
        style={{
          width: `${selectedWidth}px`,
          left: `${(selectedMidpoint * faderWidth) - (selectedWidth * 0.5)}px`
        }}
      />
      <div className="absolute left-1/2 top-1/2 h-[20px] w-px -translate-x-1/2 -translate-y-1/2 bg-black/35" />

      <div
        className="absolute top-1/2 flex h-[26px] w-[34px] -translate-y-1/2 items-center justify-center rounded-md border border-black/30 bg-[linear-gradient(180deg,_#f9fafb_0%,_#e2e8ec_100%)] text-[11px] font-semibold text-black/80 shadow-[0_1px_2px_rgba(0,0,0,0.14)]"
        style={{
          left: `${thumbX}px`
        }}
      >
        {panRoutingText(value)}
      </div>
    </div>
  );
}

function normalizedProgress(value: number, lowerBound: number, upperBound: number): number {
  const clamped = Math.min(Math.max(value, lowerBound), upperBound);
  const span = upperBound - lowerBound;
  if (span <= 0) {
    return 0.5;
  }
  return (clamped - lowerBound) / span;
}

function mappedValue(progress: number, lowerBound: number, upperBound: number): number {
  const clamped = Math.min(Math.max(progress, 0), 1);
  const span = upperBound - lowerBound;
  return lowerBound + (clamped * span);
}

function panRoutingText(pan: number): string {
  if (pan < -0.1) {
    return "L";
  }
  if (pan > 0.1) {
    return "R";
  }
  return "C";
}

function ChromeButton({
  disabled,
  glowState = "none",
  onClick,
  text
}: {
  disabled?: boolean;
  glowState?: "none" | "ready" | "playing";
  onClick: () => void;
  text: string;
}) {
  const glowClass =
    glowState === "playing"
      ? "mixer-glow-playing"
      : glowState === "ready"
        ? "mixer-glow-ready"
        : "";

  return (
    <button
      className={`min-w-[76px] rounded-sm border border-black/40 bg-[linear-gradient(180deg,_#f6f2de_0%,_#d5d1bf_100%)] px-3 py-1.5 text-[10px] font-bold tracking-[0.22em] text-black/75 shadow-[inset_0_1px_0_rgba(255,255,255,0.7),0_0_0_2px_rgba(215,180,55,0.12)] disabled:opacity-40 ${glowClass}`}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {text}
    </button>
  );
}

function VerticalFader({
  label,
  labelPosition = "top",
  mode,
  max,
  min,
  onInteractionChange,
  onChange,
  thumbPopoverSize = "regular",
  thumbPopoverSide = "none",
  readOnly = false,
  value
}: {
  label: string;
  labelPosition?: "top" | "bottom";
  mode: "volume" | "pitch";
  max: number;
  min: number;
  onInteractionChange?: (isInteracting: boolean) => void;
  onChange: (value: number) => void;
  thumbPopoverSize?: "compact" | "regular" | "large";
  thumbPopoverSide?: "none" | "left" | "right";
  readOnly?: boolean;
  value: number;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [isDraggingFromThumb, setIsDraggingFromThumb] = useState(false);
  const [isThumbPopoverVisible, setIsThumbPopoverVisible] = useState(false);
  const popoverHideTimeoutRef = useRef<number | null>(null);
  const thumbSize = 26;
  const height = Math.max((containerRef.current?.clientHeight ?? 220), 1);
  const usableHeight = Math.max(height - thumbSize, 1);
  const valueProgress = normalizedProgress(value, min, max);
  const visualValueProgress = mode === "pitch" ? (1.0 - valueProgress) : valueProgress;
  const thumbY = (1.0 - visualValueProgress) * usableHeight;
  const thumbCenterY = thumbY + (thumbSize * 0.5);
  const baselineValue = mode === "pitch" ? 1.0 : min;
  const baselineProgress = normalizedProgress(baselineValue, min, max);
  const visualBaselineProgress = mode === "pitch" ? (1.0 - baselineProgress) : baselineProgress;
  const valueY = (1.0 - visualValueProgress) * height;
  const baselineY = (1.0 - visualBaselineProgress) * height;
  const selectedTop = Math.min(valueY, baselineY);
  const selectedHeight = Math.max(Math.abs(valueY - baselineY), 2);
  const thumbText =
    mode === "volume"
      ? `${Math.round(value * 100)}%`
      : `${(((value - 1) * 100) >= 0 ? "+" : "")}${((value - 1) * 100).toFixed(1)}%`;
  const popoverMetrics = popoverStyleMetrics(thumbPopoverSize);
  const popoverAnchorStyle =
    thumbPopoverSide === "left"
      ? {
          top: `${thumbCenterY}px`,
          right: `calc(100% + 8px)`,
          transform: "translateY(-50%)"
        }
      : {
          top: `${thumbCenterY}px`,
          left: `calc(100% + 8px)`,
          transform: "translateY(-50%)"
        };

  useEffect(() => {
    return () => {
      if (popoverHideTimeoutRef.current !== null) {
        window.clearTimeout(popoverHideTimeoutRef.current);
        popoverHideTimeoutRef.current = null;
      }
    };
  }, []);

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col items-center rounded-xl bg-black/10 px-2 py-3">
      {labelPosition === "top" ? (
        <div className="mb-3 text-[10px] font-semibold uppercase tracking-[0.22em] text-black/65">
          {label}
        </div>
      ) : null}
      <div
        ref={containerRef}
        className="relative min-h-0 w-[34px] flex-1 touch-none"
        onPointerDown={(event) => {
          if (readOnly) {
            return;
          }
          if (!containerRef.current) {
            return;
          }
          const rect = containerRef.current.getBoundingClientRect();
          const y = Math.min(Math.max(event.clientY - rect.top, 0), height);
          const startedOnThumb = Math.abs(y - thumbCenterY) <= (thumbSize * 0.8);
          if (!startedOnThumb) {
            return;
          }
          if (popoverHideTimeoutRef.current !== null) {
            window.clearTimeout(popoverHideTimeoutRef.current);
            popoverHideTimeoutRef.current = null;
          }
          if (thumbPopoverSide !== "none") {
            setIsThumbPopoverVisible(true);
          }
          onInteractionChange?.(true);
          setIsDraggingFromThumb(true);
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerMove={(event) => {
          if (readOnly) {
            return;
          }
          if (!isDraggingFromThumb || !containerRef.current) {
            return;
          }
          const rect = containerRef.current.getBoundingClientRect();
          const y = Math.min(Math.max(event.clientY - rect.top, 0), height);
          const pointerProgress = 1 - (y / height);
          const mappedProgress = mode === "pitch" ? (1 - pointerProgress) : pointerProgress;
          onChange(mappedValue(mappedProgress, min, max));
          if (thumbPopoverSide !== "none") {
            setIsThumbPopoverVisible(true);
          }
        }}
        onPointerUp={(event) => {
          if (readOnly) {
            return;
          }
          if (event.currentTarget.hasPointerCapture(event.pointerId)) {
            event.currentTarget.releasePointerCapture(event.pointerId);
          }
          setIsDraggingFromThumb(false);
          onInteractionChange?.(false);
          if (thumbPopoverSide !== "none") {
            popoverHideTimeoutRef.current = window.setTimeout(() => {
              setIsThumbPopoverVisible(false);
              popoverHideTimeoutRef.current = null;
            }, 800);
          }
        }}
        onPointerCancel={() => {
          if (readOnly) {
            return;
          }
          setIsDraggingFromThumb(false);
          onInteractionChange?.(false);
          setIsThumbPopoverVisible(false);
          if (popoverHideTimeoutRef.current !== null) {
            window.clearTimeout(popoverHideTimeoutRef.current);
            popoverHideTimeoutRef.current = null;
          }
        }}
        onClick={(event) => {
          if (readOnly) {
            return;
          }
          if (!containerRef.current || thumbPopoverSide === "none") {
            return;
          }
          const rect = containerRef.current.getBoundingClientRect();
          const y = Math.min(Math.max(event.clientY - rect.top, 0), height);
          const tappedThumb = Math.abs(y - thumbCenterY) <= (thumbSize * 0.8);
          if (!tappedThumb) {
            return;
          }
          if (popoverHideTimeoutRef.current !== null) {
            window.clearTimeout(popoverHideTimeoutRef.current);
          }
          setIsThumbPopoverVisible(true);
          popoverHideTimeoutRef.current = window.setTimeout(() => {
            setIsThumbPopoverVisible(false);
            popoverHideTimeoutRef.current = null;
          }, 1100);
        }}
        role="presentation"
      >
        <div className="absolute left-1/2 top-0 h-full w-[10px] -translate-x-1/2 rounded-full border border-black/20 bg-[linear-gradient(180deg,_#eceff1_0%,_#c8ced3_100%)]" />
        <div
          className="absolute left-1/2 w-[10px] -translate-x-1/2 rounded-full bg-sky-400/35"
          style={{
            height: `${selectedHeight}px`,
            top: `${selectedTop}px`
          }}
        />
        <div className="absolute left-1/2 top-1/2 h-px w-[20px] -translate-x-1/2 -translate-y-1/2 bg-black/35" />
        <div
          className="absolute left-1/2 flex h-[26px] w-[34px] -translate-x-1/2 items-center justify-center rounded-md border border-black/25 bg-[linear-gradient(180deg,_#f9fafb_0%,_#e1e7ec_100%)] text-[9px] font-semibold tabular-nums text-black/75 shadow-[0_1px_2px_rgba(0,0,0,0.18)]"
          style={{
            top: `${thumbY}px`
          }}
        >
          {thumbText}
        </div>
        {isThumbPopoverVisible && thumbPopoverSide !== "none" ? (
          <div className="pointer-events-none absolute z-[2147483647]" style={popoverAnchorStyle}>
            <div className="relative">
              <div
                className="flex items-center justify-center rounded-[12px] border border-black/25 bg-[#f9f9f9] px-2 text-black shadow-[0_2px_6px_rgba(0,0,0,0.16)]"
                style={{
                  width: `${popoverMetrics.width}px`,
                  height: `${popoverMetrics.height}px`,
                  fontSize: `${popoverMetrics.fontSize}px`,
                  fontWeight: 700
                }}
              >
                {thumbText}
              </div>
              <div
                className="absolute top-1/2 h-[12px] w-[12px] -translate-y-1/2 rotate-45 border border-black/25 bg-[#f9f9f9]"
                style={
                  thumbPopoverSide === "left"
                    ? { right: "-6px" }
                    : { left: "-6px" }
                }
              />
            </div>
          </div>
        ) : null}
      </div>
      {labelPosition === "bottom" ? (
        <div className="mt-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-black/55">
          {label}
        </div>
      ) : null}
    </div>
  );
}

function popoverStyleMetrics(size: "compact" | "regular" | "large"): {
  width: number;
  height: number;
  fontSize: number;
} {
  switch (size) {
    case "compact":
      return { width: 64, height: 28, fontSize: 12 };
    case "regular":
      return { width: 80, height: 34, fontSize: 14 };
    case "large":
      return { width: 104, height: 40, fontSize: 16 };
  }
}
