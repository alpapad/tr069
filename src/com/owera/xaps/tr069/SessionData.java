package com.owera.xaps.tr069;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.owera.xaps.base.Log;
import com.owera.xaps.base.NoDataAvailableException;
import com.owera.xaps.base.OweraParameters;
import com.owera.xaps.base.PIIDecision;
import com.owera.xaps.base.ServiceWindow;
import com.owera.xaps.base.SessionDataI;
import com.owera.xaps.base.db.DBAccess;
import com.owera.xaps.base.db.DBAccessSession;
import com.owera.xaps.dbi.File;
import com.owera.xaps.dbi.Job;
import com.owera.xaps.dbi.JobParameter;
import com.owera.xaps.dbi.Profile;
import com.owera.xaps.dbi.ProfileParameter;
import com.owera.xaps.dbi.Unit;
import com.owera.xaps.dbi.UnitParameter;
import com.owera.xaps.dbi.Unittype;
import com.owera.xaps.dbi.Unittype.ProvisioningProtocol;
import com.owera.xaps.dbi.UnittypeParameter;
import com.owera.xaps.dbi.util.ProvisioningMessage;
import com.owera.xaps.dbi.util.SystemParameters;
import com.owera.xaps.tr069.exception.TR069DatabaseException;
import com.owera.xaps.tr069.xml.ParameterAttributeStruct;
import com.owera.xaps.tr069.xml.ParameterList;
import com.owera.xaps.tr069.xml.ParameterValueStruct;

public class SessionData implements SessionDataI {

	public static class Download {
		private String url;
		private File file;

		public Download(String url, File file) {
			this.url = url;
			this.file = file;
		}

		public String getUrl() {
			return url;
		}

		public File getFile() {
			return file;
		}
	}

	/* The session-id */
	private String id;
	/* Access to all database operations */
	private DBAccessSession dbAccess;
	/* Data for monitoring/logging */
	private List<HTTPReqResData> reqResList = new ArrayList<HTTPReqResData>();
	private Long startupTmsForSession; // When did the session start?

	private String unitId; // The unique id for the CPE
	private Unit unit; // The unit-object
	private Profile profile; // The profile name for this CPE (defined i the DB)
	private Unittype unittype; // The unittype for this CPE (defined in the DB)
	private String keyRoot; // The keyroot of this CPE (e.g. InternetGatewayDevice.)
	private String mac;
	private String serialNumber;

	/* Tells whether the noMoreRequests flag has been set by the CPE or not. */
	private boolean noMoreRequests;
	/* Tells whether the CPE is authenticated or not */
	private boolean authenticated;
	/* Tells whether the CPE is factory reset or not */
	private boolean factoryReset;
	/* Tells whether the CPE is booted or not */
	private boolean booted;
	/* Tells whether the CPE is doing a periodic inform or not */
	private boolean periodic;
	/* Tells whether the CPE is doing a value change inform or not */
	private boolean valueChange;
	/* Tells whether the CPE is kicked or not */
	private boolean kicked;
	/* Tells whether the CPE has completed a diagnostics or not */
	private boolean diagnosticsComplete;
	/* Tells whether a job is under execution - important not to start on another job */
	private boolean jobUnderExecution;
	/* The event code of the inform */
	private String eventCodes;

	/* Owera parameters */
	private OweraParameters oweraParameters;
	/* Special parameters, will always be retrieved */
	private CPEParameters cpeParameters;
	/* Special parameter, will only be retrieved from the Inform */
	private InformParameters informParameters;

	/* All parameters found in the DB, except system parameters (X)*/
	private Map<String, ParameterValueStruct> fromDB;
	/* All parameters read from the CPE */
	private List<ParameterValueStruct> valuesFromCPE;
	/* All attributes read from the CPE */
	private List<ParameterAttributeStruct> attributesFromCPE;
	/* All attributes that shall be written to the CPE */
	private List<ParameterAttributeStruct> attributesToCPE;
	/* All parameters that shall be written to the CPE */
	private ParameterList toCPE;
	/* All parameters that shall be written to the DB */
	private List<ParameterValueStruct> toDB;
	/* All parameters requested from CPE */
	private List<ParameterValueStruct> requestedCPE;

	/* Job */
	private Job job;;
	/* All parameters from a job */
	private Map<String, JobParameter> jobParams;
	/* ServiceWindow */
	private ServiceWindow serviceWindow;
	/* May be populated if a job is scheduled at a certain interval (repeat-jobs) */
	private Long secondsToNextJob;

	/* parameterkey contains a hash of all values sent to CPE */
	private ParameterKey parameterKey;
	/* commandkey contains the version number of the last download - if a download was sent */
	private CommandKey commandKey;
	/* Provisioning allowed. False if outside servicewindow or not allowed by unitJob */
	private boolean provisioningAllowed = true;

