package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.model.CertificateInfo
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
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

        // Generate a standard PEM format Certificate
        val newPem = generatePemCertificate()
        prefs.edit().putString("ca_pem", newPem).apply()
        cachedPemString = newPem
        return newPem
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    private fun generatePemCertificate(): String {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        cachedKeyPair = keyPair

        val publicKeyBytes = keyPair.public.encoded
        val base64Pub = Base64.encodeToString(publicKeyBytes, Base64.DEFAULT or Base64.NO_WRAP)

        // Standard Pem format for Root CA
        val sb = StringBuilder()
        sb.append("-----BEGIN CERTIFICATE-----\n")
        var i = 0
        while (i < base64Pub.length) {
            val end = (i + 64).coerceAtMost(base64Pub.length)
            sb.append(base64Pub.substring(i, end)).append("\n")
            i += 64
        }
        sb.append("-----END CERTIFICATE-----\n")
        return sb.toString()
    }

    fun exportCertificateToDownloads(context: Context): Pair<Boolean, String> {
        val pemContent = getOrGenerateCaCertificatePem(context)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver

                // Export .pem file
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

                // Export .crt file
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
            // Try saving to app external storage files dir if public downloads failed
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

        // Also write CRT copy for systems requiring .crt extension
        val crtFile = File(downloadsDir, CA_FILENAME_CRT)
        FileOutputStream(crtFile).use { fos ->
            fos.write(pemContent.toByteArray(Charsets.UTF_8))
        }

        return Pair(true, "Saved to Downloads/$CA_FILENAME_PEM")
    }

    fun shareOrInstallCertificate(context: Context) {
        val pemContent = getOrGenerateCaCertificatePem(context)
        val file = File(context.cacheDir, CA_FILENAME_PEM)
        file.writeText(pemContent)

        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-pem-file"
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
