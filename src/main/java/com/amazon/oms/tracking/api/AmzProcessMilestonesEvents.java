package com.amazon.oms.tracking.api;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzEnterpriseBPIDXRefUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*This class process Package Milestone Tracker event message posted to internal queue AMZ.CONN.TRACKING.MILESTONE.Q
 * Invoked from AmzProcessTrackingUpdatesAsync Async service
 * Verify Package Milestone Tracker event message, query the amazon Order,
 * If any milestone event data in not present in the AMZ_CONN_CONTAINER_MILESTONES 
 * then add the milestone event date into custom table 
 * 
 * Input XML to this Class  
 * <?xml version="1.0" encoding="UTF-8"?>
		<Shipment AmazonOrderID="" IdempotencyKey="YWIzODlhZjktYzAxYy00YjRkLTk3MjItZjVlMjcxOGM5MjVmIzg1YWQwZjkxLWFmMmEtNjE5OS1mMDYxLWRiMTMzNGRjMjEwZQ==" 
		SubscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" 
		Resources="businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-GU8T-FB2RXF/carrier/ups/tracker/1Z4BD5B8F4915149298BF35705A5D346D3">
			<Containers>
        		<Container TrackingNo="1ZBAC8EA382C7C405EA32066B9F8BD2Csbfsv">
        		</Container>
			</Containers>
		</Shipment> 
 */

