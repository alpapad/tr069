package com.owera.xaps.tr069.background;

import java.sql.Connection;
import java.util.Collection;
import java.util.Map;

import com.owera.common.db.ConnectionProvider;
import com.owera.common.log.Logger;
import com.owera.common.scheduler.TaskDefaultImpl;
import com.owera.xaps.base.db.DBAccess;
import com.owera.xaps.base.http.Authenticator;
import com.owera.xaps.base.http.ThreadCounter;

public class StabilityTask extends TaskDefaultImpl {

	private Logger logger = new Logger();

	private int lineCounter = 0;

	private boolean serverStart = true;
	private long startTms;

	private static Logger log = new Logger("Stability");
	
	public StabilityTask(String taskName) {
		super(taskName);
	}

	// returns 16-char string
	private static String getTimeSinceStart(long timeSinceStart) {
		long hours = timeSinceStart / (3600l * 1000l);
		long min = (timeSinceStart - (hours * 3600l * 1000l)) / 60000l;
		long days = timeSinceStart / (3600l * 1000l * 24l);
		return String.format("(%4sd) %5s:%02d", days, hours, min);
	}
	
	private String getUsedMemory() {
		//		Runtime.getRuntime().gc();
		long total = Runtime.getRuntime().totalMemory();
		long free = Runtime.getRuntime().freeMemory();
		long used = total - free;
		return "" + used / (1024 * 1024);

	}

	@Override
	public void runImpl() throws Throwable {
		if (serverStart) {
			log.fatal("The server starts...");
			serverStart = false;
			startTms = getThisLaunchTms();
		}
		if (lineCounter == 20)
			lineCounter = 0;
		if (lineCounter == 0)
			log.info("  TimeSinceStart | Memory (MB) | ActiveSessions | ActiveDevices | Blocked | DB-CONN (followed by a list of sec. used for each conn.)");
		lineCounter++;
		if (log != null && log.isInfoEnabled()) {
			String message = "";
			message += getTimeSinceStart(getThisLaunchTms() - startTms) + " | "; // 16-char string
			message += String.format("%11s | ", getUsedMemory());
			message += String.format("%14s | ", ThreadCounter.currentSessionsCount());
			message += String.format("%13s | ", ActiveDeviceDetectionTask.activeDevicesMap.size());
			message += String.format("%7s | ", Authenticator.getAndResetBlockedClientsCount());
			Map<Connection, Long> usedConn = ConnectionProvider.getUsedConnCopy(DBAccess.getXAPSProperties());
			if (usedConn != null) {
				message += String.format("%8s ", usedConn.size());
				Collection<Long> usedConnValues = usedConn.values();
				for (Long tms : usedConnValues) {
					long spentTime = (System.currentTimeMillis() - tms) / 1000;
					message += String.format("%4s ", spentTime);
				}
			}
			log.info(message);
		}
	}

	@Override
	public Logger getLogger() {
		return logger;
	}

}
