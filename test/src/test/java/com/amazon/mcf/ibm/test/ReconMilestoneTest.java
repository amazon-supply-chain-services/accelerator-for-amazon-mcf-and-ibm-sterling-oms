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
 * FLOW — Reconciliation, MILESTONE (package status) scenario (Amazon -> OMS), deep.
 *
 * <p>The reconciliation agent pulls the 2026-07-04 order snapshot and, for a delivered package
 * whose milestone has not yet been recorded, internally generates the package-status event and
 * hands it to the real package processor. This test enters through the REAL reconciliation path
 * ({@code ListMCFOrders -> AmzReconcileMCFOrderSnapshot}): the container lookup is scripted
 * POPULATED (so the shipment branch skips) and the milestone-list service is scripted empty (so a
 * milestone is needed). The chain then drives the package processor until it reaches the OMS
 * milestone write ({@code AmzConnUpdateMilestonesRecordInOMS}) — the deepest point in the
 * package-status flow, produced the way production actually produces the event.
 */
class ReconMilestoneTest {

	@BeforeEach
	void setUp() throws Exception {
		AmzCommonUtil.reset();
		AmzGetGenericProperty.reset();
		AmzGetGenericProperty.set("amzConn.amazonShipNode.US", "AMZ-US");
		AmzRestWebserviceUtil.reset();
		AmzRestWebserviceUtil.setNextResponse(
				OmsTestSupport.readSample("recon_milestone_snapshot.json"));
	}

	@AfterEach
	void tearDown() {
		AmzCommonUtil.reset();
		AmzGetGenericProperty.reset();
		AmzRestWebserviceUtil.reset();
	}

	@Test
	@DisplayName("Reconciliation -> milestone scenario -> reaches the OMS milestone update (deep)")
	void reconDrivesMilestoneToOmsUpdate() throws Exception {
		Document jobDoc = reconInputFromSnapshot();

		AmzCommonUtil.script("getOrderLineList",
				OmsTestSupport.readSample("oms/getOrderLineList_recon_output.xml"));
		AmzCommonUtil.script("getShipmentContainerList",
				OmsTestSupport.readSample("oms/getShipmentContainerList_populated_output.xml"));
		AmzCommonUtil.script("AmzConnGetContainerMilestonesList",
				OmsTestSupport.readSample("oms/getAmzConnContainerMilestonesList_empty_output.xml"));
		AmzCommonUtil.stopAt("AmzConnUpdateMilestonesRecordInOMS");

		AmzReconcileMCFOrderSnapshot reconciler = new AmzReconcileMCFOrderSnapshot();
		try {
			reconciler.reconcile(null, jobDoc);
			fail("Expected reconcile to drive the milestone scenario to the OMS milestone update");
		} catch (Throwable thrown) {
			EventTestSupport.assertReachedOmsBoundary(thrown, "AmzConnUpdateMilestonesRecordInOMS");
		}
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
