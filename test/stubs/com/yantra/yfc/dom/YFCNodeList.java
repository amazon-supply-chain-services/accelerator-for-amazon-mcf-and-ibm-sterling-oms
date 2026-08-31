package com.yantra.yfc.dom;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.yantra.yfc.dom.YFCNodeList}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. A minimal iterable node list, backed by
 * the standard JDK DOM, exposing only what the accelerator uses (enhanced-for iteration plus
 * {@code getLength()}/{@code item(int)}). Used only by the offline test harness; the real class is
 * supplied by a licensed IBM Sterling OMS at runtime.
 *
 * @param <T> element type (always {@link YFCElement} in accelerator usage)
 */
public final class YFCNodeList<T> implements Iterable<T> {

	private final List<T> items;

	private YFCNodeList(List<T> items) {
		this.items = items;
	}

	/** Build a list of {@link YFCElement} wrappers for descendants named {@code tag}. */
	static YFCNodeList<YFCElement> of(Element parent, String tag) {
		List<YFCElement> list = new ArrayList<>();
		if (parent != null) {
			NodeList nl = parent.getElementsByTagName(tag);
			for (int i = 0; i < nl.getLength(); i++) {
				Node n = nl.item(i);
				if (n.getNodeType() == Node.ELEMENT_NODE) {
					list.add(new YFCElement((Element) n));
				}
			}
		}
		return new YFCNodeList<>(list);
	}

	public int getLength() {
		return items.size();
	}

	public T item(int i) {
		return items.get(i);
	}

	@Override
	public Iterator<T> iterator() {
		return items.iterator();
	}
}
