package com.amazon.common.util;

import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.dom.YFCNodeList;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCException;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.apache.xpath.CachedXPathAPI;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
public class AmzXMLUtil {


	private static YFCLogCategory logger = YFCLogCategory.instance(AmzXMLUtil.class.getName());

	public static final int FOUR = 4;

	public static final int FIVE = 5;

	public static final int SIX = 6;

	public static final String YANTRA_ISNAMESPACEWARE = "yantra.document.isnamespaceaware";

	public static final String RAWTYPES = "rawtypes";

	public static Document newDocument() {
		Document docBuilder = null;
		try {
			DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
			docBuilder = (Document) fac.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			logger.error(e);
			logger.error("Exception In AmzXMLUtil.newDocument. : ", e);
			throw new YFCException(e);
		}

		return docBuilder.getOwnerDocument();
	}

	public static Document getDocument(String inXML) {
		Document retVal = null;

		try {
			if (inXML != null) {
				String modifiedInXML = inXML.trim();
				if (modifiedInXML.length() > 0) {
					if (modifiedInXML.startsWith("<")) {
						StringReader strReader = new StringReader(modifiedInXML);
						InputSource iSource = new InputSource(strReader);
						return getDocument(iSource);
					}

					FileReader inFileReader = new FileReader(modifiedInXML);
					try {
						InputSource iSource = new InputSource(inFileReader);
						retVal = getDocument(iSource);
					} finally {
						inFileReader.close();
					}
				}
			}
		} catch (IOException e) {
			logger.error(e);
			logger.error("Exception In AmzXMLUtil.getDocument : ", e);
			throw new YFCException(e);
		}

		return retVal;
	}

	public static Document getDocument(InputSource inSource) {
		DocumentBuilder dbdr = null;
		Document returndoc = null;

		try {
			DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
			dbdr = fac.newDocumentBuilder();
			returndoc = dbdr.parse(inSource);
		} catch (ParserConfigurationException e) {
			logger.error(e);
			logger.error("Exception In AmzXMLUtil.getDocument : ", e);
			throw new YFCException(e);
		} catch (IOException e) {
			logger.error(e);
			logger.error("Exception In AmzXMLUtil.getDocument : ", e);
			throw new YFCException(e);
		} catch (SAXException e) {		
			logger.error("Exception In AmzXMLUtil.getDocument : ", e);
			logger.error(e);
			throw new YFCException(e);
		}
		return returndoc;
	}

	public static Document getDocument(InputStream inStream) {
		Document retDoc = getDocument(new InputSource(new InputStreamReader(inStream)));
		try {
			inStream.close();
		} catch (IOException e) {
			logger.error(e);
			logger.error("Exception In AmzXMLUtil.getDocument : ", e);
			throw new YFCException(e);
		}
		return retDoc;
	}

	@Deprecated
	public static Document getDocument(String inXMLFileName, boolean isFile) {
		try {
			if (inXMLFileName != null && !inXMLFileName.equals("")) {
				FileReader inFileReader = new FileReader(inXMLFileName);
				InputSource iSource = new InputSource(inFileReader);
				Document doc = getDocument(iSource);
				inFileReader.close();
				return doc;
			}
		} catch (IOException e) {

			logger.error(e);
			logger.error("Exception In AmzXMLUtil.getDocument : ", e);
			throw new YFCException(e);
		}
		return null;
	}

	public static Document createDocument(String docElementTag) {
		Document doc = null;

		try {
			DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
			DocumentBuilder dbdr = fac.newDocumentBuilder();

			doc = dbdr.newDocument();
			Element ele = doc.createElement(docElementTag);
			doc.appendChild(ele);
		} catch (ParserConfigurationException e) {
			logger.error(e);
			logger.error("Exception In AmzXMLUtil.createDocument : ", e);
			throw new YFCException(e);
		}
		return doc;
	}

	@Deprecated
	public static Document addDocument(Document doc1, Document doc2) {
		Element rt1 = doc1.getDocumentElement();
		Element rt2 = doc2.getDocumentElement();

		NodeList nlst2 = rt2.getChildNodes();
		int len = nlst2.getLength();
		Node nd = null;
		for (int i = 0; i < len; i++) {
			nd = doc1.importNode(nlst2.item(i), true);
			rt1.appendChild(nd);
		}
		return doc1;
	}

