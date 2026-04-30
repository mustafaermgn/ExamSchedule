@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
package com.mustafa.sinavtakvim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import com.mustafa.sinavtakvim.shared.utils.toImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun ModernHeader(
    title: String,
    subtitle: String,
    role: String,
    profileImageUrl: String? = null,
    onLogout: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val headerBrush = Brush.horizontalGradient(
        colors = listOf(
            CorporateColors.Primary,
            Color(0xFF2A6B61)
        )
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        elevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBrush)
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo and Title Section
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("ST", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.h3.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontSize = 18.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Profile Section
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { menuExpanded = true }
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = role,
                            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        
                        Spacer(Modifier.width(12.dp))
                        
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            val bitmap = remember(profileImageUrl) {
                                try {
                                    if (!profileImageUrl.isNullOrBlank()) {
                                        Base64.decode(profileImageUrl).toImageBitmap()
                                    } else null
                                } catch (_: Exception) { null }
                            }

                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = Color.White,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(onClick = { 
                            menuExpanded = false
                            onSettings()
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = CorporateColors.Primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Ayarlar", color = CorporateColors.Ink)
                        }
                        DropdownMenuItem(onClick = { 
                            menuExpanded = false
                            onLogout()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = CorporateColors.Risk)
                            Spacer(Modifier.width(12.dp))
                            Text("Çıkış Yap", color = CorporateColors.Risk)
                        }
                    }
                }
            }
        }
    }
}
