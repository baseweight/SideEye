package ai.baseweight.sideeye.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object GooglePhotosHelper {
    private const val GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos"
    private const val PREFS_NAME = "sideeye_prefs"
    private const val KEY_BANNER_DISMISSED = "google_photos_banner_dismissed"

    fun isBannerDismissed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BANNER_DISMISSED, false)
    }

    fun dismissBanner(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BANNER_DISMISSED, true).apply()
    }

    fun isGooglePhotosInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GOOGLE_PHOTOS_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getOpenGooglePhotosSettingsIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://photos.google.com/settings")
            setPackage(GOOGLE_PHOTOS_PACKAGE)
        }
    }

    fun getOpenGooglePhotosBackupSettingsIntent(context: Context): Intent {
        // Try to open Google Photos app settings directly
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$GOOGLE_PHOTOS_PACKAGE")
        }
        return intent
    }

    fun openGooglePhotosBackupSettings(context: Context): Boolean {
        if (!isGooglePhotosInstalled(context)) {
            return false
        }

        // Launch Google Photos - user can navigate to backup settings from there
        val launchIntent = context.packageManager.getLaunchIntentForPackage(GOOGLE_PHOTOS_PACKAGE)
        return if (launchIntent != null) {
            context.startActivity(launchIntent)
            true
        } else {
            false
        }
    }
}
