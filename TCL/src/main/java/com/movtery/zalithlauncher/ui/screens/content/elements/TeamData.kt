package com.movtery.zalithlauncher.ui.screens.content.elements

import androidx.annotation.DrawableRes
import com.movtery.zalithlauncher.R

/**
 * Our Team - naya member add karna ho to bas ek naya TeamMember(...) line yahan add karo.
 * Hatana ho to us line ko delete kar do.
 *
 * category: jo bhi likhoge (Founder / Co-Founder / Manager / Contributor / kuch bhi),
 * usi naam ki heading ke neeche automatically group ho jaayega. Ek category mein
 * jitne chaho utne members add kar sakte ho, sab ek saath dikhenge.
 *
 * photoRes: About page ke "Wolf" jaisa hi - pehle apni photo ko
 * res/drawable/ folder mein daalo (jaise img_founder1.png), phir yahan
 * R.drawable.img_founder1 likh do.
 */
data class TeamMember(
    val name: String,
    val category: String,
    @DrawableRes val photoRes: Int
)

val teamMembers = listOf(
    // ===== Founder =====
    TeamMember(name = "Wolf", category = "Founder", photoRes = R.drawable.img_wolf),

    // ===== Co-Founder =====
    TeamMember(name = "Roller_Gaming", category = "Co-Founder", photoRes = R.drawable.img_roller_gaming),

    // ===== Manager =====
    TeamMember(name = "Unknown", category = "Manager", photoRes = R.drawable.img_unknown),
    TeamMember(name = "ItsKing111", category = "Manager", photoRes = R.drawable.img_itsking),

    // ===== Community Manager =====
    TeamMember(name = "Kush", category = "Community Manager", photoRes = R.drawable.img_kush),
    
    // ===== Moderator =====
    TeamMember(name = "Spicy_Gamerz", category = "Moderator", photoRes = R.drawable.img_spicy_gamerz),
    
    // ===== Beta-Testers =====
    TeamMember(name = "AHMAD XD", category = "Beta Testers", photoRes = R.drawable.img_amhad_xd),
    TeamMember(name = "SkyGaming25", category = "Beta Testers", photoRes = R.drawable.img_sky_gaming),
    TeamMember(name = "Devansh", category = "Beta Testers", photoRes = R.drawable.img_devansh),
    )

