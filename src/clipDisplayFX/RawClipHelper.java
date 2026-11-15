package clipDisplayFX;

import java.awt.Color;
import java.awt.image.BufferedImage;

import Acquisition.AcquisitionProcess;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamDataUnit;
import PamguardMVC.PamProcess;
import PamguardMVC.RawDataHolder;
import PamUtils.PamUtils;
import Spectrogram.WindowFunction;
import clipgenerator.ClipDataUnit;
import fftManager.Complex;
import fftManager.FastFFT;

/**
 * Functions which let the JavaFX clip display show any data unit which holds a
 * section of raw sound, e.g. clips from the clip generator, clicks from the click
 * detector or detections from the deep learning classifier. Clip generator clips
 * keep using their own functions so they look exactly as they did before. Other
 * data units have their spectrogram made from {@link RawDataHolder#getWaveData()}.
 *
 * @author Jamie Macaulay
 *
 */
public class RawClipHelper {

	/**
	 * Smallest FFT used for short sounds such as clicks.
	 */
	private static final int MIN_FFT = 32;

	/**
	 * Short sounds are padded with zeros to give at least this many time slices.
	 */
	private static final int MIN_SLICES = 16;

	/**
	 * Images of short sounds are stretched to at least this many columns so they
	 * can be seen and clicked on.
	 */
	public static final int MIN_COLUMNS = 64;

	private static FastFFT fastFFT = new FastFFT();

	/**
	 * Check whether a data block holds data units with raw sound which the clip
	 * display can show.
	 * @param dataBlock - the data block.
	 * @return true if the data units hold raw sound.
	 */
	public static boolean isRawClipBlock(PamDataBlock<?> dataBlock) {
		if (dataBlock == null || dataBlock.getUnitClass() == null) {
			return false;
		}
		return RawDataHolder.class.isAssignableFrom(dataBlock.getUnitClass());
	}

	/**
	 * Sample rate of the sound shown on the clip image.
	 */
	public static float getDisplaySampleRate(PamDataUnit<?, ?> unit) {
		if (unit instanceof ClipDataUnit) {
			return ((ClipDataUnit) unit).getDisplaySampleRate();
		}
		return getSampleRate(unit);
	}

	/**
	 * Sample rate of the data unit.
	 */
	public static float getSampleRate(PamDataUnit<?, ?> unit) {
		if (unit instanceof ClipDataUnit) {
			return ((ClipDataUnit) unit).getSourceSampleRate();
		}
		PamDataBlock<?> block = unit.getParentDataBlock();
		if (block != null && block.getSampleRate() > 0) {
			return block.getSampleRate();
		}
		if (unit instanceof RawDataHolder && ((RawDataHolder) unit).getDataTransforms() != null) {
			return ((RawDataHolder) unit).getDataTransforms().getSampleRate();
		}
		return 0;
	}

	/**
	 * Duration of the sound in seconds.
	 */
	public static double getDurationSeconds(PamDataUnit<?, ?> unit) {
		float fs = getSampleRate(unit);
		if (!(unit instanceof ClipDataUnit) && fs > 0) {
			// the sound held may be shorter than the data unit, e.g. the average
			// waveform of a click train.
			double[][] wave = getWaveData(unit);
			if (wave != null && wave.length > 0 && wave[0] != null && wave[0].length > 0) {
				return wave[0].length / (double) fs;
			}
		}
		Long dur = unit.getSampleDuration();
		if (dur != null && dur > 0 && fs > 0) {
			return dur / (double) fs;
		}
		Double millis = unit.getDurationInMilliseconds();
		if (millis != null && millis > 0) {
			return millis / 1000.;
		}
		return 0.1;
	}

	/**
	 * Number of time slices in the clip image, before any stretching of short sounds.
	 * @param unit - the data unit.
	 * @param fftLength - FFT length chosen in the display.
	 * @return number of image columns.
	 */
	public static double getImageColumns(PamDataUnit<?, ?> unit, int fftLength) {
		double nSamples = getDurationSeconds(unit) * getDisplaySampleRate(unit);
		if (unit instanceof ClipDataUnit) {
			int hop = fftLength / 2;
			return Math.max(1, Math.floor((nSamples - (fftLength - hop)) / hop));
		}
		int fft = getFFTLength((int) nSamples, fftLength);
		int hop = getHop(fft, fftLength);
		double nT = Math.max(MIN_SLICES, Math.floor((nSamples - (fft - hop)) / hop));
		return Math.max(MIN_COLUMNS, nT);
	}

	/**
	 * Get the data unit which triggered a clip so it can be drawn on the clip.
	 * @param unit - the data unit.
	 * @return the trigger, or null. Data units other than clips have none.
	 */
	public static PamDataUnit<?, ?> getTrigger(PamDataUnit<?, ?> unit) {
		if (unit instanceof ClipDataUnit) {
			ClipDataUnit clip = (ClipDataUnit) unit;
			PamDataUnit<?, ?> trigger = clip.getTriggerDataUnit();
			if (trigger == null) {
				trigger = clip.findTriggerDataUnit();
			}
			return trigger;
		}
		return null;
	}

