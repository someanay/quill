package com.tihor.quill.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// design_system.md §4
val QuillShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp), // fields, banners
    medium = RoundedCornerShape(16.dp), // cards, lists
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp), // dialogs, sheets
)
