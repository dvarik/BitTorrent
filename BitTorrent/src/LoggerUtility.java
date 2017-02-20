import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerUtility {
	
	private FileHandler logFileHandler;
    
	private LogFormatter formatTxt;
    
    private final Logger logger = Logger.getLogger(LoggerUtility.class.getName());
    
	public LoggerUtility(int peerId) {
		
		//Remove the console handler
        logger.setUseParentHandlers(false);
        
        //Suppress the logging output to the console - to do

        Handler[] handlers = logger.getHandlers();
        if (handlers != null && handlers.length > 0 && handlers[0] instanceof ConsoleHandler) {
                logger.removeHandler(handlers[0]);
        }

        //Set the logger level to Config
        logger.setLevel(Level.CONFIG);
        
        //Get the peer directory for which log should be written
        File peerDirectory = new File("peer_" + peerId);
        
        //If the peer directory doesn't exist then make the directory
        if (!peerDirectory.isDirectory())
        	peerDirectory.mkdir();
        
        //Initialize the file handler to the log file inside the peer directory
        try {
			logFileHandler = new FileHandler(peerDirectory.getPath() + File.separator + "log_peer_" + peerId + ".log");
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
       
        //Create an instance of custom LogFormatter class
        formatTxt = new LogFormatter();
        logFileHandler.setFormatter(formatTxt);
        
        //Add the File Handler to the logger
        logger.addHandler(logFileHandler);
	}
    
	public void log(String message) {
        logger.info(message);
    } 
	
}
