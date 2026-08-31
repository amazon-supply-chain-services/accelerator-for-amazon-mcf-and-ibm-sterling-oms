package com.amazon.common.util;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;

import com.yantra.yfs.japi.YFSEnvironment;

/**
 * OFFLINE TEST STUB of the accelerator's {@code AmzGetGenericProperty}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. Provides the accessors the connector
 * references. It is <b>scriptable</b>: a test can seed generic properties (for example the
 * ship-node mapping {@code amzConn.amazonShipNode.US -> AMZ-US}) that production code reads while
 * building OMS documents. Defaults to an empty map. Test harness use only.
 */
public final class AmzGetGenericProperty {

	private static final Map<String, String> PROPS = new HashMap<>();

	private AmzGetGenericProperty() {
	}

	/** Seed a generic property (e.g. "amzConn.amazonShipNode.US", "AMZ-US"). */
	public static void set(String key, String value) {
		PROPS.put(key, value);
	}

	/** Clear all seeded properties (call between tests). */
	public static void reset() {
		PROPS.clear();
	}

	public static Map<String, String> getGenericProperties(YFSEnvironment env, Document propertyDoc) {
		return new HashMap<>(PROPS);
	}

	/**
	 * SP-API integration properties lookup. Returns an empty map offline so the production create
	 * path falls back to its built-in default SP-API URL and default timeout.
	 */
	public static Map<String, String> getSPIntegProperties(Document propertyDoc) {
		return new HashMap<>();
	}
}
