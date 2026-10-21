package com.tihor.quill.audio

/** Audible PCM floor, before any provider, cleanup, history, clipboard or injection. */
object AudioSignal {
    fun hasAudibleSignal(wav: ByteArray): Boolean {
        fun tag(p: Int, value: String) = p + 4 <= wav.size && value.indices.all { wav[p+it].toInt() == value[it].code }
        fun u16(p: Int) = (wav[p].toInt() and 255) or ((wav[p+1].toInt() and 255) shl 8)
        fun u32(p: Int) = u16(p).toLong() or (u16(p+2).toLong() shl 16)
        require(wav.size >= 12 && tag(0,"RIFF") && tag(8,"WAVE")) { "Invalid WAV" }
        var rate = 0; var channels = 0; var offset = -1; var length = 0; var p = 12
        while (p <= wav.size - 8) {
            val size = u32(p+4)
            require(size <= wav.size - p - 8) { "Truncated WAV" }
            if (tag(p,"fmt ")) {
                require(size >= 16 && u16(p+8) == 1 && u16(p+22) == 16) { "Unsupported WAV" }
                channels = u16(p+10); rate = u32(p+12).toInt()
            } else if (tag(p,"data")) { offset = p+8; length = size.toInt() }
            p += 8 + size.toInt() + (size.toInt() and 1)
        }
        require(rate in 8000..96000 && channels in 1..2 && offset >= 0 && length % (channels*2) == 0) { "Invalid PCM" }
        val frame = rate/50; val stride = channels*2
        var audible = 0; var consecutive = 0; var longest = 0; p = offset
        val levels = ArrayList<Double>(); var previous = 0.0; var earlier = 0.0; var peak = 0.0
        while (p + frame*stride <= offset+length) {
            var active = false; var level = 0.0
            for (channel in 0 until channels) {
                var sum = 0.0; var squares = 0.0; var low = 32767; var high = -32768
                for (i in 0 until frame) {
                    val s = u16(p+i*stride+channel*2).toShort().toInt()
                    sum += s; squares += s.toDouble()*s; low = minOf(low,s); high = maxOf(high,s)
                }
                val mean = sum/frame
                val energy = maxOf(0.0,squares/frame - mean*mean)
                active = active || energy >= 80*80 && high-low >= 320
                level = maxOf(level,kotlin.math.sqrt(energy))
            }
            consecutive = if (active) consecutive+1 else 0
            longest = maxOf(longest,consecutive)
            if (active) audible++
            if (consecutive >= 3) peak = maxOf(peak,minOf(level,previous,earlier))
            levels.add(level); earlier = previous; previous = level
            p += frame*stride
        }
        if (audible < 4 || longest < 3) return false
        if (levels.size < 20) return true
        levels.sort()
        return peak >= maxOf(80.0,levels[(levels.size-1)/5]*1.4)
    }
}
