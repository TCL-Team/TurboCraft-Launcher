package com.movtery.zalithlauncher.ui.screens.content.elements

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.path.URL_SUPPORT
import com.movtery.zalithlauncher.path.URL_YOUTUBE

/** Official TurboCraft Launcher social destinations. */
data class SocialLink(
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val url: String
)

val socialLinks = listOf(
    SocialLink(
        nameRes = R.string.social_discord_name,
        descriptionRes = R.string.social_discord_description,
        iconRes = R.drawable.img_discord,
        url = URL_SUPPORT
    ),
    SocialLink(
        nameRes = R.string.social_youtube_name,
        descriptionRes = R.string.social_youtube_description,
        iconRes = R.drawable.img_youtube,
        url = URL_YOUTUBE
    )
)
