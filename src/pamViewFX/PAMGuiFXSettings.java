package pamViewFX;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Holds settings for the FX GUI.
 * @author Jamie Macaulay 
 *
 */
public class PAMGuiFXSettings implements Serializable, Cloneable {

	/**
	 * 
	 */
	public static final long serialVersionUID = 2L;
	
	/**
	 * Info on the tabs which are used in PAMGuard. 
	 */
	public ArrayList<TabInfo> tabInfos = new ArrayList<TabInfo>();
	
	/**
	 * The width of the screen. 
	 */
	public double width = 1400;
	
	/**
	 * The height in pixels. 
	 */
	public double height = 900; 

	/**
	 * The position of the window on the screen. NaN if the window has never been positioned. 
	 */
	public double x = Double.NaN;

	public double y = Double.NaN;

	/**
	 * True if the window is maximised. 
	 */
	public boolean maximized = false;

	
	
	/**
	 * True if PG is in fullscreen on startup
	 */
	public boolean fullscreen = false;
	
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public PAMGuiFXSettings clone() {
		try {
			return (PAMGuiFXSettings) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}


}
