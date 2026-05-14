package soundtrapSensor;

import PamguardMVC.PamDataBlock;
import PamguardMVC.PamProcess;

/**
 * Data block that holds {@link SudSensorDataUnit} objects generated from the
 * SWV sensor stream inside SoundTrap SUD files.
 *
 * The datagram is provided by {@link SudSensorDatagramProvider}, registered
 * via {@link #setDatagramProvider(dataGram.DatagramProvider)} from
 * {@link SudSensorProcess}.
 *
 * @author PAMGuard
 */
public class SudSensorDataBlock extends PamDataBlock<SudSensorDataUnit> {

    private SudSensorBinaryStore binaryStore;

    public SudSensorDataBlock(PamProcess parentProcess) {
        super(SudSensorDataUnit.class, "SUD Sensor Data", parentProcess, 0);
        binaryStore = new SudSensorBinaryStore(this);
        setBinaryDataSource(binaryStore);
    }

    public SudSensorBinaryStore getBinaryStore() {
        return binaryStore;
    }
}