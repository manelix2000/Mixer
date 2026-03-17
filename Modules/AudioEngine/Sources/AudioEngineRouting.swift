import Foundation

public protocol AudioEngineRoutingProviding {
    var splitDeckRole: SplitDeckRole? { get }
    var panControlRange: ClosedRange<Double> { get }
}

extension AudioEngineManager: AudioEngineRoutingProviding {
    public var splitDeckRole: SplitDeckRole? { nil }
    public var panControlRange: ClosedRange<Double> { -1.0...1.0 }
}
