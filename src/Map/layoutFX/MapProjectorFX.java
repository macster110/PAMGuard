package Map.layoutFX;

import Map.MapRectProjector;
import Map.TransformUtilities;
import PamUtils.Coordinate3d;

/**
 * Map projector for the JavaFX map. This is the same as the Swing map
 * projector but does not rely on the size of a Swing panel to work out symbol
 * sizes.
 *
 * @author Jamie Macaulay
 *
 */
public class MapProjectorFX extends MapRectProjector {

	private TransformUtilities perspectiveUtils = new TransformUtilities();

	public MapProjectorFX() {
		super();
	}

	@Override
	public int symbolSizePerpective(int originalSize, Coordinate3d screenPos, int minSize, int maxSize) {
		double cameraHeight = perspectiveUtils.getCameraHeight(getPanelHeight());
		if (cameraHeight - screenPos.z == 0) {
			return originalSize;
		}
		double newSize = (double) originalSize * cameraHeight / (cameraHeight - screenPos.z);
		return (int) Math.round(newSize);
	}

}
