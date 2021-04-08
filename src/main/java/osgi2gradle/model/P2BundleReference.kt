package osgi2gradle.model

import java.io.IOException
import java.io.OutputStreamWriter

class P2BundleReference : Comparable<P2BundleReference?> {
    var name: String? = null
    var version: String? = null

    @Throws(IOException::class)
    fun declareP2BundleCall(writer: OutputStreamWriter) {
        writer.append("p2bundle('").append(name).append("'")
        if (version != null) {
            writer.append(", '").append(version).append("'")
        }
        writer.append(")")
    }

    override fun compareTo(p2BundleReference: P2BundleReference?): Int {
        return name!!.compareTo(p2BundleReference!!.name!!)
    }
}