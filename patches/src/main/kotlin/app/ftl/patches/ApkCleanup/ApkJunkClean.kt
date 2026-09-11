package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.stringOption
import java.io.File
import java.util.logging.Logger

private val PROTECTED_PATTERNS = listOf(
    Regex(""".*META-INF/MANIFEST\.MF$"""),
    Regex(""".*META-INF/services/.*"""),
    Regex(""".*META-INF/.*\.(RSA|SF|DSA|EC)$"""),
    Regex(""".*classes\d*\.dex$"""),
    Regex(""".*resources\.arsc$"""),
    Regex(""".*AndroidManifest\.xml$"""),
)

private val JUNK_PATTERNS = listOf(
    Regex(""".*play-services-.*\.properties$"""),
    Regex(""".*firebase-.*\.properties$"""),
    Regex(""".*app-update\.properties$"""),
    Regex(""".*billing\.properties$"""),
    Regex(""".*billing-ktx\.properties$"""),
    Regex(""".*review\.properties$"""),
    Regex(""".*hsdp\.properties$"""),
    Regex(""".*core-common\.properties$"""),
    Regex(""".*user-messaging-platform\.properties$"""),
    Regex(""".*feature-delivery.*\.properties$"""),
    Regex(""".*ads-mobile-sdk\.properties$"""),
    Regex(""".*\.proto$"""),
    Regex(""".*DebugProbesKt\.bin$"""),
    Regex(""".*\.version$"""),
    Regex(""".*_VERSION$"""),
    Regex(""".*androidsupportmultidexversion\.txt$"""),
    Regex(""".*stamp-cert-sha256$"""),
    Regex(""".*version-control-info\.textproto$"""),
    Regex(""".*kotlin-tooling-metadata\.json$"""),
    Regex(""".*META-INF/CHANGES$"""),
    Regex(""".*META-INF/README\.md$"""),
    Regex(""".*META-INF/NOTICE.*"""),
    Regex(""".*META-INF/LICENSE.*"""),
    Regex(""".*(?:^|/)LICENSES$"""),
    Regex(""".*ion-java\.properties$"""),
    Regex(""".*THIRD-PARTY-NOTICES\.txt$"""),
    Regex(""".*licenses\.md$"""),
    Regex(""".*debug\.keystore$"""),
    Regex(""".*_trackers\.xml$"""),
    Regex(""".*version\.properties$"""),
    Regex(""".*integrity\.properties$"""),
    Regex(""".*androidannotations-api\.properties$"""),
    Regex(""".*transport-.*\.properties$"""),
    Regex(""".*jetty-dir\.css$"""),
    // ART baseline profiles (also catches APKs that ship them outside assets/dexopt/)
    Regex(""".*(?:^|/)baseline\.profm?$"""),
)

// === THÊM DANH SÁCH BẮN TỈA TỪ REVANCED SANG ===
private val JUNK_DIRECTORY_PREFIXES = listOf(
    "assets/dexopt",
    "com/clevertap",
    "org/jacoco",
    "org/joda",
    "services",
)

private val EXCLUDED_ROOT_CALLS = listOf(
    // === NHÓM GOOGLE PLAY SERVICES ===
    "play-services-auth.properties",
    "play-services-auth-api-phone.properties",
    "play-services-auth-base.properties",
    "play-services-base.properties",
    "play-services-cloud-messaging.properties",
    "play-services-gcm.properties",
    "play-services-tasks.properties",

    // === NHÓM FIREBASE ===
    "firebase-auth.properties",
    "firebase-auth-interop.properties",
    "firebase-common.properties",
    "firebase-components.properties",
    "firebase-core.properties",
    "firebase-database.properties",
    "firebase-datatransport.properties",
    "firebase-inappmessaging.properties",
    "firebase-inappmessaging-display.properties",
    "firebase-messaging.properties",

    // === NHÓM KHÁC ===
    "core-common.properties",
    "META-INF/androidx.compose.ui_ui.version",
    "androidannotations-api.properties",
    "jetty-dir.css"
)

private val PACKAGE_NAME = listOf(
    "com.viber.voip", "com.facebook.orca", "com.whatsapp", "com.zing.zalo"
)

