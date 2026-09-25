package com.nevoit.xdnext.data.energy

import com.nevoit.xdnext.core.crypto.AesCbc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden-vector tests for the energy system's content encryption and for the percent-encoding the
 * `content` query parameter needs.
 *
 * The ciphertexts were produced by **Node's `crypto`** (`tools/golden-vectors/energy-content-vectors.mjs`)
 * and independently by the reference's own `encrypter_plus` stack
 * (`reference/probe/energy_encoding_probe.dart`); the two agree, and neither shares code with
 * `cryptography-kotlin`.
 *
 * The encodings were produced by **Dart's `Uri.encodeComponent`**, run in that same probe, because
 * this is a third AES scheme whose key, IV and padding all differ from the IDS ones — a copy-paste
 * from `IdsCrypto` would produce a plausible-looking blob the server rejects without explanation.
 */
class EnergyCryptoTest {

    private val crypto = EnergyCrypto(AesCbc())

    @Test
    fun encryptsLikeAnIndependentImplementation() {
        // Equal to the Node script's output and to the Dart probe's, character for character.
        assertEquals(
            "Qbe8lIv8zm6TU0U55zwfKuUCmggP2VUGlnIiPM/Kk5s=",
            crypto.encryptContent("""{"CODE":"example"}"""),
            "15 bytes: short enough that PKCS#7 adds a whole extra block.",
        )
        assertEquals(
            "c0NpdtmLpRU6xANiM5kbcCgyoxosPH05SNx/ylKYUJ0=",
            crypto.encryptContent("""{"CODE":"0123456789abcdef"}"""),
        )
        assertEquals(
            "dsVLOJGPUrE/08FioEpod2dQq+7ofB4R8m8G8FoVLl4=",
            crypto.encryptContent("""{"NodeID":"NODE-1"}"""),
        )
        assertEquals(
            "bYdVOqDURQRXC6XnFOPnBXb3xfiTSvyNcpPqSLmi4uX5Isvh1VBw+/LKZiFR0XYk7hJiE5Q6M9iPfmt0iYNsmg==",
            crypto.encryptContent(
                """{"UserID":"20210001","Pwd":"","IsCehckPwd":1,"NodeID":""}""",
            ),
            "A body with an empty string and an integer: neither may be dropped or quoted.",
        )
        assertEquals(
            "NzfLqprG1h2ejEKWLxnS6stKM1MviCPnDz5OHTmvmcFMc2fij8awxfLtEB8DOpzk" +
                    "EfgcFj60csWoYjfKMSbbIlbCyY3Oe/uhbF2a8oGV38wmjHs9eKzpPwZFf6bbY+Bg",
            crypto.encryptContent(
                """{"MetID":"MET-9","ReadTimeS":"2026-03-22","ReadTimeE":"2026-04-22","ReadNum":""}""",
            ),
            "A body long enough to span several blocks.",
        )
    }

    @Test
    fun encryptsDeterministically() {
        // Fixed key and fixed IV: the server can only verify a reproducible ciphertext.
        assertEquals(
            crypto.encryptContent("""{"NodeID":"NODE-1"}"""),
            crypto.encryptContent("""{"NodeID":"NODE-1"}"""),
        )
    }

    @Test
    fun percentEncodesExactlyLikeDart() {
        // The values and expectations are copied from the probe's `Uri.encodeComponent` section.
        assertEquals(
            "Qbe8lIv8zm6TU0U55zwfKuUCmggP2VUGlnIiPM%2FKk5s%3D",
            percentEncodeComponent("Qbe8lIv8zm6TU0U55zwfKuUCmggP2VUGlnIiPM/Kk5s="),
            "Base64's `/` and `=` are the two characters that make this necessary.",
        )
        assertEquals("a%2Bb%2Fc%3D%3D", percentEncodeComponent("a+b/c=="))
        assertEquals(
            "-_.!~*'()",
            percentEncodeComponent("-_.!~*'()"),
            "Dart leaves these unreserved."
        )
        assertEquals("AZaz09", percentEncodeComponent("AZaz09"))
        assertEquals(
            "%E7%94%A8%E7%94%B5",
            percentEncodeComponent("用电"),
            "UTF-8 bytes, uppercase hex — a payload can contain Chinese in a meter name.",
        )
    }
}
