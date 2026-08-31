package com.yantra.yfc.dom;

import org.w3c.dom.Document;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.yantra.yfc.dom.YFCDocument}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. A thin clean-room wrapper over the
 * standard JDK {@link org.w3c.dom.Document}, exposing only {@code getDocumentFor(...)} and
 * {@code getDocumentElement()} as the accelerator's enterprise/BPID cross-reference lookup uses.
 * Used only by the offline test harness; the real class is supplied by a licensed IBM Sterling OMS
 * at runtime.
 */
public final class YFCDocument {

	private final Document document;

	private YFCDocument(Document document) {
		this.document = document;
	}

	/** Wrap a JDK document. */
	public static YFCDocument getDocumentFor(Document doc) {
		return new YFCDocument(doc);
	}

	public YFCElement getDocumentElement() {
		return new YFCElement(document == null ? null : document.getDocumentElement());
	}
}
