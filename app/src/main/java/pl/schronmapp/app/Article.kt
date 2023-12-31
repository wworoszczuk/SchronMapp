package pl.schronmapp.app

import androidx.compose.ui.graphics.painter.Painter

class Article (
    val id: Int,
    val name: String,
    val desc: String,
    val image: Painter
)