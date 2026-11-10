package offlineProcessing;

import java.util.ArrayList;

import PamController.PamConfiguration;
import PamController.PamControlledUnit;
import PamController.PamController;
import PamController.PamGUIManager;
import PamUtils.PamCalendar;
import PamguardMVC.PamDataBlock;

/**
 * Some global management of offline tasks / task groups. 
 * @author dg50
 *
 */
public class OfflineTaskManager implements TaskMonitor {
	
	private static OfflineTaskManager singleInstance = null;
	
//	private ArrayList<OfflineTask> globalTaskList = new ArrayList();
	
	public static final String commandFlag = "-offlinetask";
	
	public ArrayList<String> commandLineTasks = new ArrayList();

	private BatchWorker worker;
	
	public static OfflineTaskManager getManager() {
		if (singleInstance == null) {
			singleInstance = new OfflineTaskManager();
		}
		return singleInstance;
	}
	
	/**
	 * Get a list of ALL offline tasks registered with all modules 
	 * in all of PAMGUard. <p>
	 * N.B. Many tasks won't be registered. 
	 * @return list of all offline tasks
	 */
	public ArrayList<OfflineTask> getAllOfflineTasks() {
//		ArrayList<OfflineTask> allTasks = new ArrayList<>();
//		PamController pamController = PamController.getInstance();
//		int n = pamController.getNumControlledUnits();
//		for (int i = 0; i < n; i++) {
//			PamControlledUnit pcu = pamController.getControlledUnit(i);
//			ArrayList<OfflineTask> unitTasks = pcu.getOfflineTasks();
//			if (unitTasks != null) {
//				allTasks.addAll(unitTasks);
//			}
//		}
//		return allTasks;
		PamConfiguration mainConfig = PamController.getInstance().getPamConfiguration();
		ArrayList<OfflineTask> tasks = mainConfig.getAllOfflineTasks();
		return tasks;
	}
	
	/**
	 * Get a list of all tasks in the system which have the given parent data block
	 * @param taskParentDataBlock parent data block for tasks
	 * @return list of available tasks. 
	 */
	public ArrayList<OfflineTask> getOfflineTasks(PamDataBlock taskParentDataBlock) {
		ArrayList<OfflineTask> allTasks = getAllOfflineTasks();
		ArrayList<OfflineTask> wantedTasks = new ArrayList<>();
		for (OfflineTask task : allTasks) {
			if (task == null) continue;
			if (task.getDataBlock() == taskParentDataBlock) {
				wantedTasks.add(task);
			}
		}
		return wantedTasks;
	}
	
	/**
	 * Get a list of all tasks in the system which associate with the given PAMGuard module
	 * @param pamControlledUnit parent PAMGuard module
	 * @return list of available tasks. 
	 */
	public ArrayList<OfflineTask> getOfflineTasks(PamControlledUnit pamControlledUnit) {
		ArrayList<OfflineTask> allTasks = getAllOfflineTasks();
		ArrayList<OfflineTask> wantedTasks = new ArrayList<>();
		for (OfflineTask task : allTasks) {
			if (task == null) continue;
			if (task.getTaskControlledUnit() == pamControlledUnit) {
				wantedTasks.add(task);
			}
		}
		return wantedTasks;
	}
	
	/**
	 * Add all available tasks from the system which use the given datablock 
	 * as primary input. 
	 * @param taskGroup Task group to add tasks to
	 * @param taskParentDataBlock parent data block
	 * @return number of tasks added
	 */
	public int addAvailableTasks(OfflineTaskGroup taskGroup, PamDataBlock taskParentDataBlock) {
		ArrayList<OfflineTask> tasks = getOfflineTasks(taskParentDataBlock);
		// check the tasks are not already in the list. 
		return taskGroup.addTasks(tasks);
	}

//	/**
//	 * Register a task in the global list. It's possible some tasks might get 
//	 * recreated, in which case when registered they will replace the previous one
//	 * This will cause trouble if two separate tasks have the same name, but that should 
//	 * not be possible. 
//	 * @param offlineTask
//	 */
//	public void registerTask(OfflineTask offlineTask) {
//		// if it exists, replace it. 
//		OfflineTask existingTask = findOfflineTask(offlineTask);
//		if (existingTask != null) {
//			int ind = globalTaskList.indexOf(existingTask);
//			globalTaskList.set(ind, offlineTask);
//		}
//		else {
//			globalTaskList.add(offlineTask);
//		}
//	}

