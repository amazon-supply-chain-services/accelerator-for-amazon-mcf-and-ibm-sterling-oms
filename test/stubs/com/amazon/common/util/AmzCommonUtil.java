package com.amazon.common.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import com.yantra.yfs.japi.YFSEnvironment;

/**
 * OFFLINE TEST STUB of the accelerator's {@code AmzCommonUtil} — the OMS-invocation helper.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. It replaces the connector's calls into
 * IBM Sterling OMS so the REAL production classes can run OFFLINE. It is <b>scriptable</b> so tests
 * can exercise the connector PAST the first OMS call:
 * <ul>
 *   <li>{@link #script(String, String)} — register a canned XML response for an OMS API/service
 *       name (e.g. the captured {@code getOrderLineList} / {@code getShipmentContainerList}
 *       output). Subsequent calls to that name return the parsed document.</li>
 *   <li>{@link #stopAt(String)} — mark an API/service name as the intended stopping point; when the
 *       connector calls it, a {@link BoundaryReachedException} is thrown and the document the
 *       connector passed in is captured (so a test can assert the produced OMS request).</li>
 *   <li>{@link #getCapturedInput(String)} — the last input document the connector sent to a name.</li>
 * </ul>
 *
 * <p>Defaults with no scripting: {@code getCommonCodeList} returns a benign empty list; any other
 * OMS call throws {@link BoundaryReachedException} (the classic boundary-stop behavior). Call
 * {@link #reset()} between tests.
 *
 * <p>The real utility performs live OMS calls with a licensed connector at runtime; this stub is
 * used only by the test harness.
 */
public final class AmzCommonUtil {

	private static final Map<String, Document> SCRIPTED = new HashMap<>();
	private static final Map<String, Document> CAPTURED = new HashMap<>();
	private static String stopApi;

	private AmzCommonUtil() {
	}

	// ---- test scripting API --------------------------------------------------------------------

	/** Register a canned XML response document for an OMS API/service name. */
	public static void script(String api, String responseXml) {
		SCRIPTED.put(api, parse(responseXml));
	}

	/** Mark the OMS API/service name at which the connector should stop (boundary). */
	public static void stopAt(String api) {
		stopApi = api;
	}

	/** The last input document the connector passed to the given OMS API/service name. */
	public static Document getCapturedInput(String api) {
		return CAPTURED.get(api);
	}

	/** Clear all scripting/capture state. Call between tests. */
	public static void reset() {
		SCRIPTED.clear();
		CAPTURED.clear();
		stopApi = null;
	}

	// ---- boundary marker -----------------------------------------------------------------------

	/** Thrown when the connector reaches the chosen stop API (or any un-scripted OMS call). */
	public static final class BoundaryReachedException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final transient String api;

		public BoundaryReachedException(String api) {
			super("Offline Sterling boundary reached at OMS operation: " + api);
			this.api = api;
		}

		public String getApi() {
			return api;
		}
	}

	// ---- invocation entry points the connector calls -------------------------------------------

	/** callAPI(env, inDoc, apiName, template) */
	public static Document callAPI(YFSEnvironment env, Document inDoc, String api, Document template) {
		return dispatch(api, inDoc);
	}

	/** invokeAPI(env, template, apiName, inDoc) */
	public static Document invokeAPI(YFSEnvironment env, String template, String api, Document inDoc) {
		return dispatch(api, inDoc);
	}

	/** invokeService(env, inDoc, serviceName, template) */
	public static Document invokeService(YFSEnvironment env, Document inDoc, String service, Document template) {
		return dispatch(service, inDoc);
	}

	/** invokeService(env, serviceName, inDoc) */
	public static Document invokeService(YFSEnvironment env, String service, Document inDoc) {
		return dispatch(service, inDoc);
	}

	/** callService(env, inDoc, serviceName, template) — queue handoff. */
	public static Document callService(YFSEnvironment env, Document inDoc, String service, Document template) {
		return dispatch(service, inDoc);
	}

	private static Document dispatch(String api, Document inDoc) {
		CAPTURED.put(api, inDoc);
		if (api != null && api.equals(stopApi)) {
			throw new BoundaryReachedException(api);
		}
		if (SCRIPTED.containsKey(api)) {
			return SCRIPTED.get(api);
		}
		if ("getCommonCodeList".equals(api)) {
			return emptyCommonCodeList();
		}
		// No script and not the explicit stop: this is still the Sterling boundary.
		throw new BoundaryReachedException(api);
	}

	// ---- misc helpers the connector calls ------------------------------------------------------

	public static RuntimeException createException(Exception e) {
		return (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
	}

	public static HashMap<String, String> getErrorCodeAndDetails(String output) {
		return new HashMap<>();
	}

	public static void logAmzConnRequest(Document inDoc) {
		// no-op offline
	}

	public static void logAmzConnResponse(Document outDoc) {
		// no-op offline
	}

	// ---- internals -----------------------------------------------------------------------------

	private static Document emptyCommonCodeList() {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			doc.appendChild(doc.createElement("CommonCodeList"));
			return doc;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Document parse(String xml) {
		try {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse scripted OMS response: " + e.getMessage(), e);
		}
	}
}
