package test.rawDeepLearningClassifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jamdev.jdl4pam.transforms.DLTransform;
import org.jamdev.jdl4pam.transforms.FreqTransform;
import org.jamdev.jdl4pam.transforms.SimpleTransform;
import org.jamdev.jdl4pam.transforms.WaveTransform;
import org.jamdev.jdl4pam.transforms.DLTransform.DLTransformType;
import org.jamdev.jdl4pam.utils.DLUtils;
import org.jamdev.jpamutils.wavFiles.AudioData;
import org.junit.jupiter.api.Test;

import rawDeepLearningClassifier.DLStatus;
import rawDeepLearningClassifier.dlClassification.contTransforms.ContTransformParams;
import rawDeepLearningClassifier.dlClassification.contTransforms.TrailingMedianEqualizer;
import rawDeepLearningClassifier.dlClassification.genericModel.StandardPrediction;
import rawDeepLearningClassifier.dlClassification.ketos.KetosDLParams;
import rawDeepLearningClassifier.dlClassification.ketos.KetosWorker2;
import rawDeepLearningClassifier.segmenter.GroupedRawData;

/**
 * Test the North Atlantic minke whale pulse-train detector (Mouy et al. 2025,
 * DOI:10.5281/zenodo.15151160). This is a Ketos model which uses a 60 second
 * median equalizer (ecosound) to denoise the spectrogram.
 * <p>
 * The reference scores were generated using generate_minke_reference.py (in the
 * resources folder) which uses the functions in the detector's
 * run_ketos_detector_denoise.py script. The Ketos model was converted to a PAMGuard
 * compatible .ktpb file using convert_kt_to_ktpb.py.
 * <p>
 * Two sets of reference scores are compared.
 * <ul>
 * <li>segment_denoise - the median equalizer is applied to each 5 second segment.
 * This is exactly what PAMGuard does and so the scores should be (almost)
 * identical.</li>
 * <li>file_denoise - the median equalizer is applied to the whole file before it is
 * segmented, as in the Python detector. PAMGuard only has access to one segment
 * at a time so there are some differences.</li>
 * </ul>
 * Three versions of the model are tested. All have identical weights, only the metadata differs.
 * <ul>
 * <li>model_NAMW_20240523.ktpb - the median equalizer is applied to each 5 s segment.</li>
 * <li>model_NAMW_20240523_60s.ktpb - a 60 s segment which is trimmed to the centre 5 s.</li>
 * <li>model_NAMW_20240523_cont.ktpb - the median equalizer has "contparams" so PAMGuard
 * buffers the raw sound data and uses a 60 s trailing window of data for the median.</li>
 * </ul>
 */
public class KetosMinkeDLClassifierTest {

	private static final String RESOURCE_FOLDER = "./src/test/resources/rawDeepLearningClassifier/Ketos/minke_whale_NAMW/";

	/**
	 * The spectrogram hop in samples - segment indices in the reference file are in FFT hops.
	 */
	private static final int FFT_HOP = 80;

	/**
	 * Test every nth segment in the reference file (segments step by 0.08 s).
	 */
	private static final int SEGMENT_STEP = 25;

	private static final int BATCH_SIZE = 32;

	/**
	 * The 60 s model trims the centre 125 time bins of the segment - see audio_repr_pg_60s.json.
	 */
	private static final int TRIM_START_BIN = 688;

	/**
	 * 60 s segments are slower to process so test fewer of them.
	 */
	private static final int SEGMENT_STEP_60S = 100;

	private static final int BATCH_SIZE_60S = 8;

	/**
	 * The segment hop used for the continuous model in FFT hops (2 s). The reference scores
	 * are every 2 FFT hops so this must be even.
	 */
	private static final int CONT_SEGMENT_HOP = 50;

