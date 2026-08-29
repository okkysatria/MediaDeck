package com.mediadeck.app.util.scan

object NaturalOrderComparator : Comparator<String> {
    override fun compare(s1: String, s2: String): Int {
        var i = 0
        var j = 0
        val len1 = s1.length
        val len2 = s2.length

        while (i < len1 && j < len2) {
            val c1 = s1[i]
            val c2 = s2[j]

            if (c1.isDigit() && c2.isDigit()) {
                var chunk1 = ""
                while (i < len1 && s1[i].isDigit()) {
                    chunk1 += s1[i]
                    i++
                }
                var chunk2 = ""
                while (j < len2 && s2[j].isDigit()) {
                    chunk2 += s2[j]
                    j++
                }

                val val1Str = chunk1.trimStart('0')
                val val2Str = chunk2.trimStart('0')

                if (val1Str.length != val2Str.length) {
                    return val1Str.length.compareTo(val2Str.length)
                }
                val comp = val1Str.compareTo(val2Str)
                if (comp != 0) return comp
            } else {
                val comp = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                if (comp != 0) return comp
                i++
                j++
            }
        }
        return len1.compareTo(len2)
    }
}
