# Click Train Detector

The Click Train Detector identifies sequences of regular, repeating transient
pulses (click trains) from the output of the Click Detector. It is used to
detect and track cetacean click trains (particularly porpoises and dolphins)
and to reduce false positives by requiring regularity in inter-click interval
(ICI).

---

## Overview

Click train detection is a two-stage process:

1. **Click detection** — individual clicks are detected by the
   [Click Detector](click_detector.md).
2. **Click train linking** — the Click Train Detector groups individual clicks
   into trains based on their timing and bearing consistency.

---

## Algorithm: MHT (Multi-Hypothesis Tracker)

PAMGuard uses a Multi-Hypothesis Tracking (MHT) algorithm to link clicks
into trains. The algorithm evaluates all possible groupings of incoming clicks
and maintains multiple competing hypotheses simultaneously. The most likely
grouping is selected based on a χ² (chi-squared) scoring function.

---

## Settings

### Data source

Select the Click Detector whose output should be analysed. Multiple Click
Train Detectors can be added to a single configuration to use different
algorithms or settings on the same click data.

---

### MHT variables

The MHT algorithm uses several detection variables, each of which can be
enabled or disabled:

| Variable | Description |
|----------|-------------|
| **ICI (inter-click interval)** | Time gap between successive clicks in a train; regularised by χ² |
| **Amplitude** | Consistency of click amplitude within a train |
| **Bearing** | Consistency of click bearing (requires localisation) |
| **Bearing gradient** | Rate of change of bearing across the train |

---

### ICI settings

| Setting | Description |
|---------|-------------|
| Min ICI (s) | Minimum allowable inter-click interval |
| Max ICI (s) | Maximum allowable inter-click interval |
| ICI chi² degrees of freedom | Controls how strictly ICI must be regular |

For porpoises: ICI typically 60–200 ms (0.06–0.2 s).  
For dolphins: ICI typically 10–100 ms.

---

### Algorithm parameters

| Setting | Description |
|---------|-------------|
| Max coasts | Number of consecutive missing clicks before a track is terminated |
| New track threshold | χ² score threshold to start a new track |
| Confirm threshold | Minimum number of clicks to confirm a track |

---

## Electrical noise rejection

The advanced settings include an electrical noise filter that rejects click
trains with very regular ICI matching power-line harmonics (50 or 60 Hz).
Enable this if you encounter persistent 50/60 Hz electrical interference.

---

## Tips

- Ensure the Click Detector is well-tuned before adjusting Click Train
  Detector settings — click train quality depends on click detection quality.
- Use the Click Train display to visualise confirmed and unconfirmed tracks.
- For multi-array deployments, enable the *Bearing* variable to reject
  false trains caused by reverberations.
