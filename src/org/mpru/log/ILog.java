package org.mpru.log;

/**
 * The classes implementing this interface are to be used by applications to log data.
 * 
 * @author Max Petrov
 *
 */
public interface ILog {

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

	static final String LOG_PATH = "path";
	static final String LOG_PATH1 = "logPath";
	static final String FILE_LOG_LEVEL = "fileLevel";
	static final String LOG_LEVEL = "level";
	static final String LOG_LEVEL1 = "logLevel";
	static final String LOG_TIME_MILLIS = "logMillis";
	static final String ALLOW_ANSI_CODES = "allowAnsiCodes";

	ILogger getLogger();
	ILog newLog(boolean isAsChild);
	ILog getParent();
	byte getLevelByName(String level);
	byte getLevel();
	byte getFileLevel();
	String getProperty(String name);
	String getPrefix();

	void setLevel(byte level);
	void setFileLevel(byte level);
	void setProperty(String name, String value);
	void setPrefix(String prefix);

	boolean isLog(byte level);
	boolean isTrace();
	boolean isDebug();
	boolean isMoreInfo();
	
	ICancel logDefer(int deferMillis, String txt, byte level, Object...args);
	boolean cancel(ICancel cancel);

	void log(String txt, byte level, Object...args);
	void log(String txt, Throwable t, byte level, Object...args);
	
	void trace(String txt, Object...args);
	void debug(String txt, Object...args);
	void moreInfo(String txt, Object...args);
	void info(String txt, Object...args);
	void warn(String txt, Object...args);
	void error(String txt, Object...args);
	void error(Throwable t);
	void error(String txt, Throwable t, Object...args);
	
	void close();
}
