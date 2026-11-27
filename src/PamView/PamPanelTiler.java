package PamView;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/**
 * Class to lay out windows within a JDesktopPane
 * @author Doug
 *
 */
public class PamPanelTiler {
	
	JDesktopPane desktopPane;
	
	public PamPanelTiler(JDesktopPane desktopPane) {
		
		this.desktopPane = desktopPane;
		
	}
	
	/**
	 * Get the frames that should be laid out: everything in the desktop
	 * that is visible and not closed. Iconified frames are included and
	 * get restored when they are tiled.
	 * @return list of frames, possibly empty.
	 */
	private List<JInternalFrame> getTileableFrames() {
		List<JInternalFrame> tileable = new ArrayList<>();
		JInternalFrame[] frames = desktopPane.getAllFrames();
		if (frames == null) {
			return tileable;
		}
		for (JInternalFrame frame : frames) {
			if (frame == null || frame.isClosed()) {
				continue;
			}
			if (!frame.isVisible() && !frame.isIcon()) {
				continue;
			}
			tileable.add(frame);
		}
		return tileable;
	}

	/**
	 * Take a frame out of the maximised and iconified states so that it
	 * can be sized and positioned.
	 */
	private void restoreFrame(JInternalFrame frame) {
		if (frame.isMaximum()) {
			try {
				frame.setMaximum(false);
			}
			catch (PropertyVetoException Ex) {
			}
		}
		if (frame.isIcon()) {
			try {
				frame.setIcon(false);
			}
			catch (PropertyVetoException Ex) {
			}
		}
	}

	/**
	 * auto tile the windows
	 * always have zero or one more window in x than in y and increase until
	 * there are enough spaces in the grid.
	 *
	 */
	public void tile() {
		int nPanels = getTileableFrames().size();
		if (nPanels == 0) {
			return;
		}
		int nX = 1;
		int nY = 1;
		while (true) {
			if (nX*nY >= nPanels) break;
			nX++;
			if (nX*nY >= nPanels) break;
			nY++;
		}
		tileWindows(nX, nY);
	}

	/**
	 * Place windows one above the other.
	 */
	public void tileHorizontal() {
		tileWindows(1, getTileableFrames().size());
	}

	/**
	 * Place windows side by side.
	 */
	public void tileVertical() {
		tileWindows(getTileableFrames().size(), 1);
	}

	private void tileWindows(int nX, int nY) {
		List<JInternalFrame> frames = getTileableFrames();
		if (frames.isEmpty() || nX < 1 || nY < 1) {
			return;
		}
		Dimension d = desktopPane.getSize();
		if (d.width <= 0 || d.height <= 0) {
			return;
		}
		int dX = Math.max(1, d.width / nX);
		int dY = Math.max(1, d.height / nY);
		for (int i = 0; i < frames.size(); i++) {
			JInternalFrame frame = frames.get(i);
			restoreFrame(frame);
			int col = i % nX;
			int row = (i / nX) % nY;
			frame.setSize(dX, dY);
			frame.setLocation(col * dX, row * dY);
		}
	}

	public void cascade() {
		List<JInternalFrame> frames = getTileableFrames();
		if (frames.isEmpty()) {
			return;
		}
		Dimension d = desktopPane.getSize();
		if (d.width <= 0 || d.height <= 0) {
			return;
		}
		int sX = Math.max(1, d.width * 2 / 3);
		int sY = Math.max(1, d.height * 2 / 3);
		int dX = 40;
		int dY = 40;
		/*
		 * Number of steps that fit before a frame would start to fall off
		 * the bottom or right of the desktop. After that, start again at the top.
		 */
		int nSteps = Math.max(1, Math.min((d.width - sX) / dX, (d.height - sY) / dY) + 1);
		for (int i = 0; i < frames.size(); i++) {
			JInternalFrame frame = frames.get(i);
			restoreFrame(frame);
			int step = i % nSteps;
			frame.setSize(sX, sY);
			frame.setLocation(step * dX, step * dY);
		}
	}

	public JMenu getMenu() {
		JMenu tileMenu = new JMenu("Arrange Windows ...");
		JMenuItem menuItem;
		
		menuItem = new JMenuItem("Tile");
		menuItem.addActionListener(new Tile());
		menuItem.setToolTipText("Tile windows in a grid");
		tileMenu.add(menuItem);
		
		menuItem = new JMenuItem("Tile Horizontal");
		menuItem.addActionListener(new TileHorizontal());
		menuItem.setToolTipText("Place windows one above the other");
		tileMenu.add(menuItem);
		
		menuItem = new JMenuItem("Tile Vertical");
		menuItem.addActionListener(new TileVertical());
		menuItem.setToolTipText("Place windows side by side");
		tileMenu.add(menuItem);
		
		menuItem = new JMenuItem("Cascade");
		menuItem.addActionListener(new Cascade());
		menuItem.setToolTipText("Cascade windows across screen");
		tileMenu.add(menuItem);
		
		return tileMenu;
	}
	
	private class Tile implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			tile();
		}
	}
	
	private class TileHorizontal implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			tileHorizontal();
		}
	}
	
	private class TileVertical implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			tileVertical();
		}
	}
	
	private class Cascade implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			cascade();
		}
	}
}
