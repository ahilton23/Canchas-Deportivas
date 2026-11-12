package com.example.canchasdeportivas.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Ilustraciones vectoriales ligeras: no descargan imágenes ni dependen de otro servicio. */
@Composable
fun CourtIllustration(sport: String, modifier: Modifier = Modifier) {
    val indoor = sport.contains("Vóley", true) || sport.contains("Básquet", true)
    Canvas(modifier) {
        val start = if (indoor) Color(0xFF174E66) else Color(0xFF08674D)
        val end = if (indoor) Color(0xFF287D90) else Color(0xFF24966B)
        drawRect(Brush.linearGradient(listOf(start,end)))
        repeat(8) { i -> if (i % 2 == 0) drawRect(Color.White.copy(alpha=.035f), Offset(size.width*i/8,0f),Size(size.width/8,size.height)) }
        val w = size.width*.7f; val h = size.height*.74f
        val x = (size.width-w)/2; val y = (size.height-h)/2
        val line = Color.White.copy(alpha=.7f); val stroke = Stroke(2.dp.toPx())
        drawRect(line,Offset(x,y),Size(w,h),style=stroke)
        drawLine(line,Offset(size.width/2,y),Offset(size.width/2,y+h),strokeWidth=2.dp.toPx())
        if (indoor) {
            drawLine(line,Offset(x+w*.25f,y),Offset(x+w*.25f,y+h),strokeWidth=2.dp.toPx())
            drawLine(line,Offset(x+w*.75f,y),Offset(x+w*.75f,y+h),strokeWidth=2.dp.toPx())
        } else {
            drawCircle(line,h*.22f,center=Offset(size.width/2,size.height/2),style=stroke)
            drawRect(line,Offset(x,y+h*.25f),Size(w*.13f,h*.5f),style=stroke)
            drawRect(line,Offset(x+w*.87f,y+h*.25f),Size(w*.13f,h*.5f),style=stroke)
        }
    }
}

@Composable
fun WelcomeBanner(name: String = "") {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (name.isBlank()) "TU PRÓXIMO PARTIDO" else "HOLA, ${name.substringBefore(' ').uppercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Tu cancha.\nTu equipo. Tu momento.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Encuentra tu espacio y vuelve a jugar.", color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun ReservationStatus(status: String) {
    val colors = MaterialTheme.colorScheme
    val pair = when (status) {
        "Confirmada" -> colors.primaryContainer to colors.onPrimaryContainer
        "Pendiente de pago", "En revisión" -> colors.tertiaryContainer to colors.onTertiaryContainer
        "Cancelada", "Vencida" -> colors.surfaceVariant to colors.onSurfaceVariant
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(color = pair.first, shape = MaterialTheme.shapes.small) {
        Text(status, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = pair.second, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NavSymbol(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(1.8.dp.toPx()); val w=size.width; val h=size.height
        when(index) {
            0 -> { drawRect(color,Offset(w*.12f,h*.2f),Size(w*.76f,h*.6f),style=stroke); drawLine(color,Offset(w*.5f,h*.2f),Offset(w*.5f,h*.8f),strokeWidth=stroke.width); drawCircle(color,w*.14f,style=stroke) }
            1 -> { drawRect(color,Offset(w*.17f,h*.2f),Size(w*.66f,h*.65f),style=stroke); drawLine(color,Offset(w*.17f,h*.4f),Offset(w*.83f,h*.4f),strokeWidth=stroke.width); drawCircle(color,w*.04f,Offset(w*.4f,h*.6f)); drawCircle(color,w*.04f,Offset(w*.65f,h*.6f)) }
            2 -> { drawCircle(color,w*.16f,Offset(w*.5f,h*.3f),style=stroke); drawArc(color,180f,180f,false,Offset(w*.2f,h*.53f),Size(w*.6f,h*.55f),style=stroke) }
            else -> repeat(3) { i -> val y=h*(.25f+i*.25f); drawLine(color,Offset(w*.15f,y),Offset(w*.85f,y),strokeWidth=stroke.width); drawCircle(color,w*.07f,Offset(w*(if(i%2==0) .65f else .35f),y)) }
        }
    }
}
