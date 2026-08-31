package com.amazon.oms.purge.api;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.yantra.interop.japi.YIFApi;
import com.yantra.ycp.japi.util.YCPBaseAgent;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class is to purge the Container Tracking Milestone from AMZ_CONN_CONTAINER_MILESTONES Custom table,
 * Purge the record which satisfy StatusTs<=CurrentDate-RetentionDays and corresponding Shipment is purged.
 */

public class AmzPurgeMilestonesDataAgent extends YCPBaseAgent{
	protected static YIFApi api = null;
	private static YFCLogCategory logger = YFCLogCategory.instance(AmzPurgeMilestonesDataAgent.class);

	/*
	 * This method implements the getJob to get eligible records from
	 * AMZ_CONN_CONTAINER_MILESTONES table to purge
	 */
	@Override
	public List<Document> getJobs(YFSEnvironment env, Document criteria, Document lastMessageCreated) throws Exception {
		logger.beginTimer("AmzPurgeMilestonesDataAgent.getJobs start");
		logger.info("AmzPurgeMilestonesDataAgent.getJobs start");
		logger.debug("criteria Document is :: " + AmzXMLUtil.getString(criteria));
		logger.debug("lastMessageCreated is :: " + AmzXMLUtil.getString(lastMessageCreated));

		String strLastKey = "";
		List<Document> listDocuments = new ArrayList<>();
		Element criteriaElement = criteria.getDocumentElement();
		int iNoRecordElgToPurge = 0;

		if (!YFCCommon.isVoid(lastMessageCreated)) {

			logger.debug("AmzPurgeMilestonesDataAgent.getJobs :lastMessageCreated:"
					+ AmzXMLUtil.getString(lastMessageCreated));

			Element element = lastMessageCreated.getDocumentElement();
			strLastKey = element.getAttribute(criteriaElement.getAttribute(AmzLiterals.A_TABLE_KEY_XML_NAME));
		} else {

			logger.debug("AmzPurgeMilestonesDataAgent.getJobs :lastMessageCreated:null");

		}

		Document docAmzConatinerMilestonesOut = getAmzConnConatinerMilestonesListOutput(env, criteriaElement,
				strLastKey);
		if (!YFCCommon.isVoid(docAmzConatinerMilestonesOut)) {
			List<String> amzElgTrackingNoListToPurge = new ArrayList<>();
			Element eleAmzConnConatinerMilestonesList = docAmzConatinerMilestonesOut.getDocumentElement();
			NodeList nlAmzConnConatinerMilestones = eleAmzConnConatinerMilestonesList.getChildNodes();
			checkRespectiveTrackingNoShipment(env, nlAmzConnConatinerMilestones, amzElgTrackingNoListToPurge);
			logger.debug("Eligible TrackingNo List to Purge is: " + amzElgTrackingNoListToPurge);
			String strTableKey = null;
			int noOfRecords = nlAmzConnConatinerMilestones.getLength();
			for (int i = 0; i < noOfRecords; i++) {
				Element eleAmzConnConatinerMilestones = (Element) nlAmzConnConatinerMilestones.item(i);
				strTableKey = eleAmzConnConatinerMilestones
						.getAttribute(criteriaElement.getAttribute(AmzLiterals.A_TABLE_KEY_XML_NAME));
				String strTrackingNo = eleAmzConnConatinerMilestones.getAttribute(AmzLiterals.A_TRACKING_NO);
				logger.debug("getJobs: TrackingNo is: " + strTrackingNo);
				if (!YFCObject.isVoid(strTableKey) && !YFCObject.isVoid(strTrackingNo)
						&& amzElgTrackingNoListToPurge.contains(strTrackingNo)) {
					iNoRecordElgToPurge = iNoRecordElgToPurge + 1;
					listDocuments.add(createExecuteJobDocument(strTableKey, criteriaElement));
				} else {

					logger.debug("TableKey returned null Or MilestoneKey is not eligible to purge");

				}
			}
		}
		logger.info("Purging "+iNoRecordElgToPurge+ " Record from AmzConnContainerMilestones Table");
		logger.info("AmzPurgeMilestonesDataAgent.getJobs method:end");
		logger.endTimer("AmzPurgeMilestonesDataAgent.getJobs method:end");

		return listDocuments;
	}

