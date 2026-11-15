package clipDisplayFX;

import java.util.ArrayList;

import PamController.PamController;
import PamController.PamControllerInterface;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamDataUnit;
import clipgenerator.ClipDataBlock;
import javafx.application.Platform;
import userDisplayFX.UserDisplayControlFX;
import userDisplayFX.UserDisplayNodeFX;

/**
 * A JavaFX display module which shows sounds as a grid of spectrogram clips. The data
 * source can be any data block whose data units hold raw sound, i.e. implement
 * {@link PamguardMVC.RawDataHolder}, for example clips from the clip generator,
 * clicks from the click detector or detections from the deep learning classifier.
 * The data source is chosen by connecting the module in the data model or in the
 * display options.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipDisplayControlFX extends UserDisplayControlFX {

	private ArrayList<UserDisplayNodeFX> displays;

	private ClipDisplayFX clipDisplay;

	public ClipDisplayControlFX(String unitName) {
		super(unitName);
		setMultiParent(false);
		setCompatibleDataUnits();
	}

	/**
	 * Get all of the data blocks whose data units hold raw sound.
	 * @return list of data blocks.
	 */
	public static ArrayList<PamDataBlock<?>> getRawClipBlocks() {
		ArrayList<PamDataBlock<?>> blocks = new ArrayList<PamDataBlock<?>>();
		ArrayList<PamDataBlock> all = PamController.getInstance().getDataBlocks();
		if (all == null) {
			return blocks;
		}
		for (PamDataBlock<?> block : all) {
			if (RawClipHelper.isRawClipBlock(block)) {
				blocks.add(block);
			}
		}
		return blocks;
	}

	/**
	 * The data model connects modules using the exact data unit class, so list the
	 * classes of all the data blocks which hold raw sound.
	 */
	@SuppressWarnings("unchecked")
	private void setCompatibleDataUnits() {
		removeCompatibleDataUnits();
		for (PamDataBlock<?> block : getRawClipBlocks()) {
			Class<? extends PamDataUnit> unitClass = block.getUnitClass();
			if (!getUserDisplayProcess().getCompatibleDataUnits().contains(unitClass)) {
				addCompatibleUnit(unitClass);
			}
		}
	}

	/**
	 * Set the data shown on the display.
	 * @param dataBlock - a data block whose data units hold raw sound, or null.
	 */
	@SuppressWarnings("rawtypes")
	public void setSourceDataBlock(PamDataBlock dataBlock) {
		if (dataBlock != null && !RawClipHelper.isRawClipBlock(dataBlock)) {
			return;
		}
		// notifies a CHANGED_PROCESS_SETTINGS which updates the display and the data model.
		getUserDisplayProcess().setParentDataBlock(dataBlock);
		updateDisplaySource();
	}

	/**
	 * @return the data block shown on the display, or null.
	 */
	@SuppressWarnings("rawtypes")
	public PamDataBlock getSourceDataBlock() {
		return getUserDisplayProcess().getParentDataBlock();
	}

	/**
	 * Pick a data source if there is not one, using the one in the display settings,
	 * otherwise the first clip generator, otherwise the first data which holds raw sound.
	 */
	@SuppressWarnings("rawtypes")
	private void chooseSource() {
		PamDataBlock current = getSourceDataBlock();
		ArrayList<PamDataBlock<?>> blocks = getRawClipBlocks();
		if (current != null && blocks.contains(current)) {
			return;
		}
		PamDataBlock source = null;
		String name = getDisplay().getParams().dataSourceName;
		if (name != null) {
			for (PamDataBlock<?> block : blocks) {
				if (name.equals(block.getLongDataName())) {
					source = block;
					break;
				}
			}
		}
		if (source == null && name == null) {
			for (PamDataBlock<?> block : blocks) {
				if (block instanceof ClipDataBlock) {
					source = block;
					break;
				}
			}
			if (source == null && blocks.size() > 0) {
				source = blocks.get(0);
			}
		}
		if (source != current) {
			getUserDisplayProcess().setParentDataBlock(source);
		}
	}

	/**
	 * Tell the display which data block to show.
	 */
	private void updateDisplaySource() {
		Platform.runLater(() -> {
			getDisplay().setSourceDataBlock(getSourceDataBlock());
		});
	}

	@Override
	public void notifyModelChanged(int type) {
		super.notifyModelChanged(type);
		switch (type) {
		case PamControllerInterface.INITIALIZATION_COMPLETE:
			setCompatibleDataUnits();
			Platform.runLater(() -> {
				chooseSource();
				getDisplay().setSourceDataBlock(getSourceDataBlock());
			});
			break;
		case PamControllerInterface.ADD_CONTROLLEDUNIT:
		case PamControllerInterface.REMOVE_CONTROLLEDUNIT:
		case PamControllerInterface.ADD_DATABLOCK:
		case PamControllerInterface.REMOVE_DATABLOCK:
			setCompatibleDataUnits();
			if (PamController.getInstance().isInitializationComplete()) {
				Platform.runLater(() -> {
					chooseSource();
					getDisplay().setSourceDataBlock(getSourceDataBlock());
				});
			}
			break;
		case PamControllerInterface.CHANGED_PROCESS_SETTINGS:
			// the data source may have been changed in the data model.
			updateDisplaySource();
			break;
		}
		if (clipDisplay != null) {
			clipDisplay.notifyModelChanged(type);
		}
	}

	/**
	 * @return the clip display, made if needed.
	 */
	public ClipDisplayFX getDisplay() {
		getDisplays();
		return clipDisplay;
	}

	@Override
	public ArrayList<UserDisplayNodeFX> getDisplays() {
		if (displays == null) {
			clipDisplay = new ClipDisplayFX(this);
			displays = new ArrayList<UserDisplayNodeFX>();
			displays.add(clipDisplay);
		}
		return displays;
	}

}
