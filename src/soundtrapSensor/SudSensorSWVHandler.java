package soundtrapSensor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.pamguard.x3.sud.Chunk;
import org.pamguard.x3.sud.IDSudar;
import org.pamguard.x3.sud.SudAudioInputStream;
import org.pamguard.x3.sud.WavFileHandler;

import Acquisition.AcquisitionControl;
import Acquisition.sud.SUDNotificationHandler;
import Acquisition.sud.SUDNotificationManager;
import PamController.PamController;

/**
 * Handles SWV (sensor-wav) chunks encountered during PAMGuard's SUD file
 * processing. SWV chunks are 6-channel, 16-bit PCM data where:
 * <ul>
 *   <li>Channels 0-2 are the three-axis magnetometer (mx, my, mz)</li>
 *   <li>Channels 3-5 are the three-axis accelerometer (ax, ay, az)</li>
 * </ul>
 * <p>
 * For each sample, raw values are calibrated using the parameters in
 * {@link SudSensorParams}, fed through the {@link SudSensorFusion} filter and
 * a {@link SudSensorDataUnit} is created and added to the
 * {@link SudSensorDataBlock}.
 *
 * @author PAMGuard
 */
public class SudSensorSWVHandler implements SUDNotificationHandler {

    /** Number of channels in a SWV file (3 mag + 3 accel). */
    private static final int N_CHANNELS = 6;
    /** Bytes per sample per channel (16-bit PCM). */
    private static final int BYTES_PER_SAMPLE = 2;
    /** Bytes per frame (all channels). */
    private static final int FRAME_BYTES = N_CHANNELS * BYTES_PER_SAMPLE;

    private final SudSensorControl sensorControl;

    /** Current stream, kept so we can query chunk IDs. */
    private SudAudioInputStream sudAudioInputStream;

    /** Sensor fusion filter; recreated when the stream changes. */
    private SudSensorFusion fusion;

    /** Sample rate of the SWV stream in Hz. */
    private double sampleRateHz = 25.0;

    /** UTC time of the first SWV chunk's first sample in milliseconds. */
    private long fileStartMillis = 0;

    /**
     * UTC time (ms) of the very last sample written during the previous file.
     * Used to decide whether the fusion filter should be reset or carried over
     * when a new file starts.
     */
    private long lastSampleMillis = 0;

    public SudSensorSWVHandler(SudSensorControl sensorControl) {
        this.sensorControl = sensorControl;
    }

    // -----------------------------------------------------------------
    // SUDNotificationHandler callbacks
    // -----------------------------------------------------------------

    @Override
    public void newSudInputStream(SudAudioInputStream sudAudioInputStream) {
        this.sudAudioInputStream = sudAudioInputStream;
        interpretNewFile(null, sudAudioInputStream);
    }

    @Override
    public void interpretNewFile(String newFile, SudAudioInputStream sudAudioInputStream) {
        this.sudAudioInputStream = sudAudioInputStream;

        long newStartMillis = 0;
        if (sudAudioInputStream != null && sudAudioInputStream.getSudMap() != null) {
            newStartMillis = sudAudioInputStream.getSudMap().getFirstChunkTimeMicros() / 1000L;
        }

        if (isContiguousWithPrevious(newStartMillis)) {
            // Gap is small enough – keep the existing fusion state so the
            // filter continues without a bedding-in spike.
            fileStartMillis = newStartMillis;
        } else {
            // First file, or gap too large – hard-reset the filter.
            fileStartMillis = newStartMillis;
            rebuildFusion(sampleRateHz);
        }
    }

    /**
     * Returns true if the new file starts within
     * {@link SudSensorParams#maxContiguousGapMs} of the last processed sample,
     * indicating that the fusion filter state is still valid.
     */
    private boolean isContiguousWithPrevious(long newStartMillis) {
        if (fusion == null || lastSampleMillis <= 0 || newStartMillis <= 0) {
            return false;
        }
        long gapMs = newStartMillis - lastSampleMillis;
        long maxGapMs = sensorControl.getParams().maxContiguousGapMs;
        return gapMs >= 0 && gapMs <= maxGapMs;
    }

    private void rebuildFusion(double fs) {
        this.sampleRateHz = fs;
        fusion = new SudSensorFusion(sensorControl.getParams(), fs);
    }

    @Override
    public void sudStreamClosed() {
        // nothing needed
    }

    @Override
    public void progress(double arg0, int arg1, int arg2) {
        // nothing needed
    }

