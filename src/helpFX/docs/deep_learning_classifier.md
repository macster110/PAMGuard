# Deep Learning Classifier

The Deep Learning Classifier runs trained neural network models on PAMGuard
audio data or existing detections to identify species or call types. It
supports several model formats and can classify raw audio segments, clicks,
whistles, or other detection types.

---

## Overview

| Setting | Description |
|---------|-------------|
| Data source | Audio or detection data to classify |
| Classifier model | The deep learning model to use |
| Model file | Path to the trained model (`.json`, `.pb`, `.onnx`, etc.) |
| Segment length (s) | Duration of each audio segment passed to the model |
| Hop size (s) | Step between consecutive segments (< segment length = overlap) |
| Prediction threshold | Minimum probability score to accept a classification |

---

## Supported model types

PAMGuard supports several deep learning frameworks:

| Model type | File format | Notes |
|------------|-------------|-------|
| **Generic ONNX** | `.onnx` | Cross-platform; export from PyTorch/TensorFlow |
| **Ketos** | `.ktpb` or HDF5 | Ketos acoustic deep learning toolkit |
| **AnimalSpot** | `.pt` (PyTorch) | AnimalSpot framework |
| **SoundSpot** | `.pb` | TensorFlow frozen graph |
| **BirdNET** | `.tflite` | Bird vocalisation classification |

Select the model type from the drop-down before loading the model file.

---

## Data source

The classifier can process:

- **Raw audio** — audio is segmented into fixed-length windows.
- **Click detections** — each click is classified individually.
- **Whistle contours** — contour data is converted to spectrograms for
  classification.

Select the appropriate source type from the *Data source* panel.

---

## Segment settings (raw audio mode)

| Setting | Description |
|---------|-------------|
| Segment length (s) | Duration of audio window presented to the model |
| Hop size (s) | How far to advance the window between classifications |
| Min frequency (Hz) | Lower bound for spectrogram (model-dependent) |
| Max frequency (Hz) | Upper bound for spectrogram (model-dependent) |

The segment length and frequency range must match the model's expected input
dimensions. These values are usually specified in the model documentation.

---

## Prediction threshold

Classifications with a probability score below the threshold are discarded.
A higher threshold reduces false positives but may miss genuine detections.

Start with the threshold recommended by the model authors; adjust based on
your false positive tolerance.

---

## Output

Classified detections are annotated with the predicted class label and
probability score. Results are stored in the database and binary files and
can be viewed in the Data Map and Spectrogram displays.

---

## Tips

- Always confirm that the sample rate and segment length in the PAMGuard
  settings match the model's training parameters.
- Use the data selector to restrict which detections are passed to the
  classifier (e.g. only clicks above a certain amplitude).
- Multiple Deep Learning Classifier modules can be added to a configuration
  to run different models in parallel on the same data.
- For real-time deployment, test CPU/GPU usage during a pilot deployment to
  ensure the system can keep up with the data rate.