	/**
	 * find a task with the same module type, module name and task name. This should 
	 * be enough to uniquely identify every task. 
	 * @param offlineTask
	 * @return matching task or null. 
	 */
	@SuppressWarnings("rawtypes")
	public OfflineTask findOfflineTask(OfflineTask offlineTask) {
		return findOfflineTask(offlineTask.getLongName());
	}
	
	/**
	 * Find a registered task based on it's module type, module name and task name. This should 
	 * be enough to uniquely identify every task. 
	 * @param unitType
	 * @param unitName
	 * @param taskName
	 * @return matching task or null. 
	 */
	@SuppressWarnings("rawtypes")
	public OfflineTask findOfflineTask(String unitType, String unitName, String taskName) {
		// could possibly also do a check on class type ????
		ArrayList<OfflineTask> globalTaskList = getAllOfflineTasks();
		for (OfflineTask aTask : globalTaskList) {
			if (!aTask.getUnitType().equals(unitType)) {
				continue;
			}
			if (!aTask.getUnitName().equals(unitName)) {
				continue;
			}
			if (aTask.getName().equals(taskName)) {
				return aTask;
			}
		}
		return null;
	}
	
	/**
	 * Another way of finding offline tasks based on their long name. This is basically
	 * the three names unitType, unitName and taskName concatenated together. Get's used
	 * for some task management such as passing batch processing instructions. 
	 * @param taskLongName 
	 * @return matching task or null. 
	 */
	@SuppressWarnings("rawtypes")
	public OfflineTask findOfflineTask(String taskLongName) {
		ArrayList<OfflineTask> globalTaskList = getAllOfflineTasks();
		for (OfflineTask aTask : globalTaskList) {
			if (aTask.getLongName().equals(taskLongName)) {
				return aTask;
			}
		}
		return null;
	}

	/**
	 * Add a task listed in the command line when PAMGuard was started. 
	 * @param taskLongName
	 */
	public void addCommandLineTask(String taskLongName) {
		commandLineTasks.add(taskLongName);
	}

	/**
	 * The list of tasks from the command line. 
	 * @return the commandLineTasks
	 */
	public ArrayList<String> getCommandLineTasks() {
		return commandLineTasks;
	}

	/**
	 * Get the status of jobs to pass back to the batch process controller. 
	 * @return
	 */
	public String getBatchStatus() {
		/**
		 * this needs to largely follow the format of the data in folderinputsystem:
		 * status string is nFiles, currentFile, generalStatus from PamController and current file as string. 
		 * String bs = String.format("%d,%d,%d,%s", nFiles,currentFile,generalStatus,currFile);
		 */
		// copy ref in case it's changed while doing this. This is enough to make it thread sfe
		TaskMonitorData tmd = latestTaskData;
		String bs;
		if (tmd == null) {
			bs = String.format("%d,%d,%d,%s", commandLineTasks.size(), 0, PamController.PAM_INITIALISING, "Initialising");
		}
		else {
			int nGroup = worker.usedGroups.size();
			int doneGroup = worker.currentGroup;
			if (doneGroup == nGroup && tmd.taskStatus == TaskStatus.COMPLETE) {
				bs = String.format("%d,%d,%d,%s", nGroup, nGroup, PamController.PAM_IDLE, "Jobs complete");
			}
			else {
				// work it out as a percentage of total work. 
				int maxV = Math.max(1, tmd.progMaximum);
				int curr = tmd.progValue;
				curr += doneGroup*maxV;
				maxV *= nGroup;
				bs = String.format("%d,%d,%d,%s", maxV, curr, PamController.PAM_RUNNING, "Running offline tasks");				
			}
		}
//		int generalStatus = PamController.getInstance().getRealStatus();
//		String bs = String.format("%d,%d,%d,%s", commandLineTasks.size(), 0, generalStatus, "Processing");
		return bs;
	}

