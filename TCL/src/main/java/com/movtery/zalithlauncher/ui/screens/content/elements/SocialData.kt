package com.movtery.zalithlauncher.ui.screens.content.elements

import androidx.annotation.DrawableRes
import com.movtery.zalithlauncher.R

/**
 * Social Links - naya link add karna ho to bas ek naya SocialLink(...) line yahan add karo.
 * Hatana ho to us line ko delete kar do.
 *
 * iconRes: pehle apna logo (Discord/YouTube wagera) res/drawable/ folder mein daalo
 * (jaise img_discord.png), phir yahan R.drawable.img_discord likh do.
 */
data class SocialLink(
    val name: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val url: String
)

val socialLinks = listOf(
    SocialLink(
        name = "Discord",
        description = "Join our community server",
        iconRes = R.drawable.img_discord,
        url = "https://discord.gg/bwkpJZB6NS"
    ),
    SocialLink(
        name = "YouTube",
        description = "Subscribe to our channel",
        iconRes = R.drawable.img_youtube,
        url = "https://youtube.com/@TurboCraft_Launcher"
    ),
    // SocialLink(
    //     name = "Instagram",
    //     description = "Follow us",
    //     iconRes = R.drawable.img_instagram,
    //     url = "https://instagram.com/your-page"
    // ),
)
