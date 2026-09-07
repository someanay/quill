package com.tihor.quill.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// design_system.md §4
val QuillShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp), // fields, banners
    medium = RoundedCornerShape(8.dp), // cards, lists
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp), // dialogs, sheets
)
