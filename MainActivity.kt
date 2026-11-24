package com.example.gograding

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.SingleColorLut
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import java.io.File

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { GradeScreen() } } }
    }
}

@UnstableApi
@Composable
fun GradeScreen() {
    val ctx = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var lut by remember { mutableStateOf<SingleColorLut?>(null) }
    var lutName by remember { mutableStateOf("No LUT") }
    var exporting by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            videoUri = it
            player.setMediaItem(MediaItem.fromUri(it)); player.prepare(); player.playWhenReady = true
        }
    }
    val pickLut = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val cube = ctx.contentResolver.openInputStream(uri)!!.use { CubeParser.parse(it) }
            val effect = SingleColorLut.createFromCube(cube)
            lut = effect; lutName = uri.lastPathSegment?.substringAfterLast('/') ?: "LUT"
            player.setVideoEffects(listOf<Effect>(effect)) // real-time preview
        } catch (e: Exception) {
            Toast.makeText(ctx, "LUT error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Go Grading", style = MaterialTheme.typography.headlineMedium)
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { PlayerView(it).apply { this.player = player } }
        )
        Text("LUT: $lutName")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickVideo.launch("video/*") }) { Text("Pick video") }
            Button(onClick = { pickLut.launch(arrayOf("*/*")) }) { Text("Pick .cube") }
        }
        Button(
            enabled = videoUri != null && lut != null && !exporting,
            onClick = {
                exporting = true
                export(ctx, videoUri!!, lut!!) { exporting = false }
            }
        ) { Text(if (exporting) "Exporting…" else "Export graded video") }
    }
}

@UnstableApi
private fun export(ctx: android.content.Context, src: Uri, lut: SingleColorLut, done: () -> Unit) {
    val out = File(ctx.cacheDir, "graded_${System.currentTimeMillis()}.mp4")
    val edited = EditedMediaItem.Builder(MediaItem.fromUri(src))
        .setEffects(Effects(emptyList(), listOf<Effect>(lut)))
        .build()
    val transformer = Transformer.Builder(ctx)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, out.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GoGrading")
                }
                ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)?.let { u ->
                    ctx.contentResolver.openOutputStream(u)?.use { o -> out.inputStream().use { it.copyTo(o) } }
                }
                out.delete()
                Toast.makeText(ctx, "Saved to Movies/GoGrading", Toast.LENGTH_LONG).show()
                done()
            }
            override fun onError(composition: Composition, result: ExportResult, e: ExportException) {
                Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                done()
            }
        })
        .build()
    transformer.start(edited, out.absolutePath)
}
