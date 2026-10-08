package com.example.ffdiamond.apps

/** Package names that belong in the Google folder, independent of Android APIs. */
object GoogleProducts {

    fun isGoogleProduct(packageName: String): Boolean {
        if (packageName in FOLDER_EXCLUDE) return false
        if (packageName.startsWith("com.google.")) return true
        return packageName in FOLDER_EXTRA
    }

    private val FOLDER_EXTRA = setOf(
        "com.android.chrome",
        "com.android.vending"
    )

    private val FOLDER_EXCLUDE = setOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.gsf.login",
        "com.google.android.ext.services",
        "com.google.android.permissioncontroller",
        "com.google.android.webview",
        "com.google.android.apps.restore",
        "com.google.android.configupdater",
        "com.google.android.printservice.recommendation",
        "com.google.android.partnersetup",
        "com.google.android.packageinstaller"
    )
}
