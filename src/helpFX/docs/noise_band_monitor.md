# Noise Band Monitor

The Noise Band Monitor measures the received noise level (in dB re 1 µPa²/Hz)
within user-defined frequency bands. It is used to monitor ambient noise,
track vessel noise, or provide a background noise estimate for other
modules.

---

## Overview

| Setting | Description |
|---------|-------------|
| Data source | Raw audio stream to analyse |
| Channels | Which channels to monitor |
| Frequency bands | One or more custom frequency bands |
| Decimation method | Optional internal decimation for low-frequency bands |
| Output | Stored in database and optionally plotted |

---

## Configuring frequency bands

Each band is defined by:

| Parameter | Description |
|-----------|-------------|
| Name | A short label (displayed on the plot) |
| Low frequency (Hz) | Lower edge of the band |
| High frequency (Hz) | Upper edge of the band |

Use the **Add band** button to add a new row, and the **Remove** button to
delete the selected row.

---

## Decimation method

For very low frequency bands (< 1 000 Hz) the Noise Band Monitor can internally
decimate the input to reduce computation. Select the decimation method that
gives adequate resolution without unnecessary oversampling:

- **None** — no internal decimation (use for high-frequency bands)
- **By band** — automatically choose a decimation factor for each band
- **Manual** — specify the decimation factor manually

---

## Bode plot

The settings pane includes a Bode-style frequency response plot that shows
which parts of the spectrum each band covers and how the anti-aliasing filters
affect them. Use this to verify your band boundaries before running.

---

## Output and storage

Noise measurements are stored in the PAMGuard database (one row per
measurement interval per channel per band). They can be exported to CSV
for further analysis in R or Python.

---

## Tips

- Use 1/3-octave bands to produce a noise spectrum comparable to standard
  marine noise databases.
- Set the measurement interval to match your reporting requirements (e.g.
  one measurement per minute for long-term noise monitoring).
- Multiple Noise Band Monitor modules can be added to a configuration, each
  with different frequency bands or data sources.
