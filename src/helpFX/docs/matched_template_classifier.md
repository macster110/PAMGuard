# Matched Template Classifier

The Matched Template Classifier automatically classifies acoustic detections
(typically clicks from the Click Detector) by comparing them against a library
of reference templates. A detection is classified as matching a species if its
waveform or spectrum is sufficiently similar to the template.

---

## Overview

| Setting | Description |
|---------|-------------|
| Data source | The click data to classify (Click Detector or Click Train Detector) |
| Channel options | Whether to classify on a single channel, best channel, or all channels |
| Templates | One or more reference waveform/spectrum templates |

---

## Data source

Select the click detector whose output should be classified. Both raw click
detections and click train detections are supported as input sources.

---

## Channel options

| Option | Description |
|--------|-------------|
| Best amplitude | Use the channel with the highest click amplitude |
| All channels | Classify on every channel; accept if any passes |
| Specific channel | Use a fixed channel number |

---

## Templates

Each template defines a reference pattern and acceptance criteria:

| Parameter | Description |
|-----------|-------------|
| Template name | Label displayed in the classification output |
| Waveform / spectrum | Reference data (loaded from a file or captured live) |
| Match threshold | Minimum cross-correlation score to accept a match |
| Reject threshold | Cross-correlation score below which the click is explicitly rejected |
| Peak frequency | Expected peak frequency of the click (Hz); optional filter |
| Min/Max frequency | Frequency range within which the template match is evaluated |
| Peak threshold (dB) | Minimum peak-to-peak amplitude of the click |

### Template match score

The score is the normalised cross-correlation between the incoming click and
the template, ranging from 0 (no match) to 1 (perfect match).

- If the score ≥ *match threshold* → classified as matching
- If the score ≤ *reject threshold* → explicitly rejected (not just unclassified)
- If between the two thresholds → no classification applied

---

## Multiple templates

Multiple templates can be added to a single Matched Template Classifier
module. Each template is evaluated independently. A click is classified with
the first template that passes its threshold.

Use the **Add** button in the settings pane to add a new template tab, and
the **Delete** button to remove the selected template.

---

## Tips

- Capture reference templates from confirmed species recordings using the
  Click Display's *Save clip as template* feature.
- Start with a lenient threshold (e.g. 0.3–0.4) and examine the
  classified detections before tightening.
- Use the *Peak threshold (dB)* parameter to reject very quiet clicks that
  are unlikely to produce reliable classifications.
- The Matched Template Classifier works best for species with stereotyped
  clicks (porpoises, sperm whales). For variable-click species, consider the
  [Deep Learning Classifier](deep_learning_classifier.md).
