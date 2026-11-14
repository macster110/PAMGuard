package clipgenerator.layoutFX;

import java.util.ArrayList;
import java.util.List;

import PamView.paneloverlay.overlaymark.MarkDataSelector;
import PamView.paneloverlay.overlaymark.OverlayMark;
import PamView.paneloverlay.overlaymark.OverlayMarkObserver;
import PamView.paneloverlay.overlaymark.OverlayMarker;
import PamguardMVC.PamDataUnit;

/**
 * Marker for the JavaFX clip display. Clips selected (highlighted) in the display
 * are passed to mark observers, e.g. to annotate them or group them, in the same
 * way as the Swing clip display.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipDisplayMarkerFX extends OverlayMarker {

	private ClipDisplayFX clipDisplay;

	public ClipDisplayMarkerFX(ClipDisplayFX clipDisplay) {
		super(clipDisplay, 0, clipDisplay.getProjector());
		this.clipDisplay = clipDisplay;
	}

	@Override
	public String getMarkerName() {
		return clipDisplay.getUniqueName();
	}

	@Override
	public List<PamDataUnit> getSelectedMarkedDataUnits(OverlayMark overlayMark, MarkDataSelector markDataSelector) {
		return getHighlightedUnits();
	}

	@Override
	public List<PamDataUnit> getSelectedMarkedDataUnits(OverlayMark overlayMark, MarkDataSelector markDataSelector,
			int minOverlap) {
		return getHighlightedUnits();
	}

	private List<PamDataUnit> getHighlightedUnits() {
		return new ArrayList<PamDataUnit>(clipDisplay.getClipCanvas().getSelectedClips());
	}

	/**
	 * Get popup menu items from the mark observers (e.g. annotation or grouping
	 * modules) for the currently selected clips.
	 * @param e - the mouse event.
	 * @return list of FX menu items, or null if there are none.
	 */
	public List<javafx.scene.control.MenuItem> getSelectionMenuItems(javafx.scene.input.MouseEvent e) {
		if (getObserverCount() == 0 || clipDisplay.getClipCanvas().getSelectedClips().isEmpty()) {
			return null;
		}
		// the marked units are always the highlighted clips so any mark will do.
		OverlayMark mark = new OverlayMark(this, clipDisplay, null, 0, getProjector().getParameterTypes(),
				getProjector().getParameterUnits());
		setCurrentMark(mark);
		try {
			return getPopupMenuItems(e);
		}
		finally {
			setCurrentMark(null);
		}
	}

	@Override
	public boolean canMark(OverlayMarkObserver markObserver) {
		return super.canMark(markObserver);
	}

	@Override
	public boolean isCanMark(javafx.scene.input.MouseEvent e) {
		return getObserverCount() > 0;
	}

}
