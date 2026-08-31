package com.amazon.oms.order.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.amazon.oms.delivery.api.AmzProcessMCFOrderStatusChangeEvent;
import com.amazon.oms.delivery.api.AmzProcessMCFShipmentEvent;
import com.amazon.oms.tracking.api.AmzProcessMCFPackageStatusEvent;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * Reconciles an Amazon GetFulfillmentOrder snapshot against OMS current state.
 *
 * The Amazon snapshot is a full picture of the order — not a delta. This class
 * queries OMS to determine what has already been processed (shipped, cancelled,
 * milestone recorded) and only invokes the existing event processor classes for
 * actions that are still needed.
 *
 * Processing order:
 *   1. Shipments — container must exist before milestones can be written
 *   2. Package milestones — depends on container existing
 *   3. Cancellations — needs to account for what was just shipped
 *
 * Delegates to:
 *   - AmzProcessMCFShipmentEvent (SHIPMENT_STATUS_CHANGED)
 *   - AmzProcessMCFPackageStatusEvent (SHIPMENT_PACKAGE_STATUS_CHANGED)
 *   - AmzProcessMCFOrderStatusChangeEvent (ORDER_STATUS_CHANGED)
 *
 * Payload construction is handled by AmzMCFSnapshotPayloadBuilder.
 *
 * Key field mappings (Amazon → OMS):
 *   amazonShipmentId → ExtnAmazonDeliveryId
 *   lineItemId       → ExtnAmazonLineItemAlias
 *   orderId          → AmazonOrderId
 */
