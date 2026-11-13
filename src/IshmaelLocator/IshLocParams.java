/**
 * 
 */
package IshmaelLocator;

/**
 * @author Dave Mellinger
 *
 */
import java.io.Serializable;

public class IshLocParams implements Serializable, Cloneable {
	static public final long serialVersionUID = 0;
	String name = "";				//copied from FFTParams; not sure how used
	public String inputDataSource;
	public int channelList = 1;
	double tBefore = 0.2;
	double tAfter = 0.2;
	boolean useDetector;

	public String getName() { return name; }

	public void setName(String name) { this.name = name; }

	/** @return time before the detection to analyse, in seconds. */
	public double getTBefore() { return tBefore; }

	/** @param tBefore - time before the detection to analyse, in seconds. */
	public void setTBefore(double tBefore) { this.tBefore = tBefore; }

	/** @return time after the detection to analyse, in seconds. */
	public double getTAfter() { return tAfter; }

	/** @param tAfter - time after the detection to analyse, in seconds. */
	public void setTAfter(double tAfter) { this.tAfter = tAfter; }

	/** @return true if input comes from a detector. */
	public boolean isUseDetector() { return useDetector; }

	/** @param useDetector - true if input comes from a detector. */
	public void setUseDetector(boolean useDetector) { this.useDetector = useDetector; }

	@Override
	protected IshLocParams clone() {
		try {
			return (IshLocParams) super.clone();
		}
		catch (CloneNotSupportedException Ex) {
			return null;
		}
	}
}
