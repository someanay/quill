package com.tihor.quill.crash

import org.junit.Assert.assertTrue
import org.junit.Test

/** M11 crash reporting: the report carries error + stack + app/device meta, and nothing sensitive. */
class CrashReporterTest {

    @Test
    fun format_includesErrorClassStackAndMeta() {
        val report = CrashReporter.format(
            IllegalStateException("boom"),
            appVersion = "1.0.0 (1)",
            device = "Honor BVL-N49 · API 36",
        )
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("1.0.0 (1)"))
        assertTrue(report.contains("Honor BVL-N49 · API 36"))
        assertTrue("includes a stack trace", report.contains("CrashReporterTest"))
    }
}
