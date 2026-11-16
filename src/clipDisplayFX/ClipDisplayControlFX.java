package clipDisplayFX;

import java.util.ArrayList;
import java.util.List;

import PamController.PamController;
import PamController.PamControllerInterface;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamDataUnit;
import clipgenerator.ClipDataBlock;
import javafx.application.Platform;
import userDisplayFX.UserDisplayControlFX;
import userDisplayFX.UserDisplayNodeFX;
import userDisplayFX.UserDisplayProcess;

/**
 * A JavaFX display module which shows sounds as a grid of spectrogram clips. The data
 * source can be any data block whose data units hold raw sound, i.e. implement
 * {@link PamguardMVC.RawDataHolder}, for example clips from the clip generator,
 * clicks from the click detector or detections from the deep learning classifier.
 * The display can show several data sources at once, like the time display. Sources
 * are chosen by connecting the module in the data model or in the display options.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipDisplayControlFX extends UserDisplayControlFX {

	private ArrayList<UserDisplayNodeFX> displays;

	private ClipDisplayFX clipDisplay;

	public ClipDisplayControlFX(String unitName) {
		super(unitName);
		setMultiParent(true);
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
	 * Set a single data source for the display.
	 * @param dataBlock - a data block whose data units hold raw sound, or null.
	 */
	@SuppressWarnings("rawtypes")
	public void setSourceDataBlock(PamDataBlock dataBlock) {
		ArrayList<PamDataBlock> list = new ArrayList<PamDataBlock>();
		if (dataBlock != null) {
			list.add(dataBlock);
		}
		setSourceDataBlocks(list);
	}

	/**
	 * Set the data shown on the display. The first block is the parent of the display
	 * process and the others are multiplex blocks, as in the time display, so the data
	 * model shows all of the connections.
	 * @param dataBlocks - data blocks whose data units hold raw sound.
	 */
	@SuppressWarnings("rawtypes")
	public void setSourceDataBlocks(List<? extends PamDataBlock> dataBlocks) {
		ArrayList<PamDataBlock> wanted = new ArrayList<PamDataBlock>();
		if (dataBlocks != null) {
			for (PamDataBlock block : dataBlocks) {
				if (block != null && RawClipHelper.isRawClipBlock(block) && !wanted.contains(block)) {
					wanted.add(block);
				}
			}
		}
		UserDisplayProcess process = getUserDisplayProcess();
		PamDataBlock first = wanted.isEmpty() ? null : wanted.get(0);
		List<PamDataBlock> rest = wanted.size() > 1 ? wanted.subList(1, wanted.size()) : new ArrayList<PamDataBlock>();
		// each change notifies a CHANGED_PROCESS_SETTINGS which updates the display and the data model.
		ArrayList<PamDataBlock> remove = new ArrayList<PamDataBlock>();
		for (PamDataBlock block : getMultiplexBlocks()) {
			if (!rest.contains(block)) {
				remove.add(block);
			}
		}
		if (!remove.isEmpty()) {
			process.removeAllMultiPlexDataBlocks(remove);
		}
		process.setParentDataBlock(first);
		ArrayList<PamDataBlock> current = getMultiplexBlocks();
		for (PamDataBlock block : rest) {
			if (!current.contains(block)) {
				process.addMultiPlexDataBlock(block);
			}
		}
		updateDisplaySource();
	}

	/**
	 * @return the multiplex data blocks of the display process.
	 */
	@SuppressWarnings("rawtypes")
	private ArrayList<PamDataBlock> getMultiplexBlocks() {
		ArrayList<PamDataBlock> blocks = getUserDisplayProcess().getParentDataBlocks();
		blocks.remove(0);
		return blocks;
	}

	/**
	 * @return the first data block shown on the display, or null.
	 */
	@SuppressWarnings("rawtypes")
	public PamDataBlock getSourceDataBlock() {
		ArrayList<PamDataBlock> blocks = getSourceDataBlocks();
		return blocks.isEmpty() ? null : blocks.get(0);
	}

	/**
	 * @return all of the data blocks shown on the display.
	 */
	@SuppressWarnings("rawtypes")
	public ArrayList<PamDataBlock> getSourceDataBlocks() {
		ArrayList<PamDataBlock> blocks = new ArrayList<PamDataBlock>();
		for (PamDataBlock block : getUserDisplayProcess().getParentDataBlocks()) {
			if (block != null && !blocks.contains(block)) {
				blocks.add(block);
			}
		}
		return blocks;
	}

	/**
	 * Pick the data sources. Sources which no longer exist are dropped. If there are
	 * none, use the ones in the display settings, otherwise the first clip generator,
	 * otherwise the first data which holds raw sound.
	 */
	@SuppressWarnings("rawtypes")
	private void chooseSource() {
		ArrayList<PamDataBlock> current = getSourceDataBlocks();
		ArrayList<PamDataBlock<?>> blocks = getRawClipBlocks();
		ArrayList<PamDataBlock> sources = new ArrayList<PamDataBlock>();
		for (PamDataBlock block : current) {
			if (blocks.contains(block)) {
				sources.add(block);
			}
		}
		ClipDisplayFXParams params = getDisplay().getParams();
		ArrayList<String> names = params.dataSourceNames;
		if (names == null && params.dataSourceName != null) {
			names = new ArrayList<String>();
			names.add(params.dataSourceName);
		}
		if (sources.isEmpty() && names != null) {
			for (String name : names) {
				for (PamDataBlock<?> block : blocks) {
					if (name.equals(block.getLongDataName()) && !sources.contains(block)) {
						sources.add(block);
					}
				}
			}
		}
		if (sources.isEmpty() && names == null) {
			// a new display, so pick something sensible.
			for (PamDataBlock<?> block : blocks) {
				if (block instanceof ClipDataBlock) {
					sources.add(block);
					break;
				}
			}
			if (sources.isEmpty() && blocks.size() > 0) {
				sources.add(blocks.get(0));
			}
		}
		if (!sources.equals(current)) {
			setSourceDataBlocks(sources);
		}
	}

	private volatile boolean updatePending;

	/**
	 * Tell the display which data blocks to show. Changing the sources sends several
	 * notifications, so the display is only updated once.
	 */
	private void updateDisplaySource() {
		if (updatePending) {
			return;
		}
		updatePending = true;
		Platform.runLater(() -> {
			updatePending = false;
			getDisplay().setSourceDataBlocks(getSourceDataBlocks());
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
				getDisplay().setSourceDataBlocks(getSourceDataBlocks());
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
					getDisplay().setSourceDataBlocks(getSourceDataBlocks());
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
