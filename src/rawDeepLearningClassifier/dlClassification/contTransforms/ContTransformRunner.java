package rawDeepLearningClassifier.dlClassification.contTransforms;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.jamdev.jdl4pam.transforms.DLTransform;
import org.jamdev.jdl4pam.transforms.DLTransform.DLTransformType;
import org.jamdev.jdl4pam.transforms.FreqTransform;
import org.jamdev.jdl4pam.transforms.SimpleTransform;
import org.jamdev.jdl4pam.transforms.WaveTransform;
import org.jamdev.jpamutils.spectrogram.SpecTransform;
import org.jamdev.jpamutils.spectrogram.Spectrogram;
import org.jamdev.jpamutils.wavFiles.AudioData;

import rawDeepLearningClassifier.segmenter.GroupedRawData;

/**
 * Runs a model's transforms when one or more transforms need a longer time window of
 * data than the segment (i.e. they have "contparams" in the model metadata).
 * <p>
 * Every model starts with raw sound data. The runner keeps a buffer of the raw sound
 * data for each channel, built from the segments, so segment overlap does not matter.
 * For each new segment:
 * <ol>
 * <li>The context is taken from the buffer. This is up to timewindow seconds of data
 * before the segment, plus the segment.</li>
 * <li>The transforms before the first continuous transform are calculated on the
 * context instead of the segment.</li>
 * <li>The continuous transform is calculated using the whole context and its output
 * is trimmed back to the segment. Any transforms after it are calculated as normal.</li>
 * </ol>
 * If there is more than one continuous transform, each one keeps enough extra data for
 * the continuous transforms after it, i.e. the context is the sum of the time windows.
 * <p>
 * The context always starts a whole number of spectrogram hops before the segment so
 * that the spectrogram frames of the segment are the same as if the segment had been
 * transformed on its own. If there is not enough data in the buffer (e.g. at the start
 * of processing or after a gap) the context is shorter and the continuous transform
 * uses whatever data is available.
 *
 * @author Jamie Macaulay
 */
public class ContTransformRunner {

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
	 * The total length of all continuous time windows in seconds.
	 */
	private double totalTimeWindow;

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
			totalTimeWindow += cp.timeWindow;
		}
	}

	/**
	 * Run the transforms on a segment using the buffered data for continuous transforms.
	 * Segments for each channel must be passed in time order.
	 *
	 * @param rawData    - the segment.
	 * @param iChan      - the channel index within the segment.
	 * @param sampleRate - the sample rate of the segment.
	 * @return the output of the final transform.
	 */
	public synchronized DLTransform runTransforms(GroupedRawData rawData, int iChan, float sampleRate) {

		double[] segment = rawData.getRawData()[iChan];

		if (segment.length != calSegLen || sampleRate != calSampleRate) {
			calibrate(segment, sampleRate);
		}

		Long segStartL = rawData.getStartSample();
		if (segStartL == null) {
			// no timing information so cannot buffer the data.
			return runAll(new AudioData(segment, sampleRate), null, 0);
		}
		long segStart = segStartL;
		long segEnd = segStart + segment.length;

		long key = (((long) rawData.getChannelBitmap()) << 8) | iChan;
		RawSoundBuffer buffer = buffers.get(key);
		if (buffer == null) {
			int maxSamples = (int) Math.ceil(totalTimeWindow * sampleRate + 2 * alignHop) + segment.length;
			buffer = new RawSoundBuffer(maxSamples);
			buffers.put(key, buffer);
		}
		buffer.addSegment(segStart, segment);

		// the context starts a whole number of spectrogram hops before the segment.
		long preSamples = (long) Math.ceil(totalTimeWindow * sampleRate);
		long lo = Math.max(buffer.getStartSample(), segStart - preSamples);
		long nHops = (long) Math.floor((segStart - lo) / alignHop);
		long ctxStart = segStart - Math.round(nHops * alignHop);
		while (ctxStart < buffer.getStartSample() && nHops > 0) {
			nHops--;
			ctxStart = segStart - Math.round(nHops * alignHop);
		}

		double[] context = buffer.getSamples(ctxStart, segEnd);

		return runAll(new AudioData(context, sampleRate), contParamsIndex, segStart - ctxStart);
	}

	/**
	 * Run all the transforms on sound data.
	 *
	 * @param soundData - the sound data (a segment or a context).
	 * @param contIndex - the continuous parameters for each transform. If null the transforms are run
	 *                  as normal i.e. on the segment only.
	 * @param extraSamples - the number of raw sound samples in the context before the segment.
	 * @return the output of the final transform.
	 */
	private DLTransform runAll(AudioData soundData, ContTransformParams[] contIndex, long extraSamples) {

		((WaveTransform) transforms.get(0)).setWaveData(soundData);

		DLTransform transform = transforms.get(0);
		int stage = 0;
		for (int i = 0; i < transforms.size(); i++) {
			if (contIndex != null && contIndex[i] != null) {
				transform = runContTransform(i, stage, transform);
				stage++;
			}
			else if (contIndex != null && stage < contParams.size() && fixedSpecFrames[i] > 0) {
				transform = runFixedLengthSpectrogram(i, transform, extraSamples);
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
	 * @param extraSamples - the number of raw sound samples in the context before the segment.
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
	 * @return the transform output, trimmed to the segment plus any data needed by later continuous transforms.
	 */
	private DLTransform runContTransform(int index, int stage, DLTransform transform) {

		ContTransformParams cp = contParams.get(stage);

		// the output must hold the segment plus the time windows of any later continuous transforms.
		double laterWindow = 0;
		for (int j = stage + 1; j < contParams.size(); j++) {
			laterWindow += contParams.get(j).timeWindow;
		}
		int outLen = stageLen[stage] + (int) Math.floor(laterWindow * calSampleRate / stageHop[stage]);

		DLTransform modelTransform = transforms.get(index);

		if (cp.transformType == DLTransformType.MEDIAN_EQUALIZER && transform instanceof FreqTransform) {
			// a trailing median equalizer - only the output time bins are calculated.
			SpecTransform specTransform = ((FreqTransform) transform).getSpecTransfrom();
			double[][] data = specTransform.getTransformedData();
			Spectrogram spectrogram = specTransform.getSpectrgram();
			double timeRes = spectrogram.getFFTHop() / (double) spectrogram.getSampleRate();
			double windowDuration = ((SimpleTransform) modelTransform).getParams()[0].doubleValue();
			int windowBins = (int) Math.round(windowDuration / timeRes);
			int outStart = Math.max(0, data.length - outLen);
			specTransform.setTransformedData(TrailingMedianEqualizer.medianEqualize(data, outStart, windowBins));
			return transform;
		}

		// any other transform - calculate over the whole context, then trim.
		DLTransform out = modelTransform.transformData(transform);
		if (out instanceof FreqTransform) {
			SpecTransform specTransform = ((FreqTransform) out).getSpecTransfrom();
			double[][] data = specTransform.getTransformedData();
			if (data.length > outLen) {
				specTransform.setTransformedData(Arrays.copyOfRange(data, data.length - outLen, data.length));
			}
		}
		else if (out instanceof WaveTransform) {
			AudioData wave = ((WaveTransform) out).getWaveData();
			int len = wave.getScaledSampleAmplitudes().length;
			if (len > outLen) {
				((WaveTransform) out).setWaveData(wave.trim(len - outLen, len));
			}
		}
		return out;
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
		// the buffers depend on the segment length.
		buffers.clear();
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
	 * Clear all buffered data, e.g. at the start of processing.
	 */
	public synchronized void reset() {
		buffers.clear();
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
