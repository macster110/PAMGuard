# Decimator

The Decimator reduces the sample rate of an audio stream. Lower sample rates
reduce CPU and memory usage for downstream modules that do not need the full
bandwidth of the input signal.

---

## Overview

| Setting | Description |
|---------|-------------|
| Data source | The raw audio input to decimate |
| Channels | Which channels to decimate |
| Decimation factor | Divide input sample rate by this integer |
| Output sample rate | Calculated from input rate ÷ decimation factor |
| Pre-filter | Anti-aliasing low-pass filter (recommended) |

---

## How decimation works

Decimation is a two-step process:

1. **Anti-aliasing filter** — a low-pass filter removes energy above half the
   target sample rate (the new Nyquist frequency).  
   Without this step, high-frequency energy would fold back into the baseband
   (aliasing artefacts).
2. **Downsampling** — every N-th sample is kept; the rest are discarded.
   N is the decimation factor.

---

## Choosing a decimation factor

| Input rate | Decimation factor | Output rate | Useful for |
|-----------|-------------------|-------------|------------|
| 192 000 Hz | 4 | 48 000 Hz | Dolphin whistles / clicks |
| 192 000 Hz | 8 | 24 000 Hz | Low-frequency cetaceans |
| 96 000 Hz  | 2 | 48 000 Hz | General porpoise work |

---

## Pre-filter

The built-in anti-aliasing filter is a low-pass filter set automatically to
just below the new Nyquist frequency. You can override the filter design
(type and order) if needed — see [Filters](filters.md) for a description of
the available filter types.

---

## Tips

- Always enable the anti-aliasing pre-filter unless you have a specific reason
  to disable it.
- Use the Decimator to provide a lower-rate stream for whistle detection while
  the full-rate stream feeds the click detector.
- Avoid very high decimation factors in a single step — cascading two
  Decimators may produce cleaner results.
