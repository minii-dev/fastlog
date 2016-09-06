package org.mpru.log;

import java.io.IOException;

public interface ILogOut {

	void print(Msg msg) throws IOException;
	void flush() throws IOException;
	void close();
	
	boolean setProperty(String name, Object value);
	void setILogger(ILogger logger);
	String getProperty(String name);
}