	@Test
	public void ketosMinkeTest() throws Exception {

		String modelPath = Paths.get(RESOURCE_FOLDER + "model_NAMW_20240523.ktpb").toAbsolutePath().normalize().toString();
		String wavFilePath = Paths.get(RESOURCE_FOLDER + "79296_Brunswick03_002K_M06_UTCm5_CH6_20161227_110000.wav").toAbsolutePath().normalize().toString();
		Path refPath = Paths.get(RESOURCE_FOLDER + "minke_reference_scores.csv").toAbsolutePath().normalize();

		// columns: seg_index, start_sec, noise_file_denoise, minke_file_denoise, noise_segment_denoise, minke_segment_denoise
		ArrayList<double[]> reference = readReference(refPath.toString(), SEGMENT_STEP);

		KetosWorker2 ketosWorker2 = new KetosWorker2();

		KetosDLParams params = new KetosDLParams();
		params.modelPath = modelPath;

		DLStatus status = ketosWorker2.prepModel(params, null);
		assertEquals(DLStatus.MODEL_LOAD_SUCCESS, status);

		AudioData soundData = DLUtils.loadWavFile(wavFilePath);
		double[] soundDataD = soundData.getScaledSampleAmplitudes();

		int segSamples = (int) Math.round((params.defaultSegmentLen / 1000.) * soundData.sampleRate);
		System.out.println("Segment length: " + params.defaultSegmentLen + " ms " + segSamples + " samples");

		int nSegDenoiseMatch = 0;
		int nFileDenoiseMatch = 0;
		int nFileDenoiseDetMatch = 0;
		double maxSegDiff = 0;

		for (int i = 0; i < reference.size(); i += BATCH_SIZE) {

			ArrayList<GroupedRawData> groupedData = new ArrayList<GroupedRawData>();
			int nBatch = Math.min(BATCH_SIZE, reference.size() - i);
			for (int j = 0; j < nBatch; j++) {
				int startSample = (int) reference.get(i + j)[0] * FFT_HOP;
				GroupedRawData groupedRawData = new GroupedRawData(0, 1, startSample, segSamples, segSamples);
				groupedRawData.copyRawData(soundDataD, startSample, segSamples, 0);
				groupedData.add(groupedRawData);
			}

			ArrayList<StandardPrediction> predictions = ketosWorker2.runModel(groupedData, soundData.sampleRate, 0);

			for (int j = 0; j < nBatch; j++) {
				double[] ref = reference.get(i + j);
				float minke = predictions.get(j).getPrediction()[1];

				double segDiff = Math.abs(minke - ref[5]);
				maxSegDiff = Math.max(maxSegDiff, segDiff);
				if (segDiff < 0.05) nSegDenoiseMatch++;
				if (Math.abs(minke - ref[3]) < 0.1) nFileDenoiseMatch++;
				if ((minke >= 0.5) == (ref[3] >= 0.5)) nFileDenoiseDetMatch++;
			}
		}

		ketosWorker2.closeModel();

		int n = reference.size();
		double segMatch = nSegDenoiseMatch / (double) n;
		double fileMatch = nFileDenoiseMatch / (double) n;
		double fileDetMatch = nFileDenoiseDetMatch / (double) n;

		System.out.println(String.format("Minke whale: %d segments tested", n));
		System.out.println(String.format("Segment denoise: %.1f%% within 0.05, max difference %.4f", 100 * segMatch, maxSegDiff));
		System.out.println(String.format("File denoise: %.1f%% within 0.1, %.1f%% agree at 0.5 threshold", 100 * fileMatch, 100 * fileDetMatch));

		// PAMGuard should reproduce the segment denoised python scores.
		assertTrue(segMatch > 0.95);
		// ...and be broadly similar to the whole file denoised scores. In python, 85% of all segments
		// agree at a 0.5 threshold between segment and whole file denoising.
		assertTrue(fileDetMatch > 0.8);
	}