	/*
	 * This method Check check the Shipment of corresponding TrackingNo exists or
	 * not, If Shipment corresponding to the TrackingNo does not exists then add the
	 * Tracking into the amzElgTrackingNoListToPurge List to Purge
	 */
	private void checkRespectiveTrackingNoShipment(YFSEnvironment env, NodeList nlTableDetails,
			List<String> amzElgTrackingNoListToPurge) {
		logger.beginTimer("AmzPurgeMilestonesDataAgent.checkRespectiveTrackingNoShipment start");
		logger.info("AmzPurgeMilestonesDataAgent.checkRespectiveTrackingNoShipment start");
		int noOfRecords = nlTableDetails.getLength();
		List<String> amzTrackingNoList = new ArrayList<>();
		for (int i = 0; i < noOfRecords; i++) {
			Element eleDetail = (Element) nlTableDetails.item(i);
			String strTrackingNo = eleDetail.getAttribute(AmzLiterals.A_TRACKING_NO);
			logger.debug("checkRespectiveTrackingNoShipment: TrackingNo is: " + strTrackingNo);
			if (!amzTrackingNoList.contains(strTrackingNo)) {
				amzTrackingNoList.add(strTrackingNo);
				Document inDocGetShipList = AmzXMLUtil.createDocument(AmzLiterals.STR_SHIPMENT);
				Element eleInShipment = inDocGetShipList.getDocumentElement();
				Element eleInContainers = AmzXMLUtil.createChild(eleInShipment, AmzLiterals.ELE_CONTAINERS);
				Element eleInContainer = AmzXMLUtil.createChild(eleInContainers, AmzLiterals.ELE_CONTAINER);
				eleInContainer.setAttribute(AmzLiterals.A_TRACKING_NO, strTrackingNo);
				logger.debug("checkRespectiveTrackingNoShipment: getShipmentList input is: "
						+ AmzXMLUtil.getString(inDocGetShipList));
				Document outDocGetShipList = AmzCommonUtil.invokeAPI(env,
						AmzCommonConstants.TEMPLATE_GET_SHIPMENT_LIST_FOR_TRACKING_MILESTONE_PURGE,
						AmzCommonConstants.API_GET_SHIPMENT_LIST, inDocGetShipList);
				logger.debug("checkRespectiveTrackingNoShipment: getShipmentList output is: "
						+ AmzXMLUtil.getString(outDocGetShipList));
				Element eleoutShipments = outDocGetShipList.getDocumentElement();
				if (!YFCObject.isVoid(eleoutShipments)) {
					Element eleOutShipment = AmzXMLUtil.getChildElement(eleoutShipments, AmzLiterals.STR_SHIPMENT);
					if (!YFCObject.isVoid(eleOutShipment)
							&& !YFCObject.isVoid(eleOutShipment.getAttribute(AmzLiterals.ATTR_SHIPMENT_KEY))) {
						logger.debug(
								"Shipment exist associated to TrakingNo Hence Not Puring the record with TrackingNo: "
										+ strTrackingNo);
					} else {
						amzElgTrackingNoListToPurge.add(strTrackingNo);
						logger.debug(
								"Shipment does not exist associated to TrakingNo Hence Puring the record with TrackingNo: "
										+ strTrackingNo);
					}

				}

			}
		}
		logger.info("AmzPurgeMilestonesDataAgent.checkRespectiveTrackingNoShipment method:end");
		logger.endTimer("AmzPurgeMilestonesDataAgent.checkRespectiveTrackingNoShipment method:end");
	}

