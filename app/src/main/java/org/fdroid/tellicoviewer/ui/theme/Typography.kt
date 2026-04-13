package org.fdroid.tellicoviewer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typographie Material 3 pour TellicoViewer.
 * On utilise la police système (FontFamily.Default) pour le rendu natif.
 * Pour une police personnalisée, on ajouterait les fichiers .ttf dans res/font/
 * et on déclarerait un FontFamily ici.
 */
val TellicoTypography = Typography(
    // Titre principal (nom de la collection)
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    // Sous-titre (catégorie, nombre d'articles)
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize   = 16.sp,
        lineHeight = 24.sp
    ),
    // Texte normal (valeur de champ)
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp
    ),
    // Texte compact (en-têtes de colonnes, labels)
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    // Corps de fiche détail
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp
    )
)
