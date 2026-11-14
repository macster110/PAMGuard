package pamViewFX.fxNodes.utilityPanes;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxStyles.PamStylesManagerFX;
import PamController.SettingsPane;

/**
 * Create a dialog to show module settings pane. Use this version for dialogs which should look similar to the
 * standard PAMGuard Swing dialogs. 
 * 
 * @author Jamie Macaulay
 * 
 * @param <T> - settings class for the module. 
 */
public class SettingsDialog<T> extends PamDialogFX<T>{

	private SettingsPane<T> settingsPane;

	public SettingsDialog(SettingsPane<T> settingsPane){
		super(PamGuiManagerFX.getInstance().getPrimaryStage() /*TODO - add stage*/, settingsPane.getName(), StageStyle.DECORATED);
		this.setResizable(true);
		this.settingsPane=settingsPane;
	
		
		// give the settings a consistent margin inside the dialog. 
		javafx.scene.layout.StackPane holder = new javafx.scene.layout.StackPane(settingsPane.getContentNode());
		holder.setPadding(new javafx.geometry.Insets(10, 10, 0, 10));

		/*
		 * Some settings panes are taller than a laptop screen. Put the settings in a scroll pane and limit the 
		 * height of the dialog to the screen so that the OK and Cancel buttons can always be reached. The scroll 
		 * bar only appears when it is needed. 
		 */
		javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(holder) {
			/*
			 * A scroll pane does not ask for the size of its content by default, so do that here. The dialog then 
			 * opens at the same size as it did without scrolling whenever the settings fit on the screen. 
			 */
			@Override
			protected double computePrefWidth(double height) {
				double scrollBar = holder.prefHeight(-1) > maxContentHeight ? SCROLL_BAR_WIDTH : 0;
				return holder.prefWidth(-1) + scrollBar + 2;
			}

			/*
			 * The dialog style limits how wide a dialog can be unless its content has a larger minimum width, 
			 * so don't let the settings get narrower than they want to be.  
			 */
			@Override
			protected double computeMinWidth(double height) {
				return computePrefWidth(height);
			}

			@Override
			protected double computePrefHeight(double width) {
				return holder.prefHeight(-1) + 2;
			}
		};
		scrollPane.setFitToWidth(true);
		scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
		scrollPane.getStyleClass().add("edge-to-edge");
		scrollPane.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; -fx-border-width: 0;");
		scrollPane.setMinHeight(100);
		this.setContent(scrollPane);

		this.setOnShowing(event -> limitToScreen());
	

		Stage stage = (Stage) this.getDialogPane().getScene().getWindow();
		stage.toFront();
		
		this.getDialogPane().getStylesheets().addAll(PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
		
//		System.out.println("SettingsPane: DIALOG " + 	this.getDialogPane().getStylesheets().size());
//		for (int i=0;i<this.getDialogPane().getStylesheets().size();i++) {
//			System.out.println(this.getDialogPane().getStylesheets().get(i));
//		}
	}
	
	/**
	 * Fraction of the screen height a settings dialog can use. 
	 */
	private static final double MAX_SCREEN_FRACTION = 0.9;

	/**
	 * Room left for a vertical scroll bar when the settings are taller than the screen. 
	 */
	private static final double SCROLL_BAR_WIDTH = 16;

	/**
	 * The tallest the settings can be before they need a scroll bar. 
	 */
	private double maxContentHeight = Double.MAX_VALUE;

	/**
	 * Stop the dialog from being taller than the screen it's on. 
	 */
	private void limitToScreen() {
		try {
			Stage stage = (Stage) this.getDialogPane().getScene().getWindow();
			javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
			if (stage.getOwner() != null) {
				javafx.stage.Window owner = stage.getOwner();
				java.util.List<javafx.stage.Screen> screens = javafx.stage.Screen.getScreensForRectangle(
						owner.getX(), owner.getY(), Math.max(owner.getWidth(), 1), Math.max(owner.getHeight(), 1));
				if (screens.size() > 0) {
					bounds = screens.get(0).getVisualBounds();
				}
			}
			stage.setMaxHeight(bounds.getHeight() * MAX_SCREEN_FRACTION);
			// the buttons and window title take up roughly 100 pixels. 
			maxContentHeight = bounds.getHeight() * MAX_SCREEN_FRACTION - 100;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * The last parameters given to the dialog. 
	 */
	private T lastParams;

	@Override
	public void setParams(T input) {
		lastParams = input;
		settingsPane.setParams(input);
	}

	@Override
	public T getParams() {
		return settingsPane.getParams(lastParams);
	}
	
}