	/*
	 * This method get the Container Milestone records from the
	 * AMZ_CONN_CONTAINER_MILESTONES tables which are eligible to purge according to
	 * the No of retention days, Number Records To Buffer,
	 */
	private Document getAmzConnConatinerMilestonesListOutput(YFSEnvironment env, Element criteriaElement,
			String strLastKey) {
		logger.beginTimer("AmzPurgeMilestonesDataAgent.getAmzConnConatinerMilestonesListOutput start");
		logger.info("AmzPurgeMilestonesDataAgent.getAmzConnConatinerMilestonesListOutput start");
		logger.debug("criteriaElement in AmzPurgeMilestonesDataAgent.getAmzConnConatinerMilestonesListOutput is "
				+ AmzXMLUtil.getElementXMLString(criteriaElement));
		logger.debug(
				"strLastKey in AmzPurgeMilestonesDataAgent.getAmzConnConatinerMilestonesListOutput is " + strLastKey);

		Document outDocAmzConnConatinerMilestonesList = null;
		String numRecordsToBuffer = criteriaElement.getAttribute(AmzLiterals.A_NUM_RECORDE_TO_BUFFER);
		String retentionDays = criteriaElement.getAttribute(AmzLiterals.A_RETENTION_DAYS);

		String strCriteriaId = criteriaElement.getAttribute(AmzLiterals.A_CRITERIA_ID);

		if (AmzCommonConstants.STR_MILESTONES_TABLE.equalsIgnoreCase(strCriteriaId)) {
			outDocAmzConnConatinerMilestonesList = frameAndInvokeGetDetailsForGC(env, retentionDays, strLastKey,
					numRecordsToBuffer);
		}

		logger.debug("Output of AmzConnConatinerMilestonesList is :: "
				+ AmzXMLUtil.getString(outDocAmzConnConatinerMilestonesList));
		logger.info("AmzPurgeMilestonesDataAgent.getAmzConnConatinerMilestonesListOutput method:end");
		logger.endTimer("AmzPurgeMilestonesDataAgent.getAmzConnConatinerMilestonesListOutput method:end");
		return outDocAmzConnConatinerMilestonesList;
	}

	/*
	 * This method frame the input XML to invoke the
	 * AmzGetAmzConnContainerMilestonesList service, to get eligible records to
	 * purge according to StatusTs DateRange
	 */
	private Document frameAndInvokeGetDetailsForGC(YFSEnvironment env, String retentionDays, String strLastKey,
			String numRecordsToBuffer) {
		logger.beginTimer("AmzPurgeMilestonesDataAgent.frameAndInvokeGetDetailsForGC start");
		logger.info("AmzPurgeMilestonesDataAgent.frameAndInvokeGetDetailsForGC start");
		Document docAmzGetConatinerMilestones = AmzXMLUtil.createDocument(AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
		Element eleGetConatinerMilestones = docAmzGetConatinerMilestones.getDocumentElement();
		if (!YFCObject.isVoid(numRecordsToBuffer)) {
			eleGetConatinerMilestones.setAttribute(AmzLiterals.A_MAX_RECORDS, numRecordsToBuffer);
		}

		eleGetConatinerMilestones.setAttribute(AmzLiterals.A_STATUS_TS_QRY_TYPE, AmzLiterals.A_DATERANGE);
		eleGetConatinerMilestones.setAttribute(AmzLiterals.A_TO_STATUS_TS, getDateWithDaysAdded(retentionDays)); // current
		// date -
		// retentionDays
		if (!YFCObject.isVoid(strLastKey)) {
			eleGetConatinerMilestones.setAttribute(AmzLiterals.A_MILESTONE_KEY, strLastKey);
			eleGetConatinerMilestones.setAttribute(AmzLiterals.A_MILESTONE_KEY_QRY_TYPE, AmzCommonConstants.STR_VAL_GT);
		}
		Element eleOrderBy = AmzXMLUtil.createChild(eleGetConatinerMilestones, AmzLiterals.E_ORDER_BY);
		Element eleAttribute = AmzXMLUtil.createChild(eleOrderBy, AmzLiterals.E_ATTRIBUTE);
		eleAttribute.setAttribute(AmzLiterals.A_COMPLEX_QUERY_NAME, AmzLiterals.A_MILESTONE_KEY);
		eleAttribute.setAttribute(AmzLiterals.A_DESC, AmzCommonConstants.STR_VAL_N);

		logger.debug(
				"Input to get AmzConnConatinerMilestones is :: " + AmzXMLUtil.getString(docAmzGetConatinerMilestones));
		logger.info("AmzPurgeMilestonesDataAgent.frameAndInvokeGetDetailsForGC method:end");
		logger.endTimer("AmzPurgeMilestonesDataAgent.frameAndInvokeGetDetailsForGC method:end");
		return AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_GET_AMZ_CONN_CONTAINER_MILESTONES_LIST,
				docAmzGetConatinerMilestones);
	}

