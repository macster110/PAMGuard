package dataModelFX;

import java.util.ArrayList;

import PamController.PamControlledUnit;
import PamController.PamController;
import PamController.PamControllerInterface;
import dataModelFX.DataModelModulePane.ModuleRectangle;
import dataModelFX.connectionNodes.ModuleConnectionNode;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import pamViewFX.fxNodes.connectionPane.ConnectionNode;
import pamViewFX.fxNodes.connectionPane.ConnectionPane;
import pamViewFX.fxNodes.connectionPane.StandardConnectionNode;

/**
 * Sub class of ConnectionPane to deal with modules being dragged from 
 * module list to pane. 
 * 
 * @author Jamie Macaulay
 */
public class DataModelConnectPane extends ConnectionPane {

	/**
	 * The last location of a dropped node. 
	 */
	private Point2D lastDropPos=null;

	/**
	 * Preferred module width
	 */
	private double moduleWidth=100;

	/**
	 * Preferred module height
	 */
	private double moduleHeight=100;

	/**
	 * Reference to the data model
	 */
	private DataModelPaneFX dataModelPaneFX; 

    private static final DataFormat NODE_DATA_FORMAT = new DataFormat("application/x-java-custom-node");

	public DataModelConnectPane (DataModelPaneFX dataModelPaneFX){
		super();
		this.dataModelPaneFX=dataModelPaneFX; 
		
		//4k display size 3840 x 2160
		//This important because the pane is only the size of the module postions
		//unless the size is explicitly set. 
		this.setPrefSize(3840, 2160);
		
		  // Optional: Visual feedback when mouse enters/exits target
        this.setOnDragEntered(event -> {
        	
			final Dragboard dragboard = event.getDragboard();
            if (event.getGestureSource() != this &&
            		dataModelPaneFX.getModuleDragKey().equals(dragboard.getString())) {
                this.setStyle("-fx-background-color: #e0ffe0;");
            }
            event.consume();
        });
        
        this.setOnDragExited(event -> {
            // Reset background
           this.setStyle("");
           event.consume();
       });


		//set what happens when mouse is dragged over pane
		this.setOnDragOver(new EventHandler<DragEvent>(){
			
			
			@Override
			public void handle(DragEvent event)
			{
				
				//System.out.println("START drag over node drag dropped" + dataModelPaneFX.getDraggingStructure());

				final Dragboard dragboard = event.getDragboard();
				if (dragboard.hasString()
						&& dataModelPaneFX.getModuleDragKey().equals(dragboard.getString())
						&& dataModelPaneFX.getDraggingModule().get() != null || dataModelPaneFX.getDraggingStructure()!=null)
				{
					//System.out.println("ACCEPT drag over node drag dropped" + dataModelPaneFX.getDraggingStructure());
	
					event.acceptTransferModes(TransferMode.ANY);
					event.consume(); //causesd issues with dropping nodes not being detected
				}
			}
		});

		//set what happens when module is dropped. 
		this.setOnDragDropped(new EventHandler<DragEvent>(){
			@Override
			public void handle(DragEvent event)
			{
				
				//System.out.println("Add Some Node drag dropped: " + dataModelPaneFX.getDraggingStructure());

				final Dragboard dragboard = event.getDragboard();
				if (dragboard.hasString()
						&& dataModelPaneFX.getModuleDragKey().equals(dragboard.getString())
						&& dataModelPaneFX.getDraggingModule().get() != null){
					
					lastDropPos = new Point2D(event.getX(), event.getY());


					ModuleRectangle moduleRect = dataModelPaneFX.getDraggingModule().get();

//					System.out.println("Add Connection Node drag dropped");
					dataModelPaneFX.getDraggingModule().set(null);
					dataModelPaneFX.getConnectionNodeFactory().addNewModule(moduleRect.getPamModuleInfo(), event.getX(), event.getY()); 

				}

				if (dragboard.hasString()
						&& dataModelPaneFX.getModuleDragKey().equals(dragboard.getString())
						&& dataModelPaneFX.getDraggingStructure().get() != null){
//					System.out.println("Add Structure Node drag dropped");

					dataModelPaneFX.getConnectionNodeFactory().addNewStructure(dataModelPaneFX.getDraggingStructure().get(), event.getX(), event.getY()); 
					dataModelPaneFX.getDraggingStructure().set(null);

				}
				event.setDropCompleted(true);
				event.consume();

			}
		});
	}


