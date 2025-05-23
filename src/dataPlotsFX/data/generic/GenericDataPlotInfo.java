package dataPlotsFX.data.generic;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;

import PamDetection.AbstractLocalisation;
import PamDetection.LocContents;
import PamDetection.LocalisationInfo;
import PamUtils.PamUtils;
import PamView.GeneralProjector;
import PamView.HoverData;
import PamView.GeneralProjector.ParameterType;
import PamView.GeneralProjector.ParameterUnits;
import PamView.symbol.PamSymbolChooser;
import PamView.symbol.PamSymbolManager;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamDataUnit;
import dataPlotsFX.TDManagedSymbolChooserFX;
import dataPlotsFX.TDSymbolChooserFX;
import dataPlotsFX.data.TDDataInfoFX;
import dataPlotsFX.data.TDDataProviderFX;
import dataPlotsFX.data.TDScaleInfo;
import dataPlotsFX.layout.TDGraphFX;
import dataPlotsFX.layout.TDSettingsPane;
import dataPlotsFX.projector.TDProjectorFX;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import pamViewFX.fxNodes.PamSymbolFX;

/**
 * Generic data plot info which can work for a wide variety of data types. May still 
 * need some data unit specific sub classing, but will hopefully reduce the need for 
 * quite so much data specific development.  
 * 
 * @author Doug Gillespie
 *
 */
public class GenericDataPlotInfo extends TDDataInfoFX {
	
	public static final double DEFAULT_FILL_OPACITY = 0.3;

	/**
	 * Scale infos to show what axis clicks can be plotted on. 
	 */
	private GenericScaleInfo bearingScaleInfo;
	
	/**
	 * The amplitude axis
	 */
	private GenericScaleInfo ampScaleInfo;

	/**
	 * The slant angle axis 
	 */
	private GenericScaleInfo slantScaleInfo;

	private TDSymbolChooserFX managedSymbolChooser;
	
	private GenericSettingsPane genericSettingsPane;
	/**
	 * The frequency info 
	 */
	protected GenericScaleInfo frequencyInfo;

	/**
	 * Previous points for a line chart. 
	 */
	private Point2D[] lastUnits;
	

	public GenericDataPlotInfo(TDDataProviderFX tdDataProvider, TDGraphFX tdGraph, PamDataBlock pamDataBlock) {
		super(tdDataProvider, tdGraph, pamDataBlock);
		
		bearingScaleInfo = new GenericScaleInfo(180, 0, ParameterType.BEARING, ParameterUnits.DEGREES);
		bearingScaleInfo.setReverseAxis(true); //set the axis to be reverse so 0 is at top of graph
		ampScaleInfo = new GenericScaleInfo(100, 200, ParameterType.AMPLITUDE, ParameterUnits.DB);
		slantScaleInfo = new GenericScaleInfo(0, 180, ParameterType.SLANTBEARING, ParameterUnits.DEGREES);
		frequencyInfo = new GenericScaleInfo(0, 1, ParameterType.FREQUENCY, ParameterUnits.HZ);
		Arrays.fill(frequencyInfo.getPlotChannels(),1); //TODO-manage plot pane channels somehow. 
		frequencyInfo.setMaxVal(pamDataBlock.getSampleRate()/2);
		genericSettingsPane = new GenericSettingsPane(this);

		addScaleInfo(bearingScaleInfo);
		addScaleInfo(slantScaleInfo);
		addScaleInfo(ampScaleInfo);
		addScaleInfo(frequencyInfo);

	}

	/* (non-Javadoc)
	 * @see dataPlotsFX.data.TDDataInfoFX#drawDataUnit(int, PamguardMVC.PamDataUnit, javafx.scene.canvas.GraphicsContext, long, dataPlotsFX.projector.TDProjectorFX, int)
	 */
	@Override
	public Polygon drawDataUnit(int plotNumber, PamDataUnit pamDataUnit, GraphicsContext g, double scrollStart,
			TDProjectorFX tdProjector, int type) {

		if (getCurrentScaleInfo().getDataType() == ParameterType.FREQUENCY) { // frequency data !
			return drawFrequencyData(plotNumber, pamDataUnit, g, scrollStart, tdProjector, type);
		}
		else {
			return super.drawDataUnit(plotNumber, pamDataUnit, g, scrollStart, tdProjector, type);
		}
	}
	
	

