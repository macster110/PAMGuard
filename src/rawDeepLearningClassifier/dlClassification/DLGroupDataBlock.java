package rawDeepLearningClassifier.dlClassification;

import PamguardMVC.PamDataUnit;
import PamguardMVC.superdet.SuperDetDataBlock;
import clickTrainDetector.CTDataUnit;

/**
 * 
 * Data block which holds deep learning detections derived from groups of data units.
 */
public class DLGroupDataBlock extends SuperDetDataBlock<DLGroupDetection, PamDataUnit> {

	private DLClassifyProcess dlClassifyProcess;

	public DLGroupDataBlock(DLClassifyProcess parentProcess, String name, int channelMap) {
		super(CTDataUnit.class, name, parentProcess, channelMap, SuperDetDataBlock.ViewerLoadPolicy.LOAD_OVERLAPTIME);
		this.dlClassifyProcess = parentProcess;
		
		
	}


}