	/**
	 * Get the raw sound held by a data unit.
	 * @param unit - the data unit.
	 * @return wave data, one row per channel, or null.
	 */
	public static double[][] getWaveData(PamDataUnit<?, ?> unit) {
		if (unit instanceof ClipDataUnit) {
			return ((ClipDataUnit) unit).getRawData();
		}
		if (unit instanceof RawDataHolder) {
			try {
				return ((RawDataHolder) unit).getWaveData();
			}
			catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Make the spectrogram image of one channel of a data unit.
	 * @param unit - the data unit.
	 * @param channel - the channel.
	 * @param fftLength - FFT length.
	 * @param scaleMin - minimum of the colour scale in dB.
	 * @param scaleMax - maximum of the colour scale in dB.
	 * @param colours - colour table.
	 * @return the image, or null if one can't be made.
	 */
	public static BufferedImage makeClipImage(PamDataUnit<?, ?> unit, int channel, int fftLength,
			double scaleMin, double scaleMax, Color[] colours) {
		if (unit instanceof ClipDataUnit) {
			ClipDataUnit clip = (ClipDataUnit) unit;
			BufferedImage bi = clip.getClipImage(channel, fftLength, fftLength / 2, scaleMin, scaleMax, colours);
			// the image is all we need so free the spectrogram data held by the clip.
			clip.clearClipSpecData();
			return bi;
		}
		if (!(unit instanceof RawDataHolder)) {
			return null;
		}
		double[][] waveData = getWaveData(unit);
		double[] wave = getChannelWave(waveData, channel, unit.getChannelBitmap());
		if (wave == null || wave.length == 0) {
			return null;
		}
		int fft = getFFTLength(wave.length, fftLength);
		int hop = getHop(fft, fftLength);
		int minLength = fft + hop * (MIN_SLICES - 1);
		if (wave.length < minLength) {
			// pad short sounds such as clicks with zeros, centring the sound.
			double[] padded = new double[minLength];
			System.arraycopy(wave, 0, padded, (minLength - wave.length) / 2, wave.length);
			wave = padded;
		}
		int nT = (wave.length - (fft - hop)) / hop;
		if (nT <= 0) {
			return null;
		}
		int nF = fft / 2;
		double[] win = WindowFunction.getWindowFunc(WindowFunction.HANNING, fft);
		double[] bit = new double[fft];
		Complex[] out = Complex.allocateComplexArray(nF);
		int m = FastFFT.log2(fft);

		AcquisitionProcess daq = findDaqProcess(unit);
		float sampleRate = getSampleRate(unit);
		// the daq process only knows about its own channels, so convert using the first
		// channel it has if this one is out of range.
		int daqChan = channel;
		if (daq != null) {
			try {
				daq.prepareFastAmplitudeCalculation(daqChan);
			}
			catch (Exception e) {
				daqChan = 0;
				daq.prepareFastAmplitudeCalculation(daqChan);
			}
		}
		int nCols = colours.length;
		BufferedImage image = new BufferedImage(nT, nF, BufferedImage.TYPE_INT_RGB);
		synchronized (fastFFT) {
			for (int i = 0; i < nT; i++) {
				int pos = i * hop;
				for (int j = 0; j < fft; j++) {
					bit[j] = wave[pos + j] * win[j];
				}
				fastFFT.rfft(bit, out, m);
				for (int j = 0; j < nF; j++) {
					double mag = out[j].magsq();
					double dB;
					if (daq != null) {
						dB = daq.fftAmplitude2dB(mag, daqChan, sampleRate, fft, true, true);
					}
					else {
						// no calibration available, so use dB re full scale.
						dB = 10 * Math.log10(Math.max(mag, 1e-30));
					}
					int ind = (int) Math.round((dB - scaleMin) / (scaleMax - scaleMin) * nCols);
					ind = Math.min(Math.max(0, ind), nCols - 1);
					image.setRGB(i, nF - j - 1, colours[ind].getRGB());
				}
			}
		}
		// keep all images the same height, whatever FFT length was used. 
		int nRows = Math.max(nF, fftLength / 2);
		if (nT < MIN_COLUMNS || nRows != nF) {
			image = stretch(image, Math.max(nT, MIN_COLUMNS), nRows);
		}
		return image;
	}

	/**
	 * Choose the FFT length for a sound. Short sounds such as clicks use a shorter FFT
	 * than the one set in the display so that the image has a few time slices.
	 */
	private static int getFFTLength(int nSamples, int fftLength) {
		int fft = fftLength;
		while (fft > MIN_FFT && fft > nSamples) {
			fft /= 2;
		}
		return Math.max(MIN_FFT, Math.min(fft, fftLength));
	}

	/**
	 * Hop between FFTs. Short sounds use more overlap so they look smoother.
	 */
	private static int getHop(int fft, int fftLength) {
		return fft < fftLength ? Math.max(1, fft / 4) : fft / 2;
	}

	/**
	 * Stretch an image, without smoothing.
	 */
	private static BufferedImage stretch(BufferedImage image, int width, int height) {
		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage big = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < width; x++) {
			int sx = Math.min(w - 1, x * w / width);
			for (int y = 0; y < height; y++) {
				big.setRGB(x, y, image.getRGB(sx, Math.min(h - 1, y * h / height)));
			}
		}
		return big;
	}

	/**
	 * Get the row of wave data for a channel.
	 */
	private static double[] getChannelWave(double[][] waveData, int channel, int channelMap) {
		if (waveData == null || waveData.length == 0) {
			return null;
		}
		int pos = PamUtils.getChannelPos(channel, channelMap);
		if (pos < 0 || pos >= waveData.length) {
			pos = 0;
		}
		return waveData[pos];
	}

	/**
	 * Find the acquisition process which made the sound, to calibrate the colour scale.
	 */
	private static AcquisitionProcess findDaqProcess(PamDataUnit<?, ?> unit) {
		PamDataBlock<?> block = unit.getParentDataBlock();
		if (block == null) {
			return null;
		}
		try {
			PamProcess source = block.getSourceProcess();
			if (source instanceof AcquisitionProcess) {
				return (AcquisitionProcess) source;
			}
		}
		catch (Exception e) {
		}
		return null;
	}

}