	/**
	 * Draw a data unit as a box. 
	 * @param plotNumber - the pot number 
	 * @param pamDataUnit - the data unit that will be plotted
	 * @param f - the y axis max and min values
	 * @param g - the graphics context. 
	 * @param scrollStart - the scroll start in millis.
	 * @param tdProjector - the graph projector. 
	 * @param type - the plot type. 
	 * @return a polygon of the data unit. 
	 */
	public Polygon drawBoxData(int plotNumber, PamDataUnit pamDataUnit, double[] f, GraphicsContext g, double scrollStart,
			TDProjectorFX tdProjector, int type) {
				
		g.setLineDashes(null);
		g.setLineWidth(2);
		TDSymbolChooserFX symbolChooser = getSymbolChooser();
		PamSymbolFX symbol = null;
		if (symbolChooser != null) {
			symbol = symbolChooser.getPamSymbol(pamDataUnit, type);
		}
		
		if (f == null) {
			return null;
		}
		if (f.length == 1) {
			System.out.println("GenericDataPlotInfo: Single frequency measure in data unit " + pamDataUnit.toString());
		}
		
		//System.out.println("Frequency: " + f[0] + " " + f[1] + " " + pamDataUnit);
		
		// draw a frequency box. 
		double y0 = tdProjector.getYPix(f[0]);
		double y1 = tdProjector.getYPix(f[1]);
		double x0 = tdProjector.getTimePix(pamDataUnit.getTimeMilliseconds()-scrollStart);
		double x1 = tdProjector.getTimePix(pamDataUnit.getEndTimeInMilliseconds()-scrollStart);
		if (symbol != null) {
			g.setStroke(symbol.getLineColor());
			g.setLineWidth(symbol.getLineThickness());
			Color fillCol = symbol.getFillColor(); 
			double alpha = fillCol.getOpacity();
			g.setFill(Color.color(fillCol.getRed(), fillCol.getGreen(), fillCol.getBlue(), alpha)); //add alpha
		}
		

		double y = Math.min(y0,  y1);
		double h = Math.abs(y1-y0);
		
		if (x1>x0 && (!this.getTDGraph().isWrap() || (x1<tdProjector.getWidth() && x0>=0))) {
			//in wrap mode we can get some very werid plotting if x co-ordinates are less than 0 or greate than the grpah width. 
			g.strokeRect(x0, y, x1-x0, h);
			g.fillRect(x0, y, x1-x0, h);
		}
		
		//create the polygon. 
		
		/**
		 * Bit of a hack but make the selectable path a rectangle in the middle of the FFT so it can be 
		 * slected without having to draw a box right from the top of the frequency axis right to the bottom. 
		 */
		double pix = 0.25*Math.abs(y-y1); 
		
		Path2D path2D= new Path2D.Double(0,1); 
		
//		if (Math.abs(x1-x0)>50) {
//	
//			System.out.println("Generic Data Plot: " + "x0: " + x0 + " x1: " + x1 + " y1: " + y1 + " y: " + y0); 
//		}
		
		if (x1>x0) {
			path2D.moveTo(x0, y1-pix);
			path2D.lineTo(x0, y0+pix);
			path2D.lineTo(x1, y0+pix);
			path2D.lineTo(x1, y1-pix);
		}
		
		tdProjector.addHoverData(new HoverData(path2D, pamDataUnit, 0, plotNumber));
		
		
		return null;
	}

	/**
	 * Base class draws a simple frequency box. Easily overridden to draw something else, e.g. a contour. 
	 * @param plotNumber
	 * @param pamDataUnit
	 * @param g
	 * @param scrollStart
	 * @param tdProjector
	 * @param type
	 * @return
	 */
	public Polygon drawFrequencyData(int plotNumber, PamDataUnit pamDataUnit, GraphicsContext g, double scrollStart,
			TDProjectorFX tdProjector, int type) {
		double[] f = pamDataUnit.getFrequency();		
		return drawBoxData( plotNumber,  pamDataUnit,f,  g,  scrollStart, tdProjector,  type);
	}
	

	@Override
	public  TDSettingsPane getGraphSettingsPane() {
		return genericSettingsPane;
	}

	@Override
	public Double getDataValue(PamDataUnit pamDataUnit) {
		
		switch (getCurrentScaleInfo().getDataType()) {
		case BEARING:
//			System.out.printf("Bearing %3.1f deg\n", getBearingValue(pamDataUnit));
			Double val = getBearingValue(pamDataUnit);
			if (val == null) {
				return null;
			}
			else {
				return PamUtils.constrainedAngle(getBearingValue(pamDataUnit), 180.);
			}
		case SLANTANGLE:
			return getSlantValue(pamDataUnit);
		case AMPLITUDE:
			return getAmplitudeValue(pamDataUnit);
		case FREQUENCY:
			return getFrequencyValue(pamDataUnit);
		}

//		switch (getScaleInfoIndex()) {
//		case 0:
////			System.out.printf("Bearing %3.1f deg\n", getBearingValue(pamDataUnit));
//			Double val = getBearingValue(pamDataUnit);
//			if (val == null) {
//				return null;
//			}
//			else {
//				return PamUtils.constrainedAngle(getBearingValue(pamDataUnit), 180.);
//			}
//		case 1:
//			return getSlantValue(pamDataUnit);
//		case 2:
//			return getAmplitudeValue(pamDataUnit);
//		case 3:
//			return getFrequencyValue(pamDataUnit);
//		}
		return null;
	}

