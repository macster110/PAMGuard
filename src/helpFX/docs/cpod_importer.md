# CPOD / FPOD Importer

The CPOD Importer reads binary click log files produced by CPOD and FPOD
autonomous porpoise detectors (Wildlife Acoustics / CPOD.exe software). It
converts the proprietary format into PAMGuard binary files that can be viewed
and analysed alongside other PAMGuard data.

---

## Overview

| Feature | Description |
|---------|-------------|
| Supported file types | `.cp1`, `.cp3` (CPOD), `.fp1`, `.fp3` (FPOD) |
| Output | PAMGuard binary files + database records |
| Sub-folder scanning | Optionally recurse into sub-folders |

---

## Importing CPOD/FPOD data

1. Open **Settings → CPOD Importer**.
2. Click **Add files** to select one or more `.cp1` / `.cp3` / `.fp1` /
   `.fp3` files, or click **Add folder** to import all files in a directory.
3. Optionally enable **Include sub-folders** to scan nested directories.
4. Click **Import** to begin processing.

A progress bar shows import progress. PAMGuard stores the converted data in
the currently configured [Binary Storage](binary_storage.md) folder and
writes summary records to the [Database](database.md).

---

## File types

| Extension | Device | Format version |
|-----------|--------|---------------|
| `.cp1` | CPOD | Version 1 |
| `.cp3` | CPOD | Version 3 |
| `.fp1` | FPOD | Version 1 |
| `.fp3` | FPOD | Version 3 |

---

## Output data

After import, the data can be viewed in PAMGuard's Viewer Mode like any other
detector output. Click events are stored with their time, duration, and
classification code (from the CPOD software).

---

## Tips

- Import CPOD files before starting a Viewer Mode session so the data is
  indexed by the database.
- If you have a large archive of files, use the **Add folder** option with
  **Include sub-folders** enabled for batch import.
- The CPOD importer does not re-detect clicks — it imports the detections
  already made by the CPOD hardware/software.