	/**
	 * Organise and start processing offline tasks created for batck processing. 
	 */
	public void startBatchTasks() {
		/*
		 * Nothing to do here: the tasks are launched once the viewer has loaded its first
		 * data (see PamController.notifyModelChanged and pamguard.cli.ViewerCommandLine). 
		 */
	}

	/**
	 * Called from controller at end of first data load when PAMGuard is in viewer batch processing
	 * mode. Will launch a new thread to work it's way through all batch jobs. 
	 */
	public void launchOfflineBatchTasks() {
		Thread t = new Thread(() -> runCommandLineTasks(), "Offline batch tasks");
		t.start();
	}

	/**
	 * Run the tasks listed on the command line (-offlinetask) and wait for them all
	 * to finish. With a GUI each group of tasks is run in the standard offline 
	 * processing dialog; without one (-nogui, or no display) they are run directly, 
	 * with progress written to the console. Must not be called on the AWT thread.
	 * @return true if every task completed. 
	 */
	public boolean runCommandLineTasks() {
		ArrayList<OfflineTaskGroup> usedGroups = prepareCommandLineGroups();
		worker = new BatchWorker(usedGroups);
		return worker.runAll();
	}

	/**
	 * Find the groups for all tasks listed on the command line and select just those 
	 * tasks within each group. 
	 * @return list of task groups. 
	 */
	private ArrayList<OfflineTaskGroup> prepareCommandLineGroups() {
		/*
		 * Start by making a list of task groups that have one or more tasks. 
		 */
		ArrayList<OfflineTaskGroup> usedGroups = new ArrayList<>();
		for (String taskCmd: commandLineTasks) {
			OfflineTask task = findOfflineTask(taskCmd);
			if (task == null) {
				String msg = "Unable to find offline task " + taskCmd + ". Use the listtasks command or -describe to list the available tasks";
				System.out.println(msg);
				pamguard.cli.RunSummary.getInstance().addError(msg);
				pamguard.cli.RunSummary.getInstance().setExitCode(pamguard.cli.ExitCode.TASK_FAILED);
				continue;
			}
			System.out.println("Prepare offlinetask " + taskCmd + " " + task);
			OfflineTaskGroup taskGroup = task.getOfflineTaskGroup();
			if (usedGroups.contains(taskGroup) == false) {
				usedGroups.add(taskGroup);
			}
		}
		/*
		 *  then need to go through all tasks in each group and based on their index
		 *  eithr enable them or disable them. Must actively disable unused ones. 
		 */
		for (int g = 0; g < usedGroups.size(); g++) {
			OfflineTaskGroup taskGroup = usedGroups.get(g);
			TaskGroupParams groupParams = taskGroup.getTaskGroupParams();
			groupParams.dataChoice = TaskGroupParams.PROCESS_ALL;
			groupParams.deleteOld = true;
			groupParams.taskNote = "Batch process at " + PamCalendar.formatDBDateTime(System.currentTimeMillis());
			int nT = taskGroup.getNTasks();
			for (int i = 0; i < nT; i++) {
				OfflineTask task = taskGroup.getTask(i);
				String taskName = task.getLongName();
				boolean enable =  commandLineTasks.contains(taskName);
				groupParams.setTaskSelection(i, enable);
				task.setDoRun(enable);
				System.out.printf("Set task enable state for %s : %s in %s\n", taskName, enable, taskGroup.getUnitType());
			}
			
			System.out.println("Tasks in group is now " + taskGroup.getNTasks());
		}
		/*
		 *  With a GUI, use the standard dialog with a few mods: every control disabled 
		 *  and a secondary monitor that passes messages from the tasks back to here so
		 *  they can be packaged up and given to the batch processor as status messages. 
		 *  Batch status messages are requested with a multicast broadcast of batchstatuscommand
		 *  from the main controller, to which the running PAMGuards reply with a String 
		 *  (see getBatchStatus). When status is changed from processing to closed, Batch 
		 *  Control closes this instance and starts the next job.
		 */
		return usedGroups;
	}

