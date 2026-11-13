package IshmaelDetector.layoutFX;

import java.io.File;
import java.util.ArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import IshmaelDetector.IshDetParams;
import IshmaelDetector.MatchFiltParams;
import PamController.SettingsPane;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamButton;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamVBox;

/**
 * Settings for the Ishmael matched filter detector. Allows the user to select
 * the kernel sound file, keeping a list of recently used kernels.
 *
 * @author Jamie Macaulay
 *
 */
public class MatchFiltPane extends SettingsPane<IshDetParams> {

	/**
	 * The main pane.
	 */
	private PamVBox mainPane;

	/**
	 * Combo box of recently used kernel files.
	 */
	private ComboBox<String> kernelFileBox;

	/**
	 * Shows information on the selected kernel file.
	 */
	private Label kernelInfoLabel;

	public MatchFiltPane() {
		super(null);
		mainPane = createPane();
	}

	private PamVBox createPane() {
		PamVBox mainPane = new PamVBox();
		mainPane.setSpacing(5);

		Label titleLabel = new Label("Matched Filter");
		PamGuiManagerFX.titleFont2style(titleLabel);

		kernelFileBox = new ComboBox<String>();
		kernelFileBox.setMaxWidth(Double.MAX_VALUE);
		kernelFileBox.setPrefWidth(300);
		kernelFileBox.setTooltip(new Tooltip("The kernel sound file. The kernel is a recording of the sound to detect "
				+ "\nwhich is cross correlated with the incoming raw data."));
		HBox.setHgrow(kernelFileBox, Priority.ALWAYS);
		kernelFileBox.valueProperty().addListener((obs, oldVal, newVal) -> showKernelInfo(newVal));

		PamButton browseButton = new PamButton();
		browseButton.setGraphic(PamGlyphDude.createPamIcon("mdi2f-file-music-outline", PamGuiManagerFX.iconSize));
		browseButton.setTooltip(new Tooltip("Select another kernel sound file..."));
		browseButton.setOnAction(action -> selectFile());

		PamHBox fileBox = new PamHBox();
		fileBox.setSpacing(5);
		fileBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		fileBox.getChildren().addAll(new Label("Kernel sound file"), kernelFileBox, browseButton);

		kernelInfoLabel = new Label();
		kernelInfoLabel.setPadding(new Insets(0, 0, 0, 5));

		mainPane.getChildren().addAll(titleLabel, fileBox, kernelInfoLabel);
		return mainPane;
	}

	/**
	 * Open a file chooser so the user can select a new kernel file.
	 */
	private void selectFile() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select matched filter kernel file...");
		fileChooser.getExtensionFilters().addAll(new ExtensionFilter("Audio files", "*.wav", "*.WAV", "*.aif", "*.aiff", "*.AIF", "*.AIFF"),
				new ExtensionFilter("All files", "*.*"));
		String currFile = kernelFileBox.getValue();
		if (currFile != null && currFile.length() > 0) {
			File f = new File(currFile);
			if (f.getParentFile() != null && f.getParentFile().exists()) {
				fileChooser.setInitialDirectory(f.getParentFile());
			}
		}
		File file = fileChooser.showOpenDialog(mainPane.getScene() == null ? null : mainPane.getScene().getWindow());
		if (file == null) return;
		String path = file.getAbsolutePath();
		kernelFileBox.getItems().remove(path);
		kernelFileBox.getItems().add(0, path);
		kernelFileBox.getSelectionModel().select(0);
	}

	/**
	 * Show the sample rate and duration of the kernel file.
	 * @param fileName - the kernel file.
	 */
	private void showKernelInfo(String fileName) {
		if (fileName == null || fileName.length() == 0) {
			kernelInfoLabel.setText("No kernel file selected");
			return;
		}
		File file = new File(fileName);
		if (!file.exists()) {
			kernelInfoLabel.setText("Warning: the kernel file does not exist");
			return;
		}
		try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
			AudioFormat format = ais.getFormat();
			double duration = ais.getFrameLength() / format.getFrameRate();
			kernelInfoLabel.setText(String.format("%d samples, %.0f Hz, %.3f s, %d channel(s)", ais.getFrameLength(),
					format.getSampleRate(), duration, format.getChannels()));
		}
		catch (Exception e) {
			kernelInfoLabel.setText("Unable to read the kernel file: " + e.getMessage());
		}
	}

	@Override
	public IshDetParams getParams(IshDetParams currParams) {
		MatchFiltParams params = (MatchFiltParams) currParams;
		String fn = kernelFileBox.getValue();
		if (fn == null || fn.length() == 0) {
			// the Swing dialog will not close without a kernel file.
			return null;
		}
		// copy the list since clones of the parameters share it.
		ArrayList<String> newList = new ArrayList<String>(kernelFileBox.getItems());
		params.getKernelFilenameList().clear();
		params.getKernelFilenameList().addAll(newList);
		params.setKernelFilename(fn);
		return params;
	}

	@Override
	public void setParams(IshDetParams input) {
		MatchFiltParams params = (MatchFiltParams) input;
		kernelFileBox.getItems().clear();
		kernelFileBox.getItems().addAll(params.getKernelFilenameList());
		if (kernelFileBox.getItems().size() > 0) {
			kernelFileBox.getSelectionModel().select(0);
		}
		showKernelInfo(kernelFileBox.getValue());
	}

	@Override
	public String getName() {
		return "Ishmael Matched Filter";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