	/* The secret obtained by discovery-mode, basic auth.*/
	private String secret = null;
	/* The flag signals a first-time connect in discovery-mode */
	private boolean firstConnect = false;
	/* Unittype has been created, but unitId remains unknown, only for discovery-mode */
	private boolean unittypeCreated = true;
	/* Session is in test-mode */
	private boolean testMode = false;

	/* PIIDecision is important to decide the final outcome of the next Periodic Inform Interval */
	private PIIDecision piiDecision;

	// An object to store all kinds of data about the provisioning
	private ProvisioningMessage provisioningMessage = new ProvisioningMessage();

	// An object to store data about a download
	private Download download;

	public SessionData(String id) throws TR069DatabaseException {
		try {
			this.id = id;
			this.dbAccess = new DBAccessSession(DBAccess.getDBI());
			provisioningMessage.setProvProtocol(ProvisioningProtocol.TR069);
		} catch (SQLException sqle) {
			throw new TR069DatabaseException("Could not create SessionData object", sqle);
		}
	}

	public String getKeyRoot() {
		return keyRoot;
	}

	public void setKeyRoot(String keyRoot) {
		if (keyRoot != null)
			this.keyRoot = new String(keyRoot);
	}

	public Map<String, ParameterValueStruct> getFromDB() {
		return fromDB;
	}

	public Long getStartupTmsForSession() {
		return startupTmsForSession;
	}

	public void setStartupTmsForSession(long startupTmsForSession) {
		this.startupTmsForSession = new Long(startupTmsForSession);
	}

	public void updateParametersFromDB(String unitId) throws SQLException {

		if (fromDB != null)
			return;

		Log.debug(SessionData.class, "Will load unit data");
		addUnitDataToSession(this);

		if (fromDB.isEmpty()) {
			if (Properties.isDiscoveryMode()) {
				Log.debug(SessionData.class, "No unit data found & discovery mode true -> first-connect = true, allow to continue");
				this.setFirstConnect(true);
			} else
				throw new NoDataAvailableException();
		}

		if (!fromDB.isEmpty()) {
			if (oweraParameters == null)
				oweraParameters = new OweraParameters();
			Iterator<String> i = fromDB.keySet().iterator();
			int systemParamCounter = 0;
			while (i.hasNext()) {
				String utpName = i.next();
				UnittypeParameter utp = unittype.getUnittypeParameters().getByName(utpName);
				if (utp != null && utp.getFlag().isSystem()) {
					systemParamCounter++;
					oweraParameters.putPvs(utpName, fromDB.get(utpName));
					i.remove();
				}
			}
			Log.debug(SessionData.class, "Removed " + systemParamCounter + " system parameter from param-list, now contains " + fromDB.size() + " params");
		}
	}

	public String getUnitId() {
		return unitId;
	}

