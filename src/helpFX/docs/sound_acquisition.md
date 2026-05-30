# Sound Acquisition

The Sound Acquisition module is PAMGuard's audio input system. It connects to
a sound source — a sound card, audio file, network stream, or specialist DAQ
hardware — and feeds real-time audio data to all downstream processing modules.

---

## Overview

| Setting | Description |
|---------|-------------|
| DAQ system | The type of audio input (sound card, files, network, etc.) |
| Sample rate (Hz) | Number of audio samples per second |
| Number of channels | How many input channels are active |
| Volts peak-to-peak | The input voltage range of the ADC (used for calibration) |

---

## Choosing a DAQ system

Use the drop-down at the top of the settings pane to select the audio source.
PAMGuard supports several back-ends:

### Sound Card
Uses a system sound card or USB audio device via the Java Sound API.

- Select the **audio device** from the list of available devices.
- Set the **sample rate** (e.g. 96 000 Hz for porpoise / high-frequency work,
  192 000 Hz for bats).
- Set the **number of channels** (1 = mono, 2 = stereo, more for multi-element
  arrays).
- The actual supported sample rates and channel counts depend on the hardware.

### Audio Files (offline / batch)
Reads `.wav` or other supported audio files from a folder.

- Choose the **folder** containing audio files.
- PAMGuard processes files in chronological order based on their names or
  embedded timestamps.
- Set the sample rate to match the files (PAMGuard reads this automatically
  from the file header when possible).

### Network Receiver
Receives audio over a UDP or TCP network connection from a remote digitiser.

### ASIO (Windows only)
Uses the ASIO driver for low-latency access to professional audio hardware.
Requires an ASIO-compatible driver to be installed.

---

## Calibration — Volts Peak-to-Peak

The *Volts peak-to-peak* field specifies the full-scale input range of the
analogue-to-digital converter. It is used together with hydrophone sensitivity
values (set in Array Manager) to convert raw digital counts to physical
pressure units (µPa).

- For a typical 16-bit sound card: **2 V** peak-to-peak.
- For specialist 24-bit digitisers: check the hardware data sheet.

---

## Channel mapping

Each active channel is mapped to a hydrophone element defined in the **Array
Manager**. The channel-to-hydrophone assignment is set in Array Manager, not
here.

---

## Tips

- Use the highest sample rate your hardware supports if you are working with
  high-frequency cetaceans (porpoises, high-frequency dolphins, bats).
- For playback detection in the field, match the sample rate to the tag or
  recorder that produced the files.
- Always set the *Volts peak-to-peak* correctly — incorrect values lead to
  wrong received level calculations throughout all downstream modules.
