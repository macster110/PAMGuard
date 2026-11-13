package Map.layoutFX;

import java.util.ArrayList;

import Map.MapController;
import Map.MapParameters;
import Map.SimpleMap;
import PamController.SettingsPane;
import pamViewFX.PamControlledGUIFX;
import userDisplayFX.UserDisplayNodeFX;

/**
 * JavaFX GUI for the map module. Provides the map display and the map
 * options settings pane.
 * 
 * @author Jamie Macaulay
 *
 */
public class MapGUIFX extends PamControlledGUIFX {

	private MapController mapController;

	private MapDisplayFX mapDisplay;

	private MapSettingsPaneFX settingsPane;

	private ArrayList<UserDisplayNodeFX> displays;

	public MapGUIFX(MapController mapController) {
		this.mapController = mapController;
	}

	private SimpleMap getSimpleMap() {
		return mapController.getMapTabPanelControl().getSimpleMap();
	}

	@Override
	public ArrayList<UserDisplayNodeFX> getDisplays() {
		if (displays == null) {
			mapDisplay = new MapDisplayFX(mapController);
			displays = new ArrayList<UserDisplayNodeFX>();
			displays.add(mapDisplay);
		}
		return displays;
	}

	@Override
	public SettingsPane<?> getSettingsPane() {
		if (settingsPane == null) {
			settingsPane = new MapSettingsPaneFX(getSimpleMap());
		}
		if (mapDisplay != null) {
			settingsPane.setMapCanvas(mapDisplay.getMapCanvas());
		}
		settingsPane.setParams(getSimpleMap().getMapParameters());
		return settingsPane;
	}

	@Override
	public void updateParams() {
		if (settingsPane == null) {
			return;
		}
		MapParameters newParams = settingsPane.getParams(getSimpleMap().getMapParameters());
		if (newParams == null) {
			return;
		}
		if (mapDisplay != null) {
			mapDisplay.setMapParameters(newParams);
		}
		else {
			getSimpleMap().setMapParameters(newParams);
		}
	}

	@Override
	public void notifyGUIChange(int changeType) {
		if (mapDisplay != null) {
			mapDisplay.notifyModelChanged(changeType);
		}
	}

}
