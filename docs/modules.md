# PAMGuard modules

Module types in PAMGuard 2.02.19f, generated from `-describe` by `docs/generate_docs.py`.
Plugins add more. `Needs` is the type of data a module takes as input.

## Maps and Mapping

| Module | Needs | Description | Java class |
|---|---|---|---|
| NMEA Data Collection |  | Collects NMEA data from a serial port | `NMEA.NMEAControl` |
| GPS Processing | NMEADataUnit | Interprets NMEA data to extract GPS data | `GPS.GPSControl` |
| Map | GpsDataUnit | Displays a map of vessel position and detections | `Map.MapController` |
| AIS Processing | NMEADataUnit | Interprets NMEA data to extract AIS data | `AIS.AISControl` |
| Airgun Display |  | Shows the position of airguns (or any other source) on the map | `AirgunDisplay.AirgunControl` |
| Fixed Landmarks |  | Place object symbols on the PAMGuard map | `landMarks.LandmarkControl` |

## Utilities

| Module | Needs | Description | Java class |
|---|---|---|---|
| Database |  | Stores PAMGuard data in a database | `generalDatabase.DBControlUnit` |
| Binary Storage |  | Stores PAMGuard data in files on the hard drive | `binaryFileStorage.BinaryStore` |
| Secondary Binary Store |  | Additional binary data from 2nd, 3rd, etc. moorings. | `binaryFileStorage.SecondaryBinaryStore` |
| Network Sender |  | Sends PAMGuard data over a network to other computers | `networkTransfer.send.NetworkSender` |
| Network Receiver |  | Receives PAMGuard data sent over the network from the Network Sender module | `networkTransfer.receive.NetworkReceiver` |
| Data Map |  | Shows a summary of data density over time for large datasets | `dataMap.DataMapControl` |
| User input |  | Creates a form for the user to type comments into | `UserInput.UserInputController` |
| Aural Listening Form |  | Creates a form for the user to manually log things they hear | `listening.ListeningControl` |
| Signal Injection and Detector Evaluation (SIDE) |  | Signal injection and real time performance tests | `qa.QAControl` |
| Alarm |  | Alerts the operator when certain detections are made | `alarm.AlarmControl` |
| Spectrogram Annotation |  | Offline marking on the spectrogram display | `annotationMark.spectrogram.SpectrogramAnnotationModule` |
| Quick Spectrogram Annotation |  | Manual marking on the spectrogram display using user-defined 'quick' annotations | `quickAnnotation.QuickAnnotationModule` |
| Print Screen |  | Capture PAMGuard screens to time-stamped image files and the clipboard.  Use <CTRL-P> as a shortcut | `printscreen.PrintScreenControl` |
| Scroll Effort |  | Enables an observer to enter their name and information about which displays are being monitored | `effortmonitor.EffortControl` |
| Backup Manager |  | Manage automated data backups | `backupmanager.BackupManager` |
| Tethys |  | Interface to Tethys Database | `tethys.TethysControl` |
| Raven Import |  | Import data from Raven selection tables | `ravendata.RavenControl` |

## Sensors

| Module | Needs | Description | Java class |
|---|---|---|---|
| Analog Array Sensors |  | Reads Depth, Heading, Pitch and Roll) using analog sensors | `analogarraysensor.ArraySensorControl` |
| Hydrophone Depth Readout |  | Reads and displays hydrophone depth information | `depthReadout.DepthControl` |
| Array Accelerometer |  | Reads and accelerometer to orientate a hydrophone array | `ArrayAccelerometer.ArrayAccelControl` |
| Angle Measurement |  | Reads angles from a Fluxgate World shaft angle encoder. (Can be used to read angle of binocular stands) | `angleMeasurement.AngleControl` |
| IMU Measurement |  | Reads IMU data (heading, pitch and roll) from file or instrument | `IMU.IMUControl` |
| SoundTrap Detector Import |  | Tools for import of SoundTrap detector data | `soundtrap.STToolsControl` |
| SoundTrap SWV Sensor Import |  | Extracts magnetometer and accelerometer data from SoundTrap SUD (.swv) files and computes heading, pitch and roll | `soundtrapSensor.SudSensorControl` |
| CPOD Detector Import |  | Imports CPOD data | `cpod.CPODControl2` |

