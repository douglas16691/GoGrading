package com.example.gograding

import java.io.InputStream

/** Parses a 3D .cube LUT into the int[r][g][b] (packed ARGB) layout Media3 expects. */
object CubeParser {
    fun parse(input: InputStream): Array<Array<IntArray>> {
        var size = 0
        val values = ArrayList<FloatArray>()
        input.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                when {
                    line.startsWith("LUT_3D_SIZE") -> size = line.split(Regex("\\s+"))[1].toInt()
                    line.startsWith("LUT_1D_SIZE") -> error("1D LUTs are not supported")
                    line.first().isLetter() -> Unit // TITLE, DOMAIN_MIN, DOMAIN_MAX...
                    else -> {
                        val p = line.split(Regex("\\s+"))
                        if (p.size >= 3) values.add(floatArrayOf(p[0].toFloat(), p[1].toFloat(), p[2].toFloat()))
                    }
                }
            }
        }
        require(size > 0 && values.size == size * size * size) { "Invalid .cube file" }

        val cube = Array(size) { Array(size) { IntArray(size) } }
        for (i in values.indices) {
            // .cube order: red varies fastest, then green, then blue
            val r = i % size
            val g = (i / size) % size
            val b = i / (size * size)
            val v = values[i]
            fun c(x: Float) = (x.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            cube[r][g][b] = (0xFF shl 24) or (c(v[0]) shl 16) or (c(v[1]) shl 8) or c(v[2])
        }
        return cube
    }
}
