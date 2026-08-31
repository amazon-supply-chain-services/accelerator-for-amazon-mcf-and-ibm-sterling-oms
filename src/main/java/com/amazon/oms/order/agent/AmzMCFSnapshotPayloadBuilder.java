package com.amazon.oms.order.agent;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;

/**
 * Builds synthetic event payloads from an Amazon GetFulfillmentOrder snapshot.
 *
 * These payloads match the exact XML format expected by the existing MCF event
 * processor classes so they can be invoked directly without going through a queue.
 *
 * All methods are static — no state, no API calls, pure XML transformation.
 */
public class AmzMCFSnapshotPayloadBuilder {

	private AmzMCFSnapshotPayloadBuilder() {
	}

	/**
	 * Builds a SHIPMENT_STATUS_CHANGED payload from the snapshot's <shipments> element.
	 * Maps <items> to <lineItems> (tag name difference between snapshot and event).
	 *
	 * Output format matches what AmzProcessMCFShipmentEvent expects:
	 * <AmzProcessShipmentStatusChangedEvent eventType="SHIPMENT_STATUS_CHANGED" merchantId="...">
	 *   <fulfillmentOrder orderId="..." status="...">
	 *     <shipments amazonShipmentId="..." status="SHIPPED">
	 *       <lineItems lineItemId="..." packageNumber="...">
	 *         <amount unit="EACHES" value="..."/>
	 *         <productIdentifier amazonSku="..."/>
	 *       </lineItems>
	 *       <shipmentPackages packageNumber="..." status="...">
	 *         <carrierTracking carrierCode="..." trackingNumber="..."/>
	 *       </shipmentPackages>
	 *     </shipments>
	 *   </fulfillmentOrder>
	 * </AmzProcessShipmentStatusChangedEvent>
	 */
	public static Document buildShipmentEventPayload(String amazonOrderId,
			String orderStatus, Element eleShipment) {
		Document doc = SCXmlUtil.createDocument("AmzProcessShipmentStatusChangedEvent");
		Element eleRoot = doc.getDocumentElement();
		eleRoot.setAttribute("eventType", "SHIPMENT_STATUS_CHANGED");

		Element eleFO = SCXmlUtil.createChild(eleRoot, "order");
		eleFO.setAttribute("orderId", amazonOrderId);
		eleFO.setAttribute("status", orderStatus);

		Element eleNewShipment = SCXmlUtil.createChild(eleFO, "shipments");
		eleNewShipment.setAttribute("amazonShipmentId", eleShipment.getAttribute("amazonShipmentId"));
		eleNewShipment.setAttribute("status", eleShipment.getAttribute("status"));

		// Snapshot is now SP-API 2026-07-04 shaped: read 2026-07-04 names, write 2026-07-04 event names.
		NodeList itemNodes = eleShipment.getElementsByTagName("items");
		for (int i = 0; i < itemNodes.getLength(); i++) {
			Element eleItem = (Element) itemNodes.item(i);
			Element eleLineItem = SCXmlUtil.createChild(eleNewShipment, "items");
			eleLineItem.setAttribute("lineItemId", eleItem.getAttribute("lineItemId"));

			Element eleAmount = SCXmlUtil.getChildElement(eleItem, "amount");
			if (!YFCObject.isVoid(eleAmount)) {
				Element eleNewAmount = SCXmlUtil.createChild(eleLineItem, "amount");
				eleNewAmount.setAttribute("unit", eleAmount.getAttribute("unit"));
				eleNewAmount.setAttribute("value", eleAmount.getAttribute("value"));
			}

			Element eleProdId = SCXmlUtil.getChildElement(eleItem, "productIdentifier");
			if (!YFCObject.isVoid(eleProdId)) {
				Element eleNewProdId = SCXmlUtil.createChild(eleLineItem, "productIdentifier");
				eleNewProdId.setAttribute("amazonSku", eleProdId.getAttribute("amazonSku"));
			}
		}

		// Read 2026-07-04 <packages> with nested <tracking>, write the same 2026-07-04 shape
		NodeList pkgNodes = eleShipment.getElementsByTagName("packages");
		for (int i = 0; i < pkgNodes.getLength(); i++) {
			Element elePkg = (Element) pkgNodes.item(i);
			Element eleNewPkg = SCXmlUtil.createChild(eleNewShipment, "packages");
			eleNewPkg.setAttribute("packageId", elePkg.getAttribute("packageId"));
			eleNewPkg.setAttribute("status", elePkg.getAttribute("status"));

			Element eleSrcTracking = SCXmlUtil.getChildElement(elePkg, "tracking");
			Element eleCarrier = !YFCObject.isVoid(eleSrcTracking)
					? SCXmlUtil.getChildElement(eleSrcTracking, "carrier") : null;
			Element eleAmazonTracking = !YFCObject.isVoid(eleSrcTracking)
					? SCXmlUtil.getChildElement(eleSrcTracking, "amazon") : null;
			if (!YFCObject.isVoid(eleCarrier) || !YFCObject.isVoid(eleAmazonTracking)) {
				Element eleNewTracking = SCXmlUtil.createChild(eleNewPkg, "tracking");
				if (!YFCObject.isVoid(eleCarrier)) {
					Element eleNewCarrier = SCXmlUtil.createChild(eleNewTracking, "carrier");
					eleNewCarrier.setAttribute("carrierCode", eleCarrier.getAttribute("carrierCode"));
					eleNewCarrier.setAttribute("trackingNumber", eleCarrier.getAttribute("trackingNumber"));
				}
				if (!YFCObject.isVoid(eleAmazonTracking)) {
					Element eleNewAmazon = SCXmlUtil.createChild(eleNewTracking, "amazon");
					eleNewAmazon.setAttribute("trackingNumber", eleAmazonTracking.getAttribute("trackingNumber"));
				}
			}
		}

		return doc;
	}

