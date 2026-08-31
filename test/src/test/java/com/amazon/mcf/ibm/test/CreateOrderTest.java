package com.amazon.mcf.ibm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.integrator.order.api.MCFCreateFulfillmentOrderInAmazon;

/**
 * FLOW 1 — Create Fulfillment Order (OMS -> Amazon).
 *
 * <p>Input: the Sterling "ReleaseOrder" Order XML in {@code samples/order_from_sterling.xml}.
 * MCF create-order processing runs through the REAL production class
 * {@code MCFCreateFulfillmentOrderInAmazon} (invoked in production by
 * {@code AmzProcessReleaseOrderMessageWithMCF}), which uses the REAL request builder
 * {@code MCFPrepareSPAPICreateFulfillmentOrderRequest} to produce the SP-API
 * {@code createFulfillmentOrder} (2026-07-04) request body and POST it to Amazon.
 *
 * <p>Offline, the SP-API HTTP client is stubbed ({@code AmzRestWebserviceUtil}), so the POST is
 * captured rather than sent. This test asserts the captured request body equals the expected
 * {@code expected/expected_createOrder.json} — i.e. that the connector produces exactly the right
 * Amazon order JSON from the Sterling order.
 *
 * <p>A null release key is used, matching the offline path (no live {@code getOrderReleaseList}
 * lookup), so {@code orderId} is the base {@code OrderNo} without a release suffix.
 */
class CreateOrderTest {

	private static final File SAMPLES = new File(System.getProperty("samples.dir", "samples"));
	private static final File EXPECTED = new File(System.getProperty("expected.dir", "expected"));

	@BeforeEach
	void resetHttp() {
		AmzRestWebserviceUtil.reset();
		// A blank SP-API response is treated as success (no "errors"); the create path then reaches
		// the OMS changeOrder boundary, which is out of scope here.
		AmzRestWebserviceUtil.setNextResponse("");
	}

	@Test
	@DisplayName("Sterling Order XML -> SP-API createFulfillmentOrder JSON (2026-07-04)")
	void producesExpectedCreateOrderJson() throws Exception {
		Document orderDoc = parseFile(new File(SAMPLES, "order_from_sterling.xml"));
		Element eleOrder = orderDoc.getDocumentElement();

		// The one MCF-eligible line in the fixture (PrimeLineNo=1, OrderLineKey below).
		List<String> primeLineNos = new ArrayList<>();
		primeLineNos.add("1");
		List<String> orderLineKeys = new ArrayList<>();
		orderLineKeys.add("202603151549159158697");

		MCFCreateFulfillmentOrderInAmazon createOrder = new MCFCreateFulfillmentOrderInAmazon();
		try {
			// Null release key => offline path (orderId = base OrderNo, no live getOrderReleaseList).
			createOrder.createFulfillmentOrderInAmazon(null, eleOrder, primeLineNos, orderLineKeys, null);
		} catch (Throwable expectedBoundary) {
			// After building + POSTing the JSON, the success path calls OMS changeOrder, which hits
			// the stubbed Sterling boundary. The POST body was already captured before that.
		}

		String producedJson = AmzRestWebserviceUtil.getLastPostBody();
		assertNotNull(producedJson, "The create path should have POSTed a request body to SP-API");

		JSONObject produced = new JSONObject(producedJson);
		JSONObject expected = new JSONObject(
				new String(Files.readAllBytes(new File(EXPECTED, "expected_createOrder.json").toPath()),
						StandardCharsets.UTF_8));
		expected.remove("_comment");

		assertJsonEquals(expected, produced, "$");
	}

	// ---- helpers ------------------------------------------------------------------------------

	private static Document parseFile(File f) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(false);
		return dbf.newDocumentBuilder().parse(f);
	}

	/** Deep, order-insensitive JSON equality with a path for readable failure messages. */
	private static void assertJsonEquals(Object expected, Object actual, String path) throws Exception {
		if (expected instanceof JSONObject) {
			assertNotNull(actual, "missing object at " + path);
			JSONObject eo = (JSONObject) expected;
			JSONObject ao = (JSONObject) actual;
			assertEquals(keySet(eo), keySet(ao), "keys differ at " + path);
			for (Iterator<String> it = eo.keys(); it.hasNext(); ) {
				String k = it.next();
				assertJsonEquals(eo.get(k), ao.get(k), path + "." + k);
			}
		} else if (expected instanceof JSONArray) {
			JSONArray ea = (JSONArray) expected;
			JSONArray aa = (JSONArray) actual;
			assertEquals(ea.length(), aa.length(), "array length differs at " + path);
			for (int i = 0; i < ea.length(); i++) {
				assertJsonEquals(ea.get(i), aa.get(i), path + "[" + i + "]");
			}
		} else {
			assertEquals(String.valueOf(expected), String.valueOf(actual), "value differs at " + path);
		}
	}

	private static java.util.Set<String> keySet(JSONObject o) throws Exception {
		java.util.Set<String> s = new java.util.TreeSet<>();
		for (Iterator<String> it = o.keys(); it.hasNext(); ) {
			s.add(it.next());
		}
		return s;
	}
}
