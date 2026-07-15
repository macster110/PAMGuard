package pamViewFX.fxNodes.pamScrollers.acousticScroller;
import PamUtils.PamUtils;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamDataUnit;
import dataPlotsFX.projector.TDProjectorFX;
import dataPlotsFX.scrollingPlot2D.Scrolling2DPlotInfo;
import dataPlotsFX.scrollingPlot2D.Plot2DColours;
import dataPlotsFX.scrollingPlot2D.StandardPlot2DColours;
import dataPlotsFX.scrollingPlot2D.Scrolling2DPlotDataFX2;
import fftManager.FFTDataBlock;
import fftManager.FFTDataUnit;
import javafx.scene.canvas.Canvas;
import javafx.scene.shape.Rectangle;
import pamViewFX.fxNodes.pamAxis.PamAxisFX;

public class FFTScrollBarGraphics implements AcousticScrollerGraphics {
	
	/**
	 * The spectrogram plot. 
	 */
	public SpecDatagramPlot spectrogramPlot;
	
	/**
	 * Reference to the FFT data block. 
	 */
	private FFTDataBlock fftDataBlock; 
	
	/**
	 * The spectrogram channel to plot. 
	 */
	private int channel=0;

	/**
	 * The acoustic scroller. 
	 */
	private AcousticScrollerFX acousticScroller;

	private Canvas canvas;
	
//	public GeneralSpectrogramColours spectrogramColours;

//	private PamAxisFX freqAxis; 
	
	/**
	 * 
	 */
	AcousticScrollerProjector projector;

	/**
	 * Colours for this datagram 
	 */
	private StandardPlot2DColours datagramColours;

	private PamAxisFX freqAxis; 
	
	
		
	public FFTScrollBarGraphics(AcousticScrollerFX acousticScroller, FFTDataBlock fftDataBlock){
		this.acousticScroller=acousticScroller; 
		this.fftDataBlock=fftDataBlock; 
//		spectrogramColours=new GeneralSpectrogramColours();
				
		projector=new AcousticScrollerProjector(); 
		
		this.datagramColours= new StandardPlot2DColours(); 
		
		spectrogramPlot=new SpecDatagramPlot(projector, fftDataBlock, datagramColours, 0, this.acousticScroller.isViewer); 
//		channel=PamUtils.getLowestChannel(fftDataBlock.getChannelMap());
		channel=PamUtils.getLowestChannel(fftDataBlock.getSequenceMap());

		//create axis and bind frequencies. 
		createFreqAxis();
				 
	}
	
	public class AcousticScrollerProjector extends TDProjectorFX {

		public AcousticScrollerProjector() {
			super();
			Rectangle windowRect=new Rectangle(); 
			windowRect.widthProperty().bind(acousticScroller.getScrollBarPane().getDrawCanvas().widthProperty());
			windowRect.heightProperty().bind(acousticScroller.getScrollBarPane().getDrawCanvas().heightProperty());
			this.setWindowRect(windowRect); 
		}	

		@Override
		public PamAxisFX getYAxis(){
			return freqAxis; 
		}

		@Override
		public PamAxisFX getTimeAxis(){
			return acousticScroller.getTimeAxis(); 
		}
		
		@Override
		public double getVisibleTime(){
			return acousticScroller.getRangeMillis();
		}
		
		public double getGraphTimePixels(){
			return acousticScroller.getScrollBarPane().getWidth();
		}

	}
	
	private void createFreqAxis(){
		freqAxis = new PamAxisFX(0, 1, 0, 1, 0, 10, PamAxisFX.ABOVE_LEFT, "Graph Units", PamAxisFX.LABEL_NEAR_CENTRE, "%4d");
		freqAxis.y1Property().setValue(0);
		freqAxis.y2Property().bind(acousticScroller.getScrollBarPane().heightProperty().divide(2));
		freqAxis.x1Property().bind(acousticScroller.getScrollBarPane().widthProperty());
		freqAxis.x2Property().bind(acousticScroller.getScrollBarPane().widthProperty());
	}
	