	/**
	 * Run a task group without a dialog, waiting for it to finish. Progress is 
	 * written to the console. Must not be called on the AWT thread. 
	 * @param taskGroup task group 
	 * @param monitor additional monitor to receive progress, can be null. 
	 * @return the completion status of the group
	 */
	public static TaskStatus runTaskGroupAndWait(OfflineTaskGroup taskGroup, TaskMonitor monitor) {
		java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
		TaskStatus[] finalStatus = new TaskStatus[1];
		long[] lastPrint = new long[1];
		taskGroup.setTaskMonitor(new TaskMonitor() {
			@Override
			public void setTaskStatus(TaskMonitorData tmd) {
				if (monitor != null) {
					monitor.setTaskStatus(tmd);
				}
				if (tmd == null || tmd.taskStatus == null) {
					return;
				}
				long now = System.currentTimeMillis();
				if (now - lastPrint[0] > 5000) {
					lastPrint[0] = now;
					System.out.printf("Offline task %s: %s %s %d of %d\n", taskGroup.getUnitName(), tmd.taskStatus, 
							tmd.fileOrStatus == null ? "" : tmd.fileOrStatus, tmd.progValue, tmd.progMaximum);
				}
				switch (tmd.taskStatus) {
				case COMPLETE:
				case CRASHED:
				case INTERRUPTED:
					finalStatus[0] = tmd.taskStatus;
					latch.countDown();
					break;
				default:
					break;
				}
			}
		});
		taskGroup.runTasks();
		try {
			latch.await();
		} catch (InterruptedException e) {
			return TaskStatus.INTERRUPTED;
		}
		System.out.printf("Offline task group %s finished: %s\n", taskGroup.getUnitName(), finalStatus[0]);
		return finalStatus[0];
	}
	
	private class BatchWorker {

		private ArrayList<OfflineTaskGroup> usedGroups;
		private volatile int currentGroup;
		private OLProcessDialog olDialog;

		public BatchWorker(ArrayList<OfflineTaskGroup> usedGroups) {
			this.usedGroups = usedGroups;
		}

		public boolean runAll() {
			boolean headless = PamGUIManager.getGUIType() == PamGUIManager.NOGUI 
					|| java.awt.GraphicsEnvironment.isHeadless();
			boolean allOK = true;
			for (int i = 0; i < usedGroups.size(); i++) {
				OfflineTaskGroup group = usedGroups.get(i);
				if (headless) {
					TaskStatus status = runTaskGroupAndWait(group, OfflineTaskManager.this);
					if (status != TaskStatus.COMPLETE) {
						allOK = false;
						pamguard.cli.RunSummary.getInstance().addError("Offline task group " + group.getUnitName() + " finished with status " + status);
						pamguard.cli.RunSummary.getInstance().setExitCode(pamguard.cli.ExitCode.TASK_FAILED);
					}
				}
				else {
					olDialog = new OLProcessDialog(PamController.getMainFrame(), group, "Offline batch processing", 
							true, OfflineTaskManager.this);
					olDialog.setVisible(true);
					if (group.getCompletionStatus() != TaskStatus.COMPLETE) {
						allOK = false;
					}
				}
				currentGroup++;
			}
			return allOK;
		}
		
	}
	
	private volatile TaskMonitorData latestTaskData;

	@Override
	public void setTaskStatus(TaskMonitorData taskMonitorData) {
		latestTaskData = taskMonitorData;
//		System.out.println("Task monitor message " + taskMonitorData.taskStatus);
		if (taskMonitorData.taskStatus == TaskStatus.COMPLETE) {
			if (worker.olDialog != null) {
				worker.olDialog.setVisible(false);
			}
			if (worker.currentGroup >= worker.usedGroups.size()) {
				System.out.println(" all batch tasks complete.");
			}
		}
//		System.out.println("Status string " + getBatchStatus());
	}
}
