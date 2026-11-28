package rawDeepLearningClassifier.dlClassification.contTransforms;

import java.util.Arrays;

/**
 * A median equalizer which uses a running median over a window which mostly trails
 * each time bin. This is the continuous version of the ecosound "median_equalizer"
 * denoiser (see jpamutils SpecTransform.medianEqualizer) for real time data.
 * <p>
 * For each time bin a median is calculated for each frequency bin over a window of
 * windowBins time bins which ends lookaheadBins after that bin, i.e. bins
 * i + lookaheadBins - windowBins + 1 to i + lookaheadBins. The median is subtracted from
 * the spectrogram and negative values are set to zero. If there is less data than the
 * window (e.g. at the start of processing) the median is calculated from whatever data
 * is available.
 * <p>
 * Note that ecosound centres the window on each bin (i.e. the lookahead is half the
 * window). In real time a long lookahead means results are delayed, so PAMGuard models
 * can use a shorter lookahead, or none at all.
 *
 * @author Jamie Macaulay
 */
public class TrailingMedianEqualizer {

	/**
	 * Apply a trailing median equalizer (no lookahead) to the end of a spectrogram.
	 *
	 * @param img        - the spectrogram. The first index is time and the second is frequency.
	 * @param outStart   - the first time bin to output. Bins before this are only used to calculate the median.
	 * @param windowBins - the length of the running median window in time bins.
	 * @return the equalized spectrogram from time bin outStart to the end of img.
	 */
	public static double[][] medianEqualize(double[][] img, int outStart, int windowBins) {
		return medianEqualize(img, outStart, img.length, windowBins, 0);
	}

	/**
	 * Apply a median equalizer to part of a spectrogram.
	 *
	 * @param img           - the spectrogram. The first index is time and the second is frequency.
	 * @param outStart      - the first time bin to output.
	 * @param outEnd        - the time bin after the last bin to output.
	 * @param windowBins    - the length of the running median window in time bins.
	 * @param lookaheadBins - the number of time bins after each bin which are included in the window.
	 * @return the equalized spectrogram from time bin outStart to outEnd.
	 */
	public static double[][] medianEqualize(double[][] img, int outStart, int outEnd, int windowBins, int lookaheadBins) {

		double[][] median = trailingMedian(img, outStart, outEnd, windowBins, lookaheadBins);

		outStart = Math.max(0, Math.min(outStart, img.length));
		double[][] out = new double[median.length][];
		for (int i = 0; i < median.length; i++) {
			out[i] = new double[median[i].length];
			for (int j = 0; j < median[i].length; j++) {
				out[i][j] = Math.max(img[i + outStart][j] - median[i][j], 0);
			}
		}
		return out;
	}

	/**
	 * Calculate a trailing running median (no lookahead) along the time axis of each
	 * frequency bin. The median for time bin i uses bins max(0, i - windowBins + 1) to i.
	 *
	 * @param img        - the spectrogram. The first index is time and the second is frequency.
	 * @param outStart   - the first time bin to calculate the median for.
	 * @param windowBins - the length of the running median window in time bins.
	 * @return the running median from time bin outStart to the end of img.
	 */
	public static double[][] trailingMedian(double[][] img, int outStart, int windowBins) {
		return trailingMedian(img, outStart, img.length, windowBins, 0);
	}

	/**
	 * Calculate a running median along the time axis of each frequency bin. The median
	 * for time bin i uses bins i + lookaheadBins - windowBins + 1 to i + lookaheadBins,
	 * limited to the bins in img. For an even number of values the upper median is used
	 * (the same as scipy.ndimage.median_filter).
	 *
	 * @param img           - the spectrogram. The first index is time and the second is frequency.
	 * @param outStart      - the first time bin to calculate the median for.
	 * @param outEnd        - the time bin after the last bin to calculate the median for.
	 * @param windowBins    - the length of the running median window in time bins.
	 * @param lookaheadBins - the number of time bins after each bin which are included in the window.
	 * @return the running median from time bin outStart to outEnd.
	 */
	public static double[][] trailingMedian(double[][] img, int outStart, int outEnd, int windowBins, int lookaheadBins) {

		int nTime = img.length;
		outStart = Math.max(0, Math.min(outStart, nTime));
		outEnd = Math.max(outStart, Math.min(outEnd, nTime));
		int nOut = outEnd - outStart;
		if (nOut == 0) return new double[0][];

		int nFreq = img[0].length;
		int window = Math.max(1, windowBins);
		int look = Math.max(0, Math.min(lookaheadBins, window - 1));

		double[][] median = new double[nOut][nFreq];

		// sorted values in the window for one frequency bin.
		double[] sorted = new double[window];

		for (int j = 0; j < nFreq; j++) {

			// the window for the first output bin is lo to hi (inclusive).
			int lo = Math.max(0, outStart + look - window + 1);
			int hi = Math.min(nTime - 1, outStart + look);
			int count = 0;
			for (int i = lo; i <= hi; i++) {
				sorted[count++] = img[i][j];
			}
			Arrays.sort(sorted, 0, count);
			median[0][j] = sorted[count / 2];

			for (int i = outStart + 1; i < outEnd; i++) {
				// remove the bin which has left the window
				int newLo = Math.max(0, i + look - window + 1);
				if (newLo > lo) {
					int idx = Arrays.binarySearch(sorted, 0, count, img[lo][j]);
					System.arraycopy(sorted, idx + 1, sorted, idx, count - idx - 1);
					count--;
					lo = newLo;
				}
				// add the bin which has entered the window
				int newHi = Math.min(nTime - 1, i + look);
				if (newHi > hi) {
					double val = img[newHi][j];
					int idx = Arrays.binarySearch(sorted, 0, count, val);
					if (idx < 0) idx = -idx - 1;
					System.arraycopy(sorted, idx, sorted, idx + 1, count - idx);
					sorted[idx] = val;
					count++;
					hi = newHi;
				}

				median[i - outStart][j] = sorted[count / 2];
			}
		}

		return median;
	}

}
