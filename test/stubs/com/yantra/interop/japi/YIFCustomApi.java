package com.yantra.interop.japi;

import java.util.Properties;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.yantra.interop.japi.YIFCustomApi}.
 * Marker interface implemented by the accelerator's event processors, declaring the single
 * {@code setProperties} hook they override. NOT IBM code and contains no IBM proprietary material.
 * Test harness use only; the real interface ships with IBM Sterling OMS.
 */
public interface YIFCustomApi {

	void setProperties(Properties props);
}
