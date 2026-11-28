package rawDeepLearningClassifier.dlClassification.contTransforms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jamdev.jdl4pam.transforms.DLTransform;
import org.jamdev.jdl4pam.transforms.DLTransform.DLTransformType;
import org.jamdev.jdl4pam.transforms.FreqTransform;
import org.jamdev.jdl4pam.transforms.SimpleTransform;
import org.jamdev.jdl4pam.transforms.WaveTransform;
import org.jamdev.jpamutils.spectrogram.SpecTransform;
import org.jamdev.jpamutils.spectrogram.Spectrogram;
import org.jamdev.jpamutils.wavFiles.AudioData;

import PamguardMVC.PamDataUnit;
import rawDeepLearningClassifier.segmenter.GroupedRawData;

/**
 * Runs a model's transforms when one or more transforms need a longer time window of
 * data than the segment (i.e. they have "contparams" in the model metadata).
 * <p>
 * Every model starts with raw sound data. The runner keeps a buffer of the raw sound
 * data for each channel, built from the segments, so segment overlap does not matter.
 * For each segment:
 * <ol>
 * <li>The context is taken from the buffer. This is the segment plus up to
 * (timewindow - lookahead) seconds of data before it and lookahead seconds of data
 * after it.</li>
 * <li>The transforms before the first continuous transform are calculated on the
 * context instead of the segment.</li>
 * <li>The continuous transform is calculated using the whole context and its output
 * is trimmed back to the segment. Any transforms after it are calculated as normal.</li>
 * </ol>
 * If a transform has a lookahead, a segment cannot be classified until lookahead seconds
 * of data after it have arrived. Segments should therefore be passed to
 * {@link #addSegments(List, float)}, which buffers the sound data and returns the
 * segments which are ready to be classified (with their context saved), and then
 * classified. {@link #flush()} returns any remaining segments, e.g. at the end of
 * processing, which are classified using whatever data is available. Segments which
 * have not been through addSegments (e.g. in tests) are classified straight away using
 * the data available.
 * <p>
 * If there is more than one continuous transform, each one keeps enough extra data for
 * the continuous transforms after it, i.e. the context is the sum of the time windows.
 * <p>
 * The context always starts and ends a whole number of spectrogram hops from the
 * segment so that the spectrogram frames of the segment are the same as if the segment
 * had been transformed on its own. If there is not enough data in the buffer (e.g. at
 * the start of processing or before a gap) the context is shorter and the continuous
 * transform uses whatever data is available.
 *
 * @author Jamie Macaulay
 */
public class ContTransformRunner {

	/**
	 * The maximum number of saved contexts. Contexts are normally removed as soon as
	 * they are used, this just makes sure memory cannot grow forever.
	 */
	private static final int MAX_SAVED_CONTEXTS = 2000;

	/**
	 * The model transforms.
	 */
	private final List<DLTransform> transforms;

	/**
	 * The continuous parameters, in transform order.
	 */
	private final List<ContTransformParams> contParams;

	/**
	 * The continuous parameters for each transform index (null if the transform is not continuous).
	 */
	private final ContTransformParams[] contParamsIndex;

	/**
	 * The raw sound buffers for each channel.
	 */
	private final HashMap<Long, RawSoundBuffer> buffers = new HashMap<Long, RawSoundBuffer>();

	/**
	 * Segments waiting for data after them to arrive, for each channel bitmap.
	 */
	private final HashMap<Integer, ArrayDeque<GroupedRawData>> pending = new HashMap<Integer, ArrayDeque<GroupedRawData>>();

