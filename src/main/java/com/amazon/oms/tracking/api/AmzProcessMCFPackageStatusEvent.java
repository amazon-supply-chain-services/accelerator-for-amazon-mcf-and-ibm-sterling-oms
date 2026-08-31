package com.amazon.oms.tracking.api;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Map;

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
import com.amazon.oms.delivery.api.AmzProcessMCFShipmentEvent;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * This class processes MCF SHIPMENT_PACKAGE_STATUS_CHANGED events
 * and records each ShipmentPackage status as a package status entry in the
 * AMZ_CONN_CONTAINER_MILESTONES custom table.
 *
 * It is invoked through an async service which reads messages from a queue.
 *
 * This class does NOT call the Amazon API to get order details — the package
 * status is taken directly from the ShipmentPackage/@status attribute in the
 * event XML.
 *
 * Possible ShipmentPackage/@status values:
 *   SHIPPED, IN_TRANSIT, DELIVERED, DELAYED, UNDELIVERABLE, RETURNED
 *
 * Processing logic:
 *   1. Parse the MCF V2 Event XML, extract merchantId, orderId, shipments
 *   2. For each Shipment, look up the OMS container by amazonShipmentId
 *   3. Read existing package statuses from AMZ_CONN_CONTAINER_MILESTONES (dedup)
 *   4. For each ShipmentPackage, if its status is not already recorded, insert it
 *
 * Input (MCF Event XML — DELIVERED):
 * <AmzProcessShipmentPackageStatusChangedEvent eventType="SHIPMENT_PACKAGE_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="COMPLETE">
 *     <shipments amazonShipmentId="09581f64ed04398cb1953e2ca550809b0" status="SHIPPED">
 *       <amazonFacility facilityId="FC456"/>
 *       <items lineItemId="202603151921549163264" packageId="13153368">
 *         <amount unit="EACHES" value="1.0"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </items>
 *       <packages packageId="13153368" status="DELIVERED">
 *         <tracking>
 *           <carrier carrierCode="FEDEX" trackingNumber="123456789"/>
 *           <amazon trackingNumber="234567890"/>
 *         </tracking>
 *       </packages>
 *     </shipments>
 *   </order>
 * </AmzProcessShipmentPackageStatusChangedEvent>
 *
 * Mapping:
 *   amazonShipmentId       -> ExtnAmazonDeliveryId (container lookup)
 *   shipmentPackages/@status -> StatusCode in AMZ_CONN_CONTAINER_MILESTONES
 *   carrierTracking/@trackingNumber -> TrackingNo
 *   merchantId             -> used to resolve EnterpriseCode
 */
