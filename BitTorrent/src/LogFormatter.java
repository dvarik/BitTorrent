import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
	
	public String format(LogRecord record) {
		
        StringBuilder builder = new StringBuilder(1000);
        String timeStamp = calcTimeStamp(record.getMillis());
        builder.append(timeStamp);
        builder.append(" ");
        builder.append(record.getMessage());
        builder.append("\n");
        
        return builder.toString();
    }     
	
	private String calcTimeStamp(long milliSeconds)
	{
		DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss.SSS");
		Date tstamp = new Date(milliSeconds);
		return formatter.format(tstamp);
	}
	
}
