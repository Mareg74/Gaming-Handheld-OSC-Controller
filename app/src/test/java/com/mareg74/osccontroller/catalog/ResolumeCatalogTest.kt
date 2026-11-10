package com.mareg74.osccontroller.catalog

import com.mareg74.osccontroller.config.ArgKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolumeCatalogTest {
    @Test fun parsesStarter() {
        val json = """[{"label":"Column 1 connect","address":"/composition/columns/1/connect","argKind":"BANG"}]"""
        val e = ResolumeCatalog.parse(json).single()
        assertEquals("/composition/columns/1/connect", e.address)
        assertEquals(ArgKind.MOMENTARY, e.argKind)
    }
}