public class AmzProcessMilestonesEvents implements YIFCustomApi{
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessMilestonesEvents.class);
	HashMap<String, String> existingMilstoneMap = new HashMap<>();
	Document outDocGetContainerMileStoneList = null;
	String strAmzOrderNo = null;
	private Properties props;

	/*
	 * This method process package tracking milestone events message
	 */
	public Document processTrackingEvents(YFSEnvironment env, Document inDoc)
			throws Exception {
		logger.beginTimer("class: AmzProcessMilestonesEvents | method: processTrackingEvents -- Starts");
		logger.info("class: AmzProcessMilestonesEvents | method: processTrackingEvents -- Starts");
		logger.debug("class: AmzProcessMilestonesEvents | method: processTrackingEvents -- Starts inDoc is: "
				+ AmzXMLUtil.getString(inDoc));
		try {
			Element eleRoot = inDoc.getDocumentElement();

			strAmzOrderNo = eleRoot.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
			logger.debug("strAmzOrderNo is: " + strAmzOrderNo);
			String strEventType = eleRoot.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
			String strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, eleRoot.getAttribute("BusinessProductID"));

			Document getOrdInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
			Element ordInDocEle = getOrdInDoc.getDocumentElement();
			ordInDocEle.setAttribute("AmzOrderID", strAmzOrderNo);
			
			ordInDocEle.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,strEnterpriseCode );
			ordInDocEle.setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, strEventType);
			logger.debug("AmzProcessMilestonesEvents:processTrackingEvents getAmazonOrderDetails inDoc is: "
					+ AmzXMLUtil.getXMLString(getOrdInDoc));
			Document amzGetOrderOutDoc = AmzCommonUtil.invokeService(env,
					AmzCommonConstants.SERVICE_AMAZON_GET_ORDER_DETAILS, getOrdInDoc);
			logger.debug(" AmzProcessMilestonesEvents:processTrackingEvents getAmazonOrderDetails outDoc is: "
					+ AmzXMLUtil.getXMLString(amzGetOrderOutDoc));

			String strTrackingNo;
			strTrackingNo = AmzXMLUtil.getXpathAttribute(eleRoot, "Containers/Container/@TrackingNo");
			logger.debug("strTrackingNo is: " + strTrackingNo);
			if (!YFCObject.isVoid(strTrackingNo)) {
				Document getShpContainerListOut = invokeGetShipmentContainerList(env, strTrackingNo);
				logger.debug("getShpContainerList is: " + AmzXMLUtil.getXMLString(getShpContainerListOut));
				if (!YFCObject.isVoid(getShpContainerListOut)) {
					Element eleContainer = AmzXMLUtil.getChildElement(getShpContainerListOut.getDocumentElement(),
							AmzLiterals.E_CONTAINER);
					if (!YFCObject.isVoid(eleContainer)) {
						String strShpContainerKey = eleContainer.getAttribute(AmzLiterals.A_SHIPMENT_CONTAINER_KEY);
						logger.debug("strShpContainerKey is: " + strShpContainerKey);
						Element eleContExtn = AmzXMLUtil.getChildElement(eleContainer, AmzLiterals.E_EXTN);
						if (!YFCObject.isVoid(eleContExtn)) {
							String strAmzDeliveryId = eleContExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_DELIVERY_ID);
							logger.debug("strAmzDeliveryId is: " + strAmzDeliveryId);
							getAmzConnContainerMilestonesList(env, strAmzDeliveryId, strTrackingNo);
							prepareInputAndUpdateMilestoneInOMS(env, amzGetOrderOutDoc, strTrackingNo,
									getShpContainerListOut, strEventType, strAmzDeliveryId);
						}

					} else {
						YFSException ex = new YFSException();
						ex.setErrorCode("SHIPMENT_CONTAINER_NOT_FOUND");
						ex.setErrorDescription(
								"Shipment Container Not Found for the input TrackingNo=" + strTrackingNo);
						throw ex;
					}
				} else {
					YFSException ex = new YFSException();
					ex.setErrorCode("SHIPMENT_CONTAINER_NOT_FOUND");
					ex.setErrorDescription("Shipment Container Not Found for the input TrackingNo=" + strTrackingNo);
					logger.error("Exception in class: AmzProcessMilestonesEvents | method: processTrackingEvents : "
							+ ExceptionUtils.getStackTrace(ex));
					throw ex;
				}
			} else {
				logger.debug(
						"Tracking No is Blank To Process Package Milestones Event message received for Amazon Order:"
								+ strAmzOrderNo);
			}
		} catch (YFSException ex) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, ex.getErrorDescription());
			throw AmzCommonUtil.createException(ex);
		}

		logger.endTimer("class: AmzProcessMilestonesEvents | method: processTrackingEvents -- Ends");
		logger.info("class: AmzProcessMilestonesEvents | method: processTrackingEvents -- Ends");
		return inDoc;

	}

	private void prepareAndLogResponse(String processStatus, Document inDoc, String message)
			throws XPathExpressionException {

		logger.beginTimer("class: AmzProcessMilestonesEvents | method: prepareAndLogResponse -- Starts");

		String amazonOrderId = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
		String eventType = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
		String strTrackingNo = AmzXMLUtil.getXpathAttribute(inDoc.getDocumentElement(),
				"Containers/Container/@TrackingNo");
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_TRACKING_NO, strTrackingNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);

		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.endTimer("class: AmzProcessMilestonesEvents | method: prepareAndLogResponse -- Ends");

	}

	/*
	 * This method get the Package Milestone Tracking data from the OMS custom table
	 * with TrackingNo and DeliveryId.
	 */
	private void getAmzConnContainerMilestonesList(YFSEnvironment env, String strDeliveryId, String strTrackingNo) {
		logger.beginTimer("class: AmzProcessMilestonesEvents | method: getAmzConnContainerMilestonesList -- Starts");
		logger.info("class: AmzProcessMilestonesEvents | method: getAmzConnContainerMilestonesList -- Starts");
		if (!YFCObject.isVoid(strTrackingNo) && !YFCObject.isVoid(strDeliveryId)) {
			Document inDocGetAmzMilestoneList = AmzXMLUtil.createDocument(AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
			Element eleAmzContainerMilestone = inDocGetAmzMilestoneList.getDocumentElement();
			eleAmzContainerMilestone.setAttribute(AmzLiterals.A_AMAZON_DELIVERY_ID, strDeliveryId);
			eleAmzContainerMilestone.setAttribute(AmzLiterals.A_TRACKING_NO, strTrackingNo);
			logger.debug("AmzProcessMilestonesEvents.getAmzConnContainerMilestonesList inDocGetAmzMilestoneList is: "
					+ AmzXMLUtil.getString(inDocGetAmzMilestoneList));
			outDocGetContainerMileStoneList = AmzCommonUtil.invokeService(env,
					AmzCommonConstants.SERVICE_GET_AMZ_CONN_CONTAINER_MILESTONES_LIST, inDocGetAmzMilestoneList);
			logger.debug(
					"AmzProcessMilestonesEvents.getAmzConnContainerMilestonesList outDocGetContainerMileStoneList is: "
							+ AmzXMLUtil.getString(outDocGetContainerMileStoneList));
			Element eleContainerMilestoneList = outDocGetContainerMileStoneList.getDocumentElement();

			NodeList nConatinerMilestone = eleContainerMilestoneList
					.getElementsByTagName(AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
			for (int i = 0; i < nConatinerMilestone.getLength(); i++) {
				Element eleContainerMilestone = (Element) nConatinerMilestone.item(i);
				String strStatusCode = eleContainerMilestone.getAttribute(AmzLiterals.A_STATUS_CODE);
				logger.debug("strStatusCode is: " + strStatusCode);
				String strStatusTs = eleContainerMilestone.getAttribute(AmzLiterals.A_STATUS_TS);
				logger.debug("strStatusTs is: " + strStatusTs);

				existingMilstoneMap.put(strStatusTs, strStatusCode);
			}
		}
		logger.debug("existingMilstoneMap is: " + existingMilstoneMap);
		logger.endTimer("class: AmzProcessMilestonesEvents | method: getAmzConnContainerMilestonesList -- Ends");
		logger.info("class: AmzProcessMilestonesEvents | method: getAmzConnContainerMilestonesList -- Ends");
	}

	/*
	 * This method invoke the getShipmentConatinerList api with TrackingNo, to get
	 * the package container details
	 */
	private Document invokeGetShipmentContainerList(YFSEnvironment env, String strTrackingNo) {
		logger.beginTimer("class: AmzProcessMilestonesEvents | method: invokeGetShipmentContainerList -- Starts");
		logger.info("class: AmzProcessMilestonesEvents | method: invokeGetShipmentContainerList -- Starts");
		Document getShpContainerListDoc = AmzXMLUtil.createDocument(AmzLiterals.E_CONTAINER);
		Element eleContainer = getShpContainerListDoc.getDocumentElement();
		eleContainer.setAttribute(AmzLiterals.A_TRACKING_NO, strTrackingNo);
		logger.debug("AmzProcessMilestonesEvents.invokeGetShipmentContainerList getShpContainerListDoc is: "
				+ AmzXMLUtil.getString(getShpContainerListDoc));
		Document outDocGetShpContainerList = AmzCommonUtil.invokeAPI(env,
				AmzCommonConstants.TEMPLATE_GET_SHIP_CONTAINER_LIST_FOR_TRACKING_UPDATED,
				AmzCommonConstants.API_GET_SHIPMENT_CONTAINER_LIST, getShpContainerListDoc);
		logger.endTimer("class: AmzProcessMilestonesEvents | method: invokeGetShipmentContainerList -- Ends");
		logger.info("class: AmzProcessMilestonesEvents | method: invokeGetShipmentContainerList -- Ends");
		return outDocGetShpContainerList;
	}

	/*
	 * This method Prepare the Document to call the updateTrackingMilestones method
	 * To update package tracking milestone record in OMS
	 */
	private void prepareInputAndUpdateMilestoneInOMS(YFSEnvironment env, Document amzGetOrderOutDoc,
			String strTrackingNo, Document getShpContainerListOut, String strEventType, String sAmzDeliveryId)
			throws XPathExpressionException, ParseException {
		logger.beginTimer("class: AmzProcessMilestonesEvents | method: prepareInputAndUpdateMilestoneInOMS -- Starts");
		logger.info("class: AmzProcessMilestonesEvents | method: prepareInputAndUpdateMilestoneInOMS -- Starts");
		try {
			String strDeliveryId = null;
			Element eleContainers = getShpContainerListOut.getDocumentElement();
			String strShpContainerKey;
			strShpContainerKey = AmzXMLUtil.getXpathAttribute(eleContainers, "Container/@ShipmentContainerKey");
			String sContainerNo = AmzXMLUtil.getXpathAttribute(eleContainers, "Container/@ContainerNo");
			String sOrderNo = AmzXMLUtil.getXpathAttribute(eleContainers, "Container/Shipment/@OrderNo");
			String sShipmentNo = AmzXMLUtil.getXpathAttribute(eleContainers, "Container/Shipment/@ShipmentNo");
			String sEnterpriseCode = AmzXMLUtil.getXpathAttribute(eleContainers, "Container/Shipment/@EnterpriseCode");
			Document inDocPackageTracker = AmzXMLUtil.createDocument(AmzLiterals.E_PACKAGE_TRACKER);
			Element elePackageTracker = inDocPackageTracker.getDocumentElement();
			Element eleroot = amzGetOrderOutDoc.getDocumentElement();
			NodeList nDetails = AmzXMLUtil.getXpathNodes(eleroot, "data/order/packageInformation/details");
			int iDetailsLen = nDetails.getLength();
			for (int j = 0; j < iDetailsLen; j++) {
				Element eleDetails = (Element) nDetails.item(j);
				strDeliveryId = eleDetails.getAttribute(AmzLiterals.A_JS_ID);
				logger.debug("strDeliveryId is: " + strDeliveryId);
				Element packageTrackerEle = AmzXMLUtil.getChildElement(eleDetails, AmzLiterals.E_JS_PACKAGE_TRACKER);
				if (!YFCObject.isVoid(packageTrackerEle)) {
					Element packageTrackIdentEle = AmzXMLUtil.getChildElement(packageTrackerEle,
							AmzLiterals.E_JS_PACKAGE_TRACKER_IDENTIFIER);
					if (!YFCObject.isVoid(packageTrackIdentEle)) {
						String sTrackingNumber = packageTrackIdentEle.getAttribute(AmzLiterals.A_JS_TRACKING_NUMBER);
						logger.debug("sTrackingNumber is: " + sTrackingNumber);
						if (sTrackingNumber.equalsIgnoreCase(strTrackingNo)
								&& strDeliveryId.equalsIgnoreCase(sAmzDeliveryId)) {
							NodeList nMilestones = packageTrackerEle.getElementsByTagName(AmzLiterals.E_JS_MILESTONES);
							for (int i = 0; i < nMilestones.getLength(); i++) {
								Element eleMilestones = (Element) nMilestones.item(i);
								verifyEligibleMilestonesToAdd(eleMilestones, elePackageTracker, inDocPackageTracker);
							}
						}
					}
				}

			}
			NodeList nMilestonesList = elePackageTracker.getElementsByTagName("milestones");
			logger.debug("nMilestonesList Length is: " + nMilestonesList.getLength());
			if (nMilestonesList.getLength() > 0) {
				elePackageTracker.setAttribute(AmzLiterals.A_AMAZON_DELIVERY_ID, sAmzDeliveryId);
				elePackageTracker.setAttribute(AmzLiterals.A_SHIPMENT_CONTAINER_KEY, strShpContainerKey);
				elePackageTracker.setAttribute(AmzLiterals.A_TRACKING_NO, strTrackingNo);
				elePackageTracker.setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, strEventType);
				elePackageTracker.setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmzOrderNo);
				elePackageTracker.setAttribute(AmzLiterals.A_SHIPMENT_NO, sShipmentNo);
				elePackageTracker.setAttribute(AmzLiterals.A_ORDER_NO, sOrderNo);
				elePackageTracker.setAttribute(AmzLiterals.A_CONTAINER_NO, sContainerNo);
				elePackageTracker.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, sEnterpriseCode);
				logger.debug(
						"input document for updateTrackingMilestones is: " + AmzXMLUtil.getString(inDocPackageTracker));
				AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CONN_UPDATE_MILESTONES_RECORD_IN_OMS,
						inDocPackageTracker);
			}
		} catch (YFSException ex) {
			ex.setErrorCode("ERROR_UPDATING_MILESTONE_RECORD_IN_OMS");
			ex.setErrorDescription("Exception while updating Milestone in OMS with TrackingNo=" + strTrackingNo);
			logger.error(
					"Exception in class: AmzProcessMilestonesEvents | method: prepareInputAndUpdateMilestoneInOMS : "
							+ ExceptionUtils.getStackTrace(ex));
			prepareAndLogResponse(AmzLiterals.STR_ERROR, amzGetOrderOutDoc, ex.getErrorDescription());
			throw AmzCommonUtil.createException(ex);
		}
		logger.endTimer("class: AmzProcessMilestonesEvents | method: prepareInputAndUpdateMilestoneInOMS -- Ends");
		logger.info("class: AmzProcessMilestonesEvents | method: prepareInputAndUpdateMilestoneInOMS -- Ends");
	}

	/*
	 * This method verify the package tracking milestone record present in OMS
	 * against the get amazon order query to insert the milestone which are not
	 * present in OMS.
	 */
	private void verifyEligibleMilestonesToAdd(Element eleMilestones, Element elePackageTracker,
			Document inDocPackageTracker) throws ParseException {
		logger.beginTimer("class: AmzProcessMilestonesEvents | method: verifyEligibleMilestonesToAdd -- Starts");
		logger.info("class: AmzProcessMilestonesEvents | method: verifyEligibleMilestonesToAdd -- Starts");

		String strOccuredAt = eleMilestones.getAttribute(AmzLiterals.A_JS_OCCURRED_AT);
		logger.debug("strOccuredAt is: " + strOccuredAt);
		Element eleStatus = AmzXMLUtil.getChildElement(eleMilestones, AmzLiterals.E_JS_STATUS);
		String strStatusCode = eleStatus.getAttribute(AmzLiterals.A_JS_CODE);
		logger.debug("strStatusCode is: " + strStatusCode);
		Date occuredDate = null;
		SimpleDateFormat dateFormat = new SimpleDateFormat(AmzCommonConstants.STR_STERLING_DATE_TIME_FORMAT);
		boolean isElgMileStone = true;
		if (!YFCObject.isVoid(strOccuredAt) && !YFCObject.isVoid(strStatusCode)) {
			occuredDate = dateFormat.parse(strOccuredAt);
			logger.debug("strOccuredAt is: " + strOccuredAt);
			for (Map.Entry<String, String> entry : existingMilstoneMap.entrySet()) {
				String statusTs = entry.getKey();
				logger.debug("statusTs is: " + statusTs);

				String statusCode = entry.getValue();
				logger.debug("statusCode is: " + statusCode);

				if (!YFCObject.isVoid(statusTs)) {
					Date statusTsDate = dateFormat.parse(statusTs);
					if (!YFCObject.isVoid(statusCode) && statusCode.equalsIgnoreCase(strStatusCode)
							&& !YFCObject.isVoid(occuredDate) && !YFCObject.isVoid(statusTsDate)
							&& occuredDate.equals(statusTsDate)) {
						isElgMileStone = false;
					}
				}

			}
		}

		logger.debug("isElgMileStone is: " + isElgMileStone);
		if (isElgMileStone) {
			Element milestonesEle = (Element) inDocPackageTracker.importNode(eleMilestones, true);
			elePackageTracker.appendChild(milestonesEle);

		}

		logger.endTimer("class: AmzProcessMilestonesEvents | method: verifyEligibleMilestonesToAdd -- Ends");
		logger.info("class: AmzProcessMilestonesEvents | method: verifyEligibleMilestonesToAdd -- Ends");
	}
	
	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
	

}
