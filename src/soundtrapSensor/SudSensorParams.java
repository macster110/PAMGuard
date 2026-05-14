package soundtrapSensor;

import java.io.Serializable;

/**
 * Serializable parameters for the SudSensor module. Stores options for sensor
 * fusion and calibration of the magnetometer/accelerometer data extracted from
 * SoundTrap .swv files.
 *
 * @author PAMGuard
 */
public class SudSensorParams implements Serializable, Cloneable {

    public static final long serialVersionUID = 1L;

    // -------------------------------------------------------------------------
    // Hard-iron magnetometer calibration offsets (subtracted from raw values)
    // -------------------------------------------------------------------------
    public double magOffsetX = 0.0;
    public double magOffsetY = 0.0;
    public double magOffsetZ = 0.0;

    // -------------------------------------------------------------------------
    // Soft-iron magnetometer scale factors (multiplicative)
    // -------------------------------------------------------------------------
    public double magScaleX = 1.0;
    public double magScaleY = 1.0;
    public double magScaleZ = 1.0;

    // -------------------------------------------------------------------------
    // Accelerometer calibration offsets
    // -------------------------------------------------------------------------
    public double accelOffsetX = 0.0;
    public double accelOffsetY = 0.0;
    public double accelOffsetZ = 0.0;

    // -------------------------------------------------------------------------
    // File continuity
    // -------------------------------------------------------------------------
    /**
     * Maximum gap (in milliseconds) between the last sample of one SUD file and
     * the first sample of the next before the sensor-fusion filter is reset.
     * If the gap is within this threshold the filter carries on uninterrupted,
     * avoiding the bedding-in spike at the start of each file.
     * Default is 2000 ms (2 seconds).
     */
    public long maxContiguousGapMs = 2000L;

    // -------------------------------------------------------------------------
    // Sensor fusion: Mahony complementary filter gains
    // Kp – proportional gain, Ki – integral gain
    // -------------------------------------------------------------------------
    public double fusionKp = 2.0;
    public double fusionKi = 0.005;

    /**
     * Magnetic declination in degrees (added to computed magnetic heading to
     * obtain true heading). Positive East.
     */
    public double magneticDeclinationDeg = 0.0;

    /**
     * Raw ADC scale factor to convert raw 16-bit counts to physical units.
     * <p>
     * Defaults are set for the <b>LSM303AGR</b> sensor used in SoundTrap SWV files:
     * <ul>
     *   <li><b>Accelerometer</b>: ±2 g full-scale, high-resolution mode →
     *       sensitivity = 1 mg/LSB = 0.001 × 9.80665 m/s² per LSB ≈ 9.80665e-3 m/s²/LSB</li>
     *   <li><b>Magnetometer</b>: full-scale ±50 gauss →
     *       sensitivity = 1.5 mGauss/LSB = 0.15 µT/LSB</li>
     * </ul>
     */
    public double accelLsbToMsq      = 9.80665e-3;   // m/s² per LSB  (LSM303AGR ±2 g HR)
    public double magLsbToMicroTesla = 0.15;           // µT  per LSB  (LSM303AGR)

    @Override
    public SudSensorParams clone() {
        try {
            return (SudSensorParams) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }
}
