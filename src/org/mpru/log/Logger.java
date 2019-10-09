package org.mpru.log;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.WeakHashMap;

public class Logger implements ILogger{

	public static final String SYS_PROP_PREFIX=Logger.class.getPackage().getName();
	public static final String LOGGER_CLASS_PROPERTY = Logger.SYS_PROP_PREFIX+".Logger";
	public static final String FILEOUT_CLASS_PROPERTY = Logger.SYS_PROP_PREFIX+".FileOut";
	public static final String SCREENOUT_CLASS_PROPERTY = Logger.SYS_PROP_PREFIX+".ScreenOut";
	
	public static final String LOGGER_USE_ANSI_COLOR = Logger.SYS_PROP_PREFIX+".useAnsiColor";
	public static final String LOGGER_PRINT_MILLIS = Logger.SYS_PROP_PREFIX+'.'+ILog.LOG_TIME_MILLIS;

	protected static final ShutdHook HOOK = new ShutdHook();
	protected static final int MSG_MAX = 500;
	protected static final long DATE_PRINT_INTERVAL = 2*3600*1000; // 2h

	protected static final int MSG_MAX_DEFERRED = 1000;

	static final String INTERNAL_PREFIX = "*LOG";

	protected final ArrayList<ILogOut> logOuts=new ArrayList<>();
	protected static final long MAX_FLUSH_INTERVAL=330;
	protected static final long MIN_FLUSH_INTERVAL=50;
	protected LogFlusher LF;

	protected boolean isClose;
	protected StdLogOut emergencyOut;

	protected MsgFormatter msgFormatter=new MsgFormatter();

	private int msgIndex;
	private Msg[] msgs=new Msg[MSG_MAX];
	private ArrayList<Msg> msgQueue=new ArrayList<>();
	private boolean isFullQueueWarnPrinted;

	private final Object flushLock=new Object();
	private final Object logLock=new Object();

	protected volatile long appWaitMillis;
	protected long appWaitMillisBarrier=1000;

	static {
		Runtime.getRuntime().addShutdownHook(HOOK);
	}

	@Override
	public void addLogOut(ILogOut logOut) {
		synchronized(logOuts) {
			if(logOuts.indexOf(logOut)>0) return;
			logOuts.add(logOut);
		}

		logOut.setILogger(this);
		synchronized(props) {
			for(Entry<String, String> e: props.entrySet()) {
				String name=e.getKey();
				Object value=e.getValue();				
				logOut.setProperty(name, value);
			}
		}
	}

	public Logger(){
		this(true, true);
	}

	public Logger(boolean isUseScreenOut, boolean isUseFileOut){
		LF=new LogFlusher();
		LF.setDaemon(true);
		LF.start();
		String propVal;
		
		ILogOut logOut;
		if(isUseScreenOut) {
			logOut = Log.createInstance(SCREENOUT_CLASS_PROPERTY, ILogOut.class);
			if(logOut==null) logOut=new StdLogOut();
			addLogOut(logOut);
		}
		if(isUseFileOut) {
			logOut = Log.createInstance(FILEOUT_CLASS_PROPERTY, ILogOut.class);
			if(logOut==null) logOut=new FileLogOut();
			addLogOut(logOut);
		}
		propVal = System.getProperty(LOGGER_PRINT_MILLIS);
		msgFormatter.setLogMillis(Boolean.parseBoolean(propVal));

		HOOK.addLog(this);
	}

	protected Msg newMsg() {
		return new Msg();
	}

	@Override
	public boolean cancel(ICancel msg) {
		synchronized(msgQueue) {
			return msgQueue.remove(msg);
		}
	}
	
	@Override
	public void now(ICancel msg) {
		if(cancel(msg)) {
			log((Msg) msg);
		}
	}

	protected void emergencyPrint(Msg m) {
		if(emergencyOut==null) {
			emergencyOut=new StdLogOut();
			emergencyOut.setILogger(this);
		}
		emergencyOut.print(m);
	}
	
	protected boolean checkArgsFinal(Msg m) {
		if(m==null || m.args==null) return false;
		Object[] args=m.args;
		final int sz=args.length;
		Object o;
		for(int i=0;i<sz;i++) {
			o=args[i];
			if(o==null || o instanceof String || o instanceof Character ||
					(o instanceof Number && (
					o instanceof Long || o instanceof Byte || o instanceof Double || o instanceof Float || o instanceof Integer || o instanceof Short))
					) {
				continue;
			}
			msgFormatter.makeText(m);
			return true;
		}
		return false;
	}