private val EXACT_ROOT_JUNK = listOf(
    "play-services-ads.properties", "play-services-ads-base.properties", "play-services-ads-identifier.properties",
    "play-services-ads-lite.properties", "play-services-analytics.properties", "play-services-analytics-impl.properties",
    "play-services-appset.properties", "play-services-auth.properties", "play-services-auth-api-phone.properties",
    "play-services-auth-base.properties", "play-services-base.properties", "play-services-basement.properties",
    "play-services-cast.properties", "play-services-cast-framework.properties", "play-services-clearcut.properties",
    "play-services-cloud-messaging.properties", "play-services-drive.properties", "play-services-fido.properties",
    "play-services-fitness.properties", "play-services-games.properties", "play-services-gcm.properties",
    "play-services-identity.properties", "play-services-location.properties", "play-services-maps.properties",
    "play-services-measurement.properties", "play-services-measurement-api.properties", "play-services-measurement-base.properties",
    "play-services-measurement-impl.properties", "play-services-measurement-sdk.properties", "play-services-measurement-sdk-api.properties",
    "play-services-oss-licenses.properties", "play-services-pay.properties", "play-services-places-placereport.properties",
    "play-services-safetynet.properties", "play-services-stats.properties", "play-services-tasks.properties",
    "play-services-vision.properties", "play-services-vision-common.properties", "play-services-wallet.properties",
    "play-services-wearable.properties", "firebase-analytics.properties", "firebase-annotations.properties",
    "firebase-auth.properties", "firebase-auth-interop.properties", "firebase-common.properties",
    "firebase-components.properties", "firebase-config.properties", "firebase-core.properties",
    "firebase-crashlytics.properties", "firebase-database.properties", "firebase-datatransport.properties",
    "firebase-dynamic-links.properties", "firebase-encoders.properties", "firebase-encoders-proto.properties",
    "firebase-firestore.properties", "firebase-iid.properties", "firebase-iid-interop.properties",
    "firebase-inappmessaging.properties", "firebase-inappmessaging-display.properties", "firebase-installations.properties",
    "firebase-installations-interop.properties", "firebase-measurement-connector.properties", "firebase-messaging.properties",
    "firebase-perf.properties", "firebase-storage.properties", "transport-api.properties",
    "transport-backend-cct.properties", "transport-runtime.properties", "client_analytics.proto",
    "messaging_event.proto", "messaging_event_extension.proto", "app-update.properties", 
    "billing.properties", "billing-ktx.properties", "review.properties", "hsdp.properties", 
    "core-common.properties", "user-messaging-platform.properties", "ads-mobile-sdk.properties", 
    "DebugProbesKt.bin", "androidsupportmultidexversion.txt", "stamp-cert-sha256", 
    "version-control-info.textproto", "kotlin-tooling-metadata.json", "LICENSES", 
    "THIRD-PARTY-NOTICES.txt", "licenses.md", "debug.keystore", "version.properties", 
    "integrity.properties", "androidannotations-api.properties", "jetty-dir.css"
)
// ==================================================

private val EXCLUDED_PREFIXES = listOf("res/")

