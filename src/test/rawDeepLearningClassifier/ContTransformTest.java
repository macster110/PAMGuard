package test.rawDeepLearningClassifier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import rawDeepLearningClassifier.dlClassification.contTransforms.RawSoundBuffer;
import rawDeepLearningClassifier.dlClassification.contTransforms.TrailingMedianEqualizer;

/**
 * Tests for the continuous transform classes used by deep learning models with "contparams".
 * See also KetosMinkeDLClassifierTest which tests a whole model.
 */
public class ContTransformTest {

	/**
	 * The trailing median should be the same as a brute force median over the window
	 * before each time bin (using the upper median for even numbers of values).
	 */
	@Test
	public void trailingMedianTest() {
		Random random = new Random(42);
		double[][] img = new double[300][7];
		for (int i = 0; i < img.length; i++) {
			for (int j = 0; j < img[i].length; j++) {
				// include repeated values to test removal of duplicates.
				img[i][j] = random.nextInt(20) + (j == 0 ? 0 : random.nextGaussian());
			}
		}

		for (int window : new int[] {1, 2, 15, 50, 400}) {
			for (int outStart : new int[] {0, 10, 120, 299}) {
				double[][] median = TrailingMedianEqualizer.trailingMedian(img, outStart, window);
				assertEquals(img.length - outStart, median.length);
				for (int i = outStart; i < img.length; i++) {
					for (int j = 0; j < img[i].length; j++) {
						int lo = Math.max(0, i - window + 1);
						double[] vals = new double[i - lo + 1];
						for (int k = lo; k <= i; k++) vals[k - lo] = img[k][j];
						Arrays.sort(vals);
						assertEquals(vals[vals.length / 2], median[i - outStart][j], 0,
								"window " + window + " outStart " + outStart + " bin " + i);
					}
				}

				double[][] eq = TrailingMedianEqualizer.medianEqualize(img, outStart, window);
				for (int i = 0; i < eq.length; i++) {
					for (int j = 0; j < eq[i].length; j++) {
						assertEquals(Math.max(0, img[i + outStart][j] - median[i][j]), eq[i][j], 0);
					}
				}
			}
		}
	}

	/**
	 * The raw sound buffer should handle overlapping segments, gaps and data going backwards.
	 */
	@Test
	public void rawSoundBufferTest() {
		double[] sound = new double[1000];
		for (int i = 0; i < sound.length; i++) sound[i] = i;

		RawSoundBuffer buffer = new RawSoundBuffer(300);

		// overlapping segments of 100 samples with a 30 sample hop
		for (int start = 0; start + 100 <= 700; start += 30) {
			boolean reset = buffer.addSegment(start, Arrays.copyOfRange(sound, start, start + 100));
			assertEquals(start == 0, reset);
			assertEquals(start + 100, buffer.getEndSample());
			long from = Math.max(buffer.getStartSample(), start - 150);
			assertArrayEquals(Arrays.copyOfRange(sound, (int) from, start + 100), buffer.getSamples(from, start + 100), 0);
		}
		assertTrue(buffer.getNumSamples() <= 300);
		assertEquals(0, buffer.getNumResets());

		// contiguous segment
		assertFalse(buffer.addSegment(700, Arrays.copyOfRange(sound, 700, 750)));
		assertEquals(450, buffer.getStartSample());
		assertArrayEquals(Arrays.copyOfRange(sound, 450, 750), buffer.getSamples(450, 750), 0);

		// a gap resets the buffer
		assertTrue(buffer.addSegment(800, Arrays.copyOfRange(sound, 800, 900)));
		assertEquals(800, buffer.getStartSample());
		assertEquals(900, buffer.getEndSample());
		assertEquals(1, buffer.getNumResets());

		// data going backwards resets the buffer
		assertTrue(buffer.addSegment(100, Arrays.copyOfRange(sound, 100, 200)));
		assertEquals(100, buffer.getStartSample());
		assertEquals(2, buffer.getNumResets());
	}

}
