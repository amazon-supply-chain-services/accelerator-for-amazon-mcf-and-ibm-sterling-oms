package com.sterlingcommerce.baseutil;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.sterlingcommerce.baseutil.SCXmlUtil}.
 *
 * <p>This is NOT IBM code and contains NO IBM proprietary material. It is a minimal, clean-room
 * re-implementation of only the handful of static helpers the accelerator's transformation
 * classes call, backed entirely by the standard JDK XML (JAXP / org.w3c.dom) API. It exists so
 * the REAL production classes can be compiled and exercised offline.
 *
 * <p>The real Sterling {@code SCXmlUtil} (and its behavior) is supplied by your licensed IBM
 * Sterling OMS at runtime; this stub is used only by the test harness.
 */
public final class SCXmlUtil {

	private SCXmlUtil() {
	}

	/** Create a new document whose root element is {@code rootTag}. */
	public static Document createDocument(String rootTag) {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			doc.appendChild(doc.createElement(rootTag));
			return doc;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Create and append a child element {@code tag} under {@code parent}; return the child. */
	public static Element createChild(Element parent, String tag) {
		Element child = parent.getOwnerDocument().createElement(tag);
		parent.appendChild(child);
		return child;
	}

	/** Return the first direct-or-descendant child element named {@code tag}, or null. */
	public static Element getChildElement(Element parent, String tag) {
		if (parent == null) {
			return null;
		}
		// Match Sterling semantics closely enough for the accelerator: first element with the tag.
		NodeList nl = parent.getElementsByTagName(tag);
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				return (Element) n;
			}
		}
		return null;
	}

	/** Parse an XML string into a document (Sterling template/temp docs are created this way). */
	public static Document createFromString(String xml) {
		try {
			if (xml == null || xml.trim().isEmpty()) {
				// Sterling template constants may be paths/blank in the accelerator; return an empty doc.
				return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			}
			return DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new java.io.ByteArrayInputStream(
							xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (Exception e) {
			// Non-XML template identifiers are tolerated offline — hand back an empty doc.
			try {
				return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	/** Evaluate an XPath from {@code context} and return the resulting string (attribute) value. */
	public static String getXpathAttribute(Element context, String xpath) {
		if (context == null) {
			return "";
		}
		try {
			javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
			return (String) xp.evaluate(xpath, context, javax.xml.xpath.XPathConstants.STRING);
		} catch (Exception e) {
			return "";
		}
	}

	/** Serialize an element to a string (used by tests to inspect produced XML). */
	public static String getString(Node node) {
		try {
			javax.xml.transform.Transformer t =
					javax.xml.transform.TransformerFactory.newInstance().newTransformer();
			t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
			java.io.StringWriter sw = new java.io.StringWriter();
			t.transform(new javax.xml.transform.dom.DOMSource(node),
					new javax.xml.transform.stream.StreamResult(sw));
			return sw.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