	/**
	 * Test the 60 second version of the model. This uses a 60 second segment so that the
	 * median equalizer has the same 60 seconds of context as the python detector, and then
	 * trims the centre 5 seconds of the spectrogram for the model. The scores should
	 * therefore be much closer to the whole file denoised python scores.
	 * <p>
	 * Note that in PAMGuard the segmenter hop should be set to ~5 s so that every part of
	 * the recording is classified.
	 */
	@Test
	public void ketosMinke60sTest() throws Exception {

		String modelPath = Paths.get(RESOURCE_FOLDER + "model_NAMW_20240523_60s.ktpb").toAbsolutePath().normalize().toString();
		String wavFilePath = Paths.get(RESOURCE_FOLDER + "79296_Brunswick03_002K_M06_UTCm5_CH6_20161227_110000.wav").toAbsolutePath().normalize().toString();
		Path refPath = Paths.get(RESOURCE_FOLDER + "minke_reference_scores.csv").toAbsolutePath().normalize();

		ArrayList<double[]> reference = readReference(refPath.toString(), SEGMENT_STEP_60S);

		KetosWorker2 ketosWorker2 = new KetosWorker2();

		KetosDLParams params = new KetosDLParams();
		params.modelPath = modelPath;

		DLStatus status = ketosWorker2.prepModel(params, null);
		assertEquals(DLStatus.MODEL_LOAD_SUCCESS, status);

		AudioData soundData = DLUtils.loadWavFile(wavFilePath);
		double[] soundDataD = soundData.getScaledSampleAmplitudes();

		int segSamples = (int) Math.round((params.defaultSegmentLen / 1000.) * soundData.sampleRate);
		System.out.println("60s model segment length: " + params.defaultSegmentLen + " ms " + segSamples + " samples");

		int nFileDenoiseMatch = 0;
		int nFileDenoiseDetMatch = 0;
		int nTested = 0;
		double sumDiff = 0;

		ArrayList<double[]> batchRef = new ArrayList<double[]>();
		ArrayList<GroupedRawData> groupedData = new ArrayList<GroupedRawData>();

		for (int i = 0; i <= reference.size(); i++) {

			if (i < reference.size()) {
				// the model input is the centre 125 time bins of the segment so the segment starts
				// TRIM_START_BIN spectrogram hops before the reference segment.
				int startSample = ((int) reference.get(i)[0] - TRIM_START_BIN) * FFT_HOP;
				if (startSample >= 0 && startSample + segSamples <= soundDataD.length) {
					GroupedRawData groupedRawData = new GroupedRawData(0, 1, startSample, segSamples, segSamples);
					groupedRawData.copyRawData(soundDataD, startSample, segSamples, 0);
					groupedData.add(groupedRawData);
					batchRef.add(reference.get(i));
				}
			}

			if (groupedData.size() == BATCH_SIZE_60S || (i == reference.size() && groupedData.size() > 0)) {
				ArrayList<StandardPrediction> predictions = ketosWorker2.runModel(groupedData, soundData.sampleRate, 0);
				for (int j = 0; j < batchRef.size(); j++) {
					double[] ref = batchRef.get(j);
					float minke = predictions.get(j).getPrediction()[1];
					double diff = Math.abs(minke - ref[3]);
					sumDiff += diff;
					if (diff < 0.1) nFileDenoiseMatch++;
					if ((minke >= 0.5) == (ref[3] >= 0.5)) nFileDenoiseDetMatch++;
					nTested++;
				}
				groupedData = new ArrayList<GroupedRawData>();
				batchRef = new ArrayList<double[]>();
			}
		}

		ketosWorker2.closeModel();

		double fileMatch = nFileDenoiseMatch / (double) nTested;
		double fileDetMatch = nFileDenoiseDetMatch / (double) nTested;

		System.out.println(String.format("Minke whale 60s model: %d segments tested", nTested));
		System.out.println(String.format("File denoise: %.1f%% within 0.1, %.1f%% agree at 0.5 threshold, mean abs diff %.4f",
				100 * fileMatch, 100 * fileDetMatch, sumDiff / nTested));

		// measured 100% within 0.1 and 100% agreement at 0.5 (mean abs diff 0.0033)
		assertTrue(fileMatch > 0.95);
		assertTrue(fileDetMatch > 0.98);
	}

