package PamController.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import PamController.PamController;

public class TerminalController extends CommandManager {

	private BufferedReader reader;

	private static String unitName = "Terminal Controller";
	
	private static TerminalController instance;

	public TerminalController(PamController pamController) {
		super(pamController, unitName);
		instance = this;
	}

	/**
	 * @return the controller for commands typed in the terminal (may be null). 
	 */
	public static TerminalController getInstance() {
		return instance;
	}

	@Override
	public boolean sendData(ExtCommand extcommand, String dataString) {
		System.out.println(dataString);
		return true;
	}

	@Override
	public void pamClose() {
		super.pamClose();
		try {
			if (reader != null) {
				reader.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void getTerminalCommands() {
		if (pamguard.cli.TerminalDisplay.isActive()) {
			// the terminal display reads the keyboard, and passes on commands typed after ':' 
			return;
		}
		Thread t = new Thread(new TerminalTread());
		t.setDaemon(true); // don't stop the JVM from exiting at the end of batch processing
		t.start();
	}
	
	private class TerminalTread implements Runnable {

		@Override
		public void run() {
			readCommands();
		}
		
	}
	
	private void readCommands() {

        reader = new BufferedReader(
        		new InputStreamReader(System.in));
        try {
        	while (true) {
        		String command = reader.readLine();
        		if (command == null) {
        			/*
        			 * End of stream - stdin is closed (e.g. batch / container run with no
        			 * terminal attached). Without this check the loop spins at 100% CPU.
        			 */
        			break;
        		}
        		if (command.length() > 0) {
        			interpretCommand(command);
        		}
//        		System.out.println("you typed: " + inLine);
//        		if (inLine.contains("exit")) {
//        			break;
//        		}
        	}
        } catch (IOException e) {
        	e.printStackTrace();
        }
        System.out.println("Exiting PAMGuard, leave control thread");
	}
}
