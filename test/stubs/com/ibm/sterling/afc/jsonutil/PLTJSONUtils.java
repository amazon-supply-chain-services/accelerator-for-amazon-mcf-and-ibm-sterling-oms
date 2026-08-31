package com.ibm.sterling.afc.jsonutil;

import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * OFFLINE TEST STUB of IBM Sterling AFC's {@code com.ibm.sterling.afc.jsonutil.PLTJSONUtils}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. This is a minimal, clean-room
 * re-implementation of the single helper the accelerator uses to turn an Amazon SP-API JSON
 * response into the XML Document the connector then navigates:
 * {@code getXmlFromJSON(jsonString, rootTag)}.
 *
 * <p><b>Conversion convention.</b> The accelerator's production code reads the converted document
 * with SCXmlUtil/AmzXMLUtil in a specific way — scalar fields via {@code getAttribute(...)} and
 * nested objects/arrays via {@code getChildElement(...)} / {@code getElementsByTagName(...)}
 * (verified across {@code ListMCFOrders}, {@code AmzCreateOrderInAmazon}, and the event
 * processors). This stub therefore reproduces the IBM AFC mapping the code depends on:
 * <ul>
 *   <li>a JSON scalar (string/number/boolean) becomes an XML <b>attribute</b> on the enclosing
 *       element;</li>
 *   <li>a JSON object becomes a <b>child element</b> named after its key;</li>
 *   <li>a JSON array becomes a <b>repeated child element</b> (one per entry, all sharing the
 *       array's key name).</li>
 * </ul>
 * The whole payload is wrapped in a single {@code rootTag} element.
 *
 * <p>The real utility ships with a licensed IBM Sterling OMS; this stub is used only by the offline
 * test harness so the REAL production classes can convert the JSON sample fixtures offline.
 */
public final class PLTJSONUtils {

	private PLTJSONUtils() {
	}

	/** Convert a JSON string into an XML Document rooted at {@code rootTag}. */
	public static Document getXmlFromJSON(String json, String rootTag) {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			Element root = doc.createElement(rootTag);
			doc.appendChild(root);
			JSONObject obj = new JSONObject(json);
			populate(doc, root, obj);
			return doc;
		} catch (Exception e) {
			throw new RuntimeException("Offline PLTJSONUtils.getXmlFromJSON failed: " + e.getMessage(), e);
		}
	}

	/** Apply the scalar->attribute, object/array->child-element mapping onto {@code element}. */
	private static void populate(Document doc, Element element, JSONObject obj) throws Exception {
		java.util.Iterator<String> keys = obj.keys();
		while (keys.hasNext()) {
			String key = keys.next();
			Object value = obj.get(key);
			if (value instanceof JSONObject) {
				Element child = doc.createElement(key);
				element.appendChild(child);
				populate(doc, child, (JSONObject) value);
			} else if (value instanceof JSONArray) {
				JSONArray arr = (JSONArray) value;
				for (int i = 0; i < arr.length(); i++) {
					Object item = arr.get(i);
					Element child = doc.createElement(key);
					element.appendChild(child);
					if (item instanceof JSONObject) {
						populate(doc, child, (JSONObject) item);
					} else if (item instanceof JSONArray) {
						// Rare; represent nested-array entries as repeated wrapper elements.
						JSONObject wrap = new JSONObject();
						wrap.put(key, item);
						populate(doc, child, wrap);
					} else {
						// Scalar array entry -> a value attribute on the repeated element.
						child.setAttribute("value", String.valueOf(item));
					}
				}
			} else {
				// Scalar -> attribute on the enclosing element.
				element.setAttribute(key, String.valueOf(value));
			}
		}
	}
}