	/**
	 * Test the continuous version of the model (model_NAMW_20240523_cont.ktpb). The median
	 * equalizer has contparams with a 60 s time window so PAMGuard buffers the raw sound
	 * data and calculates the median over the 60 s of data before (and including) each time
	 * bin. The python detector uses a centred window (+/- 30 s) so the scores are not
	 * identical, but this does not need data from the future and so works in real time.
	 * Note that for this model a trailing window agrees less well with python than applying
	 * the median equalizer to each 5 s segment.
	 * <p>
	 * Segments are passed to the model in time order with a 2 s hop, as the segmenter would.
	 */
	@Test
	public void ketosMinkeContTest() throws Exception {

		String modelPath = Paths.get(RESOURCE_FOLDER + "model_NAMW_20240523_cont.ktpb").toAbsolutePath().normalize().toString();
		String wavFilePath = Paths.get(RESOURCE_FOLDER + "79296_Brunswick03_002K_M06_UTCm5_CH6_20161227_110000.wav").toAbsolutePath().normalize().toString();
		Path refPath = Paths.get(RESOURCE_FOLDER + "minke_reference_scores.csv").toAbsolutePath().normalize();

		// reference scores by segment index (in FFT hops).
		HashMap<Integer, double[]> reference = new HashMap<Integer, double[]>();
		for (double[] ref : readReference(refPath.toString(), 1)) {
			reference.put((int) ref[0], ref);
		}

		KetosWorker2 ketosWorker2 = new KetosWorker2();

		KetosDLParams params = new KetosDLParams();
		params.modelPath = modelPath;

		DLStatus status = ketosWorker2.prepModel(params, null);
		assertEquals(DLStatus.MODEL_LOAD_SUCCESS, status);

		// the model should have one continuous transform - the median equalizer with a 60 s window.
		assertNotNull(ketosWorker2.getContTransformRunner());
		List<ContTransformParams> contParams = ketosWorker2.getContTransformRunner().getContParams();
		assertEquals(1, contParams.size());
		assertEquals(DLTransformType.MEDIAN_EQUALIZER, contParams.get(0).transformType);
		assertEquals(60.0, contParams.get(0).timeWindow, 1e-9);

		AudioData soundData = DLUtils.loadWavFile(wavFilePath);
		double[] soundDataD = soundData.getScaledSampleAmplitudes();

		int segSamples = (int) Math.round((params.defaultSegmentLen / 1000.) * soundData.sampleRate);
		int hopSamples = CONT_SEGMENT_HOP * FFT_HOP;
		System.out.println("Continuous model segment length: " + params.defaultSegmentLen + " ms " + segSamples + " samples, hop " + hopSamples + " samples");

		int nTested = 0, nMatch = 0, nDetMatch = 0;
		int nTested60 = 0, nMatch60 = 0, nDetMatch60 = 0;
		double sumDiff = 0, sumDiff60 = 0;
		long timeStart = System.currentTimeMillis();

		ArrayList<GroupedRawData> groupedData = new ArrayList<GroupedRawData>();
		for (int startSample = 0; startSample + segSamples <= soundDataD.length; startSample += hopSamples) {

			GroupedRawData groupedRawData = new GroupedRawData(0, 1, startSample, segSamples, segSamples);
			groupedRawData.copyRawData(soundDataD, startSample, segSamples, 0);
			groupedData.add(groupedRawData);

			if (groupedData.size() == BATCH_SIZE || startSample + hopSamples + segSamples > soundDataD.length) {
				ArrayList<StandardPrediction> predictions = ketosWorker2.runModel(groupedData, soundData.sampleRate, 0);
				for (int j = 0; j < groupedData.size(); j++) {
					int segIndex = (int) (groupedData.get(j).getStartSample() / FFT_HOP);
					double[] ref = reference.get(segIndex);
					if (ref == null) continue;
					float minke = predictions.get(j).getPrediction()[1];
					double diff = Math.abs(minke - ref[3]);
					boolean detMatch = (minke >= 0.5) == (ref[3] >= 0.5);
					nTested++;
					sumDiff += diff;
					if (diff < 0.1) nMatch++;
					if (detMatch) nDetMatch++;
					// after the first 60 s the buffer is full.
					if (groupedData.get(j).getStartSample() >= 60 * soundData.sampleRate) {
						nTested60++;
						sumDiff60 += diff;
						if (diff < 0.1) nMatch60++;
						if (detMatch) nDetMatch60++;
					}
				}
				groupedData = new ArrayList<GroupedRawData>();
			}
		}

		// the buffer should never have been reset because the segments are continuous.
		assertEquals(0, ketosWorker2.getContTransformRunner().getBuffer(1, 0).getNumResets());

		ketosWorker2.closeModel();

		System.out.println(String.format("Minke whale continuous model: %d segments tested in %d ms", nTested, System.currentTimeMillis() - timeStart));
		System.out.println(String.format("File denoise (all): %.1f%% within 0.1, %.1f%% agree at 0.5 threshold, mean abs diff %.4f",
				100. * nMatch / nTested, 100. * nDetMatch / nTested, sumDiff / nTested));
		System.out.println(String.format("File denoise (after 60 s): %.1f%% within 0.1, %.1f%% agree at 0.5 threshold, mean abs diff %.4f",
				100. * nMatch60 / nTested60, 100. * nDetMatch60 / nTested60, sumDiff60 / nTested60));

		// measured (28/11/2026) 59.6% within 0.1, 78.2% agree at 0.5, mean abs diff 0.20. The python detector
		// uses a centred window which needs 30 s of future data. Using the same pipeline with a centred window
		// gives 100% agreement so the differences are due to the trailing window, not the transforms. For
		// comparison, the 5 s model (median equalizer on each segment) agrees 83.4% of the time.
		assertTrue(nDetMatch / (double) nTested > 0.75);
	}

