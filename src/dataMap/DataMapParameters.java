package dataMap;

import java.io.Serializable;
import java.util.HashMap;

import PamView.ColourArray.ColourArrayType;

import PamModel.parametermanager.ManagedParameters;
import PamModel.parametermanager.PamParameterSet;
import PamModel.parametermanager.PamParameterSet.ParameterSetType;


public class DataMapParameters implements Cloneable, Serializable, ManagedParameters {

	protected static final long serialVersionUID = 1L;
	
	public int vScaleChoice = OfflineDataMap.SCALE_PERHOUR;
	
	public boolean vLogScale = true;
	
	/*
	 * Scale factor for horizontal axis. 
	 */
	public int hScaleChoice = 4;
	
	public static final double[] hScaleChoices = {0.1, 0.5, 1, 2, 5, 10, 20, 60, 180, 600};
	
	/**
	 * Colour map to use for each 3D datagram, keyed on the data block's long data name.
	 */
	public HashMap<String, ColourArrayType> datagramColourMaps = new HashMap<>();
	
	/**
	 * Which summary to show for raw sound files - see the MODE_ constants in
	 * dataMap.filemaps.SoundFileDatagramProvider.
	 */
	public int soundFileDatagramMode = 0;
	
	public double getPixeslPerHour() {
		return hScaleChoices[hScaleChoice];
	}

	@Override
	protected DataMapParameters clone() {
		try {
			return (DataMapParameters) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public PamParameterSet getParameterSet() {
		PamParameterSet ps = PamParameterSet.autoGenerate(this, ParameterSetType.DISPLAY);
		return ps;
	}

}
