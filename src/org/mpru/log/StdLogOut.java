package org.mpru.log;

import java.io.IOException;

public class StdLogOut implements ILogOut {
	
	public static final String SILENT_TIME = "silentTimeMillis";
	public static final String ALLOW_SKIP_HEADER = "allowSkipHeader";

	public StdLogOut() {
	}
	
	private long printBegin=System.currentTimeMillis();
	private int SILENT_TIME_MILLIS = 60000;
	private boolean isAllowSkipHeader=true;
	private boolean isForceTime;
	
	protected boolean isCanSkipTime() {
		if(!isForceTime) {
			if(System.currentTimeMillis()-printBegin<SILENT_TIME_MILLIS) return true;
			isForceTime=true;
		}
		return false;
	}


	@Override
	public void print(Msg m) {
		if(m.isScreen) {
			
			final byte level=m.level;
			StringBuilder sb=new StringBuilder(500);

			String ansi=null;
			if(l.isAnsiColor()) {
				ansi=l.ansi(level, true);
			}
			if(!isAllowSkipHeader || !m.isSkipHeader()) {
				l.appendText(m, sb, (byte) (isCanSkipTime()?(ILogger.LEVEL|ILogger.PREFIX):(ILogger.LEVEL|ILogger.PREFIX|ILogger.TIME)));
			}
			if(sb.length()>0) sb.append(' ');
			if(ansi!=null) {
				sb.append(ansi);
			}
			l.appendText(m, sb, ILogger.TEXT);

			if(ansi!=null) {
				ansi=l.ansi(level, false);
				if(ansi!=null) sb.append(ansi);
			}
			l.stdPrint(sb.toString(), level<=ILog.ERROR && level>ILog.QUIET);
		}
	}

	//private PrintStream outStream;
	//	private PrintStream errStream;
	private ILogger l;


	@Override
	public void flush() throws IOException {}


	@Override
	public void close() {}

	@Override
	public boolean setProperty(String name, Object value) {
		if(SILENT_TIME.equalsIgnoreCase(name)) {
			if(value instanceof Number) {
				SILENT_TIME_MILLIS=((Number) value).intValue();
			}else SILENT_TIME_MILLIS=(int) Double.parseDouble(value.toString());
			return true;
		}
		if(ALLOW_SKIP_HEADER.equalsIgnoreCase(name)) {
			if(value instanceof Boolean) {
				isAllowSkipHeader=((Boolean) value).booleanValue();
			}else isAllowSkipHeader=value==null || Boolean.parseBoolean(value.toString());
		}
		return false;
	}

	@Override
	public void setILogger(ILogger logger) {
		this.l = logger;		
	}

	@Override
	public String getProperty(String name) {
		if(SILENT_TIME.equalsIgnoreCase(name)) {
			return String.valueOf(SILENT_TIME_MILLIS);
		}
		return null;
	}
}
