package com.amazon.mcf.ibm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazon.common.util.AmzCommonUtil;

/**
 * Shared assertion helper for the reconciliation deep tests (shipment, milestone, cancel).
 *
 * <p>For MCF this connector does not consume Amazon notifications. The reconciliation agent pulls
 * the 2026-07-04 order snapshot and internally generates the shipment / package-status / order-status
 * events, which drive the real processors until they reach the first live OMS operation (the
 * Sterling boundary). Reaching the expected OMS API proves the reconciliation-generated event was
 * consumed correctly.
 */
final class EventTestSupport {

	private EventTestSupport() {
	}

	/**
	 * Assert the connector reached the expected OMS operation offline. The boundary may propagate
	 * directly as a {@code BoundaryReachedException} or be re-wrapped in a {@code YFSException} whose
	 * message carries the OMS API name — both prove the boundary was reached.
	 */
	static void assertReachedOmsBoundary(Throwable thrown, String expectedApi) {
		for (Throwable t = thrown; t != null; t = t.getCause()) {
			if (t instanceof AmzCommonUtil.BoundaryReachedException) {
				assertEquals(expectedApi, ((AmzCommonUtil.BoundaryReachedException) t).getApi(),
						"Reached an OMS operation, but not the expected one");
				return;
			}
		}
		for (Throwable t = thrown; t != null; t = t.getCause()) {
			String msg = t.getMessage();
			if (msg != null && msg.contains("Offline Sterling boundary reached")
					&& msg.contains(expectedApi)) {
				return;
			}
		}
		throw new AssertionError("Did not reach OMS boundary '" + expectedApi + "'. Actual: " + thrown, thrown);
	}
}
