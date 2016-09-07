# Fastlog
Small, fast, special, practical java logging.
# The Ideas
1. Console logging must provide information, not flood.
When you run the application, you usually want to see its progress and results on-line. But in case of unexpected results, you also want to be able to analyse the application behaviour. So, you have two different purposes of logging.
The Fastlog (by default) provides two outputs, yes, for different purposes: the console (STDOUT and STDERROR) and a file. The console is usually used just in time of running, to view the progress, so its log level is relatively low. Also it does not print unnecessary information such as time for each line for short processes (but if the process lasts more, it starts to print time). The log file is usually used for post process analisys and debugging, so its log level is low (debug by default), and it has time for each line.
The application just prints to log, and the Fastlog itself knows whether to write to the file, to the console or both.
2. Logging should not slow down the application.
The Fastlog prints asynchronously in dedicated thread, so the output does not slow down the application. It automatically flushes buffers when the application finishes.
3. Console output should be colorized to distinguish errors and warnings.
The Fastlog uses ANSI color for STDOUT and prints colorized messages not only on Linux, but even in Windows platform, in standard Windows console.
4. Several parallel processes or threads of the application should be distinguished in one log.
It is very easy to assign a prefix, which is added to each log line, to a process.
5. An application running as a child of another application can use its own or parent logging (sharing the same log file in the latter case).

# Special features
1. A message text having the parameter placeholders can be distinguished from parameter values - as you wish.
2. Logging has differed output ability which can be used to log fast or slow run differently. Imagine, you want the line "My process finished quickly and successfully" for fast run or "My process started... Please wait" ... "My process finished, elapsed 1 hour" for slow. It is possible by logging "My process started... Please wait" message, specifying, say, 5 seconds delay, and after the process finish, try to cancel the message and log "My process finished quickly and successfully" on cancel success or "My process finished, elapsed ..." otherwise.
3. When message text parameters passed, they are automatically converted to strings, including objects INSIDE Maps, Arrays, Iterables, limiting the total message length and possible self-references.

# Usage
See [package.html](https://rawgit.com/minii-dev/fastlog/master/src/org/mpru/log/package.html) for usage description and examples.
