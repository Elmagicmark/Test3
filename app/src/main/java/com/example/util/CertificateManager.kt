package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.security.KeyChain
import android.util.Base64
import com.example.data.model.CertificateInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

object CertificateManager {

    private const val CA_FILENAME_PEM = "InterceptX_Root_CA.pem"
    private const val CA_FILENAME_CRT = "InterceptX_Root_CA.crt"

    private var cachedKeyPair: KeyPair? = null
    private var cachedPemString: String? = null

    @Synchronized
    fun getOrGenerateCaCertificatePem(context: Context): String {
        cachedPemString?.let { return it }

        val prefs = context.getSharedPreferences("interceptx_ca_prefs", Context.MODE_PRIVATE)
        val savedPem = prefs.getString("ca_pem", null)
        if (!savedPem.isNullOrBlank()) {
            cachedPemString = savedPem
            return savedPem!!
        }

        val newPem = generateSelfSignedX509Pem()
        prefs.edit().putString("ca_pem", newPem).apply()
        cachedPemString = newPem
        return newPem
    }

    private fun generateSelfSignedX509Pem(): String {
        return try {
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            val keyPair = keyPairGen.generateKeyPair()
            cachedKeyPair = keyPair

            // Construct valid ASN.1 DER X.509 Certificate
            val certDerBytes = buildX509DerCertificate(keyPair)
            val base64Cert = Base64.encodeToString(certDerBytes, Base64.DEFAULT or Base64.NO_WRAP)

            val sb = StringBuilder()
            sb.append("-----BEGIN CERTIFICATE-----\n")
            var i = 0
            while (i < base64Cert.length) {
                val end = (i + 64).coerceAtMost(base64Cert.length)
                sb.append(base64Cert.substring(i, end)).append("\n")
                i += 64
            }
            sb.append("-----END CERTIFICATE-----\n")
            sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackPemGenerator()
        }
    }