	/**
	 * Builds a SHIPMENT_PACKAGE_STATUS_CHANGED payload from the snapshot's <shipments> element.
	 * Copies the shipment with its <shipmentPackages> as-is (status is on the element).
	 *
	 * Output format matches what AmzProcessMCFPackageStatusEvent expects:
	 * <AmzProcessShipmentPackageStatusChangedEvent eventType="SHIPMENT_PACKAGE_STATUS_CHANGED" merchantId="...">
	 *   <fulfillmentOrder orderId="..." status="...">
	 *     <shipments amazonShipmentId="..." status="SHIPPED">
	 *       <shipmentPackages packageNumber="..." status="DELIVERED">
	 *         <carrierTracking carrierCode="..." trackingNumber="..."/>
	 *       </shipmentPackages>
	 *     </shipments>
	 *   </fulfillmentOrder>
	 * </AmzProcessShipmentPackageStatusChangedEvent>
	 */
	public static Document buildPackageStatusPayload(String amazonOrderId,
			String orderStatus, Element eleShipment) {
		Document doc = SCXmlUtil.createDocument("AmzProcessShipmentPackageStatusChangedEvent");
		Element eleRoot = doc.getDocumentElement();
		eleRoot.setAttribute("eventType", "SHIPMENT_PACKAGE_STATUS_CHANGED");

		Element eleFO = SCXmlUtil.createChild(eleRoot, "order");
		eleFO.setAttribute("orderId", amazonOrderId);
		eleFO.setAttribute("status", orderStatus);

		Element eleNewShipment = SCXmlUtil.createChild(eleFO, "shipments");
		eleNewShipment.setAttribute("amazonShipmentId", eleShipment.getAttribute("amazonShipmentId"));
		eleNewShipment.setAttribute("status", eleShipment.getAttribute("status"));

		// Snapshot is SP-API 2026-07-04 shaped: read 2026-07-04 <packages>/<tracking><carrier>, write 2026-07-04
		NodeList pkgNodes = eleShipment.getElementsByTagName("packages");
		for (int i = 0; i < pkgNodes.getLength(); i++) {
			Element elePkg = (Element) pkgNodes.item(i);
			Element eleNewPkg = SCXmlUtil.createChild(eleNewShipment, "packages");
			eleNewPkg.setAttribute("packageId", elePkg.getAttribute("packageId"));
			eleNewPkg.setAttribute("status", elePkg.getAttribute("status"));

			Element eleSrcTracking = SCXmlUtil.getChildElement(elePkg, "tracking");
			Element eleCarrier = !YFCObject.isVoid(eleSrcTracking)
					? SCXmlUtil.getChildElement(eleSrcTracking, "carrier") : null;
			if (!YFCObject.isVoid(eleCarrier)) {
				Element eleNewTracking = SCXmlUtil.createChild(eleNewPkg, "tracking");
				Element eleNewCarrier = SCXmlUtil.createChild(eleNewTracking, "carrier");
				eleNewCarrier.setAttribute("carrierCode", eleCarrier.getAttribute("carrierCode"));
				eleNewCarrier.setAttribute("trackingNumber", eleCarrier.getAttribute("trackingNumber"));
			}
		}

		return doc;
	}

