package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.CertificateInfo
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Generates and persists the InterceptX root CA, and signs a fresh per-host leaf
 * certificate on demand so the proxy can terminate TLS itself (real MITM) instead
 * of blindly tunneling encrypted bytes. This is what lets the proxy see the real
 * HTTP method/URL/headers/body for HTTPS traffic, the same way Reqable/Charles/
 * mitmproxy do.
 *
 * Previously this class hand-built a self-signed cert via manual ASN.1/DER
 * encoding and only ever kept the PEM text (never the private key) in
 * SharedPreferences — enough to *display and install* a root certificate, but
 * useless for actually signing anything afterwards (the key was gone after the
 * process that generated it exited). It's rewritten here on BouncyCastle with a
 * persisted keystore so the same CA (and its private key) survive app restarts,
 * and so it can mint per-host leaf certs.
 */
object CertificateManager {

    private const val CA_FILENAME_PEM = "InterceptX_Root_CA.pem"
    private const val CA_FILENAME_CRT = "InterceptX_Root_CA.crt"
    private const val KEYSTORE_FILENAME = "interceptx_ca.jks"
    private const val VERSION_FILENAME = "interceptx_ca_version.txt"
    private const val CA_SCHEMA_VERSION = 1 // bump if generateRootCa()/generateLeaf() extensions change
    private val STORE_PASSWORD = "interceptx".toCharArray()

    private val provider by lazy {
        BouncyCastleProvider().also {
            Security.removeProvider("BC")
            Security.insertProviderAt(it, 1)
        }
    }
    private val extUtils by lazy { JcaX509ExtensionUtils() }
    private val leafCache = ConcurrentHashMap<String, Pair<PrivateKey, X509Certificate>>()

    @Volatile private var caKeyPair: KeyPair? = null
    @Volatile private var caCert: X509Certificate? = null

    @Synchronized
    private fun ensureCaLoaded(context: Context) {
        if (caKeyPair != null && caCert != null) return
        provider // force BC registration

        val keyStoreFile = File(context.filesDir, KEYSTORE_FILENAME)
        val versionFile = File(context.filesDir, VERSION_FILENAME)
        val storedVersion = if (versionFile.exists()) versionFile.readText().trim().toIntOrNull() else null
        val needsRegeneration = storedVersion != CA_SCHEMA_VERSION

        val ks = KeyStore.getInstance("BKS", "BC")
        if (keyStoreFile.exists() && !needsRegeneration) {
            keyStoreFile.inputStream().use { ks.load(it, STORE_PASSWORD) }
            val cert = ks.getCertificate("ca") as X509Certificate
            val key = ks.getKey("ca", STORE_PASSWORD) as PrivateKey
            caCert = cert
            caKeyPair = KeyPair(cert.publicKey, key)
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA", "BC")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            val cert = generateRootCertificate(kp)
            caKeyPair = kp
            caCert = cert
            leafCache.clear()

            ks.load(null, null)
            ks.setKeyEntry("ca", kp.private, STORE_PASSWORD, arrayOf<Certificate>(cert))
            keyStoreFile.outputStream().use { ks.store(it, STORE_PASSWORD) }
            versionFile.writeText(CA_SCHEMA_VERSION.toString())
        }
    }

    private fun generateRootCertificate(keyPair: KeyPair): X509Certificate {
        val subject = X500Name("CN=InterceptX Root CA, O=InterceptX Cyber Labs, OU=Security Research")
        val serial = BigInteger(160, SecureRandom())
        val notBefore = Date(System.currentTimeMillis() - 24L * 3600 * 1000)
        val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000)