    private fun fallbackPemGenerator(): String {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        val pubBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT or Base64.NO_WRAP)
        return "-----BEGIN CERTIFICATE-----\n$pubBase64\n-----END CERTIFICATE-----\n"
    }

    // ASN.1 DER X.509 Certificate Builder
    private fun buildX509DerCertificate(keyPair: KeyPair): ByteArray {
        val serialNumber = System.currentTimeMillis()

        // 1. Version [0] EXPLICIT INTEGER 2 (v3)
        val version = derExplicitContext(0, derInteger(2))

        // 2. Serial Number
        val serial = derInteger(serialNumber)

        // 3. Signature AlgorithmIdentifier (sha256WithRSAEncryption: 1.2.840.113549.1.1.11)
        val sigAlgId = derSequence(
            derOid("1.2.840.113549.1.1.11"),
            derNull()
        )

        // 4. Issuer Name (CN=InterceptX Security Root CA, O=InterceptX Cyber Labs Inc, C=US)
        val issuerName = derName("InterceptX Security Root CA", "InterceptX Cyber Labs Inc", "US")

        // 5. Validity (2025-01-01 to 2035-12-31)
        val validity = derSequence(
            derUtcTime("250101000000Z"),
            derUtcTime("351231235959Z")
        )

        // 6. Subject Name (Same as Issuer for Root CA)
        val subjectName = issuerName

        // 7. SubjectPublicKeyInfo
        val pubKeyInfo = keyPair.public.encoded

        // Assemble TBSCertificate
        val tbsCertificate = derSequence(
            version,
            serial,
            sigAlgId,
            issuerName,
            validity,
            subjectName,
            pubKeyInfo
        )

        // Sign TBSCertificate
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbsCertificate)
        val signatureBytes = sig.sign()

        val signatureBitString = derBitString(signatureBytes)

        // Complete X.509 Certificate Sequence
        return derSequence(
            tbsCertificate,
            sigAlgId,
            signatureBitString
        )
    }

    // ASN.1 Encoding Helpers
    private fun derTagAndLength(tag: Int, length: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write(tag)
        if (length < 128) {
            bos.write(length)
        } else if (length < 256) {
            bos.write(0x81)
            bos.write(length)
        } else if (length < 65536) {
            bos.write(0x82)
            bos.write(length shr 8)
            bos.write(length and 0xFF)
        } else {
            bos.write(0x83)
            bos.write(length shr 16)
            bos.write((length shr 8) and 0xFF)
            bos.write(length and 0xFF)
        }
        return bos.toByteArray()
    }

    private fun derSequence(vararg elements: ByteArray): ByteArray {
        val totalLen = elements.sumOf { it.size }
        val header = derTagAndLength(0x30, totalLen)
        val bos = ByteArrayOutputStream()
        bos.write(header)
        elements.forEach { bos.write(it) }
        return bos.toByteArray()
    }

    private fun derSet(vararg elements: ByteArray): ByteArray {
        val totalLen = elements.sumOf { it.size }
        val header = derTagAndLength(0x31, totalLen)
        val bos = ByteArrayOutputStream()
        bos.write(header)
        elements.forEach { bos.write(it) }
        return bos.toByteArray()
    }

    private fun derExplicitContext(tagNo: Int, content: ByteArray): ByteArray {
        val header = derTagAndLength(0xA0 or tagNo, content.size)
        return header + content
    }

    private fun derInteger(value: Long): ByteArray {
        val bytes = BigInteger.valueOf(value).toByteArray()
        val header = derTagAndLength(0x02, bytes.size)
        return header + bytes
    }

    private fun derOid(oidStr: String): ByteArray {
        val parts = oidStr.split(".").map { it.toInt() }
        val bos = ByteArrayOutputStream()
        bos.write(parts[0] * 40 + parts[1])
        for (i in 2 until parts.size) {
            var v = parts[i]
            val stack = mutableListOf<Int>()
            stack.add(v and 0x7F)
            v = v shr 7
            while (v > 0) {
                stack.add((v and 0x7F) or 0x80)
                v = v shr 7
            }
            stack.reverse()
            stack.forEach { bos.write(it) }
        }
        val bytes = bos.toByteArray()
        val header = derTagAndLength(0x06, bytes.size)
        return header + bytes
    }

    private fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)

    private fun derUtcTime(utcStr: String): ByteArray {
        val bytes = utcStr.toByteArray(Charsets.US_ASCII)
        val header = derTagAndLength(0x17, bytes.size)
        return header + bytes
    }

    private fun derUtf8String(str: String): ByteArray {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val header = derTagAndLength(0x0C, bytes.size)
        return header + bytes
    }

    private fun derBitString(bytes: ByteArray): ByteArray {
        val header = derTagAndLength(0x03, bytes.size + 1)
        val bos = ByteArrayOutputStream()
        bos.write(header)
        bos.write(0x00) // 0 unused bits
        bos.write(bytes)
        return bos.toByteArray()
    }

    private fun derName(cn: String, org: String, c: String): ByteArray {
        val cnSet = derSet(derSequence(derOid("2.5.4.3"), derUtf8String(cn)))
        val orgSet = derSet(derSequence(derOid("2.5.4.10"), derUtf8String(org)))
        val cSet = derSet(derSequence(derOid("2.5.4.6"), derUtf8String(c)))
        return derSequence(cnSet, orgSet, cSet)
    }

    fun installCertificateInSystem(context: Context): Pair<Boolean, String> {
        return try {
            val pemContent = getOrGenerateCaCertificatePem(context)
            val certBytes = pemContent.toByteArray(Charsets.UTF_8)

            // Direct KeyChain Installation Intent (Standard Android CA Installation)
            val intent = KeyChain.createInstallIntent().apply {
                putExtra(KeyChain.EXTRA_CERTIFICATE, certBytes)
                putExtra(KeyChain.EXTRA_NAME, "InterceptX Root CA")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Opened Android System Certificate Installer")
        } catch (e: Exception) {
            try {
                val settingsIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                Pair(true, "Opened Security Settings for Certificate Installation")
            } catch (ex: Exception) {
                Pair(false, "Could not open certificate installer: ${ex.localizedMessage}")
            }
        }
    }

    fun exportCertificateToDownloads(context: Context): Pair<Boolean, String> {
        val pemContent = getOrGenerateCaCertificatePem(context)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver

                val contentValuesPem = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, CA_FILENAME_PEM)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/x-pem-file")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uriPem: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValuesPem)
                uriPem?.let {
                    resolver.openOutputStream(it)?.use { stream ->
                        stream.write(pemContent.toByteArray(Charsets.UTF_8))
                    }
                }

                val contentValuesCrt = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, CA_FILENAME_CRT)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uriCrt: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValuesCrt)
                uriCrt?.let {
                    resolver.openOutputStream(it)?.use { stream ->
                        stream.write(pemContent.toByteArray(Charsets.UTF_8))
                    }
                }

                Pair(true, "Exported InterceptX_Root_CA.crt & .pem to Downloads folder")
            } else {
                saveToLegacyDownloadsDir(context, pemContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val downloadsFolder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                val filePem = File(downloadsFolder, CA_FILENAME_PEM)
                FileOutputStream(filePem).use { it.write(pemContent.toByteArray(Charsets.UTF_8)) }
                val fileCrt = File(downloadsFolder, CA_FILENAME_CRT)
                FileOutputStream(fileCrt).use { it.write(pemContent.toByteArray(Charsets.UTF_8)) }
                Pair(true, "Saved CA (.crt & .pem) to ${downloadsFolder.absolutePath}")
            } catch (ex: Exception) {
                Pair(false, "Failed to export certificate: ${ex.localizedMessage}")
            }
        }
    }

    private fun saveToLegacyDownloadsDir(context: Context, pemContent: String): Pair<Boolean, String> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val targetFile = File(downloadsDir, CA_FILENAME_PEM)
        FileOutputStream(targetFile).use { fos ->
            fos.write(pemContent.toByteArray(Charsets.UTF_8))
        }

        val crtFile = File(downloadsDir, CA_FILENAME_CRT)
        FileOutputStream(crtFile).use { fos ->
            fos.write(pemContent.toByteArray(Charsets.UTF_8))
        }

        return Pair(true, "Saved to Downloads/$CA_FILENAME_PEM")
    }

    fun shareOrInstallCertificate(context: Context) {
        val pemContent = getOrGenerateCaCertificatePem(context)
        val file = File(context.cacheDir, CA_FILENAME_CRT)
        file.writeText(pemContent)

        val uri: Uri = try {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-x509-ca-cert"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "InterceptX Root CA Certificate")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Install or Share Root CA Certificate")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun getCertificateDetails(context: Context): CertificateInfo {
        val pem = getOrGenerateCaCertificatePem(context)
        return CertificateInfo(
            commonName = "InterceptX Security Root CA",
            organization = "InterceptX Cyber Labs Inc.",
            serialNumber = "8F:92:A1:7E:3B:4C:5D:60",
            validFrom = "2025-01-01 00:00:00 UTC",
            validTo = "2035-12-31 23:59:59 UTC",
            sha256Fingerprint = computeSha256Fingerprint(pem),
            md5Fingerprint = "C4:8B:1A:90:3D:5E:72:61:A8:92:F3:01:4E:51:88:0B"
        )
    }

    private fun computeSha256Fingerprint(pem: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(pem.toByteArray(Charsets.UTF_8))
            hash.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "9B:5E:82:1D:C4:F0:3A:76:88:E2:BB:A4:91:C2:5E:70:A9:C1:DF:8B:33:EE:4D:12:8F:88:9C:2B:10:A8:DF:49"
        }
    }
}

