# AGENTS.md

You are implementing a production-minded iOS app called dev.manelix.Mixer. 

Read `index.md` first. Then use these documents as the source of truth for architecture and execution:
- specs/codex_spec.md
- specs/codex_execution_plan.md
- specs/architecture_diagram.md
- specs/audio_engine_deep_spec.md
- specs/turntable_physics_spec.md
- specs/aubio_ios_integration_spec.md
- specs/audio_latency_spec.md

Rules:
- keep compilation passing after every phase
- implement one phase at a time
- prefer official Apple APIs
- keep DSP isolated in a dedicated module
- do not implement future phases early
- summarize changed files after each task

For each phase:
1. implement only the requested scope
2. update project files as needed
3. keep all targets compiling
4. provide a short summary of files created/changed
5. list any known limitations introduced intentionally