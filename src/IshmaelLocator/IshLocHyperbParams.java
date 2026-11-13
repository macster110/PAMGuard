/**
 * 
 */
package IshmaelLocator;

/**
 * @author Dave Mellinger
 */

import java.io.Serializable;

public class IshLocHyperbParams extends IshLocParams implements Serializable, Cloneable {
	static public final long serialVersionUID = 0;
	int nDimensions = 2;			//2 or 3

	/** @return the number of dimensions to localise in (2 or 3). */
	public int getNDimensions() { return nDimensions; }

	/** @param nDimensions - the number of dimensions to localise in (2 or 3). */
	public void setNDimensions(int nDimensions) { this.nDimensions = nDimensions; }

	@Override
	protected IshLocHyperbParams clone() {
		return (IshLocHyperbParams)super.clone();
	}
}
