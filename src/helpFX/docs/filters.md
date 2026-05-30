# Filters

The Filters module applies a digital filter to an audio stream, removing
energy outside the frequency range of interest. Filtered data can then be
passed to detectors or other processing modules, reducing false detections
caused by out-of-band noise.

---

## Overview

| Setting | Description |
|---------|-------------|
| Data source | The raw audio stream to filter |
| Filter type | Algorithm used to design the filter |
| Filter band | Low-pass, high-pass, band-pass, or band-stop |
| Low frequency | Lower corner frequency (Hz) |
| High frequency | Upper corner frequency (Hz) |
| Filter order | Controls roll-off steepness vs. computational cost |

---

## Filter types

| Type | Description |
|------|-------------|
| **Butterworth** | Maximally flat passband, moderate roll-off. Good all-round choice. |
| **Chebyshev** | Steeper roll-off than Butterworth at the expense of passband ripple. |
| **FIR (windowed)** | Finite impulse response — linear phase, no distortion. Higher order needed for steep roll-off. |
| **None** | Pass-through: no filtering is applied. Useful as a placeholder. |

---

## Filter band

| Band | Effect |
|------|--------|
| **Low-pass** | Passes frequencies below the cut-off, removes above |
| **High-pass** | Passes frequencies above the cut-off, removes below |
| **Band-pass** | Passes a frequency band between low and high frequency |
| **Band-stop (notch)** | Removes a frequency band, passes outside it |

---

## Filter order

Higher order = steeper transition from passband to stopband, but more CPU
and (for IIR filters) more phase distortion.

Recommended starting points:
- Butterworth: order 4–6
- Chebyshev: order 4–6
- FIR: order 64–256 (higher values needed for steep roll-off)

---

## Frequency response preview

A frequency-response plot is displayed in the settings pane. Check that the
response matches your expectation before applying.

---

## Tips

- Use a high-pass filter to remove low-frequency noise and vessel noise from
  recordings before click detection.
- For porpoise detection: high-pass at 100–120 kHz.
- For dolphin whistle detection: band-pass approximately 2–25 kHz.
- Avoid excessively steep filters (very high order) — they can introduce
  ringing artefacts.