	/**
	 * The saved context for each segment which is ready to be classified.
	 */
	private final LinkedHashMap<GroupedRawData, SegmentContext[]> contexts = new LinkedHashMap<GroupedRawData, SegmentContext[]>() {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<GroupedRawData, SegmentContext[]> eldest) {
			return size() > MAX_SAVED_CONTEXTS;
		}
	};

	/**
	 * The segment length and sample rate used for the calibration.
	 */
	private int calSegLen = -1;
	private float calSampleRate = -1;

	/**
	 * For each continuous transform, the length of its output (time bins or samples) when
	 * the segment is transformed on its own.
	 */
	private int[] stageLen;

	/**
	 * For each continuous transform, the number of raw sound samples per time bin (or sample) of its input.
	 */
	private double[] stageHop;

	/**
	 * For each transform, the number of time bins output by a fixed length spectrogram transform (e.g.
	 * the Ketos spectrogram) for a segment. Zero if the transform is not a fixed length spectrogram.
	 */
	private int[] fixedSpecFrames;

	/**
	 * For each fixed length spectrogram transform, the number of raw sound samples per time bin.
	 */
	private double[] fixedSpecHop;

	/**
	 * For each fixed length spectrogram transform, the sample rate of the spectrogram.
	 */
	private float[] fixedSpecSampleRate;

	/**
	 * The number of raw sound samples per spectrogram time bin used to align the context with the segment.
	 */
	private double alignHop = 1;

	/**
	 * The total time before the segment needed by all continuous transforms in seconds.
	 */
	private double totalPre;

	/**
	 * The total time after the segment needed by all continuous transforms in seconds.
	 */
	private double totalLookahead;

	/**
	 * The context for a segment.
	 */
	private static class SegmentContext {

		/**
		 * The sound data: the segment plus the data before and after it.
		 */
		final double[] samples;

		/**
		 * The number of samples before the segment.
		 */
		final long before;

		/**
		 * The number of samples after the segment.
		 */
		final long after;

		SegmentContext(double[] samples, long before, long after) {
			this.samples = samples;
			this.before = before;
			this.after = after;
		}
	}

	/**
	 * Create a continuous transform runner.
	 *
	 * @param transforms - the model transforms.
	 * @param contParams - the continuous parameters. Must not be empty.
	 */
	public ContTransformRunner(List<DLTransform> transforms, List<ContTransformParams> contParams) {
		this.transforms = transforms;
		this.contParams = contParams;
		this.contParamsIndex = new ContTransformParams[transforms.size()];
		for (ContTransformParams cp : contParams) {
			contParamsIndex[cp.transformIndex] = cp;
			totalPre += cp.getPreWindow();
			totalLookahead += cp.lookahead;
		}
	}

	/**
	 * Add segments to the sound buffers. Segments for each channel must be passed in
	 * time order. Returns the segments which are ready to be classified, i.e. which have
	 * enough data after them for the lookahead. This includes any earlier segments which
	 * are ready, and any segments before a gap in the data. The context for each segment
	 * is saved so they can be classified later, e.g. on another thread.
	 *
	 * @param dataUnits  - the new segments (GroupedRawData).
	 * @param sampleRate - the sample rate.
	 * @return the segments which are ready to be classified, in time order. May be empty.
	 */
	public synchronized ArrayList<GroupedRawData> addSegments(List<? extends PamDataUnit> dataUnits, float sampleRate) {

		ArrayList<GroupedRawData> ready = new ArrayList<GroupedRawData>();

		for (PamDataUnit dataUnit : dataUnits) {
			GroupedRawData rawData = (GroupedRawData) dataUnit;
			double[][] sound = rawData.getRawData();

			checkCalibration(sound[0], sampleRate);

			if (rawData.getStartSample() == null) {
				// no timing information so cannot buffer - classify the segment on its own.
				ready.add(rawData);
				continue;
			}
			long segStart = rawData.getStartSample();
			int bitmap = rawData.getChannelBitmap();

			ArrayDeque<GroupedRawData> queue = pending.get(bitmap);
			if (queue == null) {
				queue = new ArrayDeque<GroupedRawData>();
				pending.put(bitmap, queue);
			}

			// if the new segment is not continuous with the buffered data, classify all waiting segments
			// before the buffer is reset.
			RawSoundBuffer buffer0 = getBuffer(bitmap, 0, sound[0].length, sampleRate);
			if (!queue.isEmpty() && buffer0.wouldReset(segStart, sound[0].length)) {
				while (!queue.isEmpty()) {
					ready.add(saveContext(queue.poll(), sampleRate));
				}
			}

			for (int iChan = 0; iChan < sound.length; iChan++) {
				getBuffer(bitmap, iChan, sound[iChan].length, sampleRate).addSegment(segStart, sound[iChan]);
			}
			queue.add(rawData);

			// classify any segments which now have enough data after them.
			long lookSamples = (long) Math.ceil(totalLookahead * sampleRate);
			while (!queue.isEmpty()) {
				GroupedRawData first = queue.peek();
				long firstEnd = first.getStartSample() + first.getRawData()[0].length;
				if (firstEnd + lookSamples > buffer0.getEndSample()) break;
				ready.add(saveContext(queue.poll(), sampleRate));
			}
		}

		return ready;
	}

	/**
	 * Get all segments which are waiting for more data, e.g. at the end of processing. The
	 * contexts are saved using the data available so the segments can then be classified.
	 *
	 * @return the waiting segments.
	 */
	public synchronized ArrayList<GroupedRawData> flush() {
		ArrayList<GroupedRawData> ready = new ArrayList<GroupedRawData>();
		for (ArrayDeque<GroupedRawData> queue : pending.values()) {
			while (!queue.isEmpty()) {
				ready.add(saveContext(queue.poll(), calSampleRate));
			}
		}
		return ready;
	}

	/**
	 * Get the number of segments waiting for data after them.
	 *
	 * @return the number of waiting segments.
	 */
	public synchronized int getNumPending() {
		int n = 0;
		for (ArrayDeque<GroupedRawData> queue : pending.values()) {
			n += queue.size();
		}
		return n;
	}

	/**
	 * Save the context of each channel of a segment.
	 *
	 * @param rawData    - the segment.
	 * @param sampleRate - the sample rate.
	 * @return the segment.
	 */
	private GroupedRawData saveContext(GroupedRawData rawData, float sampleRate) {
		double[][] sound = rawData.getRawData();
		SegmentContext[] segContexts = new SegmentContext[sound.length];
		for (int iChan = 0; iChan < sound.length; iChan++) {
			RawSoundBuffer buffer = getBuffer(rawData.getChannelBitmap(), iChan, sound[iChan].length, sampleRate);
			segContexts[iChan] = makeContext(buffer, rawData.getStartSample(), sound[iChan].length, sampleRate);
		}
		contexts.put(rawData, segContexts);
		return rawData;
	}

	/**
	 * Make the context for a segment from the data in a buffer. The segment must be in the buffer.
	 *
	 * @param buffer     - the buffer.
	 * @param segStart   - the start sample of the segment.
	 * @param segLen     - the length of the segment in samples.
	 * @param sampleRate - the sample rate.
	 * @return the context.
	 */
	private SegmentContext makeContext(RawSoundBuffer buffer, long segStart, int segLen, float sampleRate) {
		long segEnd = segStart + segLen;

		// the context starts a whole number of spectrogram hops before the segment...
		long preSamples = (long) Math.ceil(totalPre * sampleRate);
		long lo = Math.max(buffer.getStartSample(), segStart - preSamples);
		long nHops = (long) Math.floor((segStart - lo) / alignHop);
		long ctxStart = segStart - Math.round(nHops * alignHop);
		while (ctxStart < buffer.getStartSample() && nHops > 0) {
			nHops--;
			ctxStart = segStart - Math.round(nHops * alignHop);
		}

		// ...and ends a whole number of spectrogram hops after it.
		long lookSamples = (long) Math.ceil(totalLookahead * sampleRate);
		long hi = Math.min(buffer.getEndSample(), segEnd + lookSamples);
		long nHopsAfter = Math.max(0, (long) Math.floor((hi - segEnd) / alignHop));
		long ctxEnd = segEnd + Math.round(nHopsAfter * alignHop);
		while (ctxEnd > buffer.getEndSample() && nHopsAfter > 0) {
			nHopsAfter--;
			ctxEnd = segEnd + Math.round(nHopsAfter * alignHop);
		}

		return new SegmentContext(buffer.getSamples(ctxStart, ctxEnd), segStart - ctxStart, ctxEnd - segEnd);
	}

	/**
	 * Run the transforms on a segment using the buffered data for continuous transforms.
	 * If the segment has been through {@link #addSegments(List, float)} its saved context is
	 * used. Otherwise the segment is added to the buffer and classified using the data
	 * available, so lookahead is limited to the end of the segment.
	 *
	 * @param rawData    - the segment.
	 * @param iChan      - the channel index within the segment.
	 * @param sampleRate - the sample rate of the segment.
	 * @return the output of the final transform.
	 */
	public synchronized DLTransform runTransforms(GroupedRawData rawData, int iChan, float sampleRate) {

		double[] segment = rawData.getRawData()[iChan];

		checkCalibration(segment, sampleRate);

		SegmentContext context = null;
		SegmentContext[] segContexts = contexts.get(rawData);
		if (segContexts != null && iChan < segContexts.length) {
			context = segContexts[iChan];
			segContexts[iChan] = null;
			boolean used = true;
			for (SegmentContext c : segContexts) {
				if (c != null) used = false;
			}
			if (used) contexts.remove(rawData);
		}

		if (context == null) {
			Long segStart = rawData.getStartSample();
			if (segStart == null) {
				// no timing information so cannot buffer the data.
				return runAll(new AudioData(segment, sampleRate), null, 0, 0);
			}
			RawSoundBuffer buffer = getBuffer(rawData.getChannelBitmap(), iChan, segment.length, sampleRate);
			buffer.addSegment(segStart, segment);
			context = makeContext(buffer, segStart, segment.length, sampleRate);
		}

		return runAll(new AudioData(context.samples, sampleRate), contParamsIndex, context.before, context.after);
	}

	/**
	 * Get the buffer for a channel, creating it if needed.
	 */
	private RawSoundBuffer getBuffer(int channelBitmap, int iChan, int segLen, float sampleRate) {
		long key = (((long) channelBitmap) << 8) | iChan;
		RawSoundBuffer buffer = buffers.get(key);
		if (buffer == null) {
			int maxSamples = (int) Math.ceil((totalPre + totalLookahead) * sampleRate + 4 * alignHop) + 2 * segLen;
			buffer = new RawSoundBuffer(maxSamples);
			buffers.put(key, buffer);
		}
		return buffer;
	}

	/**
	 * Run all the transforms on sound data.
	 *
	 * @param soundData - the sound data (a segment or a context).
	 * @param contIndex - the continuous parameters for each transform. If null the transforms are run
	 *                  as normal i.e. on the segment only.
	 * @param before    - the number of raw sound samples in the context before the segment.
	 * @param after     - the number of raw sound samples in the context after the segment.
	 * @return the output of the final transform.
	 */
	private DLTransform runAll(AudioData soundData, ContTransformParams[] contIndex, long before, long after) {

		((WaveTransform) transforms.get(0)).setWaveData(soundData);

		DLTransform transform = transforms.get(0);
		int stage = 0;
		for (int i = 0; i < transforms.size(); i++) {
			if (contIndex != null && contIndex[i] != null) {
				transform = runContTransform(i, stage, transform, before, after);
				stage++;
			}
			else if (contIndex != null && stage < contParams.size() && fixedSpecFrames[i] > 0) {
				transform = runFixedLengthSpectrogram(i, transform, before + after);
			}
			else {
				transform = transforms.get(i).transformData(transform);
			}
		}
		return transform;
	}

	/**
	 * Run a spectrogram transform which always outputs a fixed number of time bins (e.g. the Ketos
	 * spectrogram, which uses a window duration parameter). The window duration is temporarily
	 * lengthened so that the spectrogram covers the whole context.
	 *
	 * @param index        - the index of the transform.
	 * @param transform    - the output of the previous transform.
	 * @param extraSamples - the number of raw sound samples in the context which are not in the segment.
	 * @return the spectrogram of the context.
	 */
	private DLTransform runFixedLengthSpectrogram(int index, DLTransform transform, long extraSamples) {
		SimpleTransform specTransform = (SimpleTransform) transforms.get(index);
		Number[] params = specTransform.getParams();
		Number[] contextParams = Arrays.copyOf(params, params.length);
		int extraFrames = (int) Math.round(extraSamples / fixedSpecHop[index]);
		int nFrames = fixedSpecFrames[index] + extraFrames;
		// choose a duration in the middle of the range which gives nFrames so rounding errors do not matter.
		double fftLen = params[0].doubleValue();
		double fftHop = params[1].doubleValue();
		contextParams[2] = (fftLen + (nFrames - 0.5) * fftHop) / fixedSpecSampleRate[index];
		try {
			specTransform.setParams(contextParams);
			return specTransform.transformData(transform);
		}
		finally {
			specTransform.setParams(params);
		}
	}

	/**
	 * Run a continuous transform on the context and trim its output.
	 *
	 * @param index     - the index of the transform.
	 * @param stage     - the index of the continuous transform in the list of continuous transforms.
	 * @param transform - the output of the previous transform, calculated over the context.
	 * @param before    - the number of raw sound samples in the context before the segment.
	 * @param after     - the number of raw sound samples in the context after the segment.
	 * @return the transform output, trimmed to the segment plus any data needed by later continuous transforms.
	 */
	private DLTransform runContTransform(int index, int stage, DLTransform transform, long before, long after) {

		ContTransformParams cp = contParams.get(stage);

		// the output must hold the segment plus the data needed by any later continuous transforms.
		double laterPre = 0;
		double laterLook = 0;
		for (int j = stage + 1; j < contParams.size(); j++) {
			laterPre += contParams.get(j).getPreWindow();
			laterLook += contParams.get(j).lookahead;
		}
		double hop = stageHop[stage];
		int segStartBin = (int) Math.round(before / hop);
		int outStart = Math.max(0, segStartBin - (int) Math.floor(laterPre * calSampleRate / hop));
		int outEnd = segStartBin + stageLen[stage] + (int) Math.floor(laterLook * calSampleRate / hop);

		DLTransform modelTransform = transforms.get(index);

		if (cp.transformType == DLTransformType.MEDIAN_EQUALIZER && transform instanceof FreqTransform) {
			// a running median equalizer - only the output time bins are calculated.
			SpecTransform specTransform = ((FreqTransform) transform).getSpecTransfrom();
			double[][] data = specTransform.getTransformedData();
			Spectrogram spectrogram = specTransform.getSpectrgram();
			double timeRes = spectrogram.getFFTHop() / (double) spectrogram.getSampleRate();
			double windowDuration = ((SimpleTransform) modelTransform).getParams()[0].doubleValue();
			int windowBins = (int) Math.round(windowDuration / timeRes);
			int lookaheadBins = (int) Math.round(cp.lookahead / timeRes);
			specTransform.setTransformedData(TrailingMedianEqualizer.medianEqualize(data, outStart,
					Math.min(outEnd, data.length), windowBins, lookaheadBins));
			return transform;
		}

		// any other transform - calculate over the whole context, then trim.
		DLTransform out = modelTransform.transformData(transform);
		if (out instanceof FreqTransform) {
			SpecTransform specTransform = ((FreqTransform) out).getSpecTransfrom();
			double[][] data = specTransform.getTransformedData();
			int end = Math.min(outEnd, data.length);
			if (outStart > 0 || end < data.length) {
				specTransform.setTransformedData(Arrays.copyOfRange(data, Math.min(outStart, end), end));
			}
		}
		else if (out instanceof WaveTransform) {
			AudioData wave = ((WaveTransform) out).getWaveData();
			int len = wave.getScaledSampleAmplitudes().length;
			int end = Math.min(outEnd, len);
			if (outStart > 0 || end < len) {
				((WaveTransform) out).setWaveData(wave.trim(Math.min(outStart, end), end));
			}
		}
		return out;
	}

	/**
	 * Calibrate if the segment length or sample rate has changed.
	 */
	private void checkCalibration(double[] segment, float sampleRate) {
		if (segment.length != calSegLen || sampleRate != calSampleRate) {
			calibrate(segment, sampleRate);
		}
	}

	/**
	 * Calculate the length of each continuous transform's output for a segment on its own,
	 * and the number of raw samples per time bin, by running the transforms on the segment.
	 *
	 * @param segment    - a segment.
	 * @param sampleRate - the sample rate.
	 */
	private void calibrate(double[] segment, float sampleRate) {

		stageLen = new int[contParams.size()];
		stageHop = new double[contParams.size()];
		fixedSpecFrames = new int[transforms.size()];
		fixedSpecHop = new double[transforms.size()];
		fixedSpecSampleRate = new float[transforms.size()];

		((WaveTransform) transforms.get(0)).setWaveData(new AudioData(segment, sampleRate));
		DLTransform transform = transforms.get(0);
		int stage = 0;
		for (int i = 0; i < transforms.size() && stage < contParams.size(); i++) {
			if (contParamsIndex[i] != null) {
				stageHop[stage] = getHop(transform, sampleRate);
				if (stage == 0) alignHop = Math.max(1, stageHop[stage]);
			}
			transform = transforms.get(i).transformData(transform);
			if (transforms.get(i).getDLTransformType() == DLTransformType.SPECTROGRAMKETOS && transform instanceof FreqTransform) {
				Spectrogram spectrogram = ((FreqTransform) transform).getSpecTransfrom().getSpectrgram();
				fixedSpecFrames[i] = getLength(transform);
				fixedSpecHop[i] = getHop(transform, sampleRate);
				fixedSpecSampleRate[i] = spectrogram.getSampleRate();
			}
			if (contParamsIndex[i] != null) {
				stageLen[stage] = getLength(transform);
				stage++;
			}
		}

		calSegLen = segment.length;
		calSampleRate = sampleRate;
	}

	/**
	 * Get the number of raw sound samples per time bin (or sample) of a transform's output.
	 */
	private static double getHop(DLTransform transform, float sampleRate) {
		if (transform instanceof FreqTransform) {
			Spectrogram spectrogram = ((FreqTransform) transform).getSpecTransfrom().getSpectrgram();
			return spectrogram.getFFTHop() * sampleRate / (double) spectrogram.getSampleRate();
		}
		else if (transform instanceof WaveTransform) {
			return sampleRate / (double) ((WaveTransform) transform).getWaveData().getSampleRate();
		}
		return 1;
	}

	/**
	 * Get the length of a transform's output in time bins (spectrogram) or samples (waveform).
	 */
	private static int getLength(DLTransform transform) {
		if (transform instanceof FreqTransform) {
			return ((FreqTransform) transform).getSpecTransfrom().getTransformedData().length;
		}
		else if (transform instanceof WaveTransform) {
			return ((WaveTransform) transform).getWaveData().getScaledSampleAmplitudes().length;
		}
		return 0;
	}

	/**
	 * Clear all buffered data and waiting segments, e.g. at the start of processing.
	 */
	public synchronized void reset() {
		buffers.clear();
		pending.clear();
		contexts.clear();
	}

	/**
	 * Get the continuous parameters.
	 *
	 * @return the continuous parameters.
	 */
	public List<ContTransformParams> getContParams() {
		return contParams;
	}

	/**
	 * Get the total lookahead of the continuous transforms. Results are delayed by this much.
	 *
	 * @return the lookahead in seconds.
	 */
	public double getLookahead() {
		return totalLookahead;
	}

	/**
	 * Get the raw sound buffer for a channel.
	 *
	 * @param channelBitmap - the channel bitmap of the segments.
	 * @param iChan         - the channel index within the segments.
	 * @return the buffer, or null if no data has been received for the channel.
	 */
	public RawSoundBuffer getBuffer(int channelBitmap, int iChan) {
		return buffers.get((((long) channelBitmap) << 8) | iChan);
	}

}
