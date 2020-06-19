package org.mpru.log;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.text.FieldPosition;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

public class MsgFormatter {

	protected int MAX_PARAMETER_ELEMENT_COUNT=50;
	protected int MAX_STRING_LENGTH=100000;
	protected int MAX_PARAMETER_LENGTH=(int) (MAX_STRING_LENGTH*0.8);
	protected int MAX_PARAMETER_RECURSION_LEVEL=5; // at what level to stop digging and print class name only

	protected java.text.SimpleDateFormat f_time = new java.text.SimpleDateFormat("HH:mm:ss");
	protected java.text.SimpleDateFormat f_date = new java.text.SimpleDateFormat("yyyy-MM-dd");

	final static String EOL=System.getProperty("line.separator");
	protected boolean isLogMillis;
	protected long lastTimeS;
	protected Date cdate=new Date();
	protected FieldPosition fldPos = new FieldPosition(0);

	protected final StringBuffer lastTimeBuffer = new StringBuffer(10);
	protected String lastTime;

	protected static boolean isNativeAnsiColor;
	static {
		String osName=System.getProperty("os.name");
		if(osName!=null) {
			osName=osName.toLowerCase();
			if(osName.indexOf("nix")>=0 || osName.indexOf("nux")>=0 || osName.indexOf("sunos")>=0) {
				isNativeAnsiColor=true;
			}
		}
	}

	public void setLogMillis(boolean isMillis) {
		isLogMillis=isMillis;
	}

	protected boolean isLogMillis() {
		return isLogMillis;
	}

	protected void makeText(Msg m) {
		if(m.cachedText==null) {
			String txt=m.pattern;
			if(txt!=null && txt.length()>0) {
				StringBuilder sb=new StringBuilder(txt.length()+8);
				sb.setLength(0);
				if(m.args!=null) {
					final int argCount=m.args.length;
					final int txtLength=txt.length();
					char begChar='{';
					int startP;
					char c=0;
					while(true) {
						int p=0;
						int paramStart;
						int paramIdx;
						startP=0;
						while(p<txtLength) {
							p=txt.indexOf(begChar, p);
							if(p<0) break;
							paramIdx=-1; // protection from 0 characters in number
							paramStart=p;
							while(++p<txtLength) {
								c=txt.charAt(p);
								if(c=='}') {
									p++;
									c=1;
									break;
								}
								c-='0';
								if(c<0 || c>9) {
									if(begChar=='$') {
										c=1;
										break;
									}
									c=0;
									break;
								}
								if(paramIdx==-1) paramIdx=c;
								else paramIdx=paramIdx*10+c;
								if(begChar=='$') c=1;
								else c=0;
							}
							if(c==1 && paramIdx>=0) {
								sb.append(txt, startP, paramStart);
								startP=p;
								if(paramIdx<argCount) {
									appendParameter(m.args[paramIdx], sb);
								}
							}
						}
						if(begChar=='$' || startP>0) break;
						begChar='$';
					}
					sb.append(txt, startP, txtLength);
					m.args=null; // free mem
					m.pattern=null;
				}else sb.append(txt);
				char end=txt.charAt(txt.length()-1);
				if(end!=13 && end!=10) {
					sb.append(EOL);
				}
				m.cachedText=sb.toString();
			}else{
				m.cachedText=EOL;
			}
		}
	}