	public void setUnitId(String unitId) {
		if (unitId != null) {
			this.unitId = new String(unitId);
			this.provisioningMessage.setUniqueId(unitId);
		}
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public void setAuthenticated(boolean authenticated) {
		this.authenticated = authenticated;
	}

	public boolean isNoMoreRequests() {
		return noMoreRequests;
	}

	public void setNoMoreRequests(boolean noMoreRequests) {
		this.noMoreRequests = noMoreRequests;
	}

	public CPEParameters getCpeParameters() {
		return cpeParameters;
	}

	public void setCpeParameters(CPEParameters cpeParameters) {
		this.cpeParameters = cpeParameters;
	}

	public OweraParameters getOweraParameters() {
		return oweraParameters;
	}

	public void setOweraParameters(OweraParameters oweraParameters) {
		this.oweraParameters = oweraParameters;
	}

	public List<ParameterValueStruct> getFromCPE() {
		return valuesFromCPE;
	}

	public void setFromCPE(List<ParameterValueStruct> fromCPE) {
		this.valuesFromCPE = fromCPE;
	}

	public List<ParameterAttributeStruct> getAttributesFromCPE() {
		return attributesFromCPE;
	}

	public void setAttributesFromCPE(List<ParameterAttributeStruct> attributesFromCPE) {
		this.attributesFromCPE = attributesFromCPE;
	}

	public ParameterList getToCPE() {
		return toCPE;
	}

	public void setToCPE(ParameterList toCPE) {
		this.toCPE = toCPE;
	}

	public void setAttributesToCPE(List<ParameterAttributeStruct> attributes) {
		this.attributesToCPE = attributes;
	}

	public List<ParameterAttributeStruct> getAttributesToCPE() {
		return this.attributesToCPE;
	}

	public List<ParameterValueStruct> getToDB() {
		return toDB;
	}

	public void setToDB(List<ParameterValueStruct> toDB) {
		if (toDB == null)
			toDB = new ArrayList<ParameterValueStruct>();
		this.toDB = toDB;
	}

	public List<ParameterValueStruct> getRequestedCPE() {
		return requestedCPE;
	}

	public void setRequestedCPE(List<ParameterValueStruct> requestedCPE) {
		this.requestedCPE = requestedCPE;
	}

	public void setFromDB(Map<String, ParameterValueStruct> fromDB) {
		this.fromDB = fromDB;
	}

	public List<HTTPReqResData> getReqResList() {
		return reqResList;
	}

	public void setReqResList(List<HTTPReqResData> reqResList) {
		this.reqResList = reqResList;
	}

	public String getMethodBeforePreviousResponseMethod() {
		if (reqResList != null && reqResList.size() > 2)
			return reqResList.get(reqResList.size() - 3).getResponse().getMethod();
		else
			return null;
	}

	public String getPreviousResponseMethod() {
		if (reqResList != null && reqResList.size() > 1)
			return reqResList.get(reqResList.size() - 2).getResponse().getMethod();
		else
			return null;
	}

	public String getPreviousRequestMethod() {
		if (reqResList != null && reqResList.size() > 1)
			return reqResList.get(reqResList.size() - 2).getRequest().getMethod();
		else
			return null;
	}

	public boolean isProvisioningAllowed() {
		return provisioningAllowed;
	}

	public void setProvisioningAllowed(boolean provisioningAllowed) {
		this.provisioningAllowed = provisioningAllowed;
	}

	public Profile getProfile() {
		return profile;
	}

	public void setProfile(Profile profile) {
		this.profile = profile;
	}

	public Unittype getUnittype() {
		return unittype;
	}

	public void setUnittype(Unittype unittype) {
		this.unittype = unittype;
	}

	public boolean isFactoryReset() {
		return factoryReset;
	}

	public void setFactoryReset(boolean factoryReset) {
		this.factoryReset = factoryReset;
	}

	public boolean isBooted() {
		return booted;
	}

	public void setBooted(boolean booted) {
		this.booted = booted;
	}

	public boolean isValueChange() {
		return valueChange;
	}

	public void setValueChange(boolean valueChange) {
		this.valueChange = valueChange;
	}

	public boolean isKicked() {
		return kicked;
	}

	public void setKicked(boolean kicked) {
		this.kicked = kicked;
	}

	public boolean isPeriodic() {
		return periodic;
	}

	public void setPeriodic(boolean periodic) {
		this.periodic = periodic;
	}

	public Unit getUnit() {
		return unit;
	}

	public void setUnit(Unit unit) {
		this.unit = unit;
	}

	public String getEventCodes() {
		return eventCodes;
	}

	public void setEventCodes(String eventCodes) {
		this.eventCodes = eventCodes;
		this.provisioningMessage.setEventCodes(eventCodes);
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public boolean isFirstConnect() {
		return firstConnect;
	}

	public void setFirstConnect(boolean firstConnect) {
		this.firstConnect = firstConnect;
	}

	public boolean isUnittypeCreated() {
		return unittypeCreated;
	}

	public void setUnittypeCreated(boolean unittypeCreated) {
		this.unittypeCreated = unittypeCreated;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ParameterKey getParameterKey() {
		return parameterKey;
	}

	public void setParameterKey(ParameterKey parameterKey) {
		this.parameterKey = parameterKey;
	}

	public Map<String, JobParameter> getJobParams() {
		return jobParams;
	}

	public void setJobParams(Map<String, JobParameter> jobParams) {
		this.jobParams = jobParams;
	}

	public boolean isTestMode() {
		return testMode;
	}

	public void setTestMode(boolean testMode) {
		this.testMode = testMode;
	}

	public DBAccessSession getDbAccess() {
		return dbAccess;
	}

	public void setDbAccess(DBAccessSession dbAccess) {
		this.dbAccess = dbAccess;
	}

	public Job getJob() {
		return job;
	}

	public void setJob(Job job) {
		this.job = job;
	}

	public ServiceWindow getServiceWindow() {
		return serviceWindow;
	}

	public void setServiceWindow(ServiceWindow serviceWindow) {
		this.serviceWindow = serviceWindow;
	}

	public void addUnitDataToSession(SessionData sessionData) throws SQLException {
		Unit unit = this.getDbAccess().readUnit(sessionData.getUnitId());
		Map<String, ParameterValueStruct> valueMap = new TreeMap<String, ParameterValueStruct>();
		if (unit != null) {
			sessionData.setUnit(unit);
			sessionData.setUnittype(unit.getUnittype());
			sessionData.setProfile(unit.getProfile());
			ProfileParameter[] pparams = unit.getProfile().getProfileParameters().getProfileParameters();
			for (ProfileParameter pp : pparams) {
				String utpName = pp.getUnittypeParameter().getName();
				valueMap.put(utpName, new ParameterValueStruct(utpName, pp.getValue()));
			}
			int overrideCounter = 0;
			for (Entry<String, UnitParameter> entry : unit.getUnitParameters().entrySet()) {
				if (!entry.getValue().getParameter().valueWasNull()) {
					String utpName = entry.getKey();
					String value = entry.getValue().getValue();
					ParameterValueStruct pvs = new ParameterValueStruct(utpName, value);
					if (valueMap.containsKey(utpName))
						overrideCounter++;
					valueMap.put(utpName, pvs);
				} else {
					System.out.println(entry.getKey() + " is probably a session-parameter");
				}
			}
			int alwaysCounter = 0;
			for (Entry<Integer, UnittypeParameter> entry : unit.getUnittype().getUnittypeParameters().getAlwaysMap().entrySet()) {
				String utpName = entry.getValue().getName();
				if (!valueMap.containsKey(utpName)) {
					alwaysCounter++;
					valueMap.put(utpName, new ParameterValueStruct(utpName, ""));
				}
			}
			String msg = "Found unit in database - in total " + valueMap.size() + " params ";
			msg += "(" + unit.getUnitParameters().size() + " unit params, ";
			msg += pparams.length + " profile params (" + overrideCounter + " overridden), ";
			msg += alwaysCounter + " always read params added)";
			Log.debug(SessionData.class, msg);
		} else {
			Log.warn(SessionData.class, "Did not find unit in unit-table, nothing exists on this unit");
		}
		sessionData.setFromDB(valueMap);

	}

	public String getSoftwareVersion() {
		CPEParameters cpeParams = getCpeParameters();
		if (cpeParams != null)
			return cpeParams.getValue(cpeParams.SOFTWARE_VERSION);
		return null;
	}

	public void setSoftwareVersion(String softwareVersion) {
		CPEParameters cpeParams = getCpeParameters();
		if (cpeParams != null) {
			cpeParams.putPvs(cpeParams.SOFTWARE_VERSION, new ParameterValueStruct(cpeParams.SOFTWARE_VERSION, softwareVersion));
		}
	}

	public boolean lastProvisioningOK() {
		return getParameterKey().isEqual() && getCommandKey().isEqual();
	}

	public boolean isDiagnosticsComplete() {
		return diagnosticsComplete;
	}

	public void setDiagnosticsComplete(boolean diagnosticsComplete) {
		this.diagnosticsComplete = diagnosticsComplete;
	}

	public Long getSecondsToNextJob() {
		return secondsToNextJob;
	}

	public void setSecondsToNextJob(long secondsToNextJob) {
		this.secondsToNextJob = secondsToNextJob;
	}

	public InformParameters getInformParameters() {
		return informParameters;
	}

	public void setInformParameters(InformParameters informParameters) {
		this.informParameters = informParameters;
	}

	public boolean isJobUnderExecution() {
		return jobUnderExecution;
	}

	public void setJobUnderExecution(boolean jobUnderExecution) {
		this.jobUnderExecution = jobUnderExecution;
	}

	@Override
	public PIIDecision getPIIDecision() {
		if (piiDecision == null)
			piiDecision = new PIIDecision(this);
		return piiDecision;
	}

	@Override
	public void setSerialNumber(String serialNumber) {
		this.serialNumber = serialNumber;
	}

	@Override
	public String getSerialNumber() {
		return serialNumber;
	}

	@Override
	public void setMac(String mac) {
		this.mac = mac;

	}

	@Override
	public String getMac() {
		return mac;
	}

	@Override
	public ProvisioningMessage getProvisioningMessage() {
		return provisioningMessage;
	}

	@Override
	public void setProvisioningMessage(ProvisioningMessage provisioningMessage) {
		this.provisioningMessage = provisioningMessage;
	}

	public CommandKey getCommandKey() {
		return commandKey;
	}

	public void setCommandKey(CommandKey commandKey) {
		this.commandKey = commandKey;
	}

	public Download getDownload() {
		return download;
	}

	public void setDownload(Download download) {
		this.download = download;
	}

	public boolean discoverUnittype() {
		if (oweraParameters != null && oweraParameters.getValue(SystemParameters.DISCOVER) != null && oweraParameters.getValue(SystemParameters.DISCOVER).equals("1"))
			return true;
		else if (oweraParameters == null)
			Log.debug(SessionData.class, "oweraParameters not found in discoverUnittype()");
		else if (oweraParameters.getValue(SystemParameters.DISCOVER) == null)
			Log.debug(SessionData.class, "DISCOVER parameter not found of value is null in discoverUnittype() ");
		else
			Log.debug(SessionData.class, "DISCOVER parameter value is " + oweraParameters.getValue(SystemParameters.DISCOVER) + " in discoverUnittype()");
		return false;
	}

}
