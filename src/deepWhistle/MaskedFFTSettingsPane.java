package deepWhistle;

import java.io.File;
import java.net.URI;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.utilityPanes.FreqResolutionPane;
import pamViewFX.fxNodes.utilityPanes.SourcePaneFX;
import pamViewFX.fxSettingsPanes.DynamicSettingsPane;

import PamController.PamController;
import PamguardMVC.PamDataBlock;
import fftManager.FFTDataBlock;
import fftManager.FFTDataUnit;
import rawDeepLearningClassifier.DLDownloadManager;
import rawDeepLearningClassifier.DLStatus;

/**
 * Simple JavaFX settings pane for DeepWhistleParameters.
 * <p>
 * The pane allows the user to select which {@link PamFFTMask} to use. Each mask
 * has its own settings pane (see {@link FFTMaskSettingsPane}) which is shown
 * when the mask is selected. When a new mask is selected its model is
 * downloaded - a progress indicator is shown and the controls are disabled
 * until the download completes.
 */
public class MaskedFFTSettingsPane extends DynamicSettingsPane<MaskedFFTParamters> {

    private MaskedFFTParamters params;
    private PamBorderPane main;
	private SourcePaneFX sourcePane;
	private FreqResolutionPane resolutionPane;

	/**
	 * Selection control for the type of mask to use.
	 */
	private ChoiceBox<FFTMaskType> maskChoiceBox;

	/**
	 * Holds the settings pane for the currently selected mask.
	 */
	private PamBorderPane maskPaneHolder;

	/**
	 * The settings pane for the currently selected mask.
	 */
	private FFTMaskSettingsPane currentMaskPane;

	/**
	 * Progress indicator shown whilst a model is downloading.
	 */
	private ProgressIndicator modelLoadIndicator;

	/**
	 * Label showing the model download status / model file name.
	 */
	private Label modelPathLabel;

	/**
	 * Manages downloading (and unzipping) of models.
	 */
	private DLDownloadManager downloadManager = new DLDownloadManager();

	/**
	 * The local path to the currently downloaded model.
	 */
	private String currentModelPath;

	/**
	 * Flag set whilst parameters are being set programmatically so that the mask
	 * selection listener does not trigger a download.
	 */
	private boolean isSettingParams = false;

	/**
	 * Flag set whilst a model download is running so overlapping downloads are
	 * not started.
	 */
	private boolean downloadInProgress = false;

    public MaskedFFTSettingsPane(Object owner) {
        super(owner);
        createContent();
    }

    private void createContent() {

    	PamVBox vBox=new PamVBox();
		vBox.setSpacing(5);

		sourcePane = new SourcePaneFX("Raw data source for FFT", FFTDataUnit.class, true, true);
		PamGuiManagerFX.titleFont2style(sourcePane.getTitleLabel());

		resolutionPane=new FreqResolutionPane();

		sourcePane.addSelectionListener((obsVal, newVal, oldVal) -> {
			//update the frequency resolution pane.
			resolutionPane.setParams((FFTDataBlock) sourcePane.getSource());
		});

		vBox.getChildren().add(sourcePane);

		vBox.getChildren().add(resolutionPane);

		vBox.getChildren().add(createMaskPane());

		main = new PamBorderPane();
		main.setPadding(new Insets(5,5,5,5));
		main.setCenter(vBox);


    }

    /**
     * Create the pane which allows the user to select a mask, shows the model
     * download status and holds the mask specific settings.
     *
     * @return the mask selection pane.
     */
    private Node createMaskPane() {

    	PamVBox maskBox = new PamVBox();
    	maskBox.setSpacing(5);

    	Label titleLabel = new Label("FFT Mask");
    	PamGuiManagerFX.titleFont2style(titleLabel);

    	//the mask selection control
    	maskChoiceBox = new ChoiceBox<FFTMaskType>();
    	maskChoiceBox.getItems().addAll(FFTMaskType.values());
    	maskChoiceBox.setTooltip(new Tooltip("Select the type of FFT mask to use to remove noise from the spectrogram"));

    	maskChoiceBox.getSelectionModel().selectedItemProperty().addListener((obsVal, oldVal, newVal) -> {
    		if (newVal == null) return;
    		setMaskPane(newVal);
    		if (!isSettingParams) {
    			//the user changed the mask - download the model for it.
    			downloadModel(newVal);
    		}
    	});

    	//the model download status row
    	modelLoadIndicator = new ProgressIndicator(-1);
    	modelLoadIndicator.setVisible(false);
    	modelLoadIndicator.setPrefSize(20, 20);

    	modelPathLabel = new Label("No model downloaded");
    	modelPathLabel.setMaxWidth(Double.MAX_VALUE);

    	PamHBox selectBox = new PamHBox();
    	selectBox.setSpacing(5);
    	selectBox.setAlignment(Pos.CENTER_LEFT);
    	selectBox.getChildren().addAll(new Label("Mask"), maskChoiceBox, modelLoadIndicator, modelPathLabel);
    	PamHBox.setHgrow(modelPathLabel, Priority.ALWAYS);

    	//holder for the mask specific settings pane
    	maskPaneHolder = new PamBorderPane();

    	maskBox.getChildren().addAll(titleLabel, selectBox, maskPaneHolder);

    	return maskBox;
    }

