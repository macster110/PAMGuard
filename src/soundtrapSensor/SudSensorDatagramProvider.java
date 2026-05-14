package soundtrapSensor;

import PamguardMVC.PamDataUnit;
import dataGram.DatagramProvider;
import dataGram.DatagramScaleInformation;

/**
 * Datagram provider for the SudSensor module. Displays average heading, pitch
 * and roll in the PAMGuard datagram panel as three separate bands using a 2D
 * plot (one value per time slice per band).
 * <p>
 * The three datagram points are:
 * <ol>
 *   <li>Heading   – 0 to 360 °</li>
 *   <li>Pitch     – shifted +90 so the range is 0 to 180 °</li>
 *   <li>Roll      – shifted +180 so the range is 0 to 360 °</li>
 * </ol>
 *
 * @author PAMGuard
 */
public class SudSensorDatagramProvider implements DatagramProvider {

    /** Three bands: heading, pitch (shifted), roll (shifted). */
    private static final int N_POINTS = 3;

    private final SudSensorControl sensorControl;

    public SudSensorDatagramProvider(SudSensorControl sensorControl) {
        this.sensorControl = sensorControl;
    }

    @Override
    public int getNumDataGramPoints() {
        return N_POINTS;
    }

    /**
     * Accumulates one sample into the datagram line. Each of the three slots
     * holds the running total for one orientation angle; the datagram
     * framework will average across all data units in each time slice.
     *
     * @param dataUnit   the current {@link SudSensorDataUnit}
     * @param dataGramLine array of length {@link #getNumDataGramPoints()}
     * @return 1 (number of data points contributed)
     */
    @Override
    public int addDatagramData(PamDataUnit dataUnit, float[] dataGramLine) {
        if (!(dataUnit instanceof SudSensorDataUnit)) {
            return 0;
        }
        SudSensorDataUnit su = (SudSensorDataUnit) dataUnit;
        // All orientation values are in a ±180 range; shift to 0–360 for display.
        dataGramLine[0] += (float) (su.getHeading() + 180.0);  // −180..+180 → 0..360
        dataGramLine[1] += (float) (su.getPitch()   +  90.0);  // −90..+90   → 0..180
        dataGramLine[2] += (float) (su.getRoll()    + 180.0);  // −180..+180 → 0..360
        return 1;
    }

    /**
     * Returns scale information for the datagram display. The y-axis spans
     * 0–360 ° (the maximum of the three shifted ranges) and is plotted as a
     * flat 2-D colour band rather than a spectrogram.
     */
    @Override
    public DatagramScaleInformation getScaleInformation() {
        return new DatagramScaleInformation(0, 360, "degrees",
                false, DatagramScaleInformation.PLOT_2D);
    }
}
