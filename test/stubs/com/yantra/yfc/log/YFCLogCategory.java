package com.yantra.yfc.log;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.yantra.yfc.log.YFCLogCategory}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. A no-op logger so the real production
 * transformation classes can be exercised offline. The real class is supplied by your licensed
 * IBM Sterling OMS at runtime.
 */
public final class YFCLogCategory {

	public static YFCLogCategory instance(Class<?> c) {
		return new YFCLogCategory();
	}

	public void info(String s) {
	}

	public void debug(String s) {
	}

	public void error(String s) {
	}

	public void timer(String s) {
	}

	public void beginTimer(String s) {
	}

	public void endTimer(String s) {
	}
}
