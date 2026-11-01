package fr.osalabs.osatv.data

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Client adb minimal qui se connecte à la télé elle-même (127.0.0.1:5555).
 * Une app n'a pas le droit de désactiver d'autres apps, mais une session adb, si :
 * la première fois, la télé demande d'autoriser la clé d'OsaTV (« Toujours autoriser »).
 * Protocole : https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/protocol.txt
 */
class LocalAdb(private val keys: KeyPair, private val port: Int = 5555) {

    private class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /** Lance une commande shell et renvoie sa sortie. Lève une exception si adb est fermé ou la clé refusée. */
    fun shell(command: String, authTimeoutMs: Int = 60_000): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 3_000)
            socket.soTimeout = authTimeoutMs
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = socket.getOutputStream().buffered()

            send(output, CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())
            var signed = false
            while (true) {
                val message = receive(input)
                if (message.command == CNXN) break
                if (message.command != AUTH) continue
                if (!signed) {
                    send(output, AUTH, AUTH_SIGNATURE, 0, sign(message.data))
                    signed = true
                } else {
                    // Clé inconnue : la télé affiche « Autoriser le débogage ? » et répond CNXN si on accepte
                    send(output, AUTH, AUTH_PUBLIC_KEY, 0, publicKey())
                }
            }

            socket.soTimeout = 30_000
            send(output, OPEN, LOCAL_ID, 0, "shell:$command\u0000".toByteArray())
            val result = ByteArrayOutputStream()
            while (true) {
                val message = receive(input)
                when (message.command) {
                    WRTE -> {
                        result.write(message.data)
                        send(output, OKAY, LOCAL_ID, message.arg0)
                    }
                    CLSE -> {
                        send(output, CLSE, LOCAL_ID, message.arg0)
                        return result.toString(Charsets.UTF_8.name())
                    }
                }
            }
        }
    }

    private fun send(output: OutputStream, command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(data.size)
            .putInt(data.sumOf { it.toInt() and 0xff })
            .putInt(command.inv())
        output.write(header.array())
        output.write(data)
        output.flush()
    }

    private fun receive(input: DataInputStream): Message {
        val header = ByteArray(24).also { input.readFully(it) }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val data = ByteArray(buffer.int).also { input.readFully(it) }
        return Message(command, arg0, arg1, data)
    }

    /** Signature RSA PKCS#1 v1.5 du jeton, présenté comme une empreinte SHA-1 (ce qu'attend adbd). */
    private fun sign(token: ByteArray): ByteArray {
        val digestInfo = SHA1_PREFIX + token
        val block = ByteArray(KEY_BYTES)
        block[1] = 1
        for (i in 2 until KEY_BYTES - digestInfo.size - 1) block[i] = 0xff.toByte()
        digestInfo.copyInto(block, KEY_BYTES - digestInfo.size)
        return Cipher.getInstance("RSA/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, keys.private)
            doFinal(block)
        }
    }

    /** Clé publique au format d'Android (structure RSAPublicKey de mincrypt), en base64. */
    private fun publicKey(): ByteArray {
        val key = keys.public as RSAPublicKey
        val words = KEY_BYTES / 4
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n = key.modulus
        val n0inv = n.mod(r32).modInverse(r32).negate().mod(r32)
        val rr = BigInteger.ONE.shiftLeft(KEY_BYTES * 8).modPow(BigInteger.valueOf(2), n)
        val buffer = ByteBuffer.allocate(4 + 4 + KEY_BYTES * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(words).putInt(n0inv.toInt())
        for (value in listOf(n, rr)) {
            var rest = value
            repeat(words) {
                buffer.putInt(rest.mod(r32).toInt())
                rest = rest.shiftRight(32)
            }
        }
        buffer.putInt(key.publicExponent.toInt())
        return (Base64.encodeToString(buffer.array(), Base64.NO_WRAP) + " OsaTV@tv\u0000").toByteArray()
    }

    companion object {
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257
        private const val VERSION = 0x01000000
        private const val MAX_DATA = 4096
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_PUBLIC_KEY = 3
        private const val LOCAL_ID = 1
        private const val KEY_BYTES = 256

        private val SHA1_PREFIX = intArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        ).map { it.toByte() }.toByteArray()

        /** Clé RSA d'OsaTV, créée au premier usage et gardée dans le stockage privé de l'app. */
        fun keys(context: Context): KeyPair {
            val dir = File(context.filesDir, "adb").apply { mkdirs() }
            val privateFile = File(dir, "key")
            val publicFile = File(dir, "key.pub")
            val factory = KeyFactory.getInstance("RSA")
            if (privateFile.exists() && publicFile.exists()) {
                return KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes())),
                    factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes())),
                )
            }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BYTES * 8) }.generateKeyPair()
            publicFile.writeBytes(pair.public.encoded)
            privateFile.writeBytes(pair.private.encoded)
            return pair
        }
    }
}