        val builder = X509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        builder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(keyPair.public))
        builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(keyPair.public))
        builder.addExtension(
            MiscObjectIdentifiers.netscapeCertComment, false,
            DERIA5String(
                "InterceptX local root CA, generated on this device for HTTPS interception during security testing."
            )
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    /** Mints (or returns a cached) leaf certificate + private key for [host], signed by the root CA. */
    private fun leafFor(context: Context, host: String): Pair<PrivateKey, X509Certificate> {
        ensureCaLoaded(context)
        return leafCache.getOrPut(host) {
            val kpg = KeyPairGenerator.getInstance("RSA", "BC")
            kpg.initialize(2048)
            val leafKeyPair = kpg.generateKeyPair()
            val ca = caCert!!
            val caKp = caKeyPair!!

            val subject = X500Name("CN=$host")
            val serial = BigInteger(160, SecureRandom())
            val notBefore = Date(System.currentTimeMillis() - 24L * 3600 * 1000)
            val notAfter = Date(System.currentTimeMillis() + 825L * 24 * 3600 * 1000)

            val builder = X509v3CertificateBuilder(
                X500Name(ca.subjectX500Principal.name), serial, notBefore, notAfter, subject,
                SubjectPublicKeyInfo.getInstance(leafKeyPair.public.encoded)
            )
            builder.addExtension(
                Extension.subjectAlternativeName, false,
                GeneralNames(arrayOf(GeneralName(GeneralName.dNSName, host)))
            )
            builder.addExtension(Extension.basicConstraints, false, BasicConstraints(false))
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
            builder.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
            builder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(leafKeyPair.public))
            builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(caKp.public))

            val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(caKp.private)
            val leafCert = JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
            leafKeyPair.private to leafCert
        }
    }

    /**
     * Builds an SSLContext presenting a leaf certificate for [host], for use in
     * server mode when terminating the client's TLS connection inside a CONNECT
     * tunnel. This is the piece that was completely missing before: without it,
     * HTTPS traffic can only ever be blindly relayed, never decrypted, and every
     * entry logs as method "CONNECT" instead of the real GET/POST/etc.
     */
    fun sslContextForHost(context: Context, host: String): SSLContext {
        val (privateKey, leafCert) = leafFor(context, host)
        ensureCaLoaded(context)
        val ks = KeyStore.getInstance("BKS", "BC")
        ks.load(null, null)
        ks.setKeyEntry("leaf", privateKey, STORE_PASSWORD, arrayOf<Certificate>(leafCert, caCert!!))

        val kmf = KeyManagerFactory.getInstance("X509")
        kmf.init(ks, STORE_PASSWORD)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
        return sslContext
    }

    @Synchronized
    fun getOrGenerateCaCertificatePem(context: Context): String {
        ensureCaLoaded(context)
        val encoder = android.util.Base64.encodeToString(caCert!!.encoded, android.util.Base64.NO_WRAP)
        val sb = StringBuilder()
        sb.append("-----BEGIN CERTIFICATE-----\n")
        var i = 0
        while (i < encoder.length) {
            val end = (i + 64).coerceAtMost(encoder.length)
            sb.append(encoder.substring(i, end)).append("\n")
            i += 64
        }
        sb.append("-----END CERTIFICATE-----\n")
        return sb.toString()
    }

    fun installCertificateInSystem(context: Context): Pair<Boolean, String> {
        exportCertificateToDownloads(context)
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val crtFile = File(downloadsDir, CA_FILENAME_CRT)
            if (!crtFile.exists()) {
                ensureCaLoaded(context)
                crtFile.writeBytes(caCert!!.encoded)
            }

            val uri: Uri = try {
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", crtFile)
            } catch (e: Exception) {
                Uri.fromFile(crtFile)
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/x-x509-ca-cert")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                Pair(true, "تم حفظ الشهادة في Downloads وفتح مثبت الشهادات")
            } else {
                val settingsIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                Pair(true, "تم الحفظ في Downloads. افتح: الأمان -> تثبيت شهادة -> شهادة CA")
            }
        } catch (e: Exception) {
            try {
                val settingsIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                Pair(true, "تم فتح إعدادات الأمان. حدد شهادة CA من مجلد Downloads")
            } catch (ex: Exception) {
                Pair(false, "تعذر فتح إعدادات الأمان: ${ex.localizedMessage}")
            }
        }
    }

    fun exportCertificateToDownloads(context: Context): Pair<Boolean, String> {
        ensureCaLoaded(context)
        val derBytes = caCert!!.encoded
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
                    resolver.openOutputStream(it)?.use { stream -> stream.write(pemContent.toByteArray(Charsets.UTF_8)) }
                }

                val contentValuesCrt = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, CA_FILENAME_CRT)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uriCrt: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValuesCrt)
                uriCrt?.let {
                    // DER bytes (not PEM text) — Android's system installer recognizes this more reliably.
                    resolver.openOutputStream(it)?.use { stream -> stream.write(derBytes) }
                }

                Pair(true, "Exported InterceptX_Root_CA.crt & .pem to Downloads folder")
            } else {
                saveToLegacyDownloadsDir(context, pemContent, derBytes)
            }
        } catch (e: Exception) {
            try {
                val downloadsFolder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val filePem = File(downloadsFolder, CA_FILENAME_PEM)
                FileOutputStream(filePem).use { it.write(pemContent.toByteArray(Charsets.UTF_8)) }
                val fileCrt = File(downloadsFolder, CA_FILENAME_CRT)
                FileOutputStream(fileCrt).use { it.write(derBytes) }
                Pair(true, "Saved CA (.crt & .pem) to ${downloadsFolder.absolutePath}")
            } catch (ex: Exception) {
                Pair(false, "Failed to export certificate: ${ex.localizedMessage}")
            }
        }
    }

    private fun saveToLegacyDownloadsDir(context: Context, pemContent: String, derBytes: ByteArray): Pair<Boolean, String> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val targetFile = File(downloadsDir, CA_FILENAME_PEM)
        FileOutputStream(targetFile).use { it.write(pemContent.toByteArray(Charsets.UTF_8)) }
        val crtFile = File(downloadsDir, CA_FILENAME_CRT)
        FileOutputStream(crtFile).use { it.write(derBytes) }
        return Pair(true, "Saved to Downloads/$CA_FILENAME_PEM")
    }

    fun shareOrInstallCertificate(context: Context) {
        ensureCaLoaded(context)
        val file = File(context.cacheDir, CA_FILENAME_CRT)
        file.writeBytes(caCert!!.encoded)

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
        ensureCaLoaded(context)
        val cert = caCert!!
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }
        val md5 = MessageDigest.getInstance("MD5").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }
        return CertificateInfo(
            commonName = "InterceptX Root CA",
            organization = "InterceptX Cyber Labs",
            serialNumber = cert.serialNumber.toString(16).uppercase().chunked(2).joinToString(":"),
            validFrom = fmt.format(cert.notBefore),
            validTo = fmt.format(cert.notAfter),
            sha256Fingerprint = sha256,
            md5Fingerprint = md5
        )
    }
}