	/**
	 * Update the frequency axis based on fft datablock sample rate. 
	 */
	private void updateFreqLimits(){
		freqAxis.minValProperty().setValue(0);
		freqAxis.maxValProperty().setValue(fftDataBlock.getSampleRate()/2);
//		DoubleProperty[] axisVals={freqAxis.minValProperty() , freqAxis.maxValProperty()};
//		spectrogramPlot.setFreqLimits(axisVals);
	}


	@Override
	public PamDataBlock getDataBlock() {
		return fftDataBlock;
	}

	PamDataUnit lastData;

	private Rectangle windowRect;

	/**
	 * The data range (millis) whose preview has been FULLY built (an offline load
	 * completed naturally for it). Used by {@link #needsReload()} so a mere
	 * scroll-position change within an already-loaded range does not trigger a rebuild.
	 */
	private volatile long fullyLoadedMin = Long.MIN_VALUE;
	private volatile long fullyLoadedMax = Long.MIN_VALUE;

	/**
	 * The range currently being loaded. Used to tell a fresh range (clear + start over)
	 * from a continuation of the same range (resume).
	 */
	private volatile long currentRangeMin = Long.MIN_VALUE;
	private volatile long currentRangeMax = Long.MIN_VALUE;

	/**
	 * Time of the furthest (latest) accepted unit for the current range - i.e. how far
	 * the preview has been built. This is the point an interrupted load resumes from.
	 */
	private volatile long maxLoadedMillis = Long.MIN_VALUE;

	/**
	 * When a FRESH load starts we do not wipe the existing preview immediately (an
	 * interrupted or empty load would otherwise blank the display); instead we arm a
	 * clear and do the actual reset when the first new data unit of the load arrives.
	 */
	private volatile boolean pendingClear = false;

