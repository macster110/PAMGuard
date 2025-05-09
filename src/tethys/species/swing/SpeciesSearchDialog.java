package tethys.species.swing;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;

import PamUtils.worker.PamWorkDialog;
import PamUtils.worker.PamWorkProgressMessage;
import PamView.dialog.PamDialog;
import PamView.dialog.PamGridBagContraints;
import PamView.dialog.warn.WarnOnce;
import PamView.tables.SwingTableColumnWidths;
import PamView.tables.TableColumnWidthData;
import tethys.TethysControl;
import tethys.species.ITISFunctions;
import tethys.species.SpeciesMapItem;

public class SpeciesSearchDialog extends PamDialog {

	private static final long serialVersionUID = 1L;

	private TethysControl tethysControl;
	
	private SpeciesMapItem selectedItem;

	private static SpeciesSearchDialog singleInstance;
	
	private JTextField searchText;
	
	private JButton searchButton;
	
	private JTable resultTable;

	private ArrayList<SpeciesMapItem> speciesMapItems;

	private DataModel tableModel;
	
	private volatile PamWorkDialog workDialog;
	
	private Object synch = new Object();
		

	private SpeciesSearchDialog(Window parentFrame, TethysControl tethysControl) {
		super(parentFrame, "Species search", false);
		this.tethysControl = tethysControl;
		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setBorder(new TitledBorder("Search Term"));
		JPanel topPanel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new PamGridBagContraints();
		topPanel.add(new JLabel("Latin or common name ", JLabel.RIGHT), c);
		c.gridx++;
		topPanel.add(searchText = new JTextField(12), c);
		c.gridx++;
		topPanel.add(searchButton = new JButton("search"), c);
		mainPanel.add(BorderLayout.NORTH, topPanel);
		searchButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				searchTethys();
			}

		});
		searchText.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				searchTethys();
			}
		});
		
		tableModel = new DataModel();
		resultTable = new JTable(tableModel);
		JPanel centPanel = new JPanel(new BorderLayout());
		centPanel.add(BorderLayout.NORTH, new JLabel("Possible matches (select one)", JLabel.LEFT));
		JScrollPane scrollPane = new JScrollPane(resultTable);
		centPanel.add(BorderLayout.CENTER, scrollPane);
		mainPanel.add(BorderLayout.CENTER, centPanel);
		
		resultTable.addMouseListener(new TableMouse());
		resultTable.getTableHeader().addMouseListener(new TableHeaderMouse());// separate listener for header. 
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new SwingTableColumnWidths("Species Search Dialog Table", resultTable);				
			}
		});
		
		setResizable(true);
		setDialogComponent(mainPanel);
	}
	public static SpeciesMapItem showDialog(Window parentFrame, TethysControl tethysControl, Integer currentCode) {
		if (singleInstance == null) {
			singleInstance = new SpeciesSearchDialog(parentFrame, tethysControl);
		}
		singleInstance.setParams(currentCode);
		singleInstance.setVisible(true);
		return singleInstance.selectedItem;
	}

	
	private void searchTethys() {
		clearResults();
		String str = searchText.getText();
		if (str == null || str.length() == 0) {
			return;
		}
		SearchWorker searchWorker = new SearchWorker(str);
		searchWorker.execute();
		synchronized(synch) {
			if (workDialog == null || workDialog.isVisible() == false) {
				workDialog = new PamWorkDialog(getOwner(), 1, "Searching Tethys Database");
				workDialog.setVisible(true);
				//			System.out.println
			}
		}
	}
	
	public void setMapItems(ArrayList<SpeciesMapItem> newMapItems) {
		this.speciesMapItems = newMapItems;
		if (newMapItems != null && newMapItems.size() == 1) {
			setSelectedItem(newMapItems.get(0));
		}
		tableModel.fireTableDataChanged();
	}

	private class SearchWorker extends SwingWorker<Integer, PamWorkProgressMessage> {

		private String searchString;
		private ArrayList<SpeciesMapItem> newMapItems;

		public SearchWorker(String searchString) {
			this.searchString = searchString;
		}

		@Override
		protected Integer doInBackground() throws Exception {
			String msg = String.format("Searching database for names containing \"%s\"", searchString);
			PamWorkProgressMessage pm = new PamWorkProgressMessage(null, msg);
			publish(pm);
			try {
				ITISFunctions itisFunctions = tethysControl.getItisFunctions();
				this.newMapItems = itisFunctions.searchSpecies(searchString);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			if (newMapItems == null) {
				return 0;
			}
			pm = new PamWorkProgressMessage(100, "Search Complete");
			publish(pm);
			return newMapItems.size();
		}

		@Override
		protected void done() {
			if (newMapItems == null || newMapItems.size() == 0) {
				String msg = String.format("No matching ITIS types for search term %s", searchString);
				WarnOnce.showNamedWarning("ITIS Lookup failure", getOwner(), "ITIS Code search", msg, WarnOnce.WARNING_MESSAGE);
				
			}
			setMapItems(newMapItems);
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					synchronized (synch) {
						if (workDialog != null) {
							workDialog.setVisible(false);
							workDialog.dispose();
							workDialog = null;
						}
					}
				}
			});
		}

		@Override
		protected void process(List<PamWorkProgressMessage> chunks) {
			// then open the dialog to block this thread. 
			for (PamWorkProgressMessage msg : chunks) {
				synchronized (synch) {
					if (workDialog != null) {
						workDialog.update(msg);
					}
				}				
			}
		}
		
	}
	
	private void setParams(Integer currentCode) {
		if (currentCode == null) {
			searchText.setText(null);
			clearResults();
		}
		else {
			searchText.setText(currentCode.toString());
			searchTethys();
		}
	}

	private void clearResults() {
		speciesMapItems = null;
		setSelectedItem(null);
	}
	@Override
	public boolean getParams() {
		if (selectedItem == null) {
			return showWarning("You must select a row from the table of species");
		}
		return selectedItem != null;
	}

	@Override
	public void cancelButtonPressed() {
		clearResults();
	}

	@Override
	public void restoreDefaultSettings() {
		// TODO Auto-generated method stub

	}
	
	private void enableControls() {
		getOkButton().setEnabled(selectedItem != null);
	}
	
	private class TableMouse extends MouseAdapter {

		@Override
		public void mouseClicked(MouseEvent e) {
			if (speciesMapItems == null) {
				return;
			}
			int selectedRow = resultTable.getSelectedRow();
			if (selectedRow >= 0 && selectedRow < speciesMapItems.size()) {
				setSelectedItem(speciesMapItems.get(selectedRow));
			}
			// otherwise, the mouse is probably on the top margin ? 
			
			tableModel.fireTableDataChanged();
		}
		
	}
	private class TableHeaderMouse extends MouseAdapter {

		@Override
		public void mouseClicked(MouseEvent e) {
			if (speciesMapItems == null) {
				return;
			}
			Point point = e.getPoint();
			int column = resultTable.columnAtPoint(point);
			if (column < 1 || column >= tableModel.getColumnCount()) {
				return;
			}
			// sort using the table data model column data. 
			sortByColumn(column);
			
			tableModel.fireTableDataChanged();
		}
		
	}

	private int lastSortColumn;
	private int sortDirection = 1;
	private void sortByColumn(int column) {
		if (speciesMapItems == null) {
			return;
		}
		if (column == lastSortColumn) {
			sortDirection = -sortDirection;
		}
		else {
			sortDirection = 1;
		}
		lastSortColumn = column;
		speciesMapItems.sort(new Comparator<SpeciesMapItem>() {
			@Override
			public int compare(SpeciesMapItem o1, SpeciesMapItem o2) {
				Object ob1 = tableModel.getValueAt(o1, column);
				Object ob2 = tableModel.getValueAt(o2, column);
				if (ob1 instanceof Comparable == false || ob2 instanceof Comparable == false) {
					ob1 = String.format("%s", ob1);
					ob2 = String.format("%s", ob2);
				}
				if (ob1 instanceof Comparable && ob2 instanceof Comparable) {
					Comparable c1 = (Comparable) ob1;
					Comparable c2 = (Comparable) ob2;
					return c1.compareTo(c2) * sortDirection;
				}
				else {
					return 0;
				}
			}
		});
	}
	
	private void setSelectedItem(SpeciesMapItem selItem) {
		this.selectedItem = selItem;
		enableControls();
	}
	
	private class DataModel extends AbstractTableModel {
		
		private String[] colNames = {"Select", "TSN", "Name", "Common Name"};

		@Override
		public int getRowCount() {
			if (speciesMapItems == null) {
				return 0;
			}
			return speciesMapItems.size();
		}

		@Override
		public int getColumnCount() {
			return colNames.length;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			SpeciesMapItem mapItem = speciesMapItems.get(rowIndex);
			return getValueAt(mapItem, columnIndex);
		}
		
		public Object getValueAt(SpeciesMapItem mapItem, int columnIndex) {
			switch (columnIndex) {
			case 0:
				return mapItem == selectedItem;
			case 1:
				return mapItem.getItisCode();
			case 2:
				return mapItem.getLatinName();
			case 3:
				return mapItem.getCommonName();
			}
			return null;
		}

		@Override
		public String getColumnName(int column) {
			return colNames[column];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			if (columnIndex == 0) {
				return Boolean.class;
			}
			return super.getColumnClass(columnIndex);
		}
		
	}

}
