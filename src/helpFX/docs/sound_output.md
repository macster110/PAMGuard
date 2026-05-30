# Sound Output

The Sound Output module plays PAMGuard audio back through a sound card or
audio device. It is primarily used for real-time monitoring — listening to
the audio being processed — and for playback of stored recordings in
Viewer Mode.

---

## Overview

| Setting | Description |
|---------|-------------|
| Output device | The sound card / audio device to use |
| Channel | Which output channel (left, right, or both) to use |
| Volume | Playback output gain (0–100%) |
| Sample rate | Playback sample rate (must match input) |

---

## Configuring Sound Output

1. Open **Settings → Sound Output**.
2. Select the **output device** from the list of devices reported by the
   operating system.
3. Choose the **output channel**. Most applications use *Channel 0* (left) or
   a stereo mix.
4. Adjust the **volume** slider to a comfortable monitoring level.

---

## Source selection

The Sound Output module can replay any single audio channel that is available
in the PAMGuard data flow. Select the source using the **data source** drop-down.

---

## Notes

- Sound Output does **not** affect detection or analysis — it is purely a
  monitoring tool.
- Running Sound Output on the same machine as Sound Acquisition can introduce
  feedback if speakers are close to the hydrophone / microphone.
- Very high sample rates (> 192 kHz) may not be supported by standard sound
  cards; in that case Sound Output will display an error and remain silent.