	/**
	 * Called through from the PamGUiMangerFX.
	 * @param type - notification flag. 
	 */
	public void notifyModelChanged(int type) {

		switch(type){
		case PamControllerInterface.ADD_CONTROLLEDUNIT:
			//check if the module is already added to pane.
			checkModulesAdded();
			break; 
		case PamControllerInterface.INITIALIZATION_COMPLETE:
			if (autoPlaced) {
				// modules had no saved positions so lay them out by data flow. 
				autoPlaced = false;
				javafx.application.Platform.runLater(() -> {
					tidyLayout();
					tidiedConnections = getConnectionSignature();
				});
				/*
				 * Some modules (e.g. Whistle and Moan) only connect to their data source some time after 
				 * everything else has been set up. Keep checking for a while and lay the modules out 
				 * again whenever the connections change. 
				 */
				javafx.animation.Timeline recheck = new javafx.animation.Timeline(
						new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
							String connections = getConnectionSignature();
							if (!connections.equals(tidiedConnections)) {
								tidiedConnections = connections;
								tidyLayout();
							}
						}));
				recheck.setCycleCount(20);
				recheck.play();
			}
			break;
			//			case PamControllerInterface.INITIALIZATION_COMPLETE:
			//			break; 
			//		case PamControllerInterface.CHANGED_PROCESS_SETTINGS:
			//
			//			break; 
		}
	}


	/**
	 * Add a new ModuleConnectionNode, representing a new PamControlledUnit, to the ConnectionPane.
	 * @param pamControlledUnit - new PamControlledUnit (PAMGUARD module)
	 * @param x - x position to place module	
	 * @param y - y position to place module
	 */
	private ModuleConnectionNode addNewModuleNode(PamControlledUnit pamControlledUnit, double x, double y){
		ModuleConnectionNode newNode = this.dataModelPaneFX.getConnectionNodeFactory().createConnectionnNode(pamControlledUnit);
		addNewConnectionNode(newNode, x, y);
		return newNode;
	}

	/**
	 * Remove module from the pane and PAM model. 
	 * @param moduleConnectionNode - the module connection node to remove
	 */
	public void removeModuleNode(ModuleConnectionNode moduleConnectionNode) {
		/**
		 * First need to disconnect modules. 
		 * Note, the 'check connection' functions assume any parent/child ControlledUnits are 
		 * present and hence here, must graphically disconnect and disconnect in pam model.
		 */
		ArrayList<ConnectionNode> parentNodes=getParentConnectionNodes(moduleConnectionNode);
		if (parentNodes!=null){ 
			for (int i=0; i<parentNodes.size(); i++){
				ConnectionPane.disconnectNodes(moduleConnectionNode, parentNodes.get(i));
				dataModelPaneFX.attemptModuleDisconnect(moduleConnectionNode, (ModuleConnectionNode) parentNodes.get(i));
			}
		}
		ArrayList<ConnectionNode> childNodes=getChildConnectionNodes(moduleConnectionNode);
		if (childNodes!=null){ 
			for (int i=0; i<childNodes.size(); i++){
				((StandardConnectionNode) childNodes.get(i)).disconnectNode(moduleConnectionNode);
				dataModelPaneFX.attemptModuleDisconnect((ModuleConnectionNode) childNodes.get(i), moduleConnectionNode);
			}
		}
		this.removeConnectionNode(moduleConnectionNode);

		PamController.getInstance().removeControlledUnt(moduleConnectionNode.getPamControlledUnit());

		dataModelPaneFX.getModuleSelectPane().updateModuleListPane(); 

	}



	/**
	 * Check that the correct modules have been added to the ConnectionPane. If not add modules. 
	 */
	private synchronized void checkModulesAdded() {
		PamController pamController = PamController.getInstance();
		ArrayList<ConnectionNode> connectionNodes = getAllConnectionNodes(); 

		//System.out.println("checkModulesAdded: Number of controlled units: " + pamController.getNumControlledUnits() ); 
		for (int i = 0; i <= pamController.getNumControlledUnits(); i++) {
			checkModuleAdded(pamController.getControlledUnit(i), connectionNodes); 
		}

		this.dataModeltoPamModel();
	}

	/**
	 * Check whether a module has been added or not. If not then add the correct ModuleConnectionNode. 
	 * @param pamControlledUnit - the pamController. 
	 * @param connectionNodes - all the module connection nodes (does not include structures)
	 * @return true if a new module was added. 
	 */
	public boolean checkModuleAdded(PamControlledUnit pamControlledUnit, ArrayList<ConnectionNode> connectionNodes) {

		//System.out.println("datamodelConnectPane: checkModuleAdded: " + pamControlledUnit + " No. nodes: " + connectionNodes.size()); 

		if (pamControlledUnit==null) return false; 

		//There are three possibilities here. 
		//1) The module connection node does not exist and must be added
		//2) The module connection node is already in the pane and nothing needs to happen
		//3) The module connection node has been added as a shell and needs a reference to the PamContrrolledUnit. 


		//possibilities 1 and 2. 
		ModuleConnectionNode moduleConnectionNode; 
		for (int j = 0; j < connectionNodes.size(); j++) {
			moduleConnectionNode = (ModuleConnectionNode) connectionNodes.get(j); 
			//System.out.println("Looking for node: " + moduleConnectionNode.getPamControlledUnit());
			
			if (moduleConnectionNode.getPamControlledUnit()!=null && moduleConnectionNode.getPamControlledUnit().equals(pamControlledUnit)) {
				//System.out.println("There is already a node for : " + moduleConnectionNode.getPamControlledUnit().getUnitName()); 
				return true; 
			}
			else if (moduleConnectionNode.getConnectionNodeParams()!=null && moduleConnectionNode.getConnectionNodeParams().unitName!=null 
					&& moduleConnectionNode.getConnectionNodeParams().unitName.equals(pamControlledUnit.getUnitName())){
				//set the pamcontrolled unit reference within the connection node. node. 
				moduleConnectionNode.setPamControlledUnit(pamControlledUnit);
				//System.out.println("There is already a node waiting for : " + moduleConnectionNode.getPamControlledUnit().getUnitName()); 

				if (PamController.getInstance().isInitializationComplete()) {
					//only to be used on drag and dropped nodes.
					ModuleConnectionNode potentialParent = dataModelPaneFX.attemptDefaultParentConnect(moduleConnectionNode);
				}
				return true; 
			}
		}
		
		//System.out.println("Could not find the node: " + pamControlledUnit.getUnitName()); 

		//possibility 3
		ModuleConnectionNode newNode;
		// System.out.println("Need to add new module " +
		// pamController.getControlledUnit(i).getUnitName());
		if (lastDropPos == null) {
			//called if a module is added from another location other than the data model e.g. a add modules menu
			Point2D xy = getAutoDropPosition(connectionNodes); //get an automatic posiiton to drop the node that does not overlap with other nodes
			if (!PamController.getInstance().isInitializationComplete()) {
				// a configuration with no saved positions, e.g. one made in the Swing GUI, is being loaded.
				autoPlaced = true;
			}
			newNode = this.dataModelPaneFX.getConnectionNodeFactory().addNewModule(pamControlledUnit, xy.getX(), xy.getY());
		} 

		else {
			// add a new module which has been dropped from the data model. 
			newNode = this.dataModelPaneFX.getConnectionNodeFactory().addNewModule(pamControlledUnit, lastDropPos.getX(), lastDropPos.getY());
			lastDropPos = null;
		}

		//attempt to connect the module to a default parent. 
		ModuleConnectionNode potentialParent = dataModelPaneFX.attemptDefaultParentConnect(newNode);

		return true; 
		// /**
		// * Need to check if already connected as might be if the raw data block was
		// explicitly set in the pam process.
		// */
		// if (potentialParent!=null) System.out.println(" New module potential
		// parent"+potentialParent.getPamControlledUnit().getUnitName());
		// else System.out.println(" New module potential parent null");
		// if (potentialParent!=null){
		// System.out.println(" Added "+newNode.getPamControlledUnit().getUnitName() + "
		// connecting: "+potentialParent.getPamControlledUnit().getUnitName());
		// newNode.connectNode(potentialParent);
		// }
	}


	/**
	 * Find the first position on a grid that does not currently contain a node. 
	 * @param connectionNodes - the current list of connection nodes. 
	 * @return the new position in pixels. 
	 */
	private Point2D getAutoDropPosition(ArrayList<ConnectionNode> connectionNodes) {
		Point2D newPoint = null; 
		int nDrops = 0; 
		double minDist = 150; 
		double dist; 
		boolean pointOK = true; 

		//iterate sequentially through positions...
		while (nDrops<1000) {
			// work along rows of a grid. 
			newPoint =  new Point2D(LAYOUT_MARGIN + LAYOUT_SPACING_X*(nDrops % 5), LAYOUT_MARGIN + LAYOUT_SPACING_Y*(nDrops / 5));

			pointOK = true; 
			for (int i=0; i<connectionNodes.size(); i++) {
				dist= newPoint.distance(new Point2D(connectionNodes.get(i).getConnectionNodeBody().getLayoutX(),
						connectionNodes.get(i).getConnectionNodeBody().getLayoutY())); 
				if (dist<minDist) {
					//one of the nodes is too close
					pointOK=false; 
					break; 
				}
			}

			if (pointOK) {
				return newPoint; 
			}

			nDrops++; 
		}
		return newPoint;
	}


	/**
	 * Margin around the automatic layout in pixels. 
	 */
	private static final double LAYOUT_MARGIN = 40;

	/**
	 * Horizontal distance between modules in the automatic layout in pixels. 
	 */
	private static final double LAYOUT_SPACING_X = 250;

	/**
	 * Vertical distance between modules in the automatic layout in pixels. 
	 */
	private static final double LAYOUT_SPACING_Y = 170;

	/**
	 * Maximum number of modules without connections in one row of the automatic layout. 
	 */
	private static final int LAYOUT_LOOSE_PER_ROW = 6;

	/**
	 * True if modules were placed automatically while a configuration was loading. 
	 */
	private boolean autoPlaced = false;

	/**
	 * Lay the modules out by data flow. Modules which make or process data are placed in columns 
	 * from left to right, each one to the right of the module its data comes from. Modules with no 
	 * data connections (e.g. the database and displays) are placed in rows underneath. 
	 */
	/**
	 * The module connections when the modules were last laid out automatically. 
	 */
	private String tidiedConnections = "";

	/**
	 * @return a string which lists the data source of every process in every module, which 
	 * changes when any module connects to a different source. 
	 */
	private String getConnectionSignature() {
		StringBuilder sb = new StringBuilder();
		PamController pamController = PamController.getInstance();
		for (int i = 0; i < pamController.getNumControlledUnits(); i++) {
			PamControlledUnit unit = pamController.getControlledUnit(i);
			sb.append(unit.getUnitName()).append(':');
			for (int j = 0; j < unit.getNumPamProcesses(); j++) {
				PamguardMVC.PamProcess process = unit.getPamProcess(j);
				if (process != null && process.getParentDataBlock() != null) {
					sb.append(process.getParentDataBlock().getLongDataName()).append(',');
				}
			}
			sb.append(';');
		}
		// and the connections drawn in the data model, which can appear later. 
		for (ConnectionNode node : getAllConnectionNodes()) {
			if (!(node instanceof ModuleConnectionNode) || ((ModuleConnectionNode) node).getPamControlledUnit() == null) {
				continue;
			}
			sb.append(((ModuleConnectionNode) node).getPamControlledUnit().getUnitName()).append('<');
			for (ConnectionNode parent : getParentConnectionNodes((ModuleConnectionNode) node)) {
				if (parent instanceof ModuleConnectionNode && ((ModuleConnectionNode) parent).getPamControlledUnit() != null) {
					sb.append(((ModuleConnectionNode) parent).getPamControlledUnit().getUnitName()).append(',');
				}
			}
			sb.append(';');
		}
		return sb.toString();
	}

	public void tidyLayout() {
		ArrayList<ConnectionNode> nodes = getAllConnectionNodes(); 
		ArrayList<ModuleConnectionNode> moduleNodes = new ArrayList<ModuleConnectionNode>();
		for (ConnectionNode node : nodes) {
			if (node instanceof ModuleConnectionNode && ((ModuleConnectionNode) node).getPamControlledUnit() != null) {
				moduleNodes.add((ModuleConnectionNode) node);
			}
		}
		if (moduleNodes.isEmpty()) {
			return;
		}

		// find the modules which each module gets its data from. 
		java.util.HashMap<PamControlledUnit, ModuleConnectionNode> unitNodes = new java.util.HashMap<>();
		for (ModuleConnectionNode node : moduleNodes) {
			unitNodes.put(node.getPamControlledUnit(), node);
		}
		java.util.HashMap<ModuleConnectionNode, ArrayList<ModuleConnectionNode>> parents = new java.util.HashMap<>();
		java.util.HashSet<ModuleConnectionNode> hasChildren = new java.util.HashSet<>();
		for (ModuleConnectionNode node : moduleNodes) {
			ArrayList<ModuleConnectionNode> nodeParents = new ArrayList<ModuleConnectionNode>();
			PamControlledUnit unit = node.getPamControlledUnit();
			for (int i = 0; i < unit.getNumPamProcesses(); i++) {
				PamguardMVC.PamProcess process = unit.getPamProcess(i);
				if (process == null || process.getParentDataBlock() == null) continue;
				PamguardMVC.PamProcess parentProcess = process.getParentDataBlock().getParentProcess();
				if (parentProcess == null) continue;
				ModuleConnectionNode parentNode = unitNodes.get(parentProcess.getPamControlledUnit());
				if (parentNode != null && parentNode != node && !nodeParents.contains(parentNode)) {
					nodeParents.add(parentNode);
					hasChildren.add(parentNode);
				}
			}
			// and anything which is connected in the data model display. 
			for (ConnectionNode guiParent : getParentConnectionNodes(node)) {
				if (guiParent instanceof ModuleConnectionNode && guiParent != node && moduleNodes.contains(guiParent)
						&& !nodeParents.contains(guiParent)) {
					nodeParents.add((ModuleConnectionNode) guiParent);
					hasChildren.add((ModuleConnectionNode) guiParent);
				}
			}
			parents.put(node, nodeParents);
		}

		// the column of a module is one more than the furthest right of its parents. 
		java.util.HashMap<ModuleConnectionNode, Integer> columns = new java.util.HashMap<>();
		int nColumns = 0;
		for (ModuleConnectionNode node : moduleNodes) {
			nColumns = Math.max(nColumns, findColumn(node, parents, columns, 0) + 1);
		}

		ArrayList<ArrayList<ModuleConnectionNode>> columnNodes = new ArrayList<>();
		for (int i = 0; i < nColumns; i++) {
			columnNodes.add(new ArrayList<ModuleConnectionNode>());
		}
		ArrayList<ModuleConnectionNode> looseNodes = new ArrayList<ModuleConnectionNode>();
		for (ModuleConnectionNode node : moduleNodes) {
			if (parents.get(node).isEmpty() && !hasChildren.contains(node)) {
				looseNodes.add(node);
			}
			else {
				columnNodes.get(columns.get(node)).add(node);
			}
		}

		// place the columns, keeping each module close to the height of its parents to limit crossing lines. 
		java.util.HashMap<ModuleConnectionNode, Double> rows = new java.util.HashMap<>();
		int maxRows = 0;
		for (int col = 0; col < nColumns; col++) {
			ArrayList<ModuleConnectionNode> column = columnNodes.get(col);
			column.sort((a, b) -> Double.compare(meanParentRow(a, parents, rows), meanParentRow(b, parents, rows)));
			for (int row = 0; row < column.size(); row++) {
				ModuleConnectionNode node = column.get(row);
				rows.put(node, (double) row);
				node.getConnectionNodeBody().setLayoutX(LAYOUT_MARGIN + col * LAYOUT_SPACING_X);
				node.getConnectionNodeBody().setLayoutY(LAYOUT_MARGIN + row * LAYOUT_SPACING_Y);
			}
			maxRows = Math.max(maxRows, column.size());
		}

		// modules with no data connections go underneath. 
		for (int i = 0; i < looseNodes.size(); i++) {
			ModuleConnectionNode node = looseNodes.get(i);
			node.getConnectionNodeBody().setLayoutX(LAYOUT_MARGIN + (i % LAYOUT_LOOSE_PER_ROW) * LAYOUT_SPACING_X);
			node.getConnectionNodeBody().setLayoutY(LAYOUT_MARGIN + (maxRows + i / LAYOUT_LOOSE_PER_ROW) * LAYOUT_SPACING_Y);
		}
		this.requestLayout();
	}

	/**
	 * Find the layout column of a module. 
	 */
	private int findColumn(ModuleConnectionNode node, java.util.HashMap<ModuleConnectionNode, ArrayList<ModuleConnectionNode>> parents, 
			java.util.HashMap<ModuleConnectionNode, Integer> columns, int depth) {
		Integer known = columns.get(node);
		if (known != null) {
			return known;
		}
		int column = 0;
		if (depth < 50) { // protects against a loop in the data model.
			for (ModuleConnectionNode parent : parents.get(node)) {
				column = Math.max(column, findColumn(parent, parents, columns, depth + 1) + 1);
			}
		}
		columns.put(node, column);
		return column;
	}

	/**
	 * The mean row of the parents of a module which have already been placed. 
	 */
	private double meanParentRow(ModuleConnectionNode node, java.util.HashMap<ModuleConnectionNode, ArrayList<ModuleConnectionNode>> parents, 
			java.util.HashMap<ModuleConnectionNode, Double> rows) {
		double sum = 0;
		int n = 0;
		for (ModuleConnectionNode parent : parents.get(node)) {
			Double row = rows.get(parent);
			if (row != null) {
				sum += row;
				n++;
			}
		}
		return n == 0 ? Double.MAX_VALUE : sum / n;
	}

	/**
	 * Check module connections. Attempts to make the pamDataModel reflect the GUI data model. 
	 */
	public void pamModeltoDataModel() {
		dataModelPaneFX.pamModeltoDataModel(true); 
	}

	/**
	 * Check module connections. Attempts to make the GUI data model reflect the current pamDataModel. For example 
	 * use this function if source data is changed in external dialogs. 
	 */
	public void dataModeltoPamModel(){
		dataModelPaneFX.dataModeltoPamModel();
	}


	public double getModuleWidth() {
		return this.moduleWidth;
	}

	public double getModuleHeight() {
		return this.moduleHeight;
	}

}