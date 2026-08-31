package com.amazon.mcf.ibm.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.integrator.delivery.api.ListMCFOrders;
import com.amazon.oms.order.agent.AmzReconcileMCFOrderSnapshot;
import com.sterlingcommerce.baseutil.SCXmlUtil;

/**
 * FLOW — Reconciliation, SHIPMENT scenario (Amazon -> OMS), deep.
 *
 * <p>For MCF this connector does not consume Amazon notifications directly. The reconciliation
 * agent pulls the 2026-07-04 order snapshot and internally generates the shipment event, which it
 * hands to the real shipment processor. This test enters through the REAL reconciliation path
 * ({@code ListMCFOrders -> AmzReconcileMCFOrderSnapshot}) and, with the OMS container lookup
 * scripted empty (no existing container), drives the chain until it BUILDS and submits the OMS
 * {@code confirmShipment} request — the same depth the standalone shipment test reached, but
 * reached the way production actually produces the event.
 */
class ReconShipmentTest {

	@BeforeEach
	void setUp() throws Exception {
		AmzCommonUtil.reset();
		AmzGetGenericProperty.reset();
		AmzGetGenericProperty.set("amzConn.amazonShipNode.US", "AMZ-US");
		AmzRestWebserviceUtil.reset();
		AmzRestWebserviceUtil.setNextResponse(
				OmsTestSupport.readSample("recon_shipment_snapshot.json"));
	}

	@AfterEach
	void tearDown() {
		AmzCommonUtil.reset();
		AmzGetGenericProperty.reset();
		AmzRestWebserviceUtil.reset();
	}

	@Test
	@DisplayName("Reconciliation -> shipment scenario -> builds the OMS confirmShipment request (deep)")
	void reconDrivesShipmentToConfirmShipment() throws Exception {
		Document jobDoc = reconInputFromSnapshot();

		AmzCommonUtil.script("getOrderLineList",
				OmsTestSupport.readSample("oms/getOrderLineList_recon_output.xml"));
		AmzCommonUtil.script("getShipmentContainerList",
				OmsTestSupport.readSample("oms/getShipmentContainerList_empty_output.xml"));
		AmzCommonUtil.stopAt("confirmShipment");

		AmzReconcileMCFOrderSnapshot reconciler = new AmzReconcileMCFOrderSnapshot();
		try {
			reconciler.reconcile(null, jobDoc);
			fail("Expected reconcile to drive the shipment scenario to the OMS confirmShipment call");
		} catch (Throwable thrown) {
			EventTestSupport.assertReachedOmsBoundary(thrown, "confirmShipment");
		}

		// Verify the OMS confirmShipment request the connector BUILT (same depth as the standalone test).
		Document produced = AmzCommonUtil.getCapturedInput("confirmShipment");
		org.junit.jupiter.api.Assertions.assertNotNull(produced,
				"confirmShipment request should have been captured at the boundary");
		OmsTestSupport.assertXmlEquals(
				OmsTestSupport.parseXml(OmsTestSupport.readExpected("expected_confirmShipment.xml")),
				produced);
	}

	/** Build the reconcile input (&lt;Root&gt;&lt;orders/&gt;&lt;/Root&gt;) from the real ListMCFOrders output. */
	private Document reconInputFromSnapshot() throws Exception {
		Document listInput = SCXmlUtil.createDocument("Order");
		listInput.getDocumentElement().setAttribute("EnterpriseCode", "DEFAULT");
		listInput.getDocumentElement().setAttribute("UpdatedAfter", "2026-07-01T00:00:00Z");
		Document listOut = ListMCFOrders.listMCFOrders(null, listInput);
		Element order = (Element) listOut.getDocumentElement().getElementsByTagName("orders").item(0);
		Document jobDoc = SCXmlUtil.createDocument("Root");
		jobDoc.getDocumentElement().appendChild(jobDoc.importNode(order, true));
		return jobDoc;
	}
}
