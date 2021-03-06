package com.owera.xaps.tr069.methods;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.owera.common.db.NoAvailableConnectionException;
import com.owera.xaps.base.Log;
import com.owera.xaps.dbi.FileType;
import com.owera.xaps.dbi.UnittypeParameters;
import com.owera.xaps.dbi.tr069.TestCaseParameter;
import com.owera.xaps.dbi.tr069.TestCaseParameter.TestCaseParameterType;
import com.owera.xaps.dbi.util.ProvisioningMessage;
import com.owera.xaps.dbi.util.ProvisioningMessage.ProvOutput;
import com.owera.xaps.dbi.util.ProvisioningMode;
import com.owera.xaps.tr069.CPEParameters;
import com.owera.xaps.tr069.HTTPReqResData;
import com.owera.xaps.tr069.ParameterKey;
import com.owera.xaps.tr069.Properties;
import com.owera.xaps.tr069.SessionData;
import com.owera.xaps.tr069.SessionData.Download;
import com.owera.xaps.tr069.exception.TR069Exception;
import com.owera.xaps.tr069.exception.TR069ExceptionShortMessage;
import com.owera.xaps.tr069.test.system2.TestUnit;
import com.owera.xaps.tr069.test.system2.TestUnitCache;
import com.owera.xaps.tr069.test.system2.Util;
import com.owera.xaps.tr069.xml.Body;
import com.owera.xaps.tr069.xml.EmptyResponse;
import com.owera.xaps.tr069.xml.Header;
import com.owera.xaps.tr069.xml.ParameterAttributeStruct;
import com.owera.xaps.tr069.xml.ParameterList;
import com.owera.xaps.tr069.xml.ParameterValueStruct;
import com.owera.xaps.tr069.xml.ParameterValueStructComparator;
import com.owera.xaps.tr069.xml.Response;
import com.owera.xaps.tr069.xml.TR069TransactionID;

/**
 * The class is responsible for creating a suitable response to the CPE. This
 * response could be a TR-069 request or a TR-069 response.
 * 
 * @author morten
 * 
 */
public class HTTPResponseCreator {