    /**
     * Set the mask specific settings pane shown in the holder.
     *
     * @param maskType - the selected mask type.
     */
    private void setMaskPane(FFTMaskType maskType) {
    	currentMaskPane = maskType.createSettingsPane();
    	if (currentMaskPane != null) {
    		maskPaneHolder.setCenter(currentMaskPane.getContentNode());
    		if (params != null) {
    			currentMaskPane.setParams(params);
    		}
    	}
    	else {
    		maskPaneHolder.setCenter(null);
    	}
    }

    /**
     * Enable / disable the controls. Used to disable the controls whilst a model
     * is downloading.
     *
     * @param disable - true to disable the controls.
     */
    private void setControlsDisabled(boolean disable) {
    	sourcePane.setDisable(disable);
    	resolutionPane.setDisable(disable);
    	maskChoiceBox.setDisable(disable);
    	maskPaneHolder.setDisable(disable);
    }

    /**
     * Download the model for the given mask on a separate thread, showing a
     * progress indicator and disabling the controls whilst the download is in
     * progress. The controls are re-enabled once the download completes.
     *
     * @param maskType - the mask whose model should be downloaded.
     * @return the download task or null if the mask has no model to download.
     */
    public Task<DLStatus> downloadModel(FFTMaskType maskType) {

    	String url = maskType.getModelURL();
    	if (url == null || url.isBlank()) {
    		return null;
    	}

    	//don't start a second download if one is already running.
    	if (downloadInProgress) {
    		return null;
    	}

    	URI uri;
    	try {
    		uri = new URI(url);
    	} catch (Exception e) {
    		System.err.println("MaskedFFTSettingsPane: invalid model URL: " + url);
    		e.printStackTrace();
    		modelPathLabel.setText("Invalid model URL");
    		return null;
    	}

    	downloadInProgress = true;
    	modelLoadIndicator.setVisible(true);
    	setControlsDisabled(true);

    	DownloadTask task = new DownloadTask(uri, maskType.getModelFileNames());

    	modelLoadIndicator.progressProperty().bind(task.progressProperty());
    	modelPathLabel.textProperty().bind(task.messageProperty());

    	Thread th = new Thread(task);
    	th.setDaemon(true);
    	th.start();

    	return task;
    }

    @Override
    public MaskedFFTParamters getParams(MaskedFFTParamters currParams) {
        if (currParams == null) currParams = new MaskedFFTParamters();
			//			fftParameters.rawDataSource = sourceList.getSelectedItem().toString();
        currParams.dataSourceIndex = sourcePane.getSourceIndex();
        currParams.dataSourceName = sourcePane.getSourceLongName();

        currParams.channelMap = sourcePane.getChannelList();

        currParams.maskType = maskChoiceBox.getSelectionModel().getSelectedItem();
        currParams.modelPath = currentModelPath;

        //get the mask specific parameters
        if (currentMaskPane != null) {
        	currentMaskPane.getParams(currParams);
        }

        return currParams;
    }

    @Override
    public void setParams(MaskedFFTParamters input) {
        this.params = input;
        if (input == null) input = new MaskedFFTParamters();

		// and fill in the data source list (may have changed - or might in later versions)
		PamDataBlock  datablock = PamController.getInstance().getFFTDataBlock(params.dataSourceName);
//		System.out.println("Data block to set for FFT source: "+datablock.getDataName() + " FFT PARAMS: "+fftParameters.dataSourceName);
		//fft settings
		sourcePane.setSource(datablock);
		sourcePane.setChannelList(params.channelMap); //set selected channels

		//the currently downloaded model path
		currentModelPath = params.modelPath;

		//set the mask selection without triggering a download
		isSettingParams = true;
		FFTMaskType maskType = params.maskType == null ? FFTMaskType.DEEP_WHISTLE : params.maskType;
		maskChoiceBox.getSelectionModel().select(maskType);
		setMaskPane(maskType);
		isSettingParams = false;

		//update the model status label
		updateModelPathLabel();

		//if the selected mask needs a model but one has not been downloaded (or the
		//downloaded file is missing) then start the download now. The selection listener
		//only fires when the user *changes* the mask, so without this the model for the
		//default mask would never download.
		if (maskType.getModelURL() != null && !isModelAvailable()) {
			downloadModel(maskType);
		}
    }

