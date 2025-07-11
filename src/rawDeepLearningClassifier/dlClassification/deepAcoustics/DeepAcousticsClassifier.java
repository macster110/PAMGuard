package rawDeepLearningClassifier.dlClassification.deepAcoustics;

import java.util.ArrayList;
import java.util.List;

import org.jamdev.jdl4pam.deepAcoustics.DeepAcousticResultArray;

import PamguardMVC.PamDataUnit;
import rawDeepLearningClassifier.DLControl;
import rawDeepLearningClassifier.dlClassification.PredictionResult;
import rawDeepLearningClassifier.dlClassification.animalSpot.StandardModelParams;
import rawDeepLearningClassifier.dlClassification.archiveModel.ArchiveModelClassifier;
import rawDeepLearningClassifier.dlClassification.archiveModel.ArchiveModelWorker;
import rawDeepLearningClassifier.dlClassification.genericModel.GenericDLClassifier;
import rawDeepLearningClassifier.dlClassification.genericModel.StandardPrediction;


/**
 * A classifier based on the Deep Acoustics method which uses object detection models within spectrograms to predict
 * dolphin whistle detections and then classify to species.. 
 * 
 * @author Jamie Macaulay
 *
 */
public class DeepAcousticsClassifier extends ArchiveModelClassifier {

	public static String MODEL_NAME = "DeepAcoustics";


	public DeepAcousticsClassifier(DLControl dlControl) {
		super(dlControl);
		archiveModelUI = new DeepAcousticsUI(this);
	}


	@Override
	public String getName() {
		//important because this is used to identify model from JSON file
		return MODEL_NAME;
	}

	@Override
	public StandardModelParams makeParams() {
		return new DeepAcousticParams();
	}


	/**
	 * Get the KetosWorker. this handles loading and running the Ketos model. 
	 * @return the Ketos worker. 
	 */
	public ArchiveModelWorker getModelWorker() {
		if (archiveModelWorker==null) {
			archiveModelWorker= new DeepAcousticsWorker(); 
		}
		return archiveModelWorker;
	}


	@Override
	protected  ArrayList<ArrayList<? extends PredictionResult>>  processModelResults(ArrayList<? extends PamDataUnit> groupedRawData, List<StandardPrediction> modelResult) {
		System.out.println("DeepAcousticsClassifier: processModelResults called with " + modelResult.size() + " results for " + groupedRawData.size() + " segments.");
		//the main difference between deepAcoustics and most other models is that multiple results can be returned per segment. 
		//Therefore the number of prediction does not correspond to the number of input data units
		DeepAcousticsPrediction deepAcousticsPrediction;
		
		//so to calculate the time we need the start time of the segment, the position of the bounding box in the segment and the segment length.

		int imWidth = 100;
		long boxMillis;
		long boxSamples;
		
		ArrayList<ArrayList<? extends PredictionResult>>  processedResults = new ArrayList<ArrayList<? extends PredictionResult>>();
		//iterate through segments and results, matching the segment UID to the parent segment ID in the result.
		for (PamDataUnit dataUnit : groupedRawData) {

			ArrayList<DeepAcousticsPrediction> aSegmentResult = new ArrayList<DeepAcousticsPrediction>();
			for (int i=0; i<modelResult.size(); i++) {
				
				deepAcousticsPrediction = (DeepAcousticsPrediction) modelResult.get(i);
				deepAcousticsPrediction.setClassNameID(GenericDLClassifier.getClassNameIDs(getDLParams())); 
				deepAcousticsPrediction.setBinaryClassification(isDecision(modelResult.get(i), getDLParams())); 
				
				
				
				if (dataUnit.getUID() == deepAcousticsPrediction.getParentSegmentID()) {
					//we have the correct segment for the bounding box. Now need to set the correct absolute sample and datetime values

					boxMillis = DeepAcousticResultArray.calcBoundingBoxMillis(dataUnit.getTimeMilliseconds(), dataUnit.getDurationInMilliseconds(), deepAcousticsPrediction.getResult(),  imWidth);
					boxSamples = DeepAcousticResultArray.calcBoundingBoxMillis(dataUnit.getStartSample(), dataUnit.getSampleDuration(), deepAcousticsPrediction.getResult(),  imWidth);
					
					deepAcousticsPrediction.setTimeMillis(boxMillis);

					deepAcousticsPrediction.setStartSample(boxSamples);
					deepAcousticsPrediction.setDuratioSamples((int) (((double) deepAcousticsPrediction.getResult().getWidth())/imWidth * dataUnit.getSampleDuration()));
					
					aSegmentResult.add(deepAcousticsPrediction);

					break;
				}
			}
			
			processedResults.add(aSegmentResult);
			
			//TODO
			//now the model results UID should be the UID of the input data. Also, the bounding box will have frequency and time limits and these need to be set. 
			//deepAcousticsPrediction.setTimeMillis(deepAcousticsPrediction.startTimeMillis);
		}
		
	
		return processedResults;
	}

}