	public static Document addDocument(Document doc1, Document doc2, boolean ignoreRoot) {
		Element rt1 = doc1.getDocumentElement();
		Element rt2 = doc2.getDocumentElement();
		if (!ignoreRoot) {
			Node nd = doc1.importNode(rt2, true);
			rt1.appendChild(nd);
			return doc1;
		}
		NodeList nlst2 = rt2.getChildNodes();
		int len = nlst2.getLength();
		Node nd = null;
		for (int i = 0; i < len; i++) {
			nd = doc1.importNode(nlst2.item(i), true);
			rt1.appendChild(nd);
		}
		return doc1;
	}

	public static Document getDocumentForElement(Element inElement) {
		Document doc = null;
		try {
			DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();

			DocumentBuilder dbdr = fac.newDocumentBuilder();
			doc = dbdr.newDocument();
			Element docElement = doc.createElement(inElement.getNodeName());
			doc.appendChild(docElement);
			copyElement(doc, inElement, docElement);
		} catch (ParserConfigurationException e) {
			logger.error(e);
			logger.error("Exception In AmzXMLUtil.getDocumentForElement : ", e);
			throw new YFCException(e);
		}

		return doc;
	}

	public static String serialize(Node node) {
		return serialize(node, "iso-8859-1", true);
	}

	public static String serialize(Node node, String encoding, boolean indenting) {
		OutputFormat outFmt = null;
		StringWriter strWriter = null;
		XMLSerializer xmlSerializer = null;
		String retVal = null;

		try {
			outFmt = new OutputFormat("xml", encoding, indenting);
			outFmt.setOmitXMLDeclaration(true);

			strWriter = new StringWriter();

			xmlSerializer = new XMLSerializer(strWriter, outFmt);

			short ntype = node.getNodeType();

			switch (ntype) {
			case 11:
				xmlSerializer.serialize((DocumentFragment) node);
				break;
			case 9:
				xmlSerializer.serialize((Document) node);
				break;
			case 1:
				xmlSerializer.serialize((Element) node);
				break;
			default:
				throw new IOException("Can serialize only Document, DocumentFragment and Element type nodes");
			}

			retVal = strWriter.toString();
		} catch (IOException e) {
			retVal = e.getMessage();
		} finally {
			try {
				strWriter.close();
			} catch (IOException ie) {
				retVal = ie.getMessage();
			}
		}

		return retVal;
	}

	public static Element getFirstElementByName(Element ele, String tagName) {
		StringTokenizer st = new StringTokenizer(tagName, "/");
		Element curr = ele;

		while (st.hasMoreTokens()) {
			String tag = st.nextToken();
			Node node = curr.getFirstChild();
			while (node != null && (node.getNodeType() != 1 || !tag.equals(node.getNodeName()))) {

				node = node.getNextSibling();
			}

			if (node != null) {
				curr = (Element) node;
				continue;
			}
			return null;
		}

		return curr;
	}

	public static Element createElement(Document doc, String elementName, Object hashAttributes) {
		return createElement(doc, elementName, hashAttributes, false);
	}

	public static Element createTextElement(Document doc, String elementName, Object textStr) {
		return createElement(doc, elementName, textStr, true);
	}

	public static Element createTextElement(Document doc, String elementName, String textValue, Hashtable attributes) {
		Element elem = doc.createElement(elementName);
		elem.appendChild(doc.createTextNode(textValue));
		if (attributes != null) {
			Enumeration e = attributes.keys();
			while (e.hasMoreElements()) {
				String attributeName = (String) e.nextElement();
				String attributeValue = (String) attributes.get(attributeName);
				elem.setAttribute(attributeName, attributeValue);
			}
		}
		return elem;
	}

	public static Element appendTextChild(Document doc, Element parentElement, String elementName, String textValue,
			Hashtable attributes) {
		Element elem = doc.createElement(elementName);
		elem.appendChild(doc.createTextNode(textValue));
		if (attributes != null) {
			Enumeration e = attributes.keys();
			while (e.hasMoreElements()) {
				String attributeName = (String) e.nextElement();
				String attributeValue = (String) attributes.get(attributeName);
				elem.setAttribute(attributeName, attributeValue);
			}
		}
		parentElement.appendChild(elem);
		return elem;
	}