    /**
     * Check whether the model for the current mask has been downloaded and still
     * exists on disk.
     *
     * @return true if a valid local model file is available.
     */
    private boolean isModelAvailable() {
    	return currentModelPath != null && new File(currentModelPath).exists();
    }

    /**
     * Update the model path label to show the currently downloaded model.
     */
    private void updateModelPathLabel() {
    	modelPathLabel.setGraphic(null);
    	if (currentModelPath == null) {
    		modelPathLabel.setGraphic(PamGlyphDude.createPamIcon("mdi2c-close-circle-outline", Color.ORANGE, 10));
    		modelPathLabel.setText("No model downloaded - select a mask");
    		modelPathLabel.setTooltip(new Tooltip("Select a mask to download its model"));
    	}
    	else {
    		modelPathLabel.setText(new File(currentModelPath).getName());
    		modelPathLabel.setTooltip(new Tooltip(currentModelPath));
    	}
    }

    @Override
    public String getName() {
        return "Deep Whistle Mask Settings";
    }

    @Override
    public Node getContentNode() {
        return main;
    }

    @Override
    public void paneInitialized() {
        // nothing special
    }

    /**
     * Task which downloads (and if necessary unzips) a model on a separate
     * thread so that the GUI can show a progress indicator.
     */
    class DownloadTask extends Task<DLStatus> {

    	private URI uri;
    	private URI modelPath;
    	private String[] modelFileNames;

    	public DownloadTask(URI uri, String[] modelFileNames) {
    		this.uri = uri;
    		this.modelFileNames = modelFileNames;

    		downloadManager.clearDownloadListeners();
    		downloadManager.addDownloadListener((status, bytesDownLoaded) -> updateMessage(status, bytesDownLoaded));
    	}

    	private void updateMessage(DLStatus status, long bytesDownLoaded) {
    		this.updateProgress(-1, 1); //indeterminate
    		switch (status) {
    		case CONNECTION_TO_URL:
    			this.updateMessage("Checking URL");
    			break;
    		case DOWNLOADING:
    			this.updateMessage(String.format("Downloading %.2f MB", ((double) bytesDownLoaded) / 1024. / 1024.));
    			break;
    		case DOWNLOAD_FINISHED:
    			this.updateMessage("Download complete");
    			break;
    		case DOWNLOAD_STARTING:
    			this.updateMessage("Download starting");
    			break;
    		default:
    			this.updateMessage(status.getDescription());
    			break;
    		}
    	}

    	@Override
    	protected DLStatus call() throws Exception {
    		try {
    			this.updateMessage("Connecting to model...");

    			//either downloads the model or instantly returns the path if it's a local file.
    			//If the model is inside an archive, search for the mask's model file name(s).
    			if (modelFileNames != null) {
    				modelPath = downloadManager.downloadModel(uri, modelFileNames);
    			}
    			else {
    				modelPath = downloadManager.downloadModel(uri);
    			}

    			if (modelPath == null) {
    				return DLStatus.MODEL_DOWNLOAD_FAILED;
    			}

    			this.updateMessage("Download complete");
    			Thread.sleep(500); //just so the user sees something happened if the download is rapid.

    			return DLStatus.DOWNLOAD_FINISHED;

    		} catch (Exception e) {
    			System.out.println("-----UNABLE TO DOWNLOAD MODEL-----");
    			e.printStackTrace();
    			return DLStatus.MODEL_DOWNLOAD_FAILED;
    		}
    	}

    	private void finishedLoading() {

    		downloadInProgress = false;

    		//important to stop a bound property exception.
    		modelPathLabel.textProperty().unbind();
    		modelLoadIndicator.progressProperty().unbind();

    		modelLoadIndicator.setVisible(false);
    		setControlsDisabled(false);

    		DLStatus result = this.getValue();

    		if (result != null && !result.isError() && modelPath != null) {
    			currentModelPath = new File(modelPath).getAbsolutePath();
    			if (params != null) {
    				params.modelPath = currentModelPath;
    			}
    		}

    		updateModelPathLabel();

    		if (result != null && result.isError()) {
    			modelPathLabel.setGraphic(PamGlyphDude.createPamIcon("mdi2c-close-circle-outline", Color.RED, 10));
    			modelPathLabel.setText(result.getName());
    			modelPathLabel.setTooltip(new Tooltip(result.getDescription()));
    		}

    		notifySettingsListeners();
    	}

    	@Override
    	protected void succeeded() {
    		super.succeeded();
    		finishedLoading();
    	}

    	@Override
    	protected void cancelled() {
    		super.cancelled();
    		finishedLoading();
    	}

    	@Override
    	protected void failed() {
    		super.failed();
    		finishedLoading();
    	}
    }

}