	@Override
	public void addNewData(PamDataUnit rawData) {
		try{
			if (rawData.getParentDataBlock()==fftDataBlock
//					&& PamUtils.hasChannel(rawData.getChannelBitmap(), channel)
					&& PamUtils.hasChannel(rawData.getSequenceBitmap(), channel)
					&& lastData!=rawData){
				//Lazy clear: only wipe the previous preview now that real data has arrived
				//to replace it. This keeps the last good image on screen through interrupted
				//or NO_DATA load attempts (the cause of the unreliable/blank preview).
				if (pendingClear) {
					spectrogramPlot.resetForLoad();
					updateFreqLimits();
					pendingClear = false;
				}
				spectrogramPlot.new2DData((FFTDataUnit) rawData);
				lastData=rawData;
				//track how far the preview has been built so an interrupted load can resume.
				long t = rawData.getTimeMilliseconds();
				if (t > maxLoadedMillis) maxLoadedMillis = t;
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	
	@Override
	public void repaint() {
		
		//get the canvas. 
		canvas=acousticScroller.getScrollBarPane().getDrawCanvas();

		//calculate the size of the scrollbar
		canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(),canvas.getHeight());
		
		windowRect=new Rectangle(0,0, 	canvas.getWidth(), 		canvas.getHeight());

//		System.out.println("Projector: top " + projector.getYPix(24000) +" bottom "+ projector.getYPix(0)+ "  height: " + projector.getHeight() 
//			+ " lims "+	freqAxis.minValProperty().getValue() + "  " + freqAxis.maxValProperty().getValue()); 
		
		//plot the spectrogram.
		spectrogramPlot.drawSpectrogram(canvas.getGraphicsContext2D(), windowRect, acousticScroller.getOrientation(),
				acousticScroller.getTimeAxis(), acousticScroller.getMinimumMillis(), false);
	}

	@Override
	public String getName() {
		return "Spectrogram";
	}

	@Override
	public void clearStore() {
		//Defer the actual image reset until the first new data unit arrives (see addNewData).
		//This avoids blanking the preview on interrupted/empty load attempts.
		pendingClear = true;
	}

	@Override
	public long prepareOfflineLoad(long rangeStart, long rangeEnd) {
		//Decide whether this is a continuation of the range we were already loading. If so,
		//resume from the furthest point already built (no clear, append to the existing
		//image). Otherwise start fresh: remember the new range and arm a deferred clear.
		boolean sameRange = (rangeStart == currentRangeMin && rangeEnd == currentRangeMax);
		if (sameRange && maxLoadedMillis > rangeStart) {
			return maxLoadedMillis;
		}
		currentRangeMin = rangeStart;
		currentRangeMax = rangeEnd;
		maxLoadedMillis = rangeStart;
		clearStore(); //arm deferred clear - existing image kept until first new data arrives
		return rangeStart;
	}

	@Override
	public boolean needsReload() {
		//Only rebuild when the fully-loaded data range has actually moved. A scroll-position
		//change within an already fully-loaded range leaves the preview correct, so skip the
		//expensive disk re-order that was causing the reload-on-every-move behaviour.
		boolean reload = acousticScroller.getMinimumMillis() != fullyLoadedMin
				|| acousticScroller.getMaximumMillis() != fullyLoadedMax;
		return reload;
	}

	@Override
	public void loadCompleted(boolean naturalCompletion) {
		//Only record the range as fully loaded when the load ran to its natural end AND
		//actually delivered data. A load that was given up while still incomplete is
		//partial and must resume; a zero-data pass (e.g. the wav-file map still being built
		//on first load) must not be marked, or the preview would get stuck blank/half-built
		//until the range changes.
		if (!naturalCompletion) {
			return;
		}
		if (maxLoadedMillis <= currentRangeMin) {
			return;
		}
		fullyLoadedMin = currentRangeMin;
		fullyLoadedMax = currentRangeMax;
	}

//	/**
//	 * Maximum time to wait for the FFT block to become idle before giving up on this
//	 * pass (the scroller will retry on the next change), in milliseconds.
//	 */
//	private static final long MAX_IDLE_WAIT_MILLIS = 30_000;
//
//	@Override
//	public boolean orderOfflineData() {
//		/*
//		 * Idle-gating. The scroll-bar preview must only load FFT data when nothing else
//		 * (e.g. the spectrogram display) is loading from this FFT block, so that it
//		 * never competes with - or interrupts - the display. This method runs on the
//		 * scroller's background load thread (AcousticScrollerFX.LoadTask), so we can
//		 * simply wait here until the block is idle and then let the normal full-range
//		 * load proceed. If the display starts loading again the scroller cancels this
//		 * task (and its data order), and the preview load is retried on the next change.
//		 *
//		 * The preview is rendered at scroll-bar resolution, so the spectrogram renderer
//		 * time-compresses the loaded FFTs down to the preview width (a few hundred
//		 * columns) regardless of how many FFTs are loaded.
//		 */
//		long waited = 0;
//		while (waited < MAX_IDLE_WAIT_MILLIS) {
//			if (Thread.currentThread().isInterrupted()) {
//				return false;
//			}
//			boolean busy = fftDataBlock.getOfflineDataLoading() != null
//					&& fftDataBlock.getOfflineDataLoading().getOrderStatus();
//			if (!busy) {
//				break;
//			}
//			try {
//				Thread.sleep(200);
//			}
//			catch (InterruptedException e) {
//				return false;
//			}
//			waited += 200;
//		}
//		return true;
//	}

	@Override
	public void notifyUpdate(int flag) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setColors(Plot2DColours specColors) {
		spectrogramPlot.setSpecColors(specColors);
		spectrogramPlot.reBuildImage();
		spectrogramPlot.rebuildFinished();
	}
	
	@Override
	public Plot2DColours getColors() {
		return spectrogramPlot.getSpecColors(); 
	}
	
	/**
	 * Spectrogram preview for the scroll bar. Uses the tiled renderer
	 * ({@link Scrolling2DPlotDataFX2}) so that the whole displayed range is retained
	 * as re-colourable tiles - this keeps the preview correct when amplitude limits
	 * change (the single-image {@code Scrolling2DPlotDataFX} only kept a small
	 * scrolling buffer, so its earliest data was lost on a re-colour).
	 */
	private class SpecDatagramPlot extends Scrolling2DPlotDataFX2 {

		public SpecDatagramPlot(Scrolling2DPlotInfo specPlotInfo, int iChannel) {
			super(specPlotInfo, iChannel);
		}

		public SpecDatagramPlot(AcousticScrollerProjector projector, FFTDataBlock fftDataBlock,
				StandardPlot2DColours dataGramColors, int i, boolean isViewer) {
			super(projector, fftDataBlock, dataGramColors, i, isViewer);
		}

		public void rebuildFinished(){
			acousticScroller.repaint(0);
		}
		
	}

	@Override
	public boolean orderOfflineData() {
		return true;
	}

	/**
	 * Approximate budget for the FFT units held resident while loading a single chunk,
	 * in bytes. The whole displayed range is built into a compact tiled preview, so the
	 * raw FFT units only ever need to live for the chunk currently being processed.
	 */
	private static final long CHUNK_MEMORY_BUDGET = 30L * 1024 * 1024;

	@Override
	public long getLoadChunkMillis() {
		//Bound the resident FFT-unit memory by loading the range in chunks sized to the
		//budget above. Estimate the FFT data rate from the block: an FFT data unit holds
		//roughly fftLength complex doubles, and there are sampleRate/hop units per second.
		float sr = fftDataBlock.getSampleRate();
		int hop = fftDataBlock.getHopSamples();
		int fftLen = fftDataBlock.getFftLength();
		if (sr <= 0 || hop <= 0 || fftLen <= 0) {
			return 30000; //sensible fallback before the block is configured.
		}
		double unitsPerSec = sr / hop;
		long bytesPerUnit = (long) fftLen * 8L; //complex spectrum stored as doubles.
		double bytesPerSec = Math.max(unitsPerSec * bytesPerUnit, 1);
		long millis = (long) (CHUNK_MEMORY_BUDGET / bytesPerSec * 1000.0);
		//clamp so chunks stay neither tiny (excessive order overhead) nor huge (memory).
		return Math.max(2000L, Math.min(millis, 120000L));
	}

	@Override
	public boolean isOfflineLoadBlocked() {
		//The preview shares the FFT block with the main spectrogram display. If the display
		//(or anything else) is already loading this block, wait for it rather than placing a
		//competing order that would just stall behind it.
		return fftDataBlock.getOfflineDataLoading() != null
				&& fftDataBlock.getOfflineDataLoading().getOrderStatus();
	}

//	/**
//	 * Loaded data range the preview has actually finished building for (set on load
//	 * completion, not when a load starts), so that an interrupted load is resumed.
//	 */
//	private volatile long loadedMinMillis = Long.MIN_VALUE;
//	private volatile long loadedMaxMillis = Long.MIN_VALUE;

//	@Override
//	public boolean needsReload() {
//		/*
//		 * The preview is a fixed image of the whole loaded data range, so it only needs
//		 * rebuilding when that range moves to a new section of the dataset - NOT every
//		 * time the scroller asks to load (e.g. when the user pauses/resumes dragging the
//		 * scroll bar within the same loaded range, via AcousticScrollerFX.pauseDataload).
//		 * Rebuilding the whole preview is expensive, so skip it once the current range
//		 * has been fully loaded.
//		 */
//		return acousticScroller.getMinimumMillis() != loadedMinMillis
//				|| acousticScroller.getMaximumMillis() != loadedMaxMillis;
//	}
//
//	@Override
//	public void loadCompleted() {
//		// Record the range that has now finished loading. Because a cancelled load goes
//		// through cancelled()/failed() (not succeeded()), this is only reached on a
//		// genuinely complete load - so an interrupted load leaves the range unmarked and
//		// is reloaded (resumed) next time.
//		loadedMinMillis = acousticScroller.getMinimumMillis();
//		loadedMaxMillis = acousticScroller.getMaximumMillis();
//	}

}
