package PamController.pamWizard;

import java.util.Set;

import javax.swing.SwingUtilities;

import PamController.PamControlledUnit;
import PamController.PamController;
import PamController.PamControllerInterface;
import PamController.pamWizard.configurations.ConfigApplyContext;
import PamController.pamWizard.configurations.ConfigSpeciesGroup;
import PamController.soundMedium.GlobalMedium.SoundMedium;
import PamguardMVC.PamConstants;
import PamView.GroupedSourceParameters;
import PamView.dialog.GroupedSourcePanel;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamRawDataBlock;
import bearinglocaliser.BearingLocaliserControl;
import bearinglocaliser.BearingLocaliserParams;
import bearinglocaliser.algorithms.BearingAlgorithmProvider;
import userDisplay.UserDisplayComponent;
import userDisplay.UserDisplayControl;

/**
 * Auto-configuration (viewer mode) for multi-channel sound files: the spectrogram
 * and annotation setup of {@link SpectrogramViewerAutoConfig} plus a Bearing
 * Localiser which works out a bearing for every annotation the user draws, and
 * updates it whenever the annotation is moved or resized.
 * <p>
 * Only offered when every imported file has at least two channels, since a
 * bearing can't be calculated from one.
 *
 * @author Jamie Macaulay
 */
public class SpectrogramBearingViewerAutoConfig implements PamAutoConfig {

	public static final String BEARING_LOCALISER_CLASS = "bearinglocaliser.BearingLocaliserControl";

	/** Bearing algorithm used for each channel group (time delay based). */
	public static final String BEARING_ALGORITHM = "TDOA Localiser";

	/** Fraction of the User Display tab width given to the spectrogram, left of the bearing display. */
	private static final double SPECTROGRAM_WIDTH = 0.65;

	private SpectrogramOptions spectrogramOptions;

	@Override
	public boolean isValid(PamFileImport importHandler, int runMode) {
		if (runMode != PamController.RUN_PAMVIEW || !importHandler.hasType(PamImportFileType.SOUND)) {
			return false;
		}
		SoundFileSummary summary = importHandler.getSoundSummary();
		return summary != null && summary.isValid() && summary.getMinChannels() >= 2;
	}

	@Override
	public void createConfiguration(PamFileImport importHandler) {
		SpectrogramConfigBuilder builder = new SpectrogramConfigBuilder();
		builder.build(importHandler, true, spectrogramOptions);
		addBearingLocaliser(builder);
	}

	/**
	 * Add a Bearing Localiser using the annotations as its detections, and the raw
	 * audio the spectrogram is made from (all channels, as one group) to localise
	 * them - the decimator output if the user chose to decimate, otherwise the Sound
	 * Acquisition data. Using the same data as the spectrogram means the bearings
	 * are worked out over the band the user is looking at, and either can be read
	 * back from file for any annotation.
	 */
	private void addBearingLocaliser(SpectrogramConfigBuilder builder) {
		PamDataBlock annotationBlock = PamAutoConfigUtils.getOutputDataBlock(builder.getAnnotationControl());
		PamRawDataBlock rawData = builder.getSpectrogramRawData();
		if (annotationBlock == null || rawData == null) {
			System.err.println("SpectrogramBearingViewerAutoConfig: annotation or acquisition module missing - no bearing localiser added");
			return;
		}
		PamControlledUnit unit = PamAutoConfigUtils.addModule(BEARING_LOCALISER_CLASS, "Bearing Localiser");
		if (!(unit instanceof BearingLocaliserControl)) {
			System.err.println("SpectrogramBearingViewerAutoConfig: bearing localiser module could not be added");
			return;
		}
		BearingLocaliserControl bearingControl = (BearingLocaliserControl) unit;

		BearingLocaliserParams params = bearingControl.getBearingLocaliserParams();
		params.detectionSource = annotationBlock.getLongDataName();
		int channelMap = rawData.getChannelMap();
		// every channel in group 0.
		int[] channelGroups = new int[PamConstants.MAX_CHANNELS];
		params.setRawOrFFTSourceParameters(new GroupedSourceParameters(rawData.getLongDataName(), channelMap,
				channelGroups, GroupedSourcePanel.GROUP_ALL));
		params.clearAlgorithmNames();
		params.addAlgorithmName(BEARING_ALGORITHM);
		BearingAlgorithmProvider provider = bearingControl.findAlgorithmByName(BEARING_ALGORITHM);
		if (provider != null) {
			params.setAlgorithmParams(BEARING_ALGORITHM, 0, channelMap, provider.createNewParams(0, channelMap));
		}
		bearingControl.setBearingLocaliserParams(params);

		PamController.getInstance().notifyModelChanged(PamControllerInterface.CHANGED_PROCESS_SETTINGS);

		addBearingDisplay(builder, bearingControl);
	}

	/**
	 * Add the Bearing Localiser's display to the User Display tab holding the
	 * spectrogram, and tile the two side by side. The bearing display is built from
	 * the localiser's algorithm groups, so this must follow setting its parameters.
	 * Swing GUI only - in the FX GUI the time display is its own module and there's
	 * no User Display to put the bearing display in.
	 */
	private void addBearingDisplay(SpectrogramConfigBuilder builder, BearingLocaliserControl bearingControl) {
		UserDisplayControl userDisplay = builder.getUserDisplayControl();
		if (userDisplay == null) {
			System.out.println("SpectrogramBearingViewerAutoConfig: no User Display (FX GUI?) - bearing display not added");
			return;
		}
		UserDisplayComponent bearingDisplay = userDisplay.addUserDisplay(bearingControl.getBearingDisplayProvider());
		if (bearingDisplay == null) {
			System.err.println("SpectrogramBearingViewerAutoConfig: bearing display could not be added");
			return;
		}
		UserDisplayComponent spectrogram = builder.getTimeDisplayComponent();
		SwingUtilities.invokeLater(() -> userDisplay.tileSideBySide(
				new double[] { SPECTROGRAM_WIDTH, 1 - SPECTROGRAM_WIDTH }, spectrogram, bearingDisplay));
	}

	@Override
	public String getConfigName() {
		return "View a spectrogram and measure bearings";
	}

	@Override
	public String getConfigDescription() {
		return "View and annotate a spectrogram of multi-channel sound files, with a bearing "
				+ "calculated for each annotation.\n\n"
				+ "Sets up the same spectrogram and annotation modules as \"View a spectrogram\", "
				+ "plus a Bearing Localiser. Every annotation drawn on the spectrogram gets a "
				+ "bearing from the time delays between channels, which is updated whenever the "
				+ "annotation is moved or resized, and the Bearing Localiser's display is shown "
				+ "alongside the spectrogram. If the data are decimated, the bearings are calculated from "
				+ "the decimated data. Check the hydrophone positions in the array "
				+ "manager, since the bearings depend on them.";
	}

	@Override
	public String[] getSpeciesList() {
		return null;
	}

	@Override
	public Set<ConfigSpeciesGroup> getSpeciesGroups(double sampleRate, SoundMedium medium) {
		return ConfigSpeciesGroup.getVisibleGroups(sampleRate, medium);
	}

	@Override
	public SoundMedium getGlobalMediumSettings() {
		return null;
	}

	@Override
	public boolean hasSpectrogramOptions() {
		return true;
	}

	@Override
	public void setApplyContext(ConfigApplyContext applyContext) {
		spectrogramOptions = applyContext.getSpectrogramOptions();
	}
}
