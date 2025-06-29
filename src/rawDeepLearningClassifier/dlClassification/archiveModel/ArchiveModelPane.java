package rawDeepLearningClassifier.dlClassification.archiveModel;

import java.io.File;

import rawDeepLearningClassifier.DLStatus;
import rawDeepLearningClassifier.dlClassification.animalSpot.StandardModelPane;
import rawDeepLearningClassifier.dlClassification.animalSpot.StandardModelParams;
import rawDeepLearningClassifier.dlClassification.ketos.KetosDLParams;

public class ArchiveModelPane extends StandardModelPane {

	private ArchiveModelClassifier archiveModelClassifier;

	public ArchiveModelPane(ArchiveModelClassifier archiveModelClassifier) {
		super(archiveModelClassifier);
		this.archiveModelClassifier = archiveModelClassifier; 

	}

	@Override
	public DLStatus newModelSelected(File file) {
		
		//the model has to set some of the parameters for the UI . 
			
		//A ketos model contains information on the transforms, duration and the class names. 
		this.setCurrentSelectedFile(file);

		if (this.getParamsClone()==null) {
			this.setParamsClone(new StandardModelParams()); 
		}
		
			
		StandardModelParams params  = getParamsClone(); 
		
		
		//prep the model with current parameters; 
		
		/**
		 * Note that the model prep will determine whether new transforms need to be loaded from the 
		 * model or to use the existing transforms in the settings. 
		 */
		DLStatus status = archiveModelClassifier.getDLWorker().prepModel(params, archiveModelClassifier.getDLControl());
		
		//get the model transforms calculated from the model by the worker and apply them to our temporary params clone. 
		getParamsClone().dlTransfroms = this.archiveModelClassifier.getDLWorker().getModelTransforms(); 
		
//		if (getParamsClone().defaultSegmentLen!=null) {
//			usedefaultSeg.setSelected(true);
//		}
		
		///set the advanced pane parameters. 
		getAdvSettingsPane().setParams(getParamsClone());
		
		
		return status;
		
	}

}
