import java.util.logging.Logger;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogFormatter;

public class LoggerUtility {
	
	static private FileHandler logFileHandler;
    static private LogFormatter formatTxt;
    static private logger;
   
    static public void setup(String peerId) throws IOException {

            //Get the logger
            logger = Logger.getLogger(LoggerUtility.class.getName());

            //Remove the console handler
            logger.setUseParentHandlers(false);
            
            //Suppress the logging output to the console
            Logger rootLogger = Logger.*getLogger*("");
            Handler[] handlers = rootLogger.getHandlers();
            if (handlers[0] instanceof ConsoleHandler) {
                    rootLogger.removeHandler(handlers[0]);
            }

            //Set the logger level to Config
            logger.setLevel(Level.CONFIG);
            
            //Get the peer directory for which log should be written
            File peerDirectory = new File("peer_" + peerId);
            
            //If the peer directory doesn't exist then make the directory
            if (!peerDirectory.isDirectory())
            	peerDirectory.mkdir();
            
            //Initialize the file handler to the log file inside the peer directory
            logFileHandler = new FileHandler(peerDirectory.getPath() + File.separator + "log_peer_" + peerId + ".log");
           
            //Create an instance of custom LogFormatter class
            formatTxt = new LogFormatter();
            logFileHandler.setFormatter(formatTxt);
            
            //Add the File Handler to the logger
            logger.addHandler(logFileHandler);

    }
    
    //Get the logger object
    public static Logger getLogger()
    {
    	return logger;
    }
    
     
	
}
