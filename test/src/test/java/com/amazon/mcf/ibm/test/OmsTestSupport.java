package com.amazon.mcf.ibm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Shared helpers for the deep (past-the-boundary) flow tests.
 *
 * <p>Provides fixture loading and a semantic XML comparison (element name + attributes + child
 * structure, ignoring attribute order and whitespace) so that a document the connector BUILDS can
 * be compared to an expected reference without brittleness over formatting.
 */
final class OmsTestSupport {

	static final File SAMPLES = new File(System.getProperty("samples.dir", "samples"));
	static final File EXPECTED = new File(System.getProperty("expected.dir", "expected"));

	private OmsTestSupport() {
	}

	static String readSample(String relativePath) throws Exception {
		return new String(Files.readAllBytes(new File(SAMPLES, relativePath).toPath()), StandardCharsets.UTF_8);
	}

	static String readExpected(String relativePath) throws Exception {
		return new String(Files.readAllBytes(new File(EXPECTED, relativePath).toPath()), StandardCharsets.UTF_8);
	}

	static Document parseXml(String xml) throws Exception {
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setIgnoringComments(true);
		f.setIgnoringElementContentWhitespace(true);
		return f.newDocumentBuilder().parse(
				new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	/** Assert two DOM documents are semantically equal (structure + attributes, order-insensitive). */
	static void assertXmlEquals(Document expected, Document actual) {
		assertElementEquals(expected.getDocumentElement(), actual.getDocumentElement(), "/");
	}

	private static void assertElementEquals(Element expected, Element actual, String path) {
		assertNotNull(actual, "missing element at " + path);
		String p = path + expected.getNodeName();
		assertEquals(expected.getNodeName(), actual.getNodeName(), "element name at " + p);
		assertEquals(attrs(expected), attrs(actual), "attributes at " + p);

		List<Element> ec = childElements(expected);
		List<Element> ac = childElements(actual);
		assertEquals(ec.size(), ac.size(), "child element count at " + p + " (expected "
				+ names(ec) + ", actual " + names(ac) + ")");
		for (int i = 0; i < ec.size(); i++) {
			assertElementEquals(ec.get(i), ac.get(i), p + "/");
		}
	}

	private static Map<String, String> attrs(Element e) {
		Map<String, String> m = new TreeMap<>();
		NamedNodeMap nm = e.getAttributes();
		for (int i = 0; i < nm.getLength(); i++) {
			Node a = nm.item(i);
			m.put(a.getNodeName(), a.getNodeValue());
		}
		return m;
	}

	private static List<Element> childElements(Element e) {
		List<Element> out = new ArrayList<>();
		NodeList nl = e.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) {
				out.add((Element) nl.item(i));
			}
		}
		return out;
	}

	private static List<String> names(List<Element> els) {
		List<String> n = new ArrayList<>();
		for (Element e : els) {
			n.add(e.getNodeName());
		}
		return n;
	}
}
