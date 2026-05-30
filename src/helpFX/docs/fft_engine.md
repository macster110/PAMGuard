# FFT Engine

The FFT Engine converts time-domain audio data into the
frequency-domain spectrograms that most PAMGuard detectors and displays
consume. One or more FFT Engine modules can exist in a PAMGuard configuration,
each with its own settings.

---

## Overview

| Setting | Description |
|---------|-------------|
| Data source | The raw audio channel(s) to process |
| Channel map | Which channels to include in this FFT |
| FFT length | Number of samples per FFT frame (power of two) |
| FFT hop | Step size between successive FFT frames (samples) |
| Window function | Weighting applied to each frame before the FFT |

---

## Data source and channels

Select the raw audio source (typically a Sound Acquisition module) from the
**Data source** drop-down. Then use the **channel** checkboxes to choose
which channels this FFT Engine will process.

Multiple FFT Engines can be added to the model — use this to run different
FFT configurations on the same input (e.g. a coarse low-resolution FFT for
display and a fine high-resolution FFT for a click detector).

---

## FFT length

The FFT length controls the **frequency resolution** of the spectrogram:

- A longer FFT gives finer frequency resolution but poorer time resolution.
- A shorter FFT gives finer time resolution but poorer frequency resolution.

FFT length must be a **power of two** (64, 128, 256, 512, 1024, 2048, 4096, …).

**Frequency resolution** = sample rate ÷ FFT length  
**Time resolution** = FFT hop ÷ sample rate

---

## FFT hop (overlap)

The hop is the number of samples between successive FFT frames. It must be
less than or equal to the FFT length:

- Hop = FFT length → 0% overlap (no redundancy, fastest)
- Hop = FFT length / 2 → 50% overlap (standard spectrogram)
- Smaller hop → more overlap → smoother spectrogram, more CPU

A 50% overlap (hop = FFT length / 2) is recommended for most applications.

---

## Window function

The window function reduces spectral leakage by tapering the signal at the
edges of each frame:

| Window | Best for |
|--------|----------|
| Hanning | General-purpose spectrograms |
| Hamming | Moderate leakage reduction |
| Blackman | Lowest sidelobes, widest main lobe |
| Rectangular | Highest frequency resolution (no tapering) |

For most cetacean work, **Hanning** is a good default.

---

## Spectrogram noise management

The FFT Engine includes optional spectrogram noise reduction. These settings
control a background noise estimate subtracted from the spectrogram before it
is passed to detectors.

---

## Tips

- A common starting configuration: FFT length = 512, hop = 256 (50% overlap),
  Hanning window.
- For click detector work, use a *short* FFT (64–128 samples) to capture the
  click waveform finely in time.
- For whistle/tonal detection, use a *long* FFT (1024–4096 samples) to resolve
  narrow frequency modulations.