public class AmzReconcileMCFOrderSnapshot {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzReconcileMCFOrderSnapshot.class);
	private static final String CLASS_NAME = "AmzReconcileMCFOrderSnapshot";
	private static final String ERR_PREFIX = "MCF_RECONCILE_ERR_";

	String strShipNode = null;
	String strEnterpriseCode = null;
	Map<String, String> genericPropertiesMap = new HashMap<>();

	public Document reconcile(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: reconcile -- Starts");
		logger.debug("class: " + CLASS_NAME + " | method: reconcile -- inDoc: " + AmzXMLUtil.getString(inDoc));
		AmzCommonUtil.logAmzConnRequest(inDoc);

		try {
			Element eleEvent = inDoc.getDocumentElement();

			Element eleFulfillmentOrder = SCXmlUtil.getChildElement(eleEvent, "orders");
			if (YFCObject.isVoid(eleFulfillmentOrder)) {
				throwError("FULFILLMENT_ORDER_MISSING", "order element not found in input");
			}
			String amazonOrderId = eleFulfillmentOrder.getAttribute("orderId");
			String orderStatus = eleFulfillmentOrder.getAttribute("status");

			if (YFCObject.isVoid(amazonOrderId)) {
				throwError("ORDER_ID_MISSING", "FulfillmentOrder orderId is blank or empty");
			}

			// Resolve shipNode using first lineItem to look up OMS order
			NodeList lineItemNodes = eleFulfillmentOrder.getElementsByTagName("lineItems");
			if (lineItemNodes.getLength() == 0) {
				throwError("NO_LINE_ITEMS", "No lineItems found in the snapshot");
			}
			String firstLineItemId = ((Element) lineItemNodes.item(0)).getAttribute("lineItemId");
			Document getOrderLineListOutDoc = callGetOrderLineList(env, firstLineItemId);
			if (getOrderLineListOutDoc == null) {
				logger.info("Order not found in OMS for lineItemId=" + firstLineItemId
						+ " amazonOrderId=" + amazonOrderId + ". Skipping reconciliation.");
				return inDoc;
			}
			Element eleGetOrderLineListOp = getOrderLineListOutDoc.getDocumentElement();

			String extnOrderCountry = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_EXTN_COUNTRY_CODE);
			String enterpriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE);

			Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
			genericPropertiesMap = AmzGetGenericProperty.getGenericProperties(env, propertyDoc);
			strShipNode = genericPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE + extnOrderCountry);
			logger.debug("shipNode: " + strShipNode);

			strEnterpriseCode = enterpriseCode;

			// 1. Process shipments (must happen first — milestones need container to exist)
			NodeList shipmentNodes = eleFulfillmentOrder.getElementsByTagName("shipments");
			for (int s = 0; s < shipmentNodes.getLength(); s++) {
				Element eleShipment = (Element) shipmentNodes.item(s);
				checkAndProcessShipment(env, eleShipment, amazonOrderId, orderStatus);
			}

			// 2. Process package milestones (depends on container existing)
			for (int s = 0; s < shipmentNodes.getLength(); s++) {
				Element eleShipment = (Element) shipmentNodes.item(s);
				checkAndProcessPackageMilestones(env, eleShipment, amazonOrderId, orderStatus);
			}

			// 3. Process cancellations (last — accounts for what was just shipped)
			checkAndProcessCancellations(env, eleFulfillmentOrder, amazonOrderId, orderStatus);

			logger.info("class: " + CLASS_NAME + " | method: reconcile -- completed successfully");

		} catch (YFSException e) {
			logger.error("class: " + CLASS_NAME + " | method: reconcile -- YFSException: " + e.getErrorDescription());
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode(ERR_PREFIX + "EXCEPTION");
			yfse.setErrorDescription(e.getMessage());
			throw yfse;
		}

		logger.endTimer("class: " + CLASS_NAME + " | method: reconcile -- Ends");
		return inDoc;
	}

	/**
	 * Checks if a container already exists in OMS for this amazonShipmentId.
	 * If not, builds a SHIPMENT_STATUS_CHANGED payload and invokes
	 * AmzProcessMCFShipmentEvent to create the container.
	 */
	private void checkAndProcessShipment(YFSEnvironment env, Element eleShipment,
			String amazonOrderId, String orderStatus) throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: checkAndProcessShipment -- Starts");

		String amazonShipmentId = eleShipment.getAttribute("amazonShipmentId");
		if (YFCObject.isVoid(amazonShipmentId)) {
			logger.info("Skipping shipment with empty amazonShipmentId");
			return;
		}

		Document getShipContainerListDoc = SCXmlUtil.createDocument(AmzLiterals.ELE_CONTAINER);
		Element eleExtn = SCXmlUtil.createChild(getShipContainerListDoc.getDocumentElement(), AmzLiterals.E_EXTN);
		eleExtn.setAttribute(AmzLiterals.ATTR_AMZ_DELIVERY_ID, amazonShipmentId);

		Document getShipContainerListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_SHIP_CONTAINER_LIST);
		Document getShipContainerListOp = AmzCommonUtil.callAPI(env, getShipContainerListDoc,
				"getShipmentContainerList", getShipContainerListTemp);

		String totalNumberOfRecords = getShipContainerListOp.getDocumentElement()
				.getAttribute("TotalNumberOfRecords");

		if (!"0".equalsIgnoreCase(totalNumberOfRecords)) {
			logger.info("Container already exists for amazonShipmentId=" + amazonShipmentId + ". Skipping shipment.");
			return;
		}

		logger.info("Container does not exist for amazonShipmentId=" + amazonShipmentId + ". Creating shipment.");
		Document shipmentPayload = AmzMCFSnapshotPayloadBuilder.buildShipmentEventPayload(
				amazonOrderId, orderStatus, eleShipment);
		logger.debug("shipmentPayload: " + SCXmlUtil.getString(shipmentPayload));

		AmzProcessMCFShipmentEvent shipmentProcessor = new AmzProcessMCFShipmentEvent();
		shipmentProcessor.processShipmentEvent(env, shipmentPayload);

		logger.endTimer("class: " + CLASS_NAME + " | method: checkAndProcessShipment -- Ends");
	}

	/**
	 * For each shipmentPackage, checks if the milestone already exists in
	 * AMZ_CONN_CONTAINER_MILESTONES. If any are missing, builds a
	 * SHIPMENT_PACKAGE_STATUS_CHANGED payload and invokes
	 * AmzProcessMCFPackageStatusEvent (which does its own per-package dedup).
	 */
	private void checkAndProcessPackageMilestones(YFSEnvironment env, Element eleShipment,
			String amazonOrderId, String orderStatus) throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: checkAndProcessPackageMilestones -- Starts");

		String amazonShipmentId = eleShipment.getAttribute("amazonShipmentId");
		if (YFCObject.isVoid(amazonShipmentId)) {
			logger.info("Skipping milestones — empty amazonShipmentId");
			return;
		}

		NodeList packageNodes = eleShipment.getElementsByTagName("packages");
		if (packageNodes.getLength() == 0) {
			logger.info("No shipmentPackages found for amazonShipmentId=" + amazonShipmentId);
			return;
		}

		boolean anyMilestoneNeeded = false;

		for (int i = 0; i < packageNodes.getLength(); i++) {
			Element elePkg = (Element) packageNodes.item(i);
			String pkgStatus = elePkg.getAttribute("status");

			String pkgTrackingNo = "";
			Element eleTracking = SCXmlUtil.getChildElement(elePkg, "tracking");
			Element eleCarrierTracking = !YFCObject.isVoid(eleTracking)
					? SCXmlUtil.getChildElement(eleTracking, "carrier") : null;
			if (!YFCObject.isVoid(eleCarrierTracking)) {
				pkgTrackingNo = eleCarrierTracking.getAttribute("trackingNumber");
			}

			if (YFCObject.isVoid(pkgStatus) || YFCObject.isVoid(pkgTrackingNo)) {
				logger.debug("Skipping shipmentPackage with empty status or trackingNumber");
				continue;
			}

			Document inDocGetList = AmzXMLUtil.createDocument(AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
			Element eleRoot = inDocGetList.getDocumentElement();
			eleRoot.setAttribute(AmzLiterals.A_AMAZON_DELIVERY_ID, amazonShipmentId);
			eleRoot.setAttribute(AmzLiterals.A_TRACKING_NO, pkgTrackingNo);

			Document outDocGetList = AmzCommonUtil.invokeService(env,
					AmzCommonConstants.SERVICE_GET_AMZ_CONN_CONTAINER_MILESTONES_LIST, inDocGetList);

			boolean statusAlreadyExists = false;
			if (!YFCObject.isVoid(outDocGetList)) {
				NodeList milestoneEntries = outDocGetList.getDocumentElement()
						.getElementsByTagName(AmzLiterals.E_AMZ_CONN_CONTAINER_MILESTONES);
				for (int m = 0; m < milestoneEntries.getLength(); m++) {
					Element eleEntry = (Element) milestoneEntries.item(m);
					if (pkgStatus.equals(eleEntry.getAttribute(AmzLiterals.A_STATUS_CODE))) {
						statusAlreadyExists = true;
						break;
					}
				}
			}

			if (statusAlreadyExists) {
				logger.info("Milestone " + pkgStatus + " already exists for TrackingNo="
						+ pkgTrackingNo + " amazonShipmentId=" + amazonShipmentId + ". Skipping.");
				continue;
			}

			anyMilestoneNeeded = true;
		}

		if (!anyMilestoneNeeded) {
			logger.info("All milestones already recorded for amazonShipmentId=" + amazonShipmentId);
			logger.endTimer("class: " + CLASS_NAME + " | method: checkAndProcessPackageMilestones -- Ends");
			return;
		}

		Document packageStatusPayload = AmzMCFSnapshotPayloadBuilder.buildPackageStatusPayload(
				amazonOrderId, orderStatus, eleShipment);
		logger.debug("packageStatusPayload: " + SCXmlUtil.getString(packageStatusPayload));

		AmzProcessMCFPackageStatusEvent packageStatusProcessor = new AmzProcessMCFPackageStatusEvent();
		packageStatusProcessor.processPackageStatusEvent(env, packageStatusPayload);

		logger.endTimer("class: " + CLASS_NAME + " | method: checkAndProcessPackageMilestones -- Ends");
	}

	/**
	 * For each lineItem with cancelledAmount + unfulfillableAmount > 0, queries OMS
	 * for current cancelled quantity (StatusQty where Status >= 9000). Computes delta
	 * and only cancels what's still needed.
	 */
	private void checkAndProcessCancellations(YFSEnvironment env,
			Element eleFulfillmentOrder, String amazonOrderId,
			String orderStatus) throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: checkAndProcessCancellations -- Starts");

		NodeList lineItemNodes = eleFulfillmentOrder.getElementsByTagName("lineItems");
		ArrayList<String[]> deltaLineItems = new ArrayList<>();

		for (int i = 0; i < lineItemNodes.getLength(); i++) {
			Element eleLineItem = (Element) lineItemNodes.item(i);
			String lineItemId = eleLineItem.getAttribute("lineItemId");

			// Only top-level lineItems (under the order, not under shipments)
			if (!eleLineItem.getParentNode().getNodeName().equals("orders")) {
				continue;
			}

			double amazonCancelledQty = AmzMCFSnapshotPayloadBuilder.getChildElementValue(eleLineItem, "cancelledAmount");
			double amazonUnfulfillableQty = AmzMCFSnapshotPayloadBuilder.getChildElementValue(eleLineItem, "unfulfillableAmount");
			double amazonTotalCancelQty = amazonCancelledQty + amazonUnfulfillableQty;

			if (amazonTotalCancelQty <= 0) {
				continue;
			}

			Document orderLineListDoc = callGetOrderLineList(env, lineItemId);
			if (orderLineListDoc == null) {
				logger.info("Order not found in OMS for lineItemId=" + lineItemId + ". Skipping cancellation for this line.");
				continue;
			}
			Element eleOrderLineListOp = orderLineListDoc.getDocumentElement();

			NodeList orderStatusNodes = AmzXMLUtil.getXpathNodes(eleOrderLineListOp,
					"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + strShipNode + "']");

			double omsCancelledQty = 0.0;
			for (int k = 0; k < orderStatusNodes.getLength(); k++) {
				Element eleOrderStatus = (Element) orderStatusNodes.item(k);
				String status = eleOrderStatus.getAttribute("Status");
				if (!YFCObject.isVoid(status) && status.compareTo("9000") >= 0) {
					String statusQtyStr = eleOrderStatus.getAttribute("StatusQty");
					if (!YFCObject.isVoid(statusQtyStr)) {
						try {
							omsCancelledQty += Double.parseDouble(statusQtyStr);
						} catch (NumberFormatException e) {
							logger.debug("Could not parse StatusQty: " + statusQtyStr);
						}
					}
				}
			}

			double deltaCancelQty = amazonTotalCancelQty - omsCancelledQty;
			logger.debug("lineItemId=" + lineItemId + " amazonTotalCancelQty=" + amazonTotalCancelQty
					+ " omsCancelledQty=" + omsCancelledQty + " delta=" + deltaCancelQty);

			if (deltaCancelQty <= 0) {
				logger.info("Line " + lineItemId + " already cancelled in OMS (omsQty="
						+ omsCancelledQty + " >= amazonQty=" + amazonTotalCancelQty + "). Skipping.");
				continue;
			}

			double deltaCancelled = Math.min(amazonCancelledQty, deltaCancelQty);
			double deltaUnfulfillable = deltaCancelQty - deltaCancelled;

			deltaLineItems.add(new String[]{
					lineItemId,
					String.valueOf(deltaCancelled),
					String.valueOf(deltaUnfulfillable)
			});
		}

		if (deltaLineItems.isEmpty()) {
			logger.info("No cancellation deltas found for orderId=" + amazonOrderId + ". Skipping.");
			logger.endTimer("class: " + CLASS_NAME + " | method: checkAndProcessCancellations -- Ends");
			return;
		}

		Document cancelPayload = AmzMCFSnapshotPayloadBuilder.buildOrderStatusChangePayload(
				amazonOrderId, orderStatus, eleFulfillmentOrder, deltaLineItems);
		logger.debug("cancelPayload: " + SCXmlUtil.getString(cancelPayload));

		AmzProcessMCFOrderStatusChangeEvent cancelProcessor = new AmzProcessMCFOrderStatusChangeEvent();
		cancelProcessor.processOrderStatusChangeEvent(env, cancelPayload);

		logger.endTimer("class: " + CLASS_NAME + " | method: checkAndProcessCancellations -- Ends");
	}

	private Document callGetOrderLineList(YFSEnvironment env, String lineItemId) throws Exception {
		Document getOrderLineListInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_LINE);
		Element eleOrderLineExtn = SCXmlUtil.createChild(getOrderLineListInDoc.getDocumentElement(), AmzLiterals.E_EXTN);
		eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS, lineItemId);

		Document getOrderLineListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_ORDER_LINE_LIST);
		Document getOrderLineListOutDoc = AmzCommonUtil.callAPI(env, getOrderLineListInDoc,
				AmzCommonConstants.API_GET_ORDER_LINE_LIST, getOrderLineListTemp);
		logger.debug("getOrderLineListOutDoc: " + SCXmlUtil.getString(getOrderLineListOutDoc));

		NodeList nlOrderLineList = getOrderLineListOutDoc.getDocumentElement().getElementsByTagName("OrderLine");
		if (nlOrderLineList.getLength() == 0) {
			logger.info("No OrderLine found in OMS for ExtnAmazonLineItemAlias=" + lineItemId + ". Skipping.");
			return null;
		}
		return getOrderLineListOutDoc;
	}

	private void throwError(String errorSuffix, String description) throws YFSException {
		YFSException yfse = new YFSException();
		yfse.setErrorCode(ERR_PREFIX + errorSuffix);
		yfse.setErrorDescription(description);
		throw yfse;
	}
}
