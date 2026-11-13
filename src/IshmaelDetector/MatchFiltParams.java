/**
 * 
 */
package IshmaelDetector;

/**
 * @author Dave Mellinger
 */

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;

import PamModel.parametermanager.ManagedParameters;
import PamModel.parametermanager.PamParameterSet;
import PamModel.parametermanager.PrivatePamParameterData;

public class MatchFiltParams extends IshDetParams implements Serializable, Cloneable, ManagedParameters {

	static public final long serialVersionUID = 0;
	/** This is a list of file names that the user has picked recently.
	 * The 0th element of it is the matched filter kernel; later items
	 * are there only to make it easy for the user to re-pick them.
	 */ 
	ArrayList<String> kernelFilenameList = new ArrayList<String>(0);
	
	
	static public final int MAX_FILENAME_LIST_SIZE = 10;

	@Override
	protected MatchFiltParams clone() {
		return (MatchFiltParams) super.clone();
	}
	
	/** Return the name of the kernel file, or "" if none.
	 */
	public String getKernelFilename() {
		return (kernelFilenameList.size() > 0) ? kernelFilenameList.get(0) : "";
	}

	/**
	 * Get the list of recently used kernel files. The first entry is the one in use.
	 * @return the list of kernel file names.
	 */
	public ArrayList<String> getKernelFilenameList() {
		if (kernelFilenameList == null) kernelFilenameList = new ArrayList<String>(0);
		return kernelFilenameList;
	}

	/**
	 * Make a file the current kernel file, moving it to the top of the recent file list
	 * and trimming the list to {@link #MAX_FILENAME_LIST_SIZE}.
	 * @param fileName - the kernel file name.
	 */
	public void setKernelFilename(String fileName) {
		if (fileName == null) return;
		ArrayList<String> list = getKernelFilenameList();
		list.remove(fileName);
		list.add(0, fileName);
		while (list.size() > MAX_FILENAME_LIST_SIZE) {
			list.remove(list.size() - 1);
		}
	}
	
	@Override
	public PamParameterSet getParameterSet() {
		PamParameterSet ps = super.getParameterSet();
		try {
			Field field = this.getClass().getDeclaredField("kernelFilenameList");
			ps.put(new PrivatePamParameterData(this, field) {
				@Override
				public Object getData() throws IllegalArgumentException, IllegalAccessException {
					return kernelFilenameList;
				}
			});
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
		return ps;
	}
}