	public static Element createElement(Document doc, String elementName, Object hashAttributes, boolean textNodeFlag) {
		Element elem = doc.createElement(elementName);
		if (hashAttributes != null) {
			if (hashAttributes instanceof String) {
				if (textNodeFlag) {
					elem.appendChild(doc.createTextNode((String) hashAttributes));
				}
			} else if (hashAttributes instanceof Hashtable) {
				Enumeration e = ((Hashtable) hashAttributes).keys();
				while (e.hasMoreElements()) {
					String attributeName = (String) e.nextElement();
					String attributeValue = (String) ((Hashtable) hashAttributes).get(attributeName);
					elem.setAttribute(attributeName, attributeValue);
				}
			}
		}
		return elem;
	}

	public static Element appendChild(Document doc, Element parentElement, String elementName, Object value) {
		Element childElement = createElement(doc, elementName, value);
		parentElement.appendChild(childElement);
		return childElement;
	}

	public static void appendChild(Element parentElement, Element childElement) {
		parentElement.appendChild(childElement);
	}

	public static void setAttribute(Element objElement, String attributeName, String attributeValue) {
		objElement.setAttribute(attributeName, attributeValue);
	}

	public static void removeAttribute(Element objElement, String attributeName) {
		objElement.removeAttribute(attributeName);
	}

	public static void removeChild(Element parentElement, Element childElement) {
		parentElement.removeChild(childElement);
	}

	public static void createTextNode(Document doc, Element parentElement, String elementValue) {
		parentElement.appendChild(doc.createTextNode(elementValue));
	}

	public static String getXMLString(Document document) {
		return serialize(document);
	}

	public static String getElementXMLString(Element element) {
		return serialize(element);
	}

	public static void createProcessingInstruction(Document doc, Element rootElement, String strTarget,
			String strData) {
		ProcessingInstruction p = doc.createProcessingInstruction(strTarget, strData);
		doc.insertBefore(p, rootElement);
	}

	public static String getAttribute(Element element, String attributeName) {
		if (element != null) {
			return element.getAttribute(attributeName);
		}
		return null;
	}

	

	public static int getElementsCountByTagName(Element parentElement, String subElementName) {
		NodeList nodeList = parentElement.getElementsByTagName(subElementName);
		if (nodeList != null) {
			return nodeList.getLength();
		}
		return 0;
	}

	public static void removeAll(Node node, short nodeType, String name) {
		if (node.getNodeType() == nodeType && (name == null || node.getNodeName().equals(name))) {
			node.getParentNode().removeChild(node);
		} else {

			NodeList list = node.getChildNodes();
			for (int i = 0; i < list.getLength(); i++) {
				removeAll(list.item(i), nodeType, name);
			}
		}
	}

	public static void copyElement(Document destDoc, Element srcElem, Element destElem) {
		NamedNodeMap attrMap = srcElem.getAttributes();
		int attrLength = attrMap.getLength();
		for (int count = 0; count < attrLength; count++) {
			Node attr = attrMap.item(count);
			String attrName = attr.getNodeName();
			String attrValue = attr.getNodeValue();
			destElem.setAttribute(attrName, attrValue);
		}

		if (srcElem.hasChildNodes()) {
			NodeList childList = srcElem.getChildNodes();
			int numOfChildren = childList.getLength();
			for (int cnt = 0; cnt < numOfChildren; cnt++) {
				Object childSrcNode = childList.item(cnt);
				if (childSrcNode instanceof CharacterData) {
					if (childSrcNode instanceof org.w3c.dom.Text) {
						String data = ((CharacterData) childSrcNode).getData();
						Node childDestNode = destDoc.createTextNode(data);
						destElem.appendChild(childDestNode);
					} else if (childSrcNode instanceof org.w3c.dom.Comment) {
						String data = ((CharacterData) childSrcNode).getData();
						Node childDestNode = destDoc.createComment(data);
						destElem.appendChild(childDestNode);
					}
				} else {
					Element childSrcElem = (Element) childSrcNode;
					Element childDestElem = appendChild(destDoc, destElem, childSrcElem.getNodeName(), null);
					copyElement(destDoc, childSrcElem, childDestElem);
				}
			}
		}
	}