public class AmzProcessMCFPackageStatusEvent implements YIFCustomApi {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessMCFPackageStatusEvent.class);
	private static final String CLASS_NAME = "AmzProcessMCFPackageStatusEvent";
	HashMap<String, String> existingPackageStatusMap = new HashMap<>();
	Document outDocGetContainerPackageStatusList = null;
	private Properties props;

	public Document processPackageStatusEvent(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: processPackageStatusEvent -- Starts");
		logger.debug("class: " + CLASS_NAME + " | method: processPackageStatusEvent -- inDoc: "
				+ AmzXMLUtil.getString(inDoc));
		try {
			Element eleEvent = inDoc.getDocumentElement();
			String merchantId = eleEvent.getAttribute("merchantId");
			String eventType = eleEvent.getAttribute("eventType");

			Element eleFulfillmentOrder = SCXmlUtil.getChildElement(eleEvent, "order");
			String amazonOrderId = eleFulfillmentOrder.getAttribute("orderId");

			String strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, merchantId);

			NodeList shipmentNodes = eleFulfillmentOrder.getElementsByTagName("shipments");

			for (int s = 0; s < shipmentNodes.getLength(); s++) {
				Element eleShipment = (Element) shipmentNodes.item(s);
				String amazonShipmentId = eleShipment.getAttribute("amazonShipmentId");

				if (YFCObject.isVoid(amazonShipmentId)) {
					YFSException ex = new YFSException();
					ex.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
					ex.setErrorDescription("Amazon Shipment ID (amazonShipmentId) is blank or empty");
					throw ex;
				}

				if (YFCObject.isVoid(amazonOrderId)) {
					YFSException ex = new YFSException();
					ex.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
					ex.setErrorDescription("Amazon OrderId is blank or empty");
					throw ex;
				}

				// Look up OMS container by amazonShipmentId (ExtnAmazonDeliveryId)
				Document getShpContainerListOut = invokeGetShipmentContainerList(env, amazonShipmentId);
				logger.debug("getShpContainerListOut: " + AmzXMLUtil.getXMLString(getShpContainerListOut));

				Element eleContainer = null;
				if (!YFCObject.isVoid(getShpContainerListOut)) {
					eleContainer = AmzXMLUtil.getChildElement(
							getShpContainerListOut.getDocumentElement(), AmzLiterals.E_CONTAINER);
				}
				if (YFCObject.isVoid(eleContainer)) {
					getShpContainerListOut = createMissingContainer(env, inDoc, eleShipment,
							amazonOrderId, eleFulfillmentOrder.getAttribute("status"), amazonShipmentId);
					eleContainer = AmzXMLUtil.getChildElement(
							getShpContainerListOut.getDocumentElement(), AmzLiterals.E_CONTAINER);
					if (YFCObject.isVoid(eleContainer)) {
						YFSException ex = new YFSException();
						ex.setErrorCode("SHIPMENT_CONTAINER_NOT_FOUND");
						ex.setErrorDescription("Container still not found after creation attempt for amazonShipmentId=" + amazonShipmentId);
						throw ex;
					}
				}

				String strShpContainerKey = eleContainer.getAttribute(AmzLiterals.A_SHIPMENT_CONTAINER_KEY);
				String sContainerNo = eleContainer.getAttribute(AmzLiterals.A_CONTAINER_NO);
				String sOrderNo = AmzXMLUtil.getXpathAttribute(eleContainer, "Shipment/@OrderNo");
				String sShipmentNo = AmzXMLUtil.getXpathAttribute(eleContainer, "Shipment/@ShipmentNo");
				String sEnterpriseCode = AmzXMLUtil.getXpathAttribute(eleContainer, "Shipment/@EnterpriseCode");

				// Process each ShipmentPackage — match by tracking number
				prepareInputAndUpdatePackageStatusInOMS(env, inDoc, eleShipment, amazonShipmentId,
						strShpContainerKey, sContainerNo, sOrderNo, sShipmentNo,
						sEnterpriseCode, amazonOrderId, eventType);
			}
		} catch (YFSException ex) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, ex.getErrorDescription());
			throw AmzCommonUtil.createException(ex);
		}

		logger.endTimer("class: " + CLASS_NAME + " | method: processPackageStatusEvent -- Ends");
		return inDoc;
	}

	/**
	 * Looks up the OMS container by amazonShipmentId (ExtnAmazonDeliveryId).
	 */
	private Document invokeGetShipmentContainerList(YFSEnvironment env, String amazonShipmentId) {
		logger.beginTimer("class: " + CLASS_NAME + " | method: invokeGetShipmentContainerList -- Starts");
		Document getShpContainerListDoc = AmzXMLUtil.createDocument(AmzLiterals.E_CONTAINER);
		Element eleContainer = getShpContainerListDoc.getDocumentElement();
		Element eleExtn = SCXmlUtil.createChild(eleContainer, AmzLiterals.E_EXTN);
		eleExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_DELIVERY_ID, amazonShipmentId);
		logger.debug(CLASS_NAME + ".invokeGetShipmentContainerList input: "
				+ AmzXMLUtil.getString(getShpContainerListDoc));
		Document outDoc = AmzCommonUtil.invokeAPI(env,
				AmzCommonConstants.TEMPLATE_GET_SHIP_CONTAINER_LIST_FOR_TRACKING_UPDATED,
				AmzCommonConstants.API_GET_SHIPMENT_CONTAINER_LIST, getShpContainerListDoc);
		logger.endTimer("class: " + CLASS_NAME + " | method: invokeGetShipmentContainerList -- Ends");
		return outDoc;
	}

	/**
	 * Reads existing package statuses from AMZ_CONN_CONTAINER_MILESTONES
	 * for the given deliveryId and trackingNo, populating existingPackageStatusMap
	 * for dedup.
	 */
	private void getExistingPackageStatuses(YFSEnvironment env, String strDeliveryId, String strTrackingNo) {
		logger.beginTimer("class: " + CLASS_NAME + " | method: getExistingPackageStatuses -- Starts");
		if (!YFCObject.isVoid(strTrackingNo) && !YFCObject.isVoid(strDeliveryId)) {
			Document inDocGetList = AmzXMLUtil.createDocument(AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
			Element eleRoot = inDocGetList.getDocumentElement();
			eleRoot.setAttribute(AmzLiterals.A_AMAZON_DELIVERY_ID, strDeliveryId);
			eleRoot.setAttribute(AmzLiterals.A_TRACKING_NO, strTrackingNo);
			logger.debug(CLASS_NAME + ".getExistingPackageStatuses input: " + AmzXMLUtil.getString(inDocGetList));

			outDocGetContainerPackageStatusList = AmzCommonUtil.invokeService(env,
					AmzCommonConstants.SERVICE_GET_AMZ_CONN_CONTAINER_MILESTONES_LIST, inDocGetList);
			logger.debug(CLASS_NAME + ".getExistingPackageStatuses output: "
					+ AmzXMLUtil.getString(outDocGetContainerPackageStatusList));

			Element eleList = outDocGetContainerPackageStatusList.getDocumentElement();
			NodeList nEntries = eleList.getElementsByTagName(AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
			for (int i = 0; i < nEntries.getLength(); i++) {
				Element eleEntry = (Element) nEntries.item(i);
				String strStatusCode = eleEntry.getAttribute(AmzLiterals.A_STATUS_CODE);
				String strStatusTs = eleEntry.getAttribute(AmzLiterals.A_STATUS_TS);
				existingPackageStatusMap.put(strStatusCode, strStatusTs);
			}
		}
		logger.debug("existingPackageStatusMap: " + existingPackageStatusMap);
		logger.endTimer("class: " + CLASS_NAME + " | method: getExistingPackageStatuses -- Ends");
	}

	/**
	 * Iterates ShipmentPackages, extracts each package's tracking number and status,
	 * performs per-tracking-number dedup against existing records, and inserts
	 * new package status entries into OMS only for matching tracking numbers.
	 */
	private void prepareInputAndUpdatePackageStatusInOMS(YFSEnvironment env, Document inDoc,
			Element eleShipment, String amazonShipmentId,
			String strShpContainerKey, String sContainerNo, String sOrderNo,
			String sShipmentNo, String sEnterpriseCode, String amazonOrderId, String eventType)
			throws XPathExpressionException {
		logger.beginTimer("class: " + CLASS_NAME + " | method: prepareInputAndUpdatePackageStatusInOMS -- Starts");
		try {
			NodeList packageNodes = eleShipment.getElementsByTagName("packages");
			if (packageNodes.getLength() == 0) {
				logger.info("No shipmentPackages found for amazonShipmentId=" + amazonShipmentId);
				return;
			}

			SimpleDateFormat dateFormat = new SimpleDateFormat(AmzCommonConstants.STR_STERLING_DATE_TIME_FORMAT);
			String currentTs = dateFormat.format(new Date());

			for (int i = 0; i < packageNodes.getLength(); i++) {
				Element elePkg = (Element) packageNodes.item(i);
				String pkgStatus = elePkg.getAttribute("status");

				// Extract this package's tracking number
				// SP-API 2026-07-04: carrier tracking is nested under tracking.carrier
				String pkgTrackingNo = "";
				Element eleTracking = SCXmlUtil.getChildElement(elePkg, "tracking");
				Element eleCarrierTracking = !YFCObject.isVoid(eleTracking)
						? SCXmlUtil.getChildElement(eleTracking, "carrier") : null;
				if (!YFCObject.isVoid(eleCarrierTracking)) {
					pkgTrackingNo = eleCarrierTracking.getAttribute("trackingNumber");
				}

				if (YFCObject.isVoid(pkgStatus) || YFCObject.isVoid(pkgTrackingNo)) {
					logger.debug("Skipping ShipmentPackage with empty status or trackingNumber");
					continue;
				}

				// Dedup: load existing statuses for this specific tracking number
				existingPackageStatusMap.clear();
				getExistingPackageStatuses(env, amazonShipmentId, pkgTrackingNo);

				if (existingPackageStatusMap.containsKey(pkgStatus)) {
					logger.info("Package status " + pkgStatus + " already exists for TrackingNo="
							+ pkgTrackingNo + " amazonShipmentId=" + amazonShipmentId + ". Skipping.");
					continue;
				}

				// Build update document for this tracking number + status
				Document inDocPackageStatus = AmzXMLUtil.createDocument(AmzLiterals.E_PACKAGE_TRACKER);
				Element elePackageTracker = inDocPackageStatus.getDocumentElement();

				Element eleMilestone = inDocPackageStatus.createElement(AmzLiterals.E_JS_MILESTONES);
				Element eleStatus = inDocPackageStatus.createElement(AmzLiterals.E_JS_STATUS);
				eleStatus.setAttribute(AmzLiterals.A_JS_CODE, pkgStatus);
				eleMilestone.appendChild(eleStatus);
				eleMilestone.setAttribute(AmzLiterals.A_JS_OCCURRED_AT, currentTs);
				elePackageTracker.appendChild(eleMilestone);

				elePackageTracker.setAttribute(AmzLiterals.A_AMAZON_DELIVERY_ID, amazonShipmentId);
				elePackageTracker.setAttribute(AmzLiterals.A_SHIPMENT_CONTAINER_KEY, strShpContainerKey);
				elePackageTracker.setAttribute(AmzLiterals.A_TRACKING_NO, pkgTrackingNo);
				elePackageTracker.setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
				elePackageTracker.setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
				elePackageTracker.setAttribute(AmzLiterals.A_SHIPMENT_NO, sShipmentNo);
				elePackageTracker.setAttribute(AmzLiterals.A_ORDER_NO, sOrderNo);
				elePackageTracker.setAttribute(AmzLiterals.A_CONTAINER_NO, sContainerNo);
				elePackageTracker.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, sEnterpriseCode);
				logger.debug("input for updatePackageStatus (TrackingNo=" + pkgTrackingNo
						+ " status=" + pkgStatus + "): " + AmzXMLUtil.getString(inDocPackageStatus));
				AmzCommonUtil.invokeService(env,
						AmzCommonConstants.SERVICE_AMZ_CONN_UPDATE_MILESTONES_RECORD_IN_OMS,
						inDocPackageStatus);
			}
		} catch (YFSException ex) {
			ex.setErrorCode("ERROR_UPDATING_PACKAGE_STATUS_IN_OMS");
			ex.setErrorDescription("Exception while updating package status in OMS for amazonShipmentId="
					+ amazonShipmentId);
			logger.error("Exception in class: " + CLASS_NAME
					+ " | method: prepareInputAndUpdatePackageStatusInOMS : "
					+ ExceptionUtils.getStackTrace(ex));
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, ex.getErrorDescription());
			throw AmzCommonUtil.createException(ex);
		}
		logger.endTimer("class: " + CLASS_NAME + " | method: prepareInputAndUpdatePackageStatusInOMS -- Ends");
	}


	private Document createMissingContainer(YFSEnvironment env, Document inDoc,
			Element eleShipment, String amazonOrderId, String orderStatus,
			String amazonShipmentId) throws Exception {
		logger.info("Container not found for amazonShipmentId=" + amazonShipmentId + ". Creating via shipment event.");
		Document doc = SCXmlUtil.createDocument("AmzProcessShipmentStatusChangedEvent");
		Element eleRoot = doc.getDocumentElement();
		eleRoot.setAttribute("eventType", "SHIPMENT_STATUS_CHANGED");
		Element eleFO = SCXmlUtil.createChild(eleRoot, "order");
		eleFO.setAttribute("orderId", amazonOrderId);
		eleFO.setAttribute("status", orderStatus);
		eleFO.appendChild(doc.importNode(eleShipment, true));

		AmzProcessMCFShipmentEvent shipmentProcessor = new AmzProcessMCFShipmentEvent();
		shipmentProcessor.processShipmentEvent(env, doc);
		Thread.sleep(10);
		return invokeGetShipmentContainerList(env, amazonShipmentId);
	}

	private void prepareAndLogResponse(String processStatus, Document inDoc, String message)
			throws XPathExpressionException {
		logger.beginTimer("class: " + CLASS_NAME + " | method: prepareAndLogResponse -- Starts");

		Element eleEvent = inDoc.getDocumentElement();
		String eventType = eleEvent.getAttribute("eventType");
		String amazonOrderId = "";
		Element eleFO = SCXmlUtil.getChildElement(eleEvent, "order");
		if (!YFCObject.isVoid(eleFO)) {
			amazonOrderId = eleFO.getAttribute("orderId");
		}

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);

		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.endTimer("class: " + CLASS_NAME + " | method: prepareAndLogResponse -- Ends");
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