## Displays

| Module | Needs | Description | Java class |
|---|---|---|---|
| User Display |  | Creates an empty display panel which the user can add spectrograms and other displays to | `userDisplay.UserDisplayControl` |
| Local Time |  | Shows local time on the display | `localTime.LocalTime` |
| Level Meter | RawDataUnit | Shows signal level meters | `levelMeter.LevelMeterControl` |
| Array Orientation |  | Displays array depth and orientation data | `Array.ArraySidePanelControl` |

## Sound Processing

| Module | Needs | Description | Java class |
|---|---|---|---|
| Sound Acquisition |  | Controls input of sound data from sound cards, NI cards, etc.  | `Acquisition.AcquisitionControl` |
| Sound Output |  | Controls output of sound data for listening to on headphones | `soundPlayback.PlaybackControl` |
| FFT (Spectrogram) Engine | RawDataUnit | Computes spectrograms of audio data | `fftManager.PamFFTControl` |
| Mel Spectrogram | FFTDataUnit | Computes mel-spectrograms of audio data | `mel.MelControl` |
| Filters (IIR and FIR) | RawDataUnit | Filters audio data | `Filters.FilterControl` |
| Decimator | RawDataUnit | Decimates (reduces the frequency of) audio data | `decimator.DecimatorControl` |
| Cepstrum | FFTDataUnit | Calculates a continuous Cepstrum from FFT Data | `cepstrum.CepstrumControl` |
| Sound recorder | RawDataUnit | Records audio data to wav of AIF files | `SoundRecorder.RecorderControl` |
| Clip generator | RawDataUnit | Generates and stores short clips of sound data in response to detections | `clipgenerator.ClipControl` |
| Signal Amplifier | RawDataUnit | Amplifies (or attenuates) audio data | `amplifier.AmpControl` |
| Patch Panel | RawDataUnit | Reorganises and mixes audio data between channels | `patchPanel.PatchPanelControl` |
| Spectrogram smoothing kernel | FFTDataUnit | Smooths a spectrogram of audio data | `KernelSmoothing.KernelSmoothingControl` |
| Seismic Veto | FFTDataUnit | Cuts out loud sounds from audio data | `seismicVeto.VetoController` |
| Noise Monitor | FFTDataUnit | Measures noise in predefined frequency bands (e.g. third octave) using FFT data | `noiseMonitor.NoiseControl` |
| Noise Band Monitor | RawDataUnit | Measure noise in octave, third octave, decade bands, etc. using filter banks | `noiseBandMonitor.NoiseBandControl` |
| dBHt Measurement | RawDataUnit | Measure noise relative to animal hearing threshold | `dbht.DbHtControl` |
| Filtered Noise Measurement | RawDataUnit | Measure noise in a single arbitrary filter band (replaces dBHt module) | `noiseOneBand.OneBandControl` |
| Long Term Spectral Average | RawDataUnit | Make Long Term Spectral Average Measurements | `ltsa.LtsaControl` |
| Envelope Tracing | RawDataUnit | Traces the envelope of audio data and outputs it as a new waveform | `envelopeTracer.EnvelopeControl` |
| Beamformer |  | Continuous Frequency Domain Beamforming | `beamformer.continuous.BeamFormerControl` |

## Detectors

