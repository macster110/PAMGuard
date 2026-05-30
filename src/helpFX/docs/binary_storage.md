# Binary Storage

The Binary Storage module saves the output of PAMGuard detectors to compact
binary `.pgdf` files. It is separate from the database: the database stores
metadata and summary information, while binary files store the full raw
detection data (waveform clips, spectra, etc.) for later analysis and
classification.

---

## Overview

| Setting | Description |
|---------|-------------|
| Output folder | Directory where binary files are written |
| Store file length | Maximum duration of a single output file |
| Maximum file size | Maximum size (MB) of a single output file |
| Always write new file on start | Begin a new file each time PAMGuard starts |

---

## Configuring Binary Storage

1. Open **Settings → Binary Storage**.
2. Click **Browse** to choose the output folder.  
   PAMGuard will create sub-folders automatically (one per day by default).
3. Set the **File length** (minutes). When the limit is reached a new file is
   started.
4. Optionally set a **Maximum file size** (MB). If the file exceeds this size
   a new file is started even if the time limit has not been reached.

> **Tip:** Enable *Always write new file on start* when running PAMGuard in a
> logging script so that each session produces a clearly dated file.

---

## File naming

Binary files are named automatically:
```
PAMGuard_<date>_<time>_<index>.pgdf
```
Sub-folders follow the pattern `<year>/<month>/<day>`.

---

## What is stored

Every module that produces detections automatically registers with the Binary
Storage system. No per-module configuration is needed. The binary files
contain:

- Detection objects (clicks, whistles, click trains, etc.)
- Associated waveform clips and spectra (if the detector is configured to save them)
- Annotation data (species labels, quality scores, etc.)

---

## Viewer Mode

In Viewer Mode, PAMGuard reads back the binary files and re-populates the
data displays. Choose the folder that contains your binary files in the
**Viewer Mode Startup** dialog.

---

## Tips

- Store binary files on a fast local drive. Network drives can cause write
  latency that leads to dropped detections under high data rates.
- Regularly move completed files off the recording computer to free disk space.
- Use the [Database](database.md) module alongside Binary Storage — the
  database provides the index used to navigate data in Viewer Mode.