	/**
	 * Check the continuous transforms give exactly the same model input as transforming the
	 * whole file at once and then applying a trailing median equalizer i.e. the buffering,
	 * segment overlap, context alignment and trimming are all correct.
	 */
	@Test
	public void ketosMinkeContTransformTest() throws Exception {

		String modelPath = Paths.get(RESOURCE_FOLDER + "model_NAMW_20240523_cont.ktpb").toAbsolutePath().normalize().toString();
		String wavFilePath = Paths.get(RESOURCE_FOLDER + "79296_Brunswick03_002K_M06_UTCm5_CH6_20161227_110000.wav").toAbsolutePath().normalize().toString();

		KetosWorker2 ketosWorker2 = new KetosWorker2();
		KetosDLParams params = new KetosDLParams();
		params.modelPath = modelPath;
		assertEquals(DLStatus.MODEL_LOAD_SUCCESS, ketosWorker2.prepModel(params, null));

		AudioData soundData = DLUtils.loadWavFile(wavFilePath);
		double[] soundDataD = soundData.getScaledSampleAmplitudes();
		int segSamples = (int) Math.round((params.defaultSegmentLen / 1000.) * soundData.sampleRate);

		// use the first 3 minutes of the file.
		int nSamples = (int) (180 * soundData.sampleRate);
		double[] wav = java.util.Arrays.copyOf(soundDataD, nSamples);

		// transform the whole 3 minutes at once.
		List<DLTransform> transforms = ketosWorker2.getModelTransforms();
		ContTransformParams cp = ketosWorker2.getContTransformRunner().getContParams().get(0);
		((WaveTransform) transforms.get(0)).setWaveData(new AudioData(wav, soundData.sampleRate));
		DLTransform transform = transforms.get(0);
		for (int i = 0; i < cp.transformIndex; i++) {
			if (transforms.get(i).getDLTransformType() == DLTransformType.SPECTROGRAMKETOS) {
				// the Ketos spectrogram has a fixed duration - set it to the whole sound.
				Number[] specParams = ((SimpleTransform) transforms.get(i)).getParams();
				Number[] origParams = specParams.clone();
				int nFrames = (nSamples - specParams[0].intValue()) / specParams[1].intValue() + 1;
				specParams[2] = (specParams[0].doubleValue() + (nFrames - 0.5) * specParams[1].doubleValue()) / soundData.sampleRate;
				transform = transforms.get(i).transformData(transform);
				((SimpleTransform) transforms.get(i)).setParams(origParams);
			}
			else {
				transform = transforms.get(i).transformData(transform);
			}
		}
		double[][] wholeSpec = ((FreqTransform) transform).getSpecTransfrom().getTransformedData();
		int windowBins = (int) Math.round(60.0 / (FFT_HOP / (double) soundData.sampleRate));
		double[][] wholeEqualized = TrailingMedianEqualizer.medianEqualize(wholeSpec, 0, windowBins);

		// now pass overlapping segments through the model's transforms. A hop of 1 s, which is not a whole
		// number of segments, tests that segment overlap is handled.
		int hopSamples = 25 * FFT_HOP;
		ArrayList<GroupedRawData> groupedData = new ArrayList<GroupedRawData>();
		for (int startSample = 0; startSample + segSamples <= nSamples; startSample += hopSamples) {
			GroupedRawData groupedRawData = new GroupedRawData(0, 1, startSample, segSamples, segSamples);
			groupedRawData.copyRawData(wav, startSample, segSamples, 0);
			groupedData.add(groupedRawData);
		}
		float[][][] modelInput = ketosWorker2.dataUnits2ModelInput(groupedData, soundData.sampleRate, 0);

		double maxDiff = 0;
		for (int j = 0; j < groupedData.size(); j++) {
			int startFrame = (int) (groupedData.get(j).getStartSample() / FFT_HOP);
			assertEquals(125, modelInput[j].length);
			for (int t = 0; t < modelInput[j].length; t++) {
				for (int f = 0; f < modelInput[j][t].length; f++) {
					maxDiff = Math.max(maxDiff, Math.abs(modelInput[j][t][f] - wholeEqualized[startFrame + t][f]));
				}
			}
		}
		ketosWorker2.closeModel();

		System.out.println(String.format("Continuous transforms vs whole sound: %d segments, max difference %.6f", groupedData.size(), maxDiff));
		assertTrue(maxDiff < 1e-3);
	}

	/**
	 * Read the reference scores CSV file.
	 * @param path - the path to the CSV file.
	 * @param step - read every step rows.
	 * @return the reference scores.
	 */
	private static ArrayList<double[]> readReference(String path, int step) throws Exception {
		ArrayList<double[]> reference = new ArrayList<double[]>();
		try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
			reader.readLine(); // header
			String line;
			int row = 0;
			while ((line = reader.readLine()) != null) {
				if (row++ % step != 0) continue;
				String[] vals = line.split(",");
				double[] data = new double[vals.length];
				for (int i = 0; i < vals.length; i++) {
					data[i] = Double.parseDouble(vals[i]);
				}
				reference.add(data);
			}
		}
		return reference;
	}

}