	/*
	 * This method get the Date till which StatusTs Date records can be purge from
	 * the AMZ_CONN_CONTAINER_MILESTONES table according to the number of retention
	 * days
	 */
	private String getDateWithDaysAdded(String daysToAdd) {
		logger.beginTimer("AmzPurgeMilestonesDataAgent.getDateWithDaysAdded start");
		logger.info("AmzPurgeMilestonesDataAgent.getDateWithDaysAdded start");
		SimpleDateFormat sdf = new SimpleDateFormat(AmzCommonConstants.STR_DATE_FOR_YYYYMMDD);
		Calendar currentCal = new GregorianCalendar();
		if (daysToAdd != null && !"0".equals(daysToAdd)) {
			currentCal.add(Calendar.DATE, Integer.parseInt(daysToAdd) * -1);
		}
		String sNewDate = sdf.format(currentCal.getTime());
		logger.info("AmzPurgeMilestonesDataAgent.getDateWithDaysAdded method:end");
		logger.endTimer("AmzPurgeMilestonesDataAgent.getDateWithDaysAdded method:end");
		return sNewDate;
	}

	/*
	 * This method create the document and return and add document to the
	 * ListDocument to invoke the executeJob to purge the records
	 */
	private static Document createExecuteJobDocument(String strTableKey, Element criteriaElement) {
		logger.beginTimer("AmzPurgeMilestonesDataAgent.createExecuteJobDocument start");
		logger.info("AmzPurgeMilestonesDataAgent.createExecuteJobDocument start");
		String strCriteriaId = criteriaElement.getAttribute(AmzLiterals.A_CRITERIA_ID);
		String strDelServiceToInvoke = criteriaElement.getAttribute(AmzLiterals.A_SERVICE_TO_INVOKE);
		Document docAmzConatinerMilestones = null;
		if (AmzCommonConstants.STR_MILESTONES_TABLE.equalsIgnoreCase(strCriteriaId)) {
			docAmzConatinerMilestones = AmzXMLUtil.createDocument(AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
			docAmzConatinerMilestones.getDocumentElement().setAttribute(AmzLiterals.A_MILESTONE_KEY, strTableKey);
			docAmzConatinerMilestones.getDocumentElement().setAttribute(AmzLiterals.A_SERVICE_TO_INVOKE,
					strDelServiceToInvoke);
		} else {
			docAmzConatinerMilestones = AmzXMLUtil.createDocument(AmzCommonConstants.STR_EMPTY);
		}
		logger.info("AmzPurgeMilestonesDataAgent.createExecuteJobDocument method:end");
		logger.endTimer("AmzPurgeMilestonesDataAgent.createExecuteJobDocument method:end");
		return docAmzConatinerMilestones;
	}

	/*
	 * This method implements the executeJob logic to purge the all eligible records
	 * from the AMZ_CONN_CONTAINER_MILESTONES custom table by invoking the delete
	 * api
	 */
	@Override
	public void executeJob(YFSEnvironment env, Document msgToProcess) throws Exception {

		logger.beginTimer("AmzPurgeMilestonesDataAgent.executeJob method:start");
		logger.info("AmzPurgeMilestonesDataAgent.executeJob method:start");

		if (!YFCCommon.isVoid(msgToProcess)) {

			logger.debug(
					"Input to AmzPurgeMilestonesDataAgent.executeJob is : " + AmzXMLUtil.getString(msgToProcess));

			Element eleRootElement = msgToProcess.getDocumentElement();
			AmzCommonUtil.invokeService(env, eleRootElement.getAttribute(AmzLiterals.A_SERVICE_TO_INVOKE),
					msgToProcess);
		}
		logger.info("AmzPurgeMilestonesDataAgent.executeJob method:end");
		logger.beginTimer("AmzPurgeMilestonesDataAgent.executeJob method:end");

	}



}