	/**
	 * Builds an ORDER_STATUS_CHANGED payload with DELTA quantities (not full amounts).
	 * Only includes line items that actually need cancellation.
	 *
	 * The deltaLineItems list contains [lineItemId, deltaCancelled, deltaUnfulfillable]
	 * for each line that needs action.
	 *
	 * Output format matches what AmzProcessMCFOrderStatusChangeEvent expects:
	 * <AmzProcessMCFOrderStatusChangedEvent eventType="ORDER_STATUS_CHANGED" merchantId="...">
	 *   <fulfillmentOrder orderId="..." status="...">
	 *     <lineItems lineItemId="...">
	 *       <cancelledAmount unit="EACHES" value="..."/>
	 *       <unfulfillableAmount unit="EACHES" value="..."/>
	 *     </lineItems>
	 *   </fulfillmentOrder>
	 * </AmzProcessMCFOrderStatusChangedEvent>
	 */
	public static Document buildOrderStatusChangePayload(String amazonOrderId,
			String orderStatus, Element eleFulfillmentOrder,
			java.util.ArrayList<String[]> deltaLineItems) {
		Document doc = SCXmlUtil.createDocument("AmzProcessMCFOrderStatusChangedEvent");
		Element eleRoot = doc.getDocumentElement();
		eleRoot.setAttribute("eventType", "ORDER_STATUS_CHANGED");

		Element eleFO = SCXmlUtil.createChild(eleRoot, "order");
		eleFO.setAttribute("orderId", amazonOrderId);
		eleFO.setAttribute("status", orderStatus);

		NodeList origLineItems = eleFulfillmentOrder.getElementsByTagName("lineItems");

		for (String[] delta : deltaLineItems) {
			String lineItemId = delta[0];
			String deltaCancelled = delta[1];
			String deltaUnfulfillable = delta[2];

			Element eleNewLineItem = SCXmlUtil.createChild(eleFO, "lineItems");
			eleNewLineItem.setAttribute("lineItemId", lineItemId);

			// Find original lineItem to copy product info and amount
			for (int i = 0; i < origLineItems.getLength(); i++) {
				Element eleOrig = (Element) origLineItems.item(i);
				if (lineItemId.equals(eleOrig.getAttribute("lineItemId"))
						&& eleOrig.getParentNode().getNodeName().equals("orders")) {

					Element eleProduct = SCXmlUtil.getChildElement(eleOrig, "product");
					if (!YFCObject.isVoid(eleProduct)) {
						Element eleNewProduct = SCXmlUtil.createChild(eleNewLineItem, "product");
						Element eleProdId = SCXmlUtil.getChildElement(eleProduct, "productIdentifier");
						if (!YFCObject.isVoid(eleProdId)) {
							Element eleNewProdId = SCXmlUtil.createChild(eleNewProduct, "productIdentifier");
							eleNewProdId.setAttribute("amazonSku", eleProdId.getAttribute("amazonSku"));
						}
					}

					Element eleAmount = SCXmlUtil.getChildElement(eleOrig, "amount");
					if (!YFCObject.isVoid(eleAmount)) {
						Element eleNewAmount = SCXmlUtil.createChild(eleNewLineItem, "amount");
						eleNewAmount.setAttribute("unit", eleAmount.getAttribute("unit"));
						eleNewAmount.setAttribute("value", eleAmount.getAttribute("value"));
					}
					break;
				}
			}

			Element eleCancelled = SCXmlUtil.createChild(eleNewLineItem, "cancelledAmount");
			eleCancelled.setAttribute("unit", "EACHES");
			eleCancelled.setAttribute("value", deltaCancelled);

			Element eleUnfulfillable = SCXmlUtil.createChild(eleNewLineItem, "unfulfillableAmount");
			eleUnfulfillable.setAttribute("unit", "EACHES");
			eleUnfulfillable.setAttribute("value", deltaUnfulfillable);
		}

		return doc;
	}

	/**
	 * Reads a child element's "value" attribute as a double.
	 * Returns 0.0 if the child element is missing, value is empty, or not a valid number.
	 */
	public static double getChildElementValue(Element parent, String childTagName) {
		Element child = SCXmlUtil.getChildElement(parent, childTagName);
		if (YFCObject.isVoid(child)) {
			return 0.0;
		}
		String value = child.getAttribute("value");
		if (YFCObject.isVoid(value)) {
			return 0.0;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}
}
