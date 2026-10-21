package com.movtery.zalithlauncher.ui.screens.content.elements

import androidx.annotation.DrawableRes
import com.movtery.zalithlauncher.R

/** TurboCraft Launcher's official Our Team roster. */
data class TeamMember(
    val name: String,
    val category: String,
    @DrawableRes val photoRes: Int
)

val teamMembers = listOf(
    TeamMember(name = "Wolf", category = "Founder", photoRes = R.drawable.img_wolf),
    TeamMember(name = "Petitecake", category = "Founder", photoRes = R.drawable.img_petitecake),
    TeamMember(name = "Roller_Gaming", category = "Co-Founder", photoRes = R.drawable.img_roller_gaming),
    TeamMember(name = "ItzRealHyper", category = "Manager", photoRes = R.drawable.img_itzrealhyper),
    TeamMember(name = "AHMAD XD", category = "Beta Testers", photoRes = R.drawable.img_amhad_xd),
    TeamMember(name = "SkyGaming25", category = "Beta Testers", photoRes = R.drawable.img_sky_gaming),
    TeamMember(name = "Devansh", category = "Beta Testers", photoRes = R.drawable.img_devansh)
)
