package musicorbs.motion.infrastructure

import musicorbs.motion.domain.SpectrumPort
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType

/** Apache Commons Math остаётся внутри адаптера; domain не зависит от библиотеки. */
class CommonsMathSpectrum : SpectrumPort {
    private val fft = FastFourierTransformer(DftNormalization.STANDARD)

    override fun magnitudes(window: DoubleArray): DoubleArray {
        require(window.size >= 2 && window.size and (window.size - 1) == 0) { "INVALID_INPUT: FFT size" }
        require(window.all { it.isFinite() }) { "INVALID_INPUT: FFT samples" }
        val values = fft.transform(window, TransformType.FORWARD)
        return DoubleArray(window.size / 2 + 1) { values[it].abs() / window.size }
    }
}