    @Override
    public void chunkProcessed(int chunkID, Chunk sudChunk) {
        if (sudAudioInputStream == null) return;

        // Only interested in wav-type chunks with suffix "swv"
        String chunkType = sudAudioInputStream.getChunkIDString(chunkID);
        if (!"wav".equals(chunkType)) return;

        IDSudar handler = sudAudioInputStream.getChunkDataHandler(chunkID);
        if (handler == null || !(handler.dataHandler instanceof WavFileHandler)) return;

        WavFileHandler wavHandler = (WavFileHandler) handler.dataHandler;
        if (!"swv".equals(wavHandler.getFileSuffix())) return;

        // Get the chunk timestamp in milliseconds
        long chunkMillis = sudChunk.getChunkHeader().getMillisTime();
        if (chunkMillis <= 0) chunkMillis = fileStartMillis;

        processSwvChunk(sudChunk.buffer, chunkMillis, wavHandler);
    }

    // -----------------------------------------------------------------
    // SWV data processing
    // -----------------------------------------------------------------

    /**
     * Decode a raw SWV chunk buffer, run sensor fusion on each frame and produce
     * {@link SudSensorDataUnit} objects.
     */
    private void processSwvChunk(byte[] buffer, long chunkStartMillis,
                                   WavFileHandler wavHandler) {
        if (buffer == null || buffer.length < FRAME_BYTES) return;

        // Determine the sample rate from the wav handler if possible
        float fs = wavHandler.getSampleRate();
        if (fs > 0 && fs != (float) sampleRateHz) {
            rebuildFusion(fs);
        }

        int nFrames = buffer.length / FRAME_BYTES;
        double dtMs = (sampleRateHz > 0) ? (1000.0 / sampleRateHz) : 40.0;

        SudSensorParams params = sensorControl.getParams();
        SudSensorDataBlock dataBlock = sensorControl.getSensorProcess().getSensorDataBlock();

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buffer));

        for (int i = 0; i < nFrames; i++) {
            try {
                // SWV channel layout (LSM303AGR):
                //   ch 0 = accel X,  ch 1 = accel Y,  ch 2 = accel Z
                //   ch 3 = mag   X,  ch 4 = mag   Y,  ch 5 = mag   Z
                // Each sample is a signed 16-bit little-endian integer.
                short rawAx = Short.reverseBytes(dis.readShort());
                short rawAy = Short.reverseBytes(dis.readShort());
                short rawAz = Short.reverseBytes(dis.readShort());
                short rawMx = Short.reverseBytes(dis.readShort());
                short rawMy = Short.reverseBytes(dis.readShort());
                short rawMz = Short.reverseBytes(dis.readShort());

                // Apply calibration
                double ax = (rawAx - params.accelOffsetX) * params.accelLsbToMsq;
                double ay = (rawAy - params.accelOffsetY) * params.accelLsbToMsq;
                double az = (rawAz - params.accelOffsetZ) * params.accelLsbToMsq;

                double mx = (rawMx - params.magOffsetX) * params.magScaleX * params.magLsbToMicroTesla;
                double my = (rawMy - params.magOffsetY) * params.magScaleY * params.magLsbToMicroTesla;
                double mz = (rawMz - params.magOffsetZ) * params.magScaleZ * params.magLsbToMicroTesla;

                // Sensor fusion
                fusion.update(ax, ay, az, mx, my, mz);

                long sampleMillis = chunkStartMillis + Math.round(i * dtMs);

                SudSensorDataUnit du = new SudSensorDataUnit(
                        sampleMillis,
                        mx, my, mz,
                        ax, ay, az,
                        fusion.getHeadingDeg(),
                        fusion.getPitchDeg(),
                        fusion.getRollDeg());

                dataBlock.addPamData(du);
                lastSampleMillis = sampleMillis;

            } catch (IOException e) {
                break;
            }
        }
    }

    // -----------------------------------------------------------------
    // Subscription management
    // -----------------------------------------------------------------

    /**
     * Subscribe this handler to the main acquisition module's SUD notification
     * manager. Safe to call multiple times.
     *
     * @return true if subscription succeeded
     */
    public boolean subscribeSUD() {
        if (sensorControl.isViewer()) return false;
        AcquisitionControl daq = (AcquisitionControl) PamController.getInstance()
                .findControlledUnit(AcquisitionControl.unitType);
        if (daq == null) return false;
        SUDNotificationManager sudManager = daq.getSUDNotificationManager();
        if (sudManager == null) return false;
        sudManager.addNotificationHandler(this);
        return true;
    }

    public void pamStart() {
        subscribeSUD();
    }

    public void pamStop() {
        lastSampleMillis = 0;
    }
}