| Module | Needs | Description | Java class |
|---|---|---|---|
| Click Detector | RawDataUnit | Searches for transient sounds, attempts to assign species, measure bearings to source, group into click trains, etc. | `clickDetector.ClickControl` |
| Click Train Detector | RawDataUnit | Searches for click trains in detected clicks. | `clickTrainDetector.ClickTrainControl` |
| Whistle and Moan Detector | FFTDataUnit | Searches for tonal noises. Measures bearings and locations of source. Replaces older Whistle Detector | `whistlesAndMoans.WhistleMoanControl` |
| Whistle Detector | FFTDataUnit | Searches for tonal noises. Measures bearings and locations of source | `whistleDetector.WhistleControl` |
| Ishmael energy sum | FFTDataUnit | Detects sounds with energy in a specific frequency band | `IshmaelDetector.EnergySumControl` |
| Ishmael spectrogram correlation | FFTDataUnit | Detects sounds matching a user defined 'shape' on a spectrogram | `IshmaelDetector.SgramCorrControl` |
| Ishmael matched filtering | RawDataUnit | Detects sounds using a user defined matched filter | `IshmaelDetector.MatchFiltControl` |
| Likelihood Detector | RawDataUnit | An implementation of a likelihood ratio test | `likelihoodDetectionModule.LikelihoodDetectionUnit` |
| Right Whale Edge Detector | FFTDataUnit | Detects right whale upsweep calls | `RightWhaleEdgeDetector.RWEControl` |
| Generalised Power Law Detector | FFTDataUnit | Generalised Power Law Detector for tonal sounds | `gpl.GPLControlledUnit` |
| SoundTrap Click Detector |  | Click Detector module for Soundtrap detector data only | `soundtrap.STClickControl` |
| Deep Whistle | FFTDataUnit | AI used to enhance whistle detection | `deepWhistle.DeepWhistleControl` |

## Classifiers

| Module | Needs | Description | Java class |
|---|---|---|---|
| Whistle Classifier | AbstractWhistleDataUnit | Analyses multiple whistle contours to assign to species | `whistleClassifier.WhistleClassifierControl` |
| Rocca | FFTDataUnit | Real-time acoustic species identification of delphinid whistles and clicks | `rocca.RoccaControl` |
| Matched Template Click Classifer | ClickDetection | Classifies clicks based on an ideal template to match and a template to reject. An example of this is to classify beaked whale clicks in an environment with dolphin clicks | `matchedTemplateClassifer.MTClassifierControl` |
| Deep Learning Classifier | RawDataUnit | Classifies sections of raw acoustic data based on an imported deep learning classifier | `rawDeepLearningClassifier.DLControl` |

## Localisers

| Module | Needs | Description | Java class |
|---|---|---|---|
| Bearing Localiser |  | Estimate bearing to detections or spectrogram marks from small aperture arrays | `bearinglocaliser.BearingLocaliserControl` |
| Group 3D Localiser |  | 2D and 3D Localisation for large aperture arrays | `group3dlocaliser.Group3DLocaliserControl` |
| Detection Grouper |  | Groups detections and other data using manual annotations on PAMGuard displays | `detectiongrouplocaliser.DetectionGroupControl` |
| Ishmael Locator |  | Locates sounds extracted either from areas marked out on a spectrogram display or using output from a detector | `IshmaelLocator.IshLocControl` |
| Multipath 3D Localiser | ClickDetection | Locates sounds detected by the click detector using surface echo's to obtain slant angles and generate a 3-D location | `loc3d_Thode.TowedArray3DController` |
| DIFAR Directional Audio | GpsDataUnit | Audio from a DIFAR sonobuoy that has been beamformed at a user-specified single steering angle. This module can be used to reduce directional masking noise. | `difar.beamforming.BeamformControl` |
| DIFAR Localisation | RawDataUnit | DIFAR Sonobuoy localisation module - takes raw data source with multiplexed directional audio data | `difar.DifarControl` |

## Visual Methods

| Module | Needs | Description | Java class |
|---|---|---|---|
| Video Range |  | Calculates ranges based on angles measured from video, observer height and earth radius | `videoRangePanel.VRControl` |
| Logger Forms |  | Replicates the functionality of User Defined Forms in the IFAW Logger software | `loggerForms.FormsControl` |

