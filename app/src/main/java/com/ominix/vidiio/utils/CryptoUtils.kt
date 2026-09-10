package com.ominix.vidiio.utils

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.EllipticCurve
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    fun generateECKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair()
    }

    fun computeSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    fun decodePublicKey(hex: String): PublicKey {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val x = BigInteger(1, bytes.sliceArray(1..32))
        val y = BigInteger(1, bytes.sliceArray(33..64))
        val point = ECPoint(x, y)
        
        val factory = KeyFactory.getInstance("EC")
        val params = (factory.generatePublic(ECPublicKeySpec(point, (generateECKeyPair().public as java.security.interfaces.ECPublicKey).params)) as java.security.interfaces.ECPublicKey).params
        return factory.generatePublic(ECPublicKeySpec(point, params))
    }

    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(0x01.toByte())
        val okm = mac.doFinal()
        return okm.sliceArray(0 until length)
    }

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun decryptAesGcm(ciphertext: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun getUncompressedEncoded(publicKey: PublicKey): ByteArray {
        val ecPub = publicKey as java.security.interfaces.ECPublicKey
        val x = ecPub.w.affineX.toByteArray().toUnsigned(32)
        val y = ecPub.w.affineY.toByteArray().toUnsigned(32)
        return byteArrayOf(0x04) + x + y
    }

    private fun ByteArray.toUnsigned(length: Int): ByteArray {
        return if (size > length) {
            sliceArray(size - length until size)
        } else if (size < length) {
            ByteArray(length - size) + this
        } else {
            this
        }
    }
}
