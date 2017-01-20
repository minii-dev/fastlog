package org.mpru.log;

/**
 * The classes implementing this interface are to be used by applications to log data.
 * 
 * @author Max Petrov
 *
 */
public interface ILog {

	// Log levels to be used in isLog/getLevel/setLevel and log/logDefer calls 
	static byte USE_SAME_LEVEL=100;
	static byte TRACE=60;
	static byte TRACE_NOH=59;	
	static byte DEBUG=50;
	static byte DEBUG_NOH=49;
	static byte MOREINFO=40;
	static byte MOREINFO_NOH=39;
	static byte INFO=30;
	static byte WARN=20;
	static byte WARN_NOH=19;
	static byte ERROR=10;
	static byte QUIET=4;

	// Log property names
	static final String LOG_PATH = "path";
	static final String LOG_PATH1 = "logPath";
	static final String FILE_LOG_LEVEL = "fileLevel";
	static final String LOG_LEVEL = "level";
	static final String LOG_LEVEL1 = "logLevel";
	static final String LOG_TIME_MILLIS = "logMillis";
	static final String ALLOW_ANSI_CODES = "allowAnsiCodes";

	ILogger getLogger();
	
	/**
	 * Creates new ILog as peer of this ILog (having the same parent and default log levels)
	 * or a child of this ILog (in this case the message printed to the child
	 * ILog will not be shown only when both the parent and the child ILogs do not permit logging. This allows to,
	 * for instance, set "debug" level for the whole application (set the parent level "debug") or for its part only (set the child level "debug")).
	 * In any case, the log file is shared between ILogs.
	 * @param isAsChild
	 * @return new ILog object
	 */
	ILog newLog(boolean isAsChild);
	
	ILog getParent();
	
	/**
	 * Returns log level (a number) by its name
	 * @param level
	 * @return log level
	 */
	byte getLevelByName(String level);
	
	/**
	 * Returns log level of this ILog. Parent level is ignored(!) so this is not the same as calling {@link #isLog(byte)}, because
	 * the latter checks the parent level too.
	 * @return log level
	 */
	byte getLevel();
	
	/**
	 * Returns file log level of this ILog. Parent file level is ignored(!) so this is not the same as calling {@link #isLog(byte)}, because
	 * the latter checks the parent level too.
	 * @return log level
	 */
	byte getFileLevel();
	
	/**
	 * Gets property value by the property name, such as ILog.LOG_PATH, ILog.LOG_LEVEL and so on.
	 * @param name - property name. See ILog class for names
	 * @return property value or null if unknown or not set property
	 */
	String getProperty(String name);
	
	/**
	 * Gets this ILog prefix, which is added before each log line to distinguish different logical application parts in one log file.
	 * See {@link #setPrefix(String)}
	 * @return a prefix or null
	 */
	String getPrefix();

	void setLevel(byte level);
	void setFileLevel(byte level);
	void setProperty(String name, String value);
	
	/**
	 * Sets this ILog prefix, which is added before each log line to distinguish different logical application parts in one log file.
	 * See {@link #getPrefix()}
	 * @param prefix - any string. For instance, application thread number or id. By default the prefix is null
	 */
	void setPrefix(String prefix);

	/**
	 * Checks if the message with specified level will be logged (to the console and/or to the file). This ILog and parents (if any) log levels
	 * and file levels are checked.
	 * @param level
	 * @return true if it will be logged
	 */
	boolean isLog(byte level);
	
	/**
	 * The same as isLog(ILog.TRACE)
	 * @return true if TRACE level is logged
	 */
	boolean isTrace();
	
	/**
	 * The same as isLog(ILog.DEBUG)
	 * @return true if DEBUG level is logged
	 */
	boolean isDebug();
	
	/**
	 * The same as isLog(ILog.MOREINFO)
	 * @return true if MOREINFO level is logged
	 */
	boolean isMoreInfo();
	
	/**
	 * Logs the message (or adds to the deferred queue when deferMillis>0) if the level is permitted.
	 * @param deferMillis - when>0, logs the message not now, but in deferMillis milliseconds. Can be cancelled in this case
	 * @param txt - message text. Parameter placeholders as {N} or $N (N is the number starting from 0. Only one placeholder type can be used in
	 * one message) can be used. In this case, args - is a list of parameter values
	 * @param level - log level
	 * @param args - parameter values. Used only when txt has Parameter placeholders
	 * @return ICancel object (if deferMillis>0) which can be used in {@link #cancel(ICancel)} or {@link #now(ICancel)} calls
	 */
	ICancel logDefer(int deferMillis, String txt, byte level, Object...args);

	/**
	 * Cancels the message logged as deferred message (see {@link #logDefer(int, String, byte, Object...)}).
	 * See the documentation for usage examples.
	 * @param cancel - the object returned by logDefer call
	 * @return true if cancelled successfully, false if the message has been already printed
	 */
	boolean cancel(ICancel cancel);
	
	/**
	 * If the deferred message has not been already printed, prints it now (see {@link #logDefer(int, String, byte, Object...)}).
	 * @param cancel - the object returned by logDefer call
	 */
	void now(ICancel cancel);

	/**
	 * Logs the message. See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt
	 * @param level
	 * @param args
	 */
	void log(String txt, byte level, Object...args);
	
	/**
	 * Logs the message. The exception is printed with the full stack (see Log.getExceptionText).
	 * See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt
	 * @param exception 
	 * @param level
	 * @param args
	 */
	void log(String txt, Throwable exception, byte level, Object...args);
	
	/**
	 * Logs the message. See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt
	 * @param args 
	 */
	void trace(String txt, Object...args);
	
	/**
	 * Logs the message. See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt
	 * @param args 
	 */
	void debug(String txt, Object...args);
	
	/**
	 * Logs the message. See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt
	 * @param args 
	 */
	void moreInfo(String txt, Object...args);
	
	/**
	 * Logs the message. See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt
	 * @param args 
	 */
	void info(String txt, Object...args);
	
	/**
	 * Logs the message. See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt
	 * @param args 
	 */
	void warn(String txt, Object...args);
	
	/**
	 * Logs the message. See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt
	 * @param args 
	 */
	void error(String txt, Object...args);
	
	/**
	 * Logs the message. The exception is printed with the full stack (see Log.getExceptionText).
	 * See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param exception 
	 */
	void error(Throwable exception);
	
	/**
	 * Logs the message. The exception is printed with the full stack (see Log.getExceptionText).
	 * See {@link #logDefer(int, String, byte, Object...)} for description and parameters
	 * @param txt 
	 * @param exception 
	 * @param args 
	 */
	void error(String txt, Throwable exception, Object...args);
	
}