	/**
	 * Appends full message, including time, level, prefix, text, parameter values.
	 * Standard message pattern can contain {N} or $N references to arguments supplied.
	 * If arguments are not supplied (arg[]=null), the references not processed at all. Otherwise all {N} (or all $N if no {N} found in pattern)
	 * are changed to argument values. N starts from 0. If no arg[N] present, its value assumed empty.
	 * @param m - Message to append
	 * @param s - StringBuilder to append to
	 * @param type - ILogger.TEXT (append message text only), ILogger.HEADER (append time, level, prefix only) or ILogger.FULL (append both text and header)
	 */
	public void appendText(Msg m, StringBuilder s, byte type) {

		boolean isSpace=false;
		if((type&ILogger.TIME)!=0) {
			appendTimeString(m.time, s);
			isSpace=true;
		}
		if((type&ILogger.LEVEL)!=0) {
			String levelName=getLevelNamePadded(m.level);
			if(levelName!=null) {
				if(isSpace) s.append(' ');
				isSpace=true;
				s.append('[');
				s.append(levelName);
				s.append(']');
			}
		}
		if(m.prefix!=null && (type&ILogger.PREFIX)!=0) {
			if(isSpace) s.append(' ');
			isSpace=true;
			s.append(m.prefix);
		}
		if((type&ILogger.TEXT)!=0) {
			makeText(m);
			if(m.cachedText!=null) {
				if(isSpace) s.append(' ');
				s.append(m.cachedText);
			}
		}
	}

	/**
	 * Appends parameter value (of any class). Should ignore errors and, possibly, limit too long values (like lists, maps and so on)  
	 * @param value
	 * @param sb
	 */
	protected void appendParameter(Object value, StringBuilder sb) {
		try {
			appendParameter(value, sb, sb.length(), 0);
		}catch(Exception ignore) {
			sb.append("<toString() error>");
		}
	}

	/**
	 * Appends parameter value (of any class). Should, possibly, limit too long values (like lists, maps and so on)  
	 * @param value
	 * @param sb
	 * @param start - starting length of sb (to limit length)
	 * @param level - level>0 when appendParameter is called recursively (for instance, processing map or array). Used to limit recursive level
	 */
	@SuppressWarnings("rawtypes")
	protected void appendParameter(Object value, StringBuilder sb, final int start, int level) {
		if(sb.length()-start>MAX_STRING_LENGTH) return;
		if(value instanceof CharSequence) {
			int sz=((CharSequence) value).length();
			if(sz>MAX_PARAMETER_LENGTH) {
				sb.append((CharSequence) value, 0, MAX_PARAMETER_LENGTH);
				sb.append("...size:").append(sz);
			}else sb.append(value);
			return;
		}
		if(value instanceof Number || value instanceof Character || value==null) {
			sb.append(value);
			return;
		}

		if(value instanceof Date) {
			synchronized(f_date) {
				sb.append(f_date.format(value));
			}
			String tim=getTimeString(((Date) value).getTime());
			if(!tim.equals("00:00:00")) {
				sb.append(' ');
				sb.append(tim);
			}
			return;
		}

		if(level>MAX_PARAMETER_RECURSION_LEVEL || (sb.length()-start)>MAX_PARAMETER_LENGTH) {
			sb.append('<').append(value.getClass().getName()).append('>');
			return;
		}
		level++;

		boolean isMap=value instanceof Map;
		if(isMap) {
			value=((Map<?, ?>)value).entrySet();
		}
		if(value instanceof Iterable) {
			Iterator<?> it = ((Iterable<?>) value).iterator();
			sb.append(isMap?'{':'[');
			for(int i=0;i<MAX_PARAMETER_ELEMENT_COUNT && it.hasNext();i++){
				if(i>0) sb.append(", ");
				Object val = it.next();
				appendParameter(val, sb, start, val instanceof Map.Entry?level-1:level);
			}
			if(it.hasNext()) {
				sb.append(",...");
			}
			sb.append(isMap?'}':']');
			return;
		}
		if(value instanceof Map.Entry) {
			appendParameter(((Map.Entry) value).getKey(), sb, start, level);
			sb.append(':');
			appendParameter(((Map.Entry) value).getValue(), sb, start, level);
			return;
		}
		if(value.getClass().isArray()) {
			sb.append('[');
			int sz=Array.getLength(value);
			if(sz>MAX_PARAMETER_ELEMENT_COUNT) {
				sb.append("SIZE:").append(sz);
				sz=MAX_PARAMETER_ELEMENT_COUNT;
			}
			for(int i=0;i<sz;i++) {
				if(i>0) sb.append(", ");
				appendParameter(Array.get(value, i), sb, start, level);
			}
			sb.append(']');
			return;
		}
		appendParameter(value.toString(), sb, start, level); // append as String
	}

