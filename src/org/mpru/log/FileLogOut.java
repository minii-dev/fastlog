package org.mpru.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.FileLock;
import java.util.Date;

public class FileLogOut implements ILogOut {

	public static final String MAX_FILE_SIZE = "maxLogFileSize";
	
	private final Object fileLock=new Object();
	
	protected java.text.SimpleDateFormat f_filedate = new java.text.SimpleDateFormat("yyyy_MM_dd");
	
	private ILogger l;
	private String logPath;
	private long maxLogFileSize=10*1024*1024;
	private boolean isFileHeaderPrinted;
	private String charsetName="utf-8";

	private Writer logFileWriter;

	String getRealLogPath(String pattern) {
		if(pattern!=null) {
			if(pattern.indexOf("$(filedate)")>=0){
				pattern=pattern.replaceAll("(?i)\\$\\(filedate\\)", f_filedate.format(new Date(System.currentTimeMillis())));
			}
		}
		return pattern;
	}
	
	@Override
	public void print(Msg m) throws IOException {
		if(m.isFile) {
			Writer fw = logFileWriter;
			if(fw!=null) {
				StringBuilder sb=new StringBuilder(500);
				if(!isFileHeaderPrinted) {
					isFileHeaderPrinted=true;
					sb.append("=========================").append(MsgFormatter.EOL).append("======== started logging").append(MsgFormatter.EOL);
				}		
				l.appendText(m, sb, ILogger.FULL);
				fw.write(sb.toString());
			}
		}
	}

	protected void closeFile() {
		Writer logFileWriter = this.logFileWriter;
		if(logFileWriter!=null) {
			synchronized(fileLock) {
				try {
					flush();
				}catch(IOException e){
					l.reportInternalError("file flush", e);
				}
				this.logFileWriter=null;
				try{
					logFileWriter.close();
				}catch(IOException e){
					l.reportInternalError("file close", e);
				}
				try{
					fLock.release();
					fLock=null;
				}catch(IOException e){
					l.reportInternalError("lock release", e);
				}
				try{
					locFOS.close();
					locFOS=null;
				}catch(IOException e){
					l.reportInternalError("lock close", e);
				}
				locFile.delete();
				logPath=null;
			}
		}
	}

	public void setPath(String path, boolean isForce) {
		if(isForce || logPath==null) {
			closeFile();
			openFile(path);
		}
	}

	private FileLock fLock;
	private FileOutputStream locFOS;
	private File locFile;

	private void openFile(String logPath) {
		if(logPath!=null && logPath.length()>0) {
			logPath=getRealLogPath(logPath);
			try{
				synchronized(fileLock) {
					int ps=logPath.lastIndexOf('.');
					if(ps<0) {
						logPath=logPath+".log";
						ps=logPath.lastIndexOf('.');
					}
					final String logPrePath=logPath.substring(0, ps);
					final String logExt=logPath.substring(ps);
					String locPath;

					int suffix=0;
					while(true) {
						if(maxLogFileSize<=0 || maxLogFileSize>new File(logPath).length()) {
							if(suffix>0) {
								locPath=logPrePath+'_'+suffix+".loc";
							}else locPath=logPrePath+".loc";
							File locFile=new File(locPath);
							FileOutputStream locFOS=null;
							FileLock fLock=null;
							try {
								locFile.deleteOnExit();
								locFOS = new FileOutputStream(locFile, true);
								fLock = locFOS.getChannel().tryLock();

								if(fLock!=null) {
									FileOutputStream fileStream = new FileOutputStream(logPath, true);

									isFileHeaderPrinted=false;
									logFileWriter = new BufferedWriter(new OutputStreamWriter(fileStream, charsetName));
									this.fLock=fLock;
									this.locFOS=locFOS;
									this.locFile=locFile;
									this.logPath=logPath;
									break;
								}
							}catch(IOException e) {
							}
							if(fLock!=null) {
								try {
									fLock.release();
								}catch(IOException ignore) {
								}
							}
							if(locFOS!=null) {
								try {
									locFOS.close();
								}catch(IOException ignore) {
								}
								locFile.delete();
							}
						}
						suffix++;
						if(suffix>100) {
							throw new RuntimeException("Cannot create log file using .loc: "+logPath);
						}
						logPath=logPrePath+'_'+suffix+logExt;
					}
				}
				l.log(null, "Log file: "+logPath, ILog.DEBUG_NOH, null, true, false, 0);
			}catch(Exception e){
				l.reportInternalError("open file", e);
			}
		}
	}

	@Override
	public void flush() throws IOException {
		Writer lf = logFileWriter;
		if(lf!=null) lf.flush();
	}

	@Override
	public void close() {
		closeFile();
	}

	@Override
	public boolean setProperty(String name, Object value) {
		if(ILog.LOG_PATH.equals(name) || ILog.LOG_PATH1.equals(name)) {
			setPath(value==null?null:value.toString(), false);
			return true;
		}else if(MAX_FILE_SIZE.equals(name)) {
			if(value instanceof Number) maxLogFileSize=((Number) value).longValue();
			else if(value!=null) maxLogFileSize=Long.parseLong(value.toString());
			return true;
		}
		return false;
	}

	@Override
	public String getProperty(String name) {
		if(ILog.LOG_PATH.equals(name) || ILog.LOG_PATH1.equals(name)) {
			return logPath;
		}else if(MAX_FILE_SIZE.equals(name)) {
			return String.valueOf(maxLogFileSize);
		}
		return null;
	}

	@Override
	public void setILogger(ILogger logger) {
		l=logger;
	}
}
