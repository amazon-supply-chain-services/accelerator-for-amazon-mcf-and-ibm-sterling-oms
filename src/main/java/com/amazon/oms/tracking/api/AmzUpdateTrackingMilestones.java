package com.amazon.oms.tracking.api;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This class invoked from the AmazonProcessTrackingEvents.java class
 * This class is used to add the record to AMZ_CONN_CONTAINER_MILESTONES by invoking the multiApi.
 * 
 * Sample input to this class:
 *	<PackageTracker ShipmentContainerKey="" AmazonDeliveryId="" TrackingNo="" OrderNo="" ShipmentNo="" ContainerNo="" EnterpriseCode=">
		<milestones occurredAt="2025-02-20T11:15:26.718Z">
			<status code="OUT_FOR_DELIVERY">
				<message locale="en-US" value=" "/>
			</status>
		</milestones>
		<milestones occurredAt="2025-02-19T12:47:41.716Z">
			<status code="IN_TRANSIT">
				<message locale="en-US" value=" "/>
			</status>
		</milestones>
	</PackageTracker>
 */

public class AmzUpdateTrackingMilestones implements YIFCustomApi{

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzUpdateTrackingMilestones.class);
	private Properties props;
	/*
	 * This method form the input to invoke the multiApi, to add the package
	 * milestone event records into the AMZ_CONN_CONTAINER_MILESTONES custom table.
	 */

	public Document updateTrackingMilestones(YFSEnvironment env, Document inDoc) throws ParseException {
		logger.beginTimer("class: AmazonUpdateTrackingMilestones | method: updateTrackingMilestones -- Starts");
		logger.info("class: AmazonUpdateTrackingMilestones | method: updateTrackingMilestones -- Starts");
		logger.debug("class: AmazonUpdateTrackingMilestones | method: updateTrackingMilestones: inDoc is: "
				+ AmzXMLUtil.getString(inDoc));
		Document inDocMultiApi = null;

			inDocMultiApi = AmzXMLUtil.createDocument(AmzLiterals.E_MULTI_API);
			Element eleMultiApi = inDocMultiApi.getDocumentElement();

			Element rootEle = inDoc.getDocumentElement();
			String strEventType = rootEle.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
			logger.debug("AmazonUpdateTrackingMilestones: updateTrackingMilestones:strEventType is " + strEventType);
			NodeList nMilestones = rootEle.getElementsByTagName(AmzLiterals.E_JS_MILESTONES);
			int imilestonesLen = nMilestones.getLength();
			for (int i = 0; i < imilestonesLen; i++) {
				Element milestonesEle = (Element) nMilestones.item(i);
				Element eleApi = AmzXMLUtil.createChild(eleMultiApi, AmzLiterals.E_API);
				eleApi.setAttribute(AmzLiterals.A_FLOW_NAME,
						AmzCommonConstants.SERVICE_AMZ_CREATE_AMZ_CONN_CONATINER_MILESTONES);
				Element eleInput = AmzXMLUtil.createChild(eleApi, AmzLiterals.E_INPUT);
				Element eleAmzContainerMilestones = AmzXMLUtil.createChild(eleInput,
						AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
				String strrOccuredAt = milestonesEle.getAttribute(AmzLiterals.A_JS_OCCURRED_AT);
				logger.debug(
						"AmazonUpdateTrackingMilestones: updateTrackingMilestones: strrOccuredAt is: " + strrOccuredAt);
				if (!YFCObject.isVoid(strrOccuredAt)) {
					SimpleDateFormat dateFormat = new SimpleDateFormat(
							AmzCommonConstants.STR_STERLING_DATE_TIME_FORMAT);
					Date occuredDate;
					occuredDate = dateFormat.parse(strrOccuredAt);
					String statusTs = dateFormat.format(occuredDate);
					logger.debug("AmazonUpdateTrackingMilestones: updateTrackingMilestones: statusTs is: " + statusTs);
					eleAmzContainerMilestones.setAttribute(AmzLiterals.A_STATUS_TS, statusTs);
				}
				Element statusEle = AmzXMLUtil.getChildElement(milestonesEle, AmzLiterals.E_JS_STATUS);
				eleAmzContainerMilestones.setAttribute(AmzLiterals.A_STATUS_CODE,
						statusEle.getAttribute(AmzLiterals.A_JS_CODE));
				Element messageEle = AmzXMLUtil.getChildElement(statusEle, AmzLiterals.E_JS_MESSAGE);
				if (!YFCObject.isVoid(messageEle)) {
				eleAmzContainerMilestones.setAttribute(AmzLiterals.A_LOCALE,
						messageEle.getAttribute(AmzLiterals.A_JS_LOCALE));
				eleAmzContainerMilestones.setAttribute(AmzLiterals.A_MESSAGE,
						messageEle.getAttribute(AmzLiterals.A_JS_VALUE));
				}
				eleAmzContainerMilestones.setAttribute(AmzLiterals.A_SHIPMENT_CONTAINER_KEY,
						rootEle.getAttribute(AmzLiterals.A_SHIPMENT_CONTAINER_KEY));
				eleAmzContainerMilestones.setAttribute(AmzLiterals.A_AMAZON_DELIVERY_ID,
						rootEle.getAttribute(AmzLiterals.A_AMAZON_DELIVERY_ID));
				eleAmzContainerMilestones.setAttribute(AmzLiterals.A_TRACKING_NO,
						rootEle.getAttribute(AmzLiterals.A_TRACKING_NO));
			}
			logger.debug("inDocMultiApi is: " + AmzXMLUtil.getXMLString(inDocMultiApi));
			try {
				AmzCommonUtil.invoke(env, AmzCommonConstants.API_MULTI_API, inDocMultiApi);
			} catch(YFSException e) {
				e.printStackTrace();
				YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
				yfse.setErrorDescription(e.getMessage());
				prepareAndLogResponse(AmzLiterals.STR_ERROR, strEventType,inDoc,yfse.getErrorDescription());
				throw yfse;
			}
			prepareAndLogResponse(AmzLiterals.STR_SUCCESS,strEventType, inDoc,null);
		
		logger.endTimer("class: AmazonUpdateTrackingMilestones | method: updateTrackingMilestones -- Ends");
		logger.info("class: AmazonUpdateTrackingMilestones | method: updateTrackingMilestones -- Ends");
		return inDocMultiApi;

	}

	public void prepareAndLogResponse(String processStatus, String eventType, Document inDoc, String message) {
		logger.beginTimer("class: AmazonUpdateTrackingMilestones | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmazonUpdateTrackingMilestones | method: prepareAndLogResponse -- Starts");
		logger.debug("class: AmazonUpdateTrackingMilestones | method: prepareAndLogResponse: inDoc is: "
				+ AmzXMLUtil.getString(inDoc));
		Element eleRoot = inDoc.getDocumentElement();
		String shipmentNo = eleRoot.getAttribute(AmzLiterals.A_SHIPMENT_NO);
		logger.debug("AmazonUpdateTrackingMilestones: prepareAndLogResponse: shipmentNo is: " + shipmentNo);

		String containerNo = eleRoot.getAttribute(AmzLiterals.A_CONTAINER_NO);
		logger.debug("AmazonUpdateTrackingMilestones: prepareAndLogResponse: containerNo is: " + containerNo);

		String orderNo = eleRoot.getAttribute(AmzLiterals.A_ORDER_NO);
		logger.debug("AmazonUpdateTrackingMilestones: prepareAndLogResponse: orderNo is: " + orderNo);

		String enterPriseCode = eleRoot.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		logger.debug("AmazonUpdateTrackingMilestones: prepareAndLogResponse: enterPriseCode is: " + enterPriseCode);

		String extnAmazonDeliveryID = eleRoot.getAttribute(AmzLiterals.A_AMAZON_DELIVERY_ID);
		logger.debug("AmazonUpdateTrackingMilestones: prepareAndLogResponse: extnAmazonDeliveryID is: "
				+ extnAmazonDeliveryID);

		String amazonOrderId = eleRoot.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
		logger.debug("AmazonUpdateTrackingMilestones: prepareAndLogResponse: amazonOrderId is: " + amazonOrderId);

		String trackingNo = eleRoot.getAttribute(AmzLiterals.ATTR_TRACKING_NO);
		logger.debug("AmazonUpdateTrackingMilestones: prepareAndLogResponse: amazonOrderId is: " + amazonOrderId);

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE, enterPriseCode);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO, orderNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_CONTAINER_NO, containerNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_SHIPMENT_NO, shipmentNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_DELIVERY_ID, extnAmazonDeliveryID);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_TRACKING_NO, trackingNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, "SUCCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);
		logger.endTimer("class: AmazonUpdateTrackingMilestones | method: prepareAndLogResponse -- Ends");
		logger.info("class: AmazonUpdateTrackingMilestones | method: prepareAndLogResponse -- Ends");
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}




}
