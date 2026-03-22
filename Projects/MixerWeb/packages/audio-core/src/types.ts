export type AudioCapabilityReport = {
  supported: boolean;
  microphoneSupported: boolean;
  message: string;
};

export function detectBrowserAudioCapabilities(): AudioCapabilityReport {
  if (typeof window === "undefined") {
    return {
      supported: false,
      microphoneSupported: false,
      message: "Waiting for browser runtime."
    };
  }

  const hasAudioContext =
    typeof window.AudioContext !== "undefined" ||
    typeof (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext !==
      "undefined";
  const microphoneSupported =
    hasAudioContext &&
    window.isSecureContext &&
    typeof navigator.mediaDevices !== "undefined" &&
    typeof navigator.mediaDevices.getUserMedia === "function";

  return {
    supported: hasAudioContext,
    microphoneSupported,
    message: hasAudioContext
      ? "Use modern desktop Chrome, Edge, or Safari for the best latency."
      : "This browser does not expose the Web Audio primitives required by MixerWeb."
  };
}