	public Double getBearingValue(PamDataUnit pamDataUnit) {
		AbstractLocalisation locData = pamDataUnit.getLocalisation();
		if (locData == null) {
			return null;
		}
		double[] angles = locData.getAngles();
		if (angles != null) {
			return 90-Math.toDegrees(angles[0]);
		}
		
		return null;
	}

	public Double getSlantValue(PamDataUnit pamDataUnit) {
		AbstractLocalisation locData = pamDataUnit.getLocalisation();
		if (locData == null) {
			return null;
		}
		double[] angles = locData.getAngles();
		if (angles != null && angles.length >= 2) {
			return angles[1];
		}
		
		return null;
	}

	public Double getAmplitudeValue(PamDataUnit pamDataUnit) {
		//System.out.println("max val: " + ampScaleInfo.getMaxVal()); 
		return pamDataUnit.getAmplitudeDB();
	}

	private Double getFrequencyValue(PamDataUnit pamDataUnit) {
		double[] f = pamDataUnit.getFrequency();
		if (f == null) {
			return null;
		}
		else if (f.length == 1) {
			return f[0];
		}
		else {
			return (f[0]+f[1]) / 2.;
		}
	}

	@Override
	public TDSymbolChooserFX getSymbolChooser() {
//		if (managedSymbolChooser == null) {
			managedSymbolChooser = createSymbolChooser();
//		}
		return managedSymbolChooser;
	}

	public TDSymbolChooserFX createSymbolChooser() {
		PamSymbolManager symbolManager = getDataBlock().getPamSymbolManager();
		if (symbolManager == null) {
			return null;
		}
		GeneralProjector p = this.getTDGraph().getGraphProjector();
		PamSymbolChooser sc = symbolManager.getSymbolChooser(getTDGraph().getUniqueName(), p);
		return new TDManagedSymbolChooserFX(this, sc, TDSymbolChooserFX.DRAW_SYMBOLS);
	}

	/* (non-Javadoc)
	 * @see dataPlotsFX.data.TDDataInfoFX#getScaleInfos()
	 */
	@Override
	public ArrayList<TDScaleInfo> getScaleInfos() {
		updateAvailability();
		setNPlotPanes(this.frequencyInfo,this.getDataBlock(), false); 
		return super.getScaleInfos();
	}

	/**
	 * Go through all the different scale types and check which ones are 
	 * available. (only need to do this for ones associated with localisation data
	 * since that's the think likely to change dynamically ?). May have to also 
	 * do some which may be associated with other annotations ? 
	 */
	protected void updateAvailability() {
		LocalisationInfo locInfo = getDataBlock().getLocalisationContents();
		bearingScaleInfo.setAvailable(locInfo.hasLocContent(LocContents.HAS_BEARING));
		slantScaleInfo.setAvailable(locInfo.hasLocContent(LocContents.HAS_BEARING));
	}
	
	/**
	 * Get the amplitude scale info. 
	 * @return the amplitude scale info. 
	 */
	public GenericScaleInfo getAmplitudeScaleInfo() {
		return ampScaleInfo; 
	}
	
	/**
	 * Get the frequency scale info. 
	 * @return the frequency scale info. 
	 */
	public GenericScaleInfo getFrequencyScaleInfo() {
		return frequencyInfo;
	}
	
	
	/**
	 * Get the frequency scale info. 
	 * @return the frequency scale info. 
	 */
	public GenericScaleInfo getBearingScaleInfo() {
		return bearingScaleInfo;
	}
	

	
	/**
	 * Get the frequency scale info. 
	 * @return the frequency scale info. 
	 */
	public GenericScaleInfo getSlantScaleInfo() {
		return slantScaleInfo;
	}
	

	/**
	 * Called when the user selects a specific data line
	 * @param dataLine
	 */
	@Override
	public boolean setCurrentAxisName(ParameterType dataType, ParameterUnits dataUnits) {
		setDefaultOpacity(dataType);
		
		return super.setCurrentAxisName(dataType, dataUnits);
	}
	
	protected void setDefaultOpacity(ParameterType dataType) {
		if (dataType.equals(ParameterType.FREQUENCY)) {
			//we set the default opacity because often frequency is shown as a box on a spectrogram. 
			this.genericSettingsPane.setDefaultFillOpacity(DEFAULT_FILL_OPACITY);
		}
		else {
			this.genericSettingsPane.setDefaultFillOpacity(1.0);
		}
	}


}