	/**
	 * Appends time string. If isLogMillis==true, appends millis also
	 * @param time
	 * @param sb 
	 */
	public void appendTimeString(long time, StringBuilder sb){
		if(time!=0) {
			sb.append(getTimeString(time));
			if(isLogMillis) {
				sb.append('.');
				int millis=(int) (time%1000);
				if(millis<100) {
					sb.append('0');
					if(millis<10) sb.append('0');
				}
				sb.append(millis);
			}
		}
	}

	/**
	 * Returns time string (w/o millis). Should cache values to prevent same times reformatting
	 * @param time
	 * @return time string
	 */
	public String getTimeString(long time){
		if(time!=0) {
			synchronized(lastTimeBuffer) {
				long time_s = time / 1000;
				if(time_s != lastTimeS){
					lastTimeS = time_s;
					cdate.setTime(time);
					f_time.format(cdate, lastTimeBuffer, fldPos);
					lastTime=lastTimeBuffer.toString();
					lastTimeBuffer.setLength(0);
				}
				return lastTime;
			}
		}
		return "";
	}

	public String getLevelNamePadded(byte lev) {
		//if((lev&ILog.NO_HEADER_CHECK_MASK)!=1 || lev<=ILog.QUIET) return null;
		if(lev<=ILog.QUIET) return null;
		if(lev<=ILog.FAILURE) {
			return "ERROR";
		}
		if(lev<=ILog.ERROR) {
			return "ERROR";
		}
		if(lev<=ILog.WARN) {
			return "warn ";
		}
		if(lev<=ILog.INFO) {
			return "info ";
		}
		if(lev<=ILog.MOREINFO) {
			return "info1";
		}
		if(lev<=ILog.DEBUG) {
			return "dbg  ";
		}
		return "trc  ";
	}

	public String getDateForDateLogging(long time) {
		synchronized(f_date) {
			return "date: "+f_date.format(new Date(time));
		}
	}

	public String ansi(byte level, boolean isBegin) {
		if(isBegin) {
			if(level>ILog.MOREINFO) {
				return "\u001b[36m";// cyan - debug, trace
			}else if(level>ILog.INFO) {
				return "\u001b[33;1m";// yellow - moreinfo
			}else if(level>ILog.WARN) { // info - default
			}else if(level>ILog.WARN_ERR) {
				return "\u001b[35m";// mag - warn
			}else if(level>ILog.QUIET) {
				return "\u001b[31;1m";// red - error
			}else {

			}
			return null;
		}
		return "\u001b[m";
	}

	protected PrintStream outStream;
	protected PrintStream errStream;
	protected boolean isAnsiColor;
	protected boolean isStreamsChecked;

	public PrintStream getAnsiStdStream(boolean isErrorStream) {
		if(!isStreamsChecked) {
			// do not set isAnsiColor=false, because it can be set by setAnsiColor call
			isStreamsChecked=true;
			String logger_use_ansi = System.getProperty(Logger.LOGGER_USE_ANSI_COLOR);
			if(logger_use_ansi==null || Boolean.parseBoolean(logger_use_ansi)){
				if(!isNativeAnsiColor) {
					try{
						final Class<?> ansiConsole = Class.forName("org.fusesource.jansi.AnsiConsole");
						outStream=(PrintStream) ansiConsole.getField("out").get(null);
						errStream=(PrintStream) ansiConsole.getField("err").get(null);
						this.isAnsiColor=true;
					}catch(Throwable e){
					}
				}else this.isAnsiColor=true;
			}
		}
		return isErrorStream?errStream:outStream;
	}

	public void setOutStream(PrintStream outStream) {
		this.outStream = outStream;
	}

	public void setErrStream(PrintStream errStream) {
		this.errStream = errStream;
	}

	public void setAnsiColor(boolean isAnsiColor) {
		this.isAnsiColor = isAnsiColor;
	}

	public boolean isAnsiColor() {
		if(!isStreamsChecked) {
			getAnsiStdStream(false);
		}
		return isAnsiColor;
	}
}
