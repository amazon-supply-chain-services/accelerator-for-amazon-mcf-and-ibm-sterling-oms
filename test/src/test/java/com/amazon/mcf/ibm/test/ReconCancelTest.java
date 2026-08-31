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
 * FLOW — Reconciliation, CANCEL scenario (Amazon -> OMS), deep.
 *
 * <p>The reconciliation agent pulls the 2026-07-04 order snapshot and, for a line item whose
 * cancelled/unfulfillable quantity exceeds what OMS has already cancelled, internally generates the
 * order-status-changed event and hands it to the real cancel processor. This test enters through
 * the REAL reconciliation path ({@code ListMCFOrders -> AmzReconcileMCFOrderSnapshot}) and, with
 * the OMS order-line lookup scripted as a Released line, drives the chain until it BUILDS and
 * submits the OMS {@code changeRelease} request — the same depth the standalone cancel test
 * reached, produced the way production actually produces the event.
 */
class ReconCancelTest {

	@BeforeEach
	void setUp() throws Exception {
		AmzCommonUtil.reset();
		AmzGetGenericProperty.reset();
		AmzGetGenericProperty.set("amzConn.amazonShipNode.US", "AMZ-US");
		AmzRestWebserviceUtil.reset();
		AmzRestWebserviceUtil.setNextResponse(
				OmsTestSupport.readSample("recon_cancel_snapshot.json"));
	}

	@AfterEach
	void tearDown() {
		AmzCommonUtil.reset();
		AmzGetGenericProperty.reset();
		AmzRestWebserviceUtil.reset();
	}

	@Test
	@DisplayName("Reconciliation -> cancel scenario -> builds the OMS changeRelease request (deep)")
	void reconDrivesCancelToChangeRelease() throws Exception {
		Document jobDoc = reconInputFromSnapshot();

		AmzCommonUtil.script("getOrderLineList",
				OmsTestSupport.readSample("oms/getOrderLineList_recon_output.xml"));
		AmzCommonUtil.stopAt("AmzChangeRelease");

		AmzReconcileMCFOrderSnapshot reconciler = new AmzReconcileMCFOrderSnapshot();
		try {
			reconciler.reconcile(null, jobDoc);
			fail("Expected reconcile to drive the cancel scenario to the OMS changeRelease call");
		} catch (Throwable thrown) {
			EventTestSupport.assertReachedOmsBoundary(thrown, "AmzChangeRelease");
		}

		// Verify the OMS changeRelease request the connector BUILT (same depth as the standalone test).
		Document produced = AmzCommonUtil.getCapturedInput("AmzChangeRelease");
		org.junit.jupiter.api.Assertions.assertNotNull(produced,
				"changeRelease request should have been captured at the boundary");
		OmsTestSupport.assertXmlEquals(
				OmsTestSupport.parseXml(OmsTestSupport.readExpected("expected_changeRelease.xml")),
				produced);
	}

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