	@Override
	public ICancel log(String prefix, String txt, byte level, Object[] args, boolean isScreen, boolean isFile, int deferMillis) {
		if(!isFile && !isScreen) return null;
		Msg m=newMsg();
		m.isFile=isFile;
		m.isScreen=isScreen;
		m.level=level;
		m.pattern=txt;
		m.args=args;
		m.prefix=prefix;
		long time=System.currentTimeMillis();
		m.time=time;
		if(isClose) {
			emergencyPrint(m);
			return m;
		}
		checkArgsFinal(m);
		if(deferMillis>0) {
			m.printAt=time+deferMillis;
			if(msgQueue.size()>=MSG_MAX_DEFERRED) {
				if(!isFullQueueWarnPrinted) {
					isFullQueueWarnPrinted=true;
					log(INTERNAL_PREFIX, "Delayed message queue full - some delayed messages ignored (this message is printed only once)", ILog.ERROR, null, true, true, 0);
				}
				return m;
			}
			synchronized(msgQueue) {
				msgQueue.add(m);
			}
		}else	log(m);
		return m;
	}

	private void log(Msg m) {
		long timeStart=System.currentTimeMillis();
		while(true) {
			synchronized(logLock) {
				if(msgIndex<MSG_MAX) {
					msgs[msgIndex++]=m;
					if(msgIndex*2>MSG_MAX) LF.askFlush();
					appWaitMillis+=System.currentTimeMillis()-timeStart;
					return;
				}
			}
			flush();
		}
	}

	boolean flush() {
		if(msgIndex>0) {
			Msg[] p_msgs;
			int p_msgIndex;
			synchronized(flushLock) {
				synchronized(logLock) {
					if(msgIndex==0) return false;
					p_msgs = msgs;
					p_msgIndex = msgIndex;			
					msgs=new Msg[MSG_MAX];
					msgIndex=0;
				}
				for(int i=0; i<p_msgIndex; i++) {
					output(p_msgs[i]);
				}
			}
			int i=0;
			while(logOuts.size()>i) {
				try {
					logOuts.get(i++).flush();
				}catch(Exception e) {
					reportInternalError("log flush error", e); // TODO skip if many errors				
				}
			}
			return true;
		}
		return false;
	}

	protected boolean printDeferred(boolean isForce) {
		if(msgQueue.size()>0) {
			synchronized(msgQueue) {
				long time=isForce?Long.MAX_VALUE:System.currentTimeMillis();
				Msg m;
				int i=0;
				while(i<msgQueue.size()) {
					m=msgQueue.get(i);
					if(m.printAt<=time) {
						synchronized(logLock) {
							if(msgIndex<MSG_MAX) {
								msgs[msgIndex++]=m;
								msgQueue.remove(i);
							}else return false;
						}
					}else i++;
				}
			}
		}
		return true;
	}

	@Override
	public void reportInternalError(String text, Exception ex) {
		log(INTERNAL_PREFIX, Log.getExceptionText(text,  ex, true), ILog.ERROR, null, true, false, 0);		
	}

	/**
	 * Appends full message, including time, level, prefix, text, parameter values
	 * @param m
	 * @param sb
	 */
	@Override
	public void appendText(Msg m, StringBuilder sb, byte type) {
		msgFormatter.appendText(m, sb, type);
	}

	public void setMsgFormatter(MsgFormatter msgFormatter) {
		this.msgFormatter = msgFormatter;
	}
	
	public MsgFormatter getMsgFormatter() {
		return msgFormatter;
	}

	protected void output(Msg m) {
		if(m!=null) {
			Msg tm;
			// log current date
			if(nextDatePrintTime<=m.time) {
				tm = newMsg();
				tm.cachedText=msgFormatter.getDateForDateLogging(m.time);
				if(tm.cachedText!=null) {
					tm.cachedText=tm.cachedText+MsgFormatter.EOL;
					tm.time=m.time;
					tm.level=ILog.QUIET;
					tm.isFile=true;
					tm.isScreen=nextDatePrintTime!=0l; // do not log to screen for the 1st time
					output1(tm);
				}
				nextDatePrintTime=m.time+DATE_PRINT_INTERVAL;
			}
			if(appWaitMillis>=appWaitMillisBarrier) {
				long tTime=appWaitMillis;
				appWaitMillis=0;
				if(appWaitMillisBarrier<60000) {
					appWaitMillisBarrier=appWaitMillisBarrier*3/2;
				}
				// log delay detected
				tm=newMsg();
				tm.time=m.time;
				tm.prefix=INTERNAL_PREFIX;
				tm.level=ILog.WARN;
				tm.isScreen=tm.isFile=true;
				StringBuilder sb=new StringBuilder(40);
				tm.pattern="logger delayed: "+tTime+" ms";
				appendText(tm, sb, ILogger.FULL);
				output1(tm);
			}
			output1(m);
		}
	}

	protected void output1(Msg m) {
		int i=0;
		while(logOuts.size()>i) {
			try {
				logOuts.get(i++).print(m);
			}catch(Exception e) {
				reportInternalError("log error", e); // TODO skip if many errors				
			}
		}
	}

