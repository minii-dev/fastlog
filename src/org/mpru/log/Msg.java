package org.mpru.log;

public class Msg implements ICancel{
	// class public because should be accessed from custom MsgFormatter
	public Msg() {
	}

	public long time;
	public long printAt;
	public boolean isFile;
	public boolean isScreen;
	public byte level;
	public String pattern;
	public Object[] args;
	public String prefix;
	
	public String cachedText;
	
	public boolean isSkipHeader() {
		return (level&1)!=0 || (level<=ILog.INFO && level>ILog.WARN);
	}
}