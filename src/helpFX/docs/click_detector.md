# Click Detector

The Click Detector detects transient broadband pulses (echolocation clicks,
impulsive noise) in audio data. It is one of PAMGuard's primary acoustic
cetacean detection tools.

---

## Overview

The detector works in three stages:

1. **Pre-filter** — band-pass or high-pass filter applied to all channels to
   remove out-of-band noise.
2. **Trigger filter** — a second filter applied to derive the detection
   statistic (typically a high-pass at the expected click frequency).
3. **Threshold comparison** — a click is detected when the instantaneous
   energy exceeds a long-term background estimate by a set amount (dB).

---

## Settings tabs

### Source / Channels

| Setting | Description |
|---------|-------------|
| Data source | Raw audio input (typically Sound Acquisition) |
| Trigger channels | Which channels can trigger a detection |
| Storage channels | Which channels are saved with each detection |
| Channel grouping | Whether triggers on different channels count as the same event |

---

### Pre-filter

An optional band-pass or high-pass filter applied before the detection
algorithm. Use this to remove low-frequency vessel noise or other
interference that would otherwise cause false detections.

See [Filters](filters.md) for a description of filter types and parameters.

---

### Trigger filter

The trigger filter is applied to produce the signal energy used for
thresholding. It is typically set to pass the frequency range of the species
of interest (e.g. high-pass at 100 kHz for porpoise).

| Setting | Description |
|---------|-------------|
| Filter type | Butterworth, Chebyshev, FIR, or None |
| Filter band | High-pass is most common for clicks |
| Corner frequency | Set above vessel noise, below click frequency |
| Filter order | Higher = steeper roll-off |

---

### Detector thresholds

| Setting | Description |
|---------|-------------|
| Threshold (dB) | Minimum signal-to-background ratio to trigger a detection |
| Long filter (α) | Time constant for the slow background energy estimate |
| Long filter 2 (α) | Second slower background estimate (used for some trigger modes) |
| Pre-samples | Number of samples saved before the trigger point |
| Post-samples | Number of samples saved after the trigger point |
| Min separation (samples) | Minimum gap between successive detections |
| Max click length (samples) | Detections longer than this are discarded |

**Background estimate:** PAMGuard uses an exponential moving average of signal
energy. The `longFilter` parameter (α) controls how quickly the background
adapts. A smaller α gives a slower, more stable background; a larger α allows
faster adaptation to changing conditions.

---

### Classification

The Classification tab controls automatic species identification after
detection. See [Matched Template Classifier](matched_template_classifier.md)
and [Deep Learning Classifier](deep_learning_classifier.md) for details on
available classifiers.

---

## Tips

- Start with a threshold of 10–12 dB and adjust based on your false positive
  rate.
- For porpoise clicks: pre-filter high-pass at 100 kHz, trigger filter
  high-pass at 100 kHz, threshold ≈ 10 dB.
- Increase pre-samples and post-samples if click waveforms appear clipped at
  the start or end in the Click display.
- Use the min separation parameter to prevent a single long click from
  producing many spurious detections.