	@Override
	public void close() {
		appWaitMillisBarrier=1000;
		if(appWaitMillis>1000) {
			log(INTERNAL_PREFIX, "Finished with appWaits "+appWaitMillis, ILog.WARN, null, true, false, 0);
		}
		LF.abort();
		synchronized(flushLock) {
			// wait flush by flusher finished
		}
		for(int i=0;i<20;i++) {
			flush();
			if(printDeferred(true))break;
		}
		flush();
		isClose=true;
		int i=logOuts.size();
		while(i-->0) {
			try {
				logOuts.get(i).close();
			}catch(Exception ignore) {
			}
		}
	}

	private long nextDatePrintTime;

	static class ShutdHook extends Thread{
		private WeakHashMap<ILogger, Object> logs=new WeakHashMap<>();

		public void addLog(Logger log) {
			logs.put(log, null);
		}

		@Override
		public void run() {
			setName("Log Shutdown");
			for(ILogger logger: logs.keySet()) {
				if(logger!=null) {
					logger.close();
				}
			}
		}
	}

	protected HashMap<String, String> props=new HashMap<>();

	@Override
	public void setProperty(String name, String value) {
		synchronized(props) {
			Object oldVal=props.get(name);
			if(oldVal==null) {
				if(value==null) return;
			}else if(oldVal.equals(value)) return;
			props.put(name, value);
		}
		if(ILog.LOG_TIME_MILLIS.equalsIgnoreCase(name)) {
			msgFormatter.setLogMillis("true".equalsIgnoreCase(value));
		}else if(ILog.ALLOW_ANSI_CODES.equalsIgnoreCase(name)) {
			if(value!=null) {
				msgFormatter.setAnsiColor(Boolean.parseBoolean(value.toString()));
			}
		}
		flush();
		int i=0;
		while(logOuts.size()>i) {
			logOuts.get(i++).setProperty(name, value);
		}
	}


	@Override
	public String getProperty(String name) {
		if(ILog.ALLOW_ANSI_CODES.equalsIgnoreCase(name)) {
			return Boolean.toString(msgFormatter.isAnsiColor());
		}
		String val;
		int i=0;
		while(logOuts.size()>i) {
			val=logOuts.get(i++).getProperty(name);
			if(val!=null) return val;
		}
		val=props.get(name);
		return props.get(name);
	}

	@Override
	public byte getLevelByName(String level) {
		if(level!=null) {
			level=level.toUpperCase();
			switch(level) {
				case "TRC":
				case "TRACE": return ILog.TRACE;
				case "DBG":
				case "DEBUG": return ILog.DEBUG;
				case "INFO1":
				case "MOREINFO": return ILog.MOREINFO;
				case "WARNING":
				case "WARN": return ILog.WARN;
				case "ERROR": return ILog.ERROR;
				case "FAILURE": return ILog.FAILURE;
				case "QUIET": return ILog.QUIET;
			}
		}
		return ILog.INFO;
	}

	@Override
	public String getLevelName(byte lev) {
		if(lev<=ILog.QUIET) {
			return "QUIET";
		}
		if(lev<=ILog.FAILURE) {
			return "FAILURE";
		}
		if(lev<=ILog.ERROR) {
			return "ERROR";
		}
		if(lev<=ILog.WARN) {
			return "WARN";
		}
		if(lev<=ILog.INFO) {
			return "INFO";
		}
		if(lev<=ILog.MOREINFO) {
			return "INFO1";
		}
		if(lev<=ILog.DEBUG) {
			return "DEBUG";
		}
		return "TRACE";
	}

	private class LogFlusher extends Thread{

		private volatile boolean isAbort;


		public LogFlusher() {
		}

		public void askFlush() {
			interrupt();
		}

		public void abort() {
			isAbort=true;
			askFlush();
		}

		@Override
		public void run() {
			setName("Log Flusher");
			long pauseMillis=MAX_FLUSH_INTERVAL;
			while(!isAbort) {
				try {
					printDeferred(false);
					if(flush()) {
						pauseMillis=MIN_FLUSH_INTERVAL;
					}else pauseMillis=MAX_FLUSH_INTERVAL;
				}catch(Exception e) {
					e.printStackTrace();
				}
				try{
					Thread.sleep(pauseMillis);
				}catch(InterruptedException e){
				}
			}
		}
	}

	@Override
	public String ansi(byte level, boolean isBegin) {
		return msgFormatter.ansi(level, isBegin);
	}

	@Override
	public boolean isAnsiColor() {
		return msgFormatter.isAnsiColor();
	}

	@Override
	public void stdPrint(String text, boolean isStdError) {
		PrintStream stream = msgFormatter.getAnsiStdStream(isStdError);
		if(stream==null) stream=isStdError?System.err:System.out;
		stream.print(text);
	}

}