	public static Element importElement(Element parentEle, Element ele2beImported) {
		Element child = null;
		if (parentEle != null && ele2beImported != null) {
			child = (Element) parentEle.getOwnerDocument().importNode(ele2beImported, true);
			parentEle.appendChild(child);
		}
		return child;
	}

	public static Element importElement(Document parentDoc, Element ele2beImported) {
		Element child = null;
		if (parentDoc != null && ele2beImported != null) {
			child = (Element) parentDoc.importNode(ele2beImported, true);
			parentDoc.appendChild(child);
		}
		return child;
	}

	public static boolean isVoid(Object obj) {
	     boolean retVal = false;
	     if (obj == null) {
	       retVal = true;
	  }
	  
	     return retVal;
	}

	

	public static Element getChildElement(Element parentEle, String childName) {
		return getChildElement(parentEle, childName, false);
	}

	public static Element getChildElement(Element parentEle, String childName, boolean createIfNotExists) {
	     Element child = null;
	     if (parentEle != null && !isVoid(childName)) {
	       for (Node n = parentEle.getFirstChild(); n != null; n = n.getNextSibling()) {
	         if (n.getNodeType() == 1 && n.getNodeName().equals(childName)) {
	           return (Element)n;
	      }
	    } 

	    
	       if (createIfNotExists) {
	         child = createChild(parentEle, childName);
	    }
	  } 
	  
	     return child;
	}

	public static Element createChild(Element parentEle, String childName) {
	     Element child = null;
	     if (parentEle != null && !isVoid(childName)) {
	       child = parentEle.getOwnerDocument().createElement(childName);
	       parentEle.appendChild(child);
	  } 
	     return child;
	}

	public static Iterator getChildren(Element ele) {
	     ArrayList list = new ArrayList();
	     if (ele != null && ele.hasChildNodes()) {
	       NodeList childList = ele.getChildNodes();
	       for (int i = 0; i < childList.getLength(); i++) {
	         if (childList.item(i) instanceof Element) {
	           list.add(childList.item(i));
	      }
	    } 
	  } 
	     return list.iterator();
	}

	public static double getDoubleAttribute(Element ele, String attrName) {
	     String val = getAttribute(ele, attrName);
	     if (isVoid(val) || "".equals(val)) {
	       return 0.0D;
	  }
	     return Double.parseDouble(val);
	}

	public static void copyAttributes(Element toEle, Element fromEle) {
	     NamedNodeMap fromAttrbMap = fromEle.getAttributes();
	  
	     if (fromAttrbMap != null) {
	       int fromAttrbMapLength = fromAttrbMap.getLength();
	    
	       for (int i = 0; i < fromAttrbMapLength; i++) {
	         Node attrbNode = fromAttrbMap.item(i);
	      
	         if (attrbNode != null && 
	           attrbNode.getNodeType() == 2) {


	        
	           String attrbName = attrbNode.getNodeName();
	           String attrbVal = attrbNode.getNodeValue();
	        
	           String toAttrbVal = getAttribute(toEle, attrbName);
	        
	           if (toAttrbVal.length() == 0) {
	             setAttribute(toEle, attrbName, attrbVal);
	        }
	      } 
	    } 
	  } 
	}

	public static Document getDocument() {
	  try {
		  DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
	       DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
	       return documentBuilder.newDocument();
	     } catch (ParserConfigurationException e) {
	       logger.error(e);
	       logger.error("Exception In AmzXMLUtil.getDocument : ", e);
	       throw new YFCException(e);
	  } 
	}

