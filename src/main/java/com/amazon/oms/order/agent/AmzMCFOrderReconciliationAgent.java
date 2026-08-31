package com.amazon.oms.order.agent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzLiterals;
import com.amazon.integrator.delivery.api.ListMCFOrders;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.ycp.japi.util.YCPBaseAgent;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * Agent that reconciles in-flight MCF orders against Amazon's GetFulfillmentOrder snapshot.
 *
 * getJobs:
 *   Calls ListMCFOrders (SP-API) with updatedAfter timestamp to get all MCF orders
 *   updated since the last run. Iterates through the response and returns one message
 *   per fulfillmentOrder.
 *
 * executeJob:
 *   Passes the full fulfillmentOrder snapshot directly to AmzReconcileMCFOrderSnapshot
 *   to reconcile against OMS state.
 */
public class AmzMCFOrderReconciliationAgent extends YCPBaseAgent {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzMCFOrderReconciliationAgent.class);
	private static final String CLASS_NAME = "AmzMCFOrderReconciliationAgent";

	@Override
	public List<Document> getJobs(YFSEnvironment env, Document criteria, Document lastMessageCreated)
			throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: getJobs -- Starts");
		List<Document> listDocuments = new ArrayList<>();

		if (lastMessageCreated != null && lastMessageCreated.getDocumentElement() != null) {
			return listDocuments;
		}

		String strEnterpriseCode = criteria.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		if (YFCObject.isVoid(strEnterpriseCode)) {
			strEnterpriseCode = AmzCommonConstants.STR_DEFAULT;
		}

		String updatedAfter = getLastUpdatedAfter(criteria);
		if (YFCObject.isVoid(updatedAfter)) {
			logger.info("No updatedAfter timestamp available. Skipping.");
			return listDocuments;
		}

		// Call SP-API ListFulfillmentOrders (handles pagination internally)
		Document listInput = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
		listInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE, strEnterpriseCode);
		listInput.getDocumentElement().setAttribute("UpdatedAfter", updatedAfter);

		logger.debug("ListMCFOrders input: " + SCXmlUtil.getString(listInput));
		Document listOutput = ListMCFOrders.listMCFOrders(env, listInput);
		logger.debug("ListMCFOrders output: " + SCXmlUtil.getString(listOutput));

		if (YFCObject.isVoid(listOutput)) {
			logger.info("No orders returned from ListMCFOrders.");
			return listDocuments;
		}

		// Iterate order elements — pass entire order XML as job.
		// SP-API 2026-07-04: ListMCFOrders accumulates "orders" elements (was "fulfillmentOrders").
		NodeList fulfillmentOrders = listOutput.getDocumentElement().getElementsByTagName("orders");
		for (int i = 0; i < fulfillmentOrders.getLength(); i++) {
			Element eleFO = (Element) fulfillmentOrders.item(i);
			String amazonOrderId = eleFO.getAttribute("orderId");

			if (YFCObject.isVoid(amazonOrderId)) {
				continue;
			}

			// Wrap fulfillmentOrder in a root element with merchantId/enterpriseCode
			Document jobDoc = SCXmlUtil.createDocument("Root");
			jobDoc.getDocumentElement().appendChild(jobDoc.importNode(eleFO, true));

			logger.debug("Adding job for amazonOrderId=" + amazonOrderId);
			listDocuments.add(jobDoc);
		}

		logger.info("class: " + CLASS_NAME + " | method: getJobs -- found " + listDocuments.size() + " orders to reconcile");
		logger.endTimer("class: " + CLASS_NAME + " | method: getJobs -- Ends");
		return listDocuments;
	}

	/**
	 * Returns the updatedAfter timestamp for the ListMCFOrders call.
	 * Reads LastUpdatedHours and LastUpdatedMinutes from the agent criteria document.
	 * Defaults to 2 hours and 0 minutes if not specified.
	 */
	private String getLastUpdatedAfter(Document criteria) {
		long hours = 2;
		long minutes = 0;
		String strHours = criteria.getDocumentElement().getAttribute("LastUpdatedHours");
		String strMinutes = criteria.getDocumentElement().getAttribute("LastUpdatedMinutes");
		if (!YFCObject.isVoid(strHours) && strHours.chars().allMatch(Character::isDigit)) {
			hours = Long.parseLong(strHours);
		}
		if (!YFCObject.isVoid(strMinutes) && strMinutes.chars().allMatch(Character::isDigit)) {
			minutes = Long.parseLong(strMinutes);
		}
		OffsetDateTime updatedAfter = OffsetDateTime.now(ZoneOffset.UTC).minusHours(hours).minusMinutes(minutes);
		String strUpdatedAfter = updatedAfter.toString();
		logger.debug("getLastUpdatedAfter hours=" + hours + " minutes=" + minutes + " updatedAfter=" + strUpdatedAfter);
		return strUpdatedAfter;
	}

	@Override
	public void executeJob(YFSEnvironment env, Document inputDoc) throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: executeJob -- Starts");
		String amazonOrderId = "";
		Element eleFO = SCXmlUtil.getChildElement(inputDoc.getDocumentElement(), "orders");
		if (!YFCObject.isVoid(eleFO)) {
			amazonOrderId = eleFO.getAttribute("orderId");
		}
		logger.debug("executeJob for amazonOrderId=" + amazonOrderId);

		try {
			AmzReconcileMCFOrderSnapshot reconciler = new AmzReconcileMCFOrderSnapshot();
			reconciler.reconcile(env, inputDoc);

			logger.info("Reconciliation completed for amazonOrderId=" + amazonOrderId);

		} catch (YFSException e) {
			String errorCode = e.getErrorCode();
			if (errorCode != null && errorCode.equals("MCF_RECONCILE_ERR_OL_LIST_EMPTY")) {
				// Order or order line not found in OMS — skip, do not retry
				logger.info("class: " + CLASS_NAME + " | executeJob skipping amazonOrderId="
						+ amazonOrderId + " error=" + e.getErrorDescription());
			} else {
				logger.error("class: " + CLASS_NAME + " | executeJob failed for amazonOrderId="
						+ amazonOrderId + " error=" + e.getErrorDescription());
				throw e;
			}
		} catch (Exception e) {
			logger.error("class: " + CLASS_NAME + " | executeJob failed for amazonOrderId="
					+ amazonOrderId + " error=" + e.getMessage());
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode("MCF_RECONCILE_AGENT_ERR");
			yfse.setErrorDescription("Failed to reconcile amazonOrderId=" + amazonOrderId + ": " + e.getMessage());
			throw yfse;
		}

		logger.endTimer("class: " + CLASS_NAME + " | method: executeJob -- Ends");
	}
}
