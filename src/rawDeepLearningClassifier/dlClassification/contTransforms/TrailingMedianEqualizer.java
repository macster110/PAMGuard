package rawDeepLearningClassifier.dlClassification.contTransforms;

import java.util.Arrays;

/**
 * A median equalizer which uses a trailing running median. This is the continuous
 * version of the ecosound "median_equalizer" denoiser (see jpamutils
 * SpecTransform.medianEqualizer) for real time data.
 * <p>
 * For each time bin a median, over the time window before and including that bin, is
 * calculated for each frequency bin. The median is subtracted from the spectrogram and
 * negative values are set to zero. If there is less data than the window (e.g. at the
 * start of processing) the median is calculated from whatever data is available.
 * <p>
 * Note that ecosound centres the window on each bin, which requires data from half a
 * window in the future. PAMGuard is real time so uses a trailing window instead.
 *
 * @author Jamie Macaulay
 */
public class TrailingMedianEqualizer {

	/**
	 * Apply a trailing median equalizer to the end of a spectrogram.
	 *
	 * @param img        - the spectrogram. The first index is time and the second is frequency.
	 * @param outStart   - the first time bin to output. Bins before this are only used to calculate the median.
	 * @param windowBins - the length of the running median window in time bins.
	 * @return the equalized spectrogram from time bin outStart to the end of img.
	 */
	public static double[][] medianEqualize(double[][] img, int outStart, int windowBins) {

		double[][] median = trailingMedian(img, outStart, windowBins);

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
	 * Calculate a trailing running median along the time axis of each frequency bin.
	 * The median for time bin i uses bins max(0, i - windowBins + 1) to i. For an even
	 * number of values the upper median is used (the same as scipy.ndimage.median_filter).
	 *
	 * @param img        - the spectrogram. The first index is time and the second is frequency.
	 * @param outStart   - the first time bin to calculate the median for.
	 * @param windowBins - the length of the running median window in time bins.
	 * @return the running median from time bin outStart to the end of img.
	 */
	public static double[][] trailingMedian(double[][] img, int outStart, int windowBins) {

		int nTime = img.length;
		outStart = Math.max(0, Math.min(outStart, nTime));
		int nOut = nTime - outStart;
		if (nOut == 0) return new double[0][];

		int nFreq = img[0].length;
		int window = Math.max(1, windowBins);

		double[][] median = new double[nOut][nFreq];

		// sorted values in the window for one frequency bin.
		double[] sorted = new double[window];

		for (int j = 0; j < nFreq; j++) {

			int lo = Math.max(0, outStart - window + 1);
			int count = 0;
			for (int i = lo; i <= outStart; i++) {
				sorted[count++] = img[i][j];
			}
			Arrays.sort(sorted, 0, count);
			median[0][j] = sorted[count / 2];

			for (int i = outStart + 1; i < nTime; i++) {
				// remove the bin which has left the window
				int old = i - window;
				if (old >= 0) {
					int idx = Arrays.binarySearch(sorted, 0, count, img[old][j]);
					System.arraycopy(sorted, idx + 1, sorted, idx, count - idx - 1);
					count--;
				}
				// add the new bin
				double val = img[i][j];
				int idx = Arrays.binarySearch(sorted, 0, count, val);
				if (idx < 0) idx = -idx - 1;
				System.arraycopy(sorted, idx, sorted, idx + 1, count - idx);
				sorted[idx] = val;
				count++;

				median[i - outStart][j] = sorted[count / 2];
			}
		}

		return median;
	}

}
