# Whistle & Moan Detector

The Whistle & Moan Detector finds continuous tonal vocalisations in spectrogram
data. It is primarily used to detect dolphin whistles but can also detect other
tonal sounds (moans, up-sweeps, down-sweeps).

---

## Overview

The detector operates on FFT spectrogram data and works in two steps:

1. **Peak detection** — the spectrogram is searched for local maxima that
   exceed a threshold.
2. **Peak linking** — peaks that are close in frequency and time are connected
   to form contours (the whistle shape).

---

## Settings

### Data source

Select the [FFT Engine](fft_engine.md) module that produces the spectrogram
data. The FFT length and hop determine the time/frequency resolution of the
whistle detector.

> **Tip:** Use a long FFT (1024–4096 samples) for dolphin whistle detection to
> resolve the narrow frequency structure of the whistles.

---

### Threshold

| Setting | Description |
|---------|-------------|
| Threshold (dB) | Minimum dB above background to accept a spectral peak |

The background level is estimated using a time-averaged noise model. Increase
the threshold to reduce false positives; decrease it to detect quieter whistles.

---

### Contour parameters

| Setting | Description |
|---------|-------------|
| Min whistle length (bins) | Minimum number of spectral frames for a valid contour |
| Max whistle gap (bins) | Maximum gap (in FFT frames) allowed within a contour |
| Min frequency (Hz) | Ignore peaks below this frequency |
| Max frequency (Hz) | Ignore peaks above this frequency |
| Min duration (s) | Discard contours shorter than this duration |

---

### Fragment linking

Short contour fragments that are close in time and frequency can be
automatically linked into longer contours. Enable *fragment linking* when
whistles are intermittently above threshold due to multipath or background
variability.

---

## Output

Detected whistles are stored as contour objects in the database and binary
files, containing:

- Start and end time
- Contour of frequency vs. time
- Peak and mean amplitude

---

## Tips

- For common dolphin whistles: threshold ≈ 6–10 dB, FFT length 1024–2048,
  frequency range 2–25 kHz.
- High ambient noise environments may require a higher threshold (12–15 dB)
  to avoid excessive false positives.
- Use the spectrogram display to visually check whether detected contours align
  with visible whistles.
