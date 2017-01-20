package org.mpru.log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Log implements ILog {

	private final ILogger l;
	private ILog parent;
	private String prefix;
	private byte fileLevel=ILog.MOREINFO; // FIXME: must be level for each logout type? Or logout configured itself (for instance, DB logger), and Log has only two: screen/file log levels
	private boolean isFileUsesSameLevel;
	private byte level=ILog.INFO;

	@SuppressWarnings("unchecked")
	protected static <T> T createInstance(String classPropertyName, Class<T> cl){
		String className=System.getProperty(classPropertyName);
		if(className!=null && className.length()>0) {
			try{
				Object loggerObject = Class.forName(className).newInstance();
				if(!(cl.isInstance(loggerObject))) {
					throw new RuntimeException("Instance specified by system property "
							+classPropertyName+"=\""+className+"\" must implement "+cl.getName()+" interface. Correct the class or remove the property to use the default class");
				}
				return (T) loggerObject;
			}catch(InstantiationException | IllegalAccessException | ClassNotFoundException e){
				throw new RuntimeException("Cannot create "+cl.getName()+" instance specified by system property "
						+classPropertyName+"=\""+className+"\". Correct the class or remove the property to use the default class", e);
			}
		}
		return null;
	}

	public static ILog createInitial() {
		return createInitial(null);
	}

	public static ILog createInitial(ILogger logger) {
		if(logger==null) {
			logger=createInstance(Logger.LOGGER_CLASS_PROPERTY, ILogger.class);
			if(logger==null) logger=Logger.getDefault();
		}
		return new Log(logger);
	}

	Log(ILogger logger) {
		l=logger;
	}

	@Override
	public ILog newLog(boolean isAsChild) {
		Log log=new Log(l);
		log.prefix=prefix;
		if(isAsChild) {
			log.parent=this;
			log.fileLevel=ILog.QUIET;
			log.level=ILog.QUIET;
		}else {
			log.parent=parent;
			log.fileLevel=fileLevel;
			log.level=level;
		}
		return log;
	}

	@Override
	public ILog getParent() {
		return parent;
	}

	@Override
	public void setPrefix(String prefix) {
		this.prefix=prefix;
	}

	@Override
	public boolean isLog(byte level) {
		if(level<=this.level || level<=fileLevel) {
			return true;
		}
		return parent==null?false:parent.isLog(level);
	}

	@Override
	public void setLevel(byte level) {
		this.level=level;
		if(isFileUsesSameLevel || level>fileLevel) fileLevel=level;
	}

	@Override
	public void setFileLevel(byte level) {
		isFileUsesSameLevel=level==ILog.USE_SAME_LEVEL;
		if(isFileUsesSameLevel) fileLevel=this.level;
		else fileLevel=level;
	}

	@Override
	public void setProperty(String name, String value) {
		if(ILog.LOG_LEVEL.equals(name) || ILog.LOG_LEVEL1.equals(name)) {
			setLevel(l.getLevelByName(value));
			return;
		}
		if(ILog.FILE_LOG_LEVEL.equals(name)) {
			setFileLevel(l.getLevelByName(value));
			return;
		}
		if(parent!=null) parent.setProperty(name, value);
		else l.setProperty(name, value);
	}

	@Override
	public String getProperty(String name) {
		if(ILog.LOG_LEVEL.equals(name) || ILog.LOG_LEVEL1.equals(name)) {
			return l.getLevelName(getLevel());
		}
		if(ILog.FILE_LOG_LEVEL.equals(name)) {
			return l.getLevelName(getFileLevel());
		}
		if(parent!=null) return parent.getProperty(name);
		return l.getProperty(name);
	}

	@Override
	public boolean cancel(ICancel msg) {
		return l.cancel(msg);
	}
	
	@Override
	public void now(ICancel msg) {
		l.now(msg);
	}

	@Override
	public ICancel logDefer(int deferMillis, String txt, byte level, Object...args) {
		boolean isScreen=level<=this.level;
		boolean isFile=level<=fileLevel;
		if(parent!=null) {
			ILog c=getParent();
			do {
				if(!isScreen) isScreen=level<=c.getLevel();
				if(!isFile) isFile=level<=c.getFileLevel();
				c=c.getParent();
			}while(c!=null);
		}
		return l.log(prefix, txt, level, args, isScreen, isFile, deferMillis);
	}

	@Override
	public void log(String txt, byte level, Object...args) {
		logDefer(0, txt, level, args);
	}

	@Override
	public void log(String txt, Throwable t, byte level, Object... args) {
		log(getExceptionText(txt, t, true), level, args);
	}

	@Override
	public void trace(String txt, Object...args) {
		log(txt, ILog.TRACE, args);
	}

	@Override
	public void debug(String txt, Object...args) {
		log(txt, ILog.DEBUG, args);
	}

	@Override
	public void moreInfo(String txt, Object...args) {
		log(txt, ILog.MOREINFO, args);
	}

	@Override
	public void info(String txt, Object...args) {
		log(txt, ILog.INFO, args);
	}

	@Override
	public void warn(String txt, Object...args) {
		log(txt, ILog.WARN, args);
	}

	@Override
	public void error(String txt, Object...args) {
		log(txt, ILog.ERROR, args);
	}

	@Override
	public void error(Throwable t) {
		log(getExceptionText(t, true), ILog.ERROR);
	}


	@Override
	public void error(String txt, Throwable t, Object...args) {
		log(txt, t, ILog.ERROR, args);
	}

	@Override
	public byte getLevelByName(String level) {
		return l.getLevelByName(level);
	}

	public static String getExceptionText(String message, Throwable throwable, boolean isPrintStackTrace) {
		String eText=getExceptionText(throwable, isPrintStackTrace);
		if(message!=null && message.length()>0) {
			if(message.charAt(message.length()-1)>' ')eText=message+' '+eText;
			else eText=message+eText;
		}
		return eText;
	}
	
	private static void appendExceptionText(StringBuilder sb, Throwable throwable) {
		String str = throwable.getLocalizedMessage();
		if(str==null) {
			str=throwable.getClass().getName();
		}
		sb.append(str);
	}

	public static String getExceptionText(Throwable throwable, final boolean isPrintStackTrace) {
		if(throwable!=null) {
			if(!isPrintStackTrace) {
				StringBuilder sb = new StringBuilder(64);
				while(true) {
					appendExceptionText(sb, throwable);
					throwable=throwable.getCause();
					if(throwable==null) break;
					sb.append(MsgFormatter.EOL);
					sb.append(" caused by: ");
				}
				return sb.toString();
			}
			try {
				String str;
				StringWriter sw=new StringWriter();
				PrintWriter pr=new PrintWriter(sw);
				throwable.printStackTrace(pr);
				str=sw.toString();
				pr.close();
				sw.close();
				return str;
			}catch(Throwable t) {
				t.printStackTrace();
			}
		}
		return null;
	}

	@Override
	public boolean isTrace() {
		return isLog(ILog.TRACE);
	}

	@Override
	public boolean isDebug() {
		return isLog(ILog.DEBUG);
	}

	@Override
	public boolean isMoreInfo() {
		return isLog(ILog.MOREINFO);
	}

	@Override
	public byte getLevel() {
		return level;
	}

	@Override
	public byte getFileLevel() {
		return fileLevel;
	}

	@Override
	public String getPrefix() {
		return prefix;
	}

	@Override
	public ILogger getLogger() {
		return l;
	}
}
