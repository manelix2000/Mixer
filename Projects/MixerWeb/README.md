# MixerWeb

Web version of `dev.manelix.Mixer`, organized as a self-contained workspace under `Projects/MixerWeb`.

## Local Development

From this directory:

```bash
cd /Users/manelix/Projects/Mixer/Projects/MixerWeb
pnpm install
pnpm dev
```

Open:

```text
http://localhost:3000
```

If port `3000` is already in use, Next.js will print the alternate local URL in the terminal.

## Useful Commands

```bash
pnpm dev
pnpm build
pnpm typecheck
pnpm typecheck:all
```

## Workspace Structure

```text
Projects/MixerWeb
├─ src
│  ├─ app
│  ├─ components
│  └─ lib
├─ packages
│  ├─ audio-core
│  └─ mixer-domain
├─ package.json
├─ pnpm-workspace.yaml
└─ tsconfig.base.json
```

## Current Scope

- Next.js app shell for the browser UI
- Two-deck training layout
- Local audio file loading
- Waveform rendering
- Playback, seek, pitch, volume, and pan controls
- Browser-side BPM estimation
- Best-effort microphone BPM detection

## Known Limitations

- Scratch behavior is still implemented with seek/reschedule logic, not an `AudioWorklet` engine yet.
- BPM detection is heuristic and not equivalent to the iOS aubio pipeline.
- Microphone BPM depends on browser support, permissions, and secure context behavior.
