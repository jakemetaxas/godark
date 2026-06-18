package com.godark.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Neon = Color(0xFF00E676)
private val Dim = Color(0xFF9E9E9E)
private val Panel = Color(0xFF161616)

private const val URL_GITHUB = "https://github.com/jakemetaxas/godark"
private const val URL_LIBERAPAY = "https://liberapay.com/jakemetaxas"
private const val EMAIL = "jakemetaxas@proton.me"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
    fun email() {
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth()
                .background(Panel, RoundedCornerShape(12.dp))
                .clickable { onBack() }
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("←", color = Neon, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("Back", color = Neon, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(20.dp))
        Text("GODARK", color = Color.White, fontSize = 28.sp,
            fontWeight = FontWeight.Black, letterSpacing = 6.sp)
        Text("Take your privacy back.", color = Neon, fontSize = 14.sp)

        Spacer(Modifier.height(28.dp))

        Section("Why this exists")
        Body(
            "GoDark was made for people who are concerned about their privacy " +
                    "but aren't security engineers. Most privacy tools are built for " +
                    "experts. This one is for everyone else: one screen, plain words, " +
                    "and a clear picture of who is watching and what you can shut off."
        )

        Spacer(Modifier.height(20.dp))
        Section("Who made it")
        Body(
            "Built by Jake Metaxas, independently, in the open. No company, no " +
                    "investors, no data business behind it."
        )

        Spacer(Modifier.height(20.dp))
        Section("Our promise")
        Body(
            "Everything stays local, and we say so openly:\n\n" +
                    "• No analytics, no accounts, no servers of ours\n" +
                    "• Logs never leave this device\n" +
                    "• GoDark sends nothing anywhere, except your DNS queries to the " +
                    "resolver you chose, encrypted\n" +
                    "• Fully open source. Read the code. Build it yourself. Don't take " +
                    "our word for any of this."
        )

        Spacer(Modifier.height(24.dp))
        Section("Support the developer")
        Body(
            "GoDark is free and always will be. If it earns a place on your " +
                    "phone, a small donation keeps it maintained and independent. " +
                    "No pressure. Using it and telling a friend helps just as much."
        )
        Spacer(Modifier.height(12.dp))
        LinkButton("♥  Donate via Liberapay") { open(URL_LIBERAPAY) }

        Spacer(Modifier.height(24.dp))
        Section("Code & contact")
        Spacer(Modifier.height(12.dp))
        LinkButton("Source code on GitHub") { open(URL_GITHUB) }
        Spacer(Modifier.height(8.dp))
        LinkButton("Contact: $EMAIL") { email() }

        Spacer(Modifier.height(32.dp))
        Text("GoDark is licensed under GPL-3.0.", color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Neon, fontSize = 12.sp, letterSpacing = 3.sp,
        fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, color = Dim, fontSize = 14.sp, lineHeight = 21.sp)
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}