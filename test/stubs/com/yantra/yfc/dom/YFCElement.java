package com.yantra.yfc.dom;

import org.w3c.dom.Element;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.yantra.yfc.dom.YFCElement}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. A thin clean-room wrapper over the
 * standard JDK {@link org.w3c.dom.Element}, exposing only the handful of accessors the accelerator
 * uses. Used only by the offline test harness; the real class is supplied by a licensed IBM
 * Sterling OMS at runtime.
 */
public final class YFCElement {

	private final Element element;

	public YFCElement(Element element) {
		this.element = element;
	}

	/** Underlying JDK element. */
	public Element getElement() {
		return element;
	}

	public String getAttribute(String name) {
		return element == null ? "" : element.getAttribute(name);
	}

	public YFCNodeList<YFCElement> getElementsByTagName(String tag) {
		return YFCNodeList.of(element, tag);
	}
}