	@SuppressWarnings(value = { "unused" })
	private static Response buildEM(HTTPReqResData reqRes) {
		return new EmptyResponse();
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildRE(HTTPReqResData reqRes) {
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		TR069TransactionID tr069ID = reqRes.getTR069TransactionID();
		Header header = new Header(tr069ID, null, null);
		Body body = new REreq();
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildFR(HTTPReqResData reqRes) {
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		TR069TransactionID tr069ID = reqRes.getTR069TransactionID();
		Header header = new Header(tr069ID, null, null);
		Body body = new FRreq();
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildTC(HTTPReqResData reqRes) {
		TR069TransactionID tr069ID = reqRes.getTR069TransactionID();
		Header header = new Header(tr069ID, null, null);
		Body body = new TCres();
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildIN(HTTPReqResData reqRes) {
		TR069TransactionID tr069ID = reqRes.getTR069TransactionID();
		Header header = new Header(tr069ID, null, null);
		Body body = new INres();
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildGRMReq(HTTPReqResData reqRes) {
		TR069TransactionID tr069ID = reqRes.getTR069TransactionID();
		Header header = new Header(tr069ID, null, null);
		Body body = new GRMreq();
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildGRMRes(HTTPReqResData reqRes) {
		TR069TransactionID tr069ID = reqRes.getTR069TransactionID();
		Header header = new Header(tr069ID, null, null);
		Body body = new GRMres();
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildCU(HTTPReqResData reqRes) {
		TR069TransactionID tr069ID = reqRes.getTR069TransactionID();
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		Header header = new Header(reqRes.getTR069TransactionID(), null, null);
		SessionData sessionData = reqRes.getSessionData();
		String keyRoot = sessionData.getKeyRoot();
		String unitId = sessionData.getUnitId();
		Body body = new CUreq(keyRoot, unitId);
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildGPN(HTTPReqResData reqRes) {
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		Header header = new Header(reqRes.getTR069TransactionID(), null, null);
		String keyRoot = reqRes.getSessionData().getKeyRoot();
		Body body = new GPNreq(keyRoot);
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildGPA(HTTPReqResData reqRes) {
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		Header header = new Header(reqRes.getTR069TransactionID(), null, null);
		String keyRoot = reqRes.getSessionData().getKeyRoot();
		List<ParameterAttributeStruct> parameterAttributeList = new ArrayList<ParameterAttributeStruct>();
		if (Util.testEnabled(reqRes, false)) {
			// TODO:TF - build GPA - completed
			TestUnit tu = TestUnitCache.get(reqRes.getSessionData().getUnitId());
			if (tu != null) {
				List<TestCaseParameter> params = tu.getCurrentCase().getParams();
				for (TestCaseParameter param : params) {
					if (param.getType() == TestCaseParameterType.GET) {
						parameterAttributeList.add(new ParameterAttributeStruct(param.getUnittypeParameter().getName(), param.getNotification()));
					}
				}
			}
		}
		Body body = new GPAreq(parameterAttributeList);
		return new Response(header, body);
	}

	private static boolean isOldPingcomDevice(String unitId) {
		if (unitId.contains("NPA201E"))
			return true;
		if (unitId.contains("RGW208EN"))
			return true;
		if (unitId.contains("NPA101E"))
			return true;
		return false;
	}

	private static void addCPEParameters(SessionData sessionData) throws SQLException, NoAvailableConnectionException {
		Map<String, ParameterValueStruct> paramValueMap = sessionData.getFromDB();
		CPEParameters cpeParams = sessionData.getCpeParameters();
		UnittypeParameters utps = sessionData.getUnittype().getUnittypeParameters();

		// If device is not old Ping Communication device (NPA201E or RGW208EN) and vendor config file is not explicitely turned off,
		// then we may ask for VendorConfigFile object.
		boolean useVendorConfigFile = !isOldPingcomDevice(sessionData.getUnitId()) && !Properties.isIgnoreVendorConfigFile(sessionData);
		if (useVendorConfigFile)
			Log.debug(HTTPResponseCreator.class, "VendorConfigFile object will be requested (default behavior)");
		else
			Log.debug(HTTPResponseCreator.class, "VendorConfigFile object will not be requested. (quirk behavior: old Pingcom device or quirk enabled)");

		int counter = 0;
		for (String key : cpeParams.getCpeParams().keySet()) {
			if (key.endsWith(".") && useVendorConfigFile) {
				paramValueMap.put(key, new ParameterValueStruct(key, "ExtraCPEParam"));
				counter++;
			} else if (paramValueMap.get(key) == null && utps.getByName(key) != null) {
				paramValueMap.put(key, new ParameterValueStruct(key, "ExtraCPEParam"));
				counter++;
			}
		}
		Log.debug(HTTPResponseCreator.class, counter + " cpe-param (not found in database, but of special interest to ACS) added to the GPV-request");
	}

	/**
	 * Special treatment for PeriodicInformInterval, we want to get that
	 * parameter from the CPE, regardless of what parameteres we have in the
	 * database. That's because we have 2 different ways to set it in the
	 * database, the standard and the Owera-way.
	 * @throws NoAvailableConnectionException 
	 * @throws SQLException 
	 */
	@SuppressWarnings(value = { "unused" })
	private static Response buildGPV(HTTPReqResData reqRes) throws SQLException, NoAvailableConnectionException {
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		Header header = new Header(reqRes.getTR069TransactionID(), null, null);
		SessionData sessionData = reqRes.getSessionData();
		ProvisioningMode mode = sessionData.getUnit().getProvisioningMode();
		List<ParameterValueStruct> parameterValueList = new ArrayList<ParameterValueStruct>();
		if (Util.testEnabled(reqRes, false)) {
			// TODO:TF - build GPV - completed
			TestUnit tu = TestUnitCache.get(sessionData.getUnitId());
			if (tu != null) {
				List<TestCaseParameter> params = tu.getCurrentCase().getParams();
				for (TestCaseParameter param : params) {
					if (param.getType() == TestCaseParameterType.GET) {
						ParameterValueStruct pvs = new ParameterValueStruct(param.getUnittypeParameter().getName(), "");
						parameterValueList.add(pvs);
					}
				}
			}
			//		} else if (mode == ProvisioningMode.INSPECTION) {
			//			UnittypeParameters utps = sessionData.getUnittype().getUnittypeParameters();
			//			Map<String, ParameterValueStruct> paramValueMap = sessionData.getFromDB();
			//			addCPEParameters(sessionData);
			//			int inspectionCount = 0;
			//			for (UnittypeParameter utp : utps.getUnittypeParameters()) {
			//				if (utp.getFlag().isInspection() || paramValueMap.get(utp.getName()) != null) {
			//					inspectionCount++;
			//					ParameterValueStruct pvs = new ParameterValueStruct(utp.getName(), "");
			//					parameterValueList.add(pvs);
			//				}
			//			}
			//			Log.debug(HTTPResponseCreator.class, "Added " + inspectionCount + " inspection params to GPV-req");
			//			Log.debug(HTTPResponseCreator.class, "Asks for " + parameterValueList.size() + " parameters in GPV-req");
			//			Collections.sort(parameterValueList, new ParameterValueStructComparator());
			//		} else if (mode == ProvisioningMode.EXTRACTION) {
		} else if (mode == ProvisioningMode.READALL) {
			Log.debug(HTTPResponseCreator.class, "Asks for all params (" + sessionData.getKeyRoot() + "), since in " + ProvisioningMode.READALL.toString() + " mode");
			ParameterValueStruct pvs = new ParameterValueStruct(sessionData.getKeyRoot(), "");
			parameterValueList.add(pvs);
		} else { // mode == ProvisioningMode.PERIODIC
			//			List<RequestResponseData> reqResList = sessionData.getReqResList();
			String previousMethod = sessionData.getPreviousResponseMethod();
			if (Properties.isUnitDiscovery(sessionData) || previousMethod.equals(TR069Method.GET_PARAMETER_VALUES)) {
				Log.debug(HTTPResponseCreator.class, "Asks for all params (" + sessionData.getKeyRoot() + "), either because unitdiscovery-quirk or prev. GPV failed");
				ParameterValueStruct pvs = new ParameterValueStruct(sessionData.getKeyRoot(), "");
				parameterValueList.add(pvs);
			} else {
				Map<String, ParameterValueStruct> paramValueMap = sessionData.getFromDB();
				UnittypeParameters utps = sessionData.getUnittype().getUnittypeParameters();
				addCPEParameters(sessionData);
				for (Entry<String, ParameterValueStruct> entry : paramValueMap.entrySet()) {
					//					if (utps.getByName(entry.getKey()) != null) {
					parameterValueList.add(entry.getValue());
					//					} else {
					//						Log.debug(HTTPResponseCreator.class, "Skipped " + entry.getKey() + " in GPV, since the unittype parameter did not exist");
					//					}
				}
				Log.debug(HTTPResponseCreator.class, "Asks for " + parameterValueList.size() + " parameters in GPV-req");
				Collections.sort(parameterValueList, new ParameterValueStructComparator());
			}
		}
		sessionData.setRequestedCPE(parameterValueList);
		Body body = new GPVreq(parameterValueList);
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildSPV(HTTPReqResData reqRes) throws NoSuchAlgorithmException, SQLException, NoAvailableConnectionException {
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		Header header = new Header(reqRes.getTR069TransactionID(), null, null);
		Body body = null;
		ParameterList paramList = new ParameterList();
		if (Util.testEnabled(reqRes, false)) {
			// TODO:TF - build SPV - completed
			TestUnit tu = TestUnitCache.get(reqRes.getSessionData().getUnitId());
			if (tu != null) {
				List<TestCaseParameter> params = tu.getCurrentCase().getParams();
				for (TestCaseParameter param : params) {
					if (param.getType() == TestCaseParameterType.SET) {
						ParameterValueStruct pvs = new ParameterValueStruct(param.getUnittypeParameter().getName(), param.getValue());
						if (param.getDataModelParameter() == null)
							Log.error(HTTPResponseCreator.class, "Could not find datamodel parameter for " + param.getUnittypeParameter().getName());
						else
							pvs.setType(param.getDataModelParameter().getDatatype().getXsdType());
						paramList.addParameterValueStruct(pvs);
					}
				}
				reqRes.getSessionData().setToCPE(paramList);
			}
		}
		paramList = reqRes.getSessionData().getToCPE();
		ParameterKey pk = new ParameterKey();
		if (!Properties.isParameterkeyQuirk(reqRes.getSessionData()))
			pk.setServerKey(reqRes);
		body = new SPVreq(paramList.getParameterValueList(), pk.getServerKey());
		Log.notice(HTTPResponseCreator.class, "Sent to CPE: " + paramList.getParameterValueList().size() + " parameters.");
		reqRes.getSessionData().getProvisioningMessage().setParamsWritten(paramList.getParameterValueList().size());
		return new Response(header, body);
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildDO(HTTPReqResData reqRes) throws SQLException, NoAvailableConnectionException {
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		Header header = new Header(reqRes.getTR069TransactionID(), null, null);
		SessionData sessionData = reqRes.getSessionData();
		Download download = sessionData.getDownload();
		ProvisioningMessage pm = sessionData.getProvisioningMessage();
		String downloadType = null;
		String tn = download.getFile().getTargetName();
		String commandKey = download.getFile().getVersion();
		if (download.getFile().getType() == FileType.SOFTWARE) {
			downloadType = DOreq.FILE_TYPE_FIRMWARE;
			pm.setProvOutput(ProvOutput.SOFTWARE);
		}
		if (download.getFile().getType() == FileType.TR069_SCRIPT) {
			downloadType = DOreq.FILE_TYPE_CONFIG;
			pm.setProvOutput(ProvOutput.SCRIPT);
			//			String tfnPN = SystemParameters.getTR069ScriptParameterName(download.getFile().getTargetName(), TR069ScriptType.TargetFileName);
			//			tfn = sessionData.getOweraParameters().getValue(tfnPN);
			//			commandKey = download.getFile().getVersion();
		}
		String version = download.getFile().getVersion();
		pm.setFileVersion(version);
		Body body = new DOreq(download.getUrl(), downloadType, tn, download.getFile().getLength(), commandKey);
		return new Response(header, body);
	}

	public static void createResponse(HTTPReqResData reqRes) throws TR069Exception {
		try {
			String methodName = reqRes.getResponse().getMethod();
			Response response = null;
			HTTPResponseAction resAction = TR069Method.responseMap.get(methodName);
			if (resAction != null)
				response = (Response) resAction.getCreateResponseMethod().invoke(null, reqRes);
			else {
				response = new EmptyResponse();
				Log.error(HTTPResponseCreator.class, "The methodName " + methodName + " has no corresponding ResponseAction-method");
			}
			String responseStr = response.toXml();
			Log.conversation(reqRes.getSessionData(), "=============== FROM ACS ============\n" + responseStr + "\n");
			reqRes.getResponse().setXml(responseStr);
		} catch (Throwable t) {
			throw new TR069Exception("Not possible to create HTTP-response (to the TR-069 client)", TR069ExceptionShortMessage.MISC, t);
			//			reqRes.setThrowable(t);
			//			Log.error(HTTPResponseCreator.class, "The response with methodname " + reqRes.getResponse().getMethod() + " failed, return EmptyResponse", t);
			//			reqRes.getResponse().setXml("");
		}
	}

	@SuppressWarnings(value = { "unused" })
	private static Response buildSPA(HTTPReqResData reqRes) throws NoSuchAlgorithmException, SQLException, NoAvailableConnectionException {
		if (reqRes.getTR069TransactionID() == null)
			reqRes.setTR069TransactionID(new TR069TransactionID("OWERA-" + System.currentTimeMillis()));
		Header header = new Header(reqRes.getTR069TransactionID(), null, null);
		Body body = null;
		List<ParameterAttributeStruct> attributes = new ArrayList<ParameterAttributeStruct>();
		if (Util.testEnabled(reqRes, false)) {
			// TODO:TF - build SPA - completed
			TestUnit tu = TestUnitCache.get(reqRes.getSessionData().getUnitId());
			if (tu != null) {
				List<TestCaseParameter> params = tu.getCurrentCase().getParams();
				for (TestCaseParameter param : params) {
					if (param.getType() == TestCaseParameterType.SET) {
						attributes.add(new ParameterAttributeStruct(param.getUnittypeParameter().getName(), param.getNotification()));
					}
				}
				reqRes.getSessionData().setAttributesToCPE(attributes);
			}
		}
		attributes = reqRes.getSessionData().getAttributesToCPE();
		body = new SPAreq(attributes);
		Log.notice(HTTPResponseCreator.class, "Sent to CPE: " + attributes.size() + " attributes.");
		for (ParameterAttributeStruct pvs : attributes)
			Log.notice(HTTPResponseCreator.class, "\t" + pvs.getName() + " : " + pvs.getNotifcation());
		return new Response(header, body);
	}
}
