package rawDeepLearningClassifier.dlClassification.contTransforms;

/**
 * A buffer of continuous raw sound data for one channel, built from a series of
 * (possibly overlapping) segments.
 * <p>
 * Each new segment adds only the samples after the current end of the buffer, so
 * segment overlap does not matter, and a segment which is already in the buffer is
 * ignored. If there is a gap between segments, or a segment starts before the start of
 * the buffer (e.g. the data has jumped backwards in time), the buffer is reset and
 * starts again from the new segment.
 *
 * @author Jamie Macaulay
 */
public class RawSoundBuffer {

	/**
	 * The sound data. Only the samples from offset to offset + n are valid.
	 */
	private double[] data = new double[0];

	/**
	 * The index in data of the first valid sample.
	 */
	private int offset = 0;

	/**
	 * The number of valid samples.
	 */
	private int n = 0;

	/**
	 * The absolute sample number of the first valid sample.
	 */
	private long startSample = 0;

	/**
	 * The maximum number of samples to keep.
	 */
	private int maxSamples;

	/**
	 * The number of resets (excluding the first segment).
	 */
	private int nResets = 0;

	/**
	 * Create a raw sound buffer.
	 *
	 * @param maxSamples - the maximum number of samples to keep.
	 */
	public RawSoundBuffer(int maxSamples) {
		this.maxSamples = maxSamples;
	}

	/**
	 * Add a segment of sound data.
	 *
	 * @param segStart - the absolute start sample of the segment.
	 * @param segment  - the segment sound data.
	 * @return true if the buffer was reset by this segment.
	 */
	public boolean addSegment(long segStart, double[] segment) {

		long end = startSample + n;

		boolean reset = false;
		if (wouldReset(segStart, segment.length)) {
			// first data, a gap or data going backwards - start again.
			if (n > 0) nResets++;
			reset = true;
			n = 0;
			offset = 0;
			startSample = segStart;
			end = segStart;
		}

		// only add the samples after the current end of the buffer.
		int newStart = (int) (end - segStart);
		int nNew = segment.length - newStart;
		if (nNew > 0) {
			ensureCapacity(nNew);
			System.arraycopy(segment, newStart, data, offset + n, nNew);
			n += nNew;
		}

		// drop old samples.
		if (n > maxSamples) {
			int drop = n - maxSamples;
			offset += drop;
			n -= drop;
			startSample += drop;
		}

		return reset;
	}

	/**
	 * Check whether adding a segment would reset the buffer, i.e. the segment is not
	 * continuous with the data in the buffer.
	 *
	 * @param segStart - the absolute start sample of the segment.
	 * @param segLen   - the length of the segment in samples.
	 * @return true if adding the segment would reset the buffer.
	 */
	public boolean wouldReset(long segStart, int segLen) {
		long end = startSample + n;
		return n == 0 || segStart > end || segStart < startSample;
	}

	/**
	 * Make sure there is space to add samples to the end of the buffer. Rather than
	 * copying the data every time samples are dropped, old samples are only removed
	 * when the array is full.
	 *
	 * @param nNew - the number of new samples.
	 */
	private void ensureCapacity(int nNew) {
		if (offset + n + nNew <= data.length) return;
		int required = n + nNew;
		if (required <= data.length && offset > 0) {
			System.arraycopy(data, offset, data, 0, n);
		}
		else {
			double[] newData = new double[Math.max(required, 2 * Math.max(maxSamples, required))];
			System.arraycopy(data, offset, newData, 0, n);
			data = newData;
		}
		offset = 0;
	}

	/**
	 * Get the sound data from a start sample to an end sample.
	 *
	 * @param from - the absolute start sample (inclusive). Must be within the buffer.
	 * @param to   - the absolute end sample (exclusive). Must be within the buffer.
	 * @return the sound data.
	 */
	public double[] getSamples(long from, long to) {
		if (from < startSample || to > startSample + n || to < from) {
			throw new IllegalArgumentException("RawSoundBuffer: samples " + from + " to " + to
					+ " are not in the buffer (" + startSample + " to " + (startSample + n) + ")");
		}
		double[] samples = new double[(int) (to - from)];
		System.arraycopy(data, offset + (int) (from - startSample), samples, 0, samples.length);
		return samples;
	}

	/**
	 * Get the absolute sample number of the first sample in the buffer.
	 *
	 * @return the first sample.
	 */
	public long getStartSample() {
		return startSample;
	}

	/**
	 * Get the absolute sample number after the last sample in the buffer.
	 *
	 * @return the end sample (exclusive).
	 */
	public long getEndSample() {
		return startSample + n;
	}

	/**
	 * Get the number of samples in the buffer.
	 *
	 * @return the number of samples.
	 */
	public int getNumSamples() {
		return n;
	}

	/**
	 * Get the maximum number of samples the buffer holds.
	 *
	 * @return the maximum number of samples.
	 */
	public int getMaxSamples() {
		return maxSamples;
	}

	/**
	 * Get the number of times the buffer has been reset because of a gap in the data.
	 *
	 * @return the number of resets.
	 */
	public int getNumResets() {
		return nResets;
	}

	/**
	 * Clear the buffer.
	 */
	public void clear() {
		n = 0;
		offset = 0;
		startSample = 0;
		data = new double[0];
	}

}
