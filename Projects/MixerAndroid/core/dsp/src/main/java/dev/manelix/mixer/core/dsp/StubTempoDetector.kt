package dev.manelix.mixer.core.dsp

import dev.manelix.mixer.core.dsp.model.BpmResult
import dev.manelix.mixer.core.dsp.model.TempoInputBuffer

class StubTempoDetector : TempoDetector {
    override fun detectTempo(input: TempoInputBuffer): BpmResult =
        BpmResult.Unavailable(reason = "DSP backend not integrated yet")
}
