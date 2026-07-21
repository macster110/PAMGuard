package deepWhistle;

import java.util.List;

import fftManager.FFTDataUnit;

/**
 * Interface for FFT masking algorithms.
 */
public interface PamFFTMask {

	/**
	 * Initialize the mask. Called once before processing starts.
	 * 
	 * @return true if initialization was successful, false otherwise
	 */
	public boolean initMask();

	/**
	 * Apply the mask to a batch of FFTDataUnit objects. The mask performs some operation
	 * on the FFT data within each FFTDataUnit in the batch and returns the modified data units.
	 * 
	 * @param batch List of FFTDataUnit objects to process
	 * @return List of FFTDataUnit objects after applying the mask
	 */
	public List<FFTDataUnit> applyMask(List<FFTDataUnit> batch);

	/**
	 * The buffer length (in seconds) this mask prefers to process at. Masks whose
	 * model was trained on a fixed block length can override this so the number of
	 * buffered FFT slices matches the model, regardless of the user-configured
	 * buffer. A value &le; 0 (the default) means "use the user-configured buffer".
	 *
	 * @return the preferred buffer length in seconds, or &le; 0 to use the
	 *         configured buffer.
	 */
	default double getPreferredBufferSeconds() {
		return -1;
	}

}
