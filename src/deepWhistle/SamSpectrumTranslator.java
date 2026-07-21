package deepWhistle;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/**
 * DJL translator for the SAM-Whistle model.
 * <p>
 * The SAM-Whistle model is a Segment-Anything based network which expects a
 * three-channel image of shape <code>[1, 3, H, W]</code>. Whistle spectrograms
 * are single-channel, so - exactly as the Python inference code does
 * (<code>np.stack([block, block, block])</code>) - the single spectrogram
 * channel is replicated across all three input channels.
 * <p>
 * The input is a single-channel spectrogram block <code>float[H][W]</code> where
 * <code>H</code> is frequency and <code>W</code> is time. The output is the
 * flattened confidence surface (sigmoid, values in [0, 1]) returned by the
 * model, which the caller reshapes back to <code>[H][W]</code>.
 *
 * @author Jamie Macaulay
 */
public class SamSpectrumTranslator implements Translator<float[][], float[]> {

	@Override
	public NDList processInput(TranslatorContext ctx, float[][] data) {
		NDManager manager = ctx.getNDManager();

		int height = data.length;
		int width = data[0].length;

		//replicate the single spectrogram channel across the 3 input channels the
		//SAM image encoder expects. Layout is [1, 3, H, W] in row-major (C) order.
		float[] input = new float[3 * height * width];
		int idx = 0;
		for (int c = 0; c < 3; c++) {
			for (int h = 0; h < height; h++) {
				for (int w = 0; w < width; w++) {
					input[idx++] = data[h][w];
				}
			}
		}

		NDArray array = manager.create(input, new Shape(1, 3, height, width));

		NDList list = new NDList();
		list.add(array);
		return list;
	}

	@Override
	public float[] processOutput(TranslatorContext ctx, NDList list) {
		NDArray output = list.get(0);
		Number[] number = output.toArray();
		float[] results = new float[number.length];
		for (int i = 0; i < number.length; i++) {
			results[i] = number[i].floatValue();
		}
		return results;
	}

	@Override
	public Batchifier getBatchifier() {
		//the input is already batched ([1, 3, H, W]) so no batchifier is needed.
		return null;
	}

}