	public static YFCDocument getDocumentFromElement(YFCElement yeleInputElement) {
		YFCDocument ydocInput = YFCDocument.createDocument(yeleInputElement.getNodeName());
		YFCElement yeleInput = ydocInput.getDocumentElement();
		yeleInput.setAttributes(yeleInputElement.getAttributes());
		YFCElement yeleFirstChild = yeleInputElement.getFirstChildElement();
		if (yeleFirstChild != null) {
			yeleInput.importNode(
					yeleInputElement.getChildElement(yeleInputElement.getFirstChildElement().getNodeName(), true));
		}
		if (yeleInputElement.getFirstChildElement().getNodeName() != yeleInputElement.getLastChildElement()
				.getNodeName()) {
			YFCElement yeleLastChild = yeleInputElement.getLastChildElement();
			if (yeleLastChild != null) {
				yeleInput.importNode(
						yeleInputElement.getChildElement(yeleInputElement.getLastChildElement().getNodeName(), true));
			}
		}
		return ydocInput;
	}

	public static YFCElement selectElementsByAttributeValue(YFCElement yelement, String strParentListElementName,
			String strChildListElementName, String strSearchAttribute, String strSearchAttributeValue) {
		YFCNodeList<YFCElement> ynlElement = yelement.getElementsByTagName(strParentListElementName);
		YFCElement yeleReturnElement = null;

		for (int i = 0; i < ynlElement.getLength(); i++) {

			YFCElement yeleElementLine = (YFCElement) ynlElement.item(i);

			String strAttribute = yeleElementLine.getChildElement(strChildListElementName)
					.getAttribute(strSearchAttribute);
			if (strAttribute != null && strAttribute.equals(strSearchAttributeValue)) {
				yeleReturnElement = yeleElementLine;

				break;
			}
		}

		return yeleReturnElement;
	}

	public static NodeList getXpathNodes(Element ele, String strXpath) throws XPathExpressionException {
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		XPathExpression expr = xpath.compile(strXpath);
		Object result = expr.evaluate(ele, XPathConstants.NODESET);
		return (NodeList) result;
	}

	public static String getXpathAttribute(Element ele, String strXpath) throws XPathExpressionException {
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		XPathExpression expr = xpath.compile(strXpath);
		Object result = expr.evaluate(ele, XPathConstants.STRING);
		return (String) result;
	}

	public static Element getXpathElement(Element ele, String strXpath) throws XPathExpressionException {
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		XPathExpression expr = xpath.compile(strXpath);
		Object result = expr.evaluate(ele, XPathConstants.NODE);
		return (Element) result;
	}

	public static String getString(Document document) {
		if (document == null) {
			return null;
		}
		return getString(document.getDocumentElement());
	}

	public static String getString(Element element) {
		if (element == null) {
			return null;
		}
		StringWriter stringWriter = new StringWriter();
		writeXml(element, stringWriter, false);
		String retVal = stringWriter.toString();

		try {
			stringWriter.close();
		} catch (Exception exception) {
		}
		return retVal;
	}

	public static void writeXml(Document document, Writer writer, boolean closeWriter) {
		if (document != null && writer != null) {
			writeXml(document.getDocumentElement(), writer, closeWriter);
		}
	}

	public static boolean writeXml(Element element, Writer writer, boolean closeWriter) {
		try {
			if (element != null && writer != null) {

				OutputFormat formatter = new OutputFormat();
				formatter.setEncoding("UTF-8");
				formatter.setIndenting(true);
				XMLSerializer ser = new XMLSerializer(writer, formatter);
				ser.serialize(element);
				writer.flush();
			}
			if (writer != null && closeWriter) {
				writer.close();
			}
			return true;
		} catch (Exception e) {

			e.printStackTrace();
			logger.error("Exception In AmzXMLUtil.writeXml : ", e);
			return false;
		}
	}

	
	
	//PMO-2720
		public static Element addTextElement(Document doc, String name,
	            String value, Element parent) {
	        Element child = doc.createElement(name);
	        child.appendChild(doc.createTextNode(value));
	        parent.appendChild(child);
	        return child;
	    }
		
		public static NodeList getNodeListByXpath(Document inXML,String XPath) throws ParserConfigurationException, TransformerException
		{
			NodeList nodeList=null;
			CachedXPathAPI aCachedXPathAPI = new CachedXPathAPI();
			nodeList=aCachedXPathAPI.selectNodeList(inXML,XPath);
			
			return nodeList;
		}
		


}
