package com.yantra.yfc.core;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.yantra.yfc.core.YFCObject}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. Clean-room re-implementation of the
 * single helper the accelerator uses ({@code isVoid}), for offline testing only. The real class
 * is provided by your licensed IBM Sterling OMS at runtime.
 */
public final class YFCObject {

	private YFCObject() {
	}

	/** True when the value is null or an empty string (matches the accelerator's usage). */
	public static boolean isVoid(Object o) {
		if (o == null) {
			return true;
		}
		if (o instanceof String) {
			return ((String) o).isEmpty();
		}
		return false;
	}
}
