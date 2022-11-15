package org.mpru.log;

/**
 * ILogger can output strings to the screen and/or file (or to optional ILogOut class instead) formatting
 * and caching them. Strings are formatted and printed asynchronously to provide very fast log() call
 * allowing applications to log tons of information without performance loss. But be aware, if the output
 * stream or file is slow and the message buffer is being filled much faster than the stream writing,
 * the log() call can wait the full buffer to be flushed.
 * @author Max Petrov
 */
public interface ILogger {
	
	static final byte FULL=127;
	static final byte TEXT=1;
	static final byte PREFIX=2;
	static final byte LEVEL=4;
	static final byte TIME=8;

	/**
	 * Sets logger and LogOuts properties. This method should not be called directly, but via ILog class.
	 * @param name
	 * @param value
	 */
	void setProperty(String name, String value);
	
	String getProperty(String name);
	
	void addLogOut(ILogOut logOut);
	void reportInternalError(String text, Exception ex);
	
	/**
	 * Logs text adding time, log level and optional prefix. This method should not be called directly, but via ILog class.
	 * @param prefix (optional) to be inserted before the text. Can be used to identify different threads or modules in one log
	 * @param text - text to log
	 * @param args - optional values referenced in text as parameters
	 * @param level - log level
	 * @param isScreen - whether to output the string to the StdOut/StdError (or ILogOut, if set)
	 * @param isFile - whether to output the string to the log file, if it is set
	 * @param deferMillis - defer message logging for deferMillis. The message will be logged
	 * in deferMillis milliseconds (checked every 1/3 second) if not cancelled 
	 * @return IMsg object used for cancelling (if deferMillis is used)
	 */
	ICancel log(String prefix, String text, byte level, Object[] args, boolean isScreen, boolean isFile, int deferMillis);
	
	/**
	 * Cancels deferred message
	 * @param msg - IMsg returned by {@link #log(String, String, byte, Object[], boolean, boolean, int)} call
	 * @return true if cancelled, false if already printed
	 */
	boolean cancel(ICancel msg);
	
	/**
	 * Prints deferred message now if not already printed
	 * @param msg - IMsg returned by {@link #log(String, String, byte, Object[], boolean, boolean, int)} call
	 */
	void now(ICancel msg);
	
	void appendText(Msg m, StringBuilder sb, byte type);
	byte getLevelByName(String level);
	String getLevelName(byte lev);
	String ansi(byte level, boolean isBegin);

	default boolean flush() { return false; }
	void close();
	
	boolean isAnsiColor();
	void stdPrint(String text, boolean isStdError);

}
