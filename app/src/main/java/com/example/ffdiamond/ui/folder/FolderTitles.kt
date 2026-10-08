package com.example.ffdiamond.ui.folder

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import com.example.ffdiamond.R
import com.example.ffdiamond.model.HomeItem

/**
 * The name a folder gets when it is created by dropping one icon on another.
 *
 * Guessed from `ApplicationInfo.category`, which the app itself declares, rather than from anything
 * the launcher knows about the app. That means the guess is only as good as the manifest — plenty
 * of apps declare nothing — so a folder of unclassifiable apps is called "Folder" and left for the
 * user to rename, which is better than confidently naming it something wrong.
 */
object FolderTitles {

    fun suggest(context: Context, members: List<HomeItem>): String {
        val packageManager = context.packageManager
        val counts = members
            .filterIsInstance<HomeItem.App>()
            .mapNotNull { categoryOf(packageManager, it.info.packageName) }
            .groupingBy { it }
            .eachCount()

        val winner = counts.maxByOrNull { it.value }?.key
        return context.getString(winner?.let(::labelFor) ?: R.string.folder_default_title)
    }

    private fun categoryOf(packageManager: PackageManager, packageName: String): Int? =
        runCatching { packageManager.getApplicationInfo(packageName, 0).category }
            .getOrNull()
            ?.takeIf { it != ApplicationInfo.CATEGORY_UNDEFINED }

    @StringRes
    private fun labelFor(category: Int): Int = when (category) {
        ApplicationInfo.CATEGORY_GAME -> R.string.folder_category_games
        ApplicationInfo.CATEGORY_AUDIO -> R.string.folder_category_audio
        ApplicationInfo.CATEGORY_VIDEO -> R.string.folder_category_video
        ApplicationInfo.CATEGORY_IMAGE -> R.string.folder_category_image
        ApplicationInfo.CATEGORY_SOCIAL -> R.string.folder_category_social
        ApplicationInfo.CATEGORY_NEWS -> R.string.folder_category_news
        ApplicationInfo.CATEGORY_MAPS -> R.string.folder_category_maps
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> R.string.folder_category_productivity
        else -> R.string.folder_default_title
    }
}
