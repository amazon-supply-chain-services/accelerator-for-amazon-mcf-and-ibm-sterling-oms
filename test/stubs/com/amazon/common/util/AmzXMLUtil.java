package com.amazon.common.util;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * OFFLINE TEST STUB of the accelerator's {@code AmzXMLUtil}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. Clean-room re-implementation, backed
 * by the standard JDK XML/XPath API, of only the helpers the create-order builder uses. Used only
 * by the offline test harness; the real utility ships with the accelerator source.
 */
public final class AmzXMLUtil {

	private AmzXMLUtil() {
	}

	public static Document createDocument(String rootTag) {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			doc.appendChild(doc.createElement(rootTag));
			return doc;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Element getChildElement(Element parent, String tag) {
		if (parent == null) {
			return null;
		}
		NodeList nl = parent.getElementsByTagName(tag);
		for (int i = 0; i < nl.getLength(); i++) {
			if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) {
				return (Element) nl.item(i);
			}
		}
		return null;
	}

	/** Create and append a child element {@code tag} under {@code parent}; return the child. */
	public static Element createChild(Element parent, String tag) {
		Element child = parent.getOwnerDocument().createElement(tag);
		parent.appendChild(child);
		return child;
	}

	/** Evaluate an XPath returning the first matching element (relative to context). */
	public static Element getXpathElement(Element context, String xpath) {
		if (context == null) {
			return null;
		}
		try {
			XPath xp = XPathFactory.newInstance().newXPath();
			Node n = (Node) xp.evaluate(xpath, context, XPathConstants.NODE);
			return (n instanceof Element) ? (Element) n : null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Alias of {@link #getString(Node)} — the accelerator uses both names. */
	public static String getXMLString(Node node) {
		return getString(node);
	}

	/** Evaluate an XPath from {@code context} and return the resulting string (attribute) value. */
	public static String getXpathAttribute(Element context, String xpath) {
		if (context == null) {
			return "";
		}
		try {
			XPath xp = XPathFactory.newInstance().newXPath();
			return (String) xp.evaluate(xpath, context, XPathConstants.STRING);
		} catch (Exception e) {
			return "";
		}
	}

	/** Evaluate an XPath from {@code context} and return the matching nodes. */
	public static NodeList getXpathNodes(Element context, String xpath) {
		try {
			XPath xp = XPathFactory.newInstance().newXPath();
			return (NodeList) xp.evaluate(xpath, context, XPathConstants.NODESET);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

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