val apkCleanupPatch = rawResourcePatch(
    name = "APK Junk Cleanup",
    description = "Removes junk and useless files with no runtime purpose inside apk.",
    default = false,
) {
    val splitByArch by booleanOption(
        key = "splitByArch",
        default = false,
        title = "Keep Only One Architecture",
        description = "Keep native libraries (.so files) for only one CPU architecture. To generate separate APKs for each architecture, run this patch multiple times with a different architecture selected each time.",
    )

    val targetArch by stringOption(
        key = "targetArch",
        default = "arm64-v8a",
        values = mapOf(
            "arm64-v8a" to "ARM64 (arm64-v8a)",
            "armeabi-v7a" to "ARMv7 (armeabi-v7a)",
            "x86" to "x86",
            "x86_64" to "x86_64",
        ),
        title = "Target architecture",
        description = "Which architecture to keep when splitting is enabled.",
    )

    execute {
        val logger = Logger.getLogger(this::class.java.name)
        val manifestFile = get("AndroidManifest.xml")
        val apkRoot = manifestFile.parentFile ?: File(".")

        // === LOGIC KIỂM TRA PACKAGE AN TOÀN TUYỆT ĐỐI ===
        var isExcludedApp = false
        var detectedPackage = "unknown"

        try {
            if (manifestFile.isFile) {
                val rawBytes = manifestFile.readBytes()
                val strUtf8 = String(rawBytes, Charsets.UTF_8)
                val text = if (strUtf8.contains("<manifest")) strUtf8 else String(rawBytes, Charsets.UTF_16LE)
                
                val topLines = text.lines().take(5)
                val manifestLine = topLines.find { it.contains("<manifest") }
                
                if (manifestLine != null) {
                    for (pkg in PACKAGE_NAME) {
                        if (manifestLine.contains("package=\"$pkg\"") || manifestLine.contains("package='$pkg'")) {
                            isExcludedApp = true
                            detectedPackage = pkg
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("APK Cleanup: Failed to verify package from raw Manifest - ${e.message}")
        }

        if (isExcludedApp) {
            logger.info("APK Cleanup: Detected protected package ($detectedPackage). Applying EXCLUDED_ROOT_CALLS rules.")
        }
        // ==============================================

        var removedFiles = 0
        var freedBytes = 0L

        fun isProtected(relativePath: String) = PROTECTED_PATTERNS.any { it.matches(relativePath) }

        fun removeTree(path: String) {
            // Chốt chặn ngay cửa: Nếu app thuộc diện bảo kê và file/folder nằm trong list thì cấm xoá
            if (isExcludedApp && EXCLUDED_ROOT_CALLS.contains(path)) return

            val entry = get(path) // java.io.File
            if (entry.isDirectory) {
                val children = entry.list()
                val preview = children?.take(5)?.joinToString()
                logger.info("APK Cleanup: $path/ -> ${children?.size ?: -1} entries (e.g. $preview)")
                children?.forEach { child -> removeTree("$path/$child") }
            } else if (entry.isFile) {
                if (isProtected(path)) return
                
                // Chốt chặn cho file con bên trong phòng hờ
                if (isExcludedApp && EXCLUDED_ROOT_CALLS.any { path.endsWith(it) }) return

                val size = entry.length()
                if (entry.delete()) {
                    removedFiles++
                    freedBytes += size
                    logger.fine("Removed: $path (${size}B)")
                } else {
                    logger.warning("APK Cleanup: failed to delete $path")
                }
            } else {
                logger.fine("APK Cleanup: $path -> neither file nor directory")
            }
        }

        // 1. Quét tự động đệ quy bằng walkTopDown (API chuẩn của Morphe)
        apkRoot.walkTopDown()
            .filter { it.isFile }
            .toList()
            .forEach { file ->
                val relativePath = file.relativeTo(apkRoot).path.replace("\\", "/")

                if (isProtected(relativePath)) return@forEach
                if (EXCLUDED_PREFIXES.any { relativePath.startsWith(it) }) return@forEach
                
                // Áp dụng luật bảo kê cho file khi quét tự động
                if (isExcludedApp && EXCLUDED_ROOT_CALLS.contains(relativePath)) return@forEach

                if (JUNK_PATTERNS.any { it.matches(relativePath) }) {
                    val size = file.length()
                    if (file.delete()) {
                        removedFiles++
                        freedBytes += size
                        logger.info("Removed file: $relativePath (${size}B)")
                    }
                }
            }

        // 2. Bắn tỉa trực tiếp các file rác nằm rải rác ở root (EXACT_ROOT_JUNK)
        EXACT_ROOT_JUNK.forEach { exactName ->
            val entry = get(exactName)
            if (entry.isFile && !isProtected(exactName)) {
                if (isExcludedApp && EXCLUDED_ROOT_CALLS.contains(exactName)) return@forEach
                
                val size = entry.length()
                if (entry.delete()) {
                    removedFiles++
                    freedBytes += size
                    logger.info("Removed Direct Target: $exactName (${size}B)")
                }
            }
        }

        // 3. Gọi hàm removeTree gốc của Morphe (Đã được nâng cấp để né EXCLUDED_ROOT_CALLS)
        try { removeTree("kotlin") } catch (e: Exception) { logger.severe("APK Cleanup: failed removing kotlin/ folder: ${e.message}") }
        try { removeTree("assets/audience_network.dex") } catch (e: Exception) { logger.severe("APK Cleanup: failed removing assets/audience_network.dex: ${e.message}") }
        
        // 4. Bắn tỉa thư mục rác (JUNK_DIRECTORY_PREFIXES)
        JUNK_DIRECTORY_PREFIXES.forEach { prefix ->
            try {
                removeTree(prefix)
            } catch (e: Exception) {
                logger.warning("APK Cleanup: failed removing $prefix: ${e.message}")
            }
        }

        // 5. Xử lý META-INF
        try {
            val metaInf = get("META-INF")
            if (metaInf.isDirectory) {
                metaInf.list()?.forEach { name ->
                    if (name.lowercase() == "services") return@forEach
                    try {
                        removeTree("META-INF/$name")
                    } catch (e: Exception) {
                        logger.severe("APK Cleanup: failed removing META-INF/$name/: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("APK Cleanup: failed scanning META-INF/: ${e.message}")
        }

        // Dọn dẹp thư mục rỗng (Logic gốc của Morphe)
        apkRoot.walkBottomUp()
            .filter { it.isDirectory && it != apkRoot && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }

        // Xử lý giữ lại kiến trúc CPU (Split by Arch)
        if (splitByArch == true) {
            val archToKeep = targetArch ?: "arm64-v8a"
            val libDir = get("lib")

            if (libDir.isDirectory) {
                val archNames = libDir.list()?.toList() ?: emptyList()
                val hasTarget = archNames.contains(archToKeep)

                if (hasTarget) {
                    archNames.filter { it != archToKeep }.forEach { arch ->
                        try {
                            removeTree("lib/$arch")
                        } catch (e: Exception) {
                            logger.severe("APK Cleanup: failed removing lib/$arch/: ${e.message}")
                        }
                    }
                } else {
                    logger.warning(
                        "APK Cleanup: selected architecture \"$archToKeep\" not found in lib/. " +
                        "Available: ${archNames.joinToString()}. Keeping all architectures."
                    )
                }
            }
        }

        logger.info("APK Cleanup: removed $removedFiles files, freed ${freedBytes / 1024}KB")
    }
}