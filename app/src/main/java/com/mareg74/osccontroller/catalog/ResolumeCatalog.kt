package com.mareg74.osccontroller.catalog

import com.mareg74.osccontroller.config.ArgKind
import com.mareg74.osccontroller.config.parseArgKind
import org.json.JSONArray

data class CatalogEntry(val label: String, val address: String, val argKind: ArgKind)

object ResolumeCatalog {
    fun parse(json: String): List<CatalogEntry> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CatalogEntry(
                        label = o.getString("label"),
                        address = o.getString("address"),
                        argKind = parseArgKind(o.getString("argKind"))
                    )
                )
            }
        }
    }
}
