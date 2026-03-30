package dev.manelix.mixer.core.dsp

import dev.manelix.mixer.core.dsp.model.BpmResult
import dev.manelix.mixer.core.dsp.model.TempoDetectorConfiguration
import dev.manelix.mixer.core.dsp.model.TempoInputBuffer

class FallbackTempoDetector(
    configuration: TempoDetectorConfiguration = TempoDetectorConfiguration(),
) : TempoDetector {
    private val primary = AubioTempoDetector(configuration = configuration)
    private val heuristic = HeuristicTempoDetector()
    private val stub = StubTempoDetector()

    override fun detectTempo(input: TempoInputBuffer): BpmResult {
        val primaryResult = primary.detectTempo(input)
        if (primaryResult is BpmResult.Detected) return primaryResult

        val heuristicResult = heuristic.detectTempo(input)
        return if (heuristicResult is BpmResult.Detected) heuristicResult else stub.detectTempo(input)
    }
}
