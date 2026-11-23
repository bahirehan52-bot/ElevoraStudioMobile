@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.elevora.studio

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.*
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.Locale

private val bg = Color(0xFF0B1018)
private val panel = Color(0xFF172331)
private val mint = Color(0xFF54E0C0)
private val muted = Color(0xFF9AA9B9)

data class EditClip(val uri:String,val name:String,val duration:Long,val start:Long=0,val end:Long=duration,val speed:Float=1f,val rotation:Int=0,val look:String="Original")

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{EditorScreen()}}
}

@Composable
@OptIn(UnstableApi::class)
private fun EditorScreen(){
 val context= LocalContext.current
 val clips= remember{ mutableStateListOf<EditClip>() }
 var selected by remember{ mutableIntStateOf(0) }
 var projectName by remember{ mutableStateOf("My Video") }
 var audioUri by remember{ mutableStateOf<Uri?>(null) }
 var audioName by remember{ mutableStateOf("No background music") }
 var status by remember{ mutableStateOf("Import videos to start editing.") }
 var exporting by remember{ mutableStateOf(false) }
 var lookMenu by remember{ mutableStateOf(false) }
 val player= remember{ExoPlayer.Builder(context).build()}
 DisposableEffect(Unit){onDispose{player.release()}}
 fun duration(uri:Uri):Long{val r=MediaMetadataRetriever();return try{r.setDataSource(context,uri);r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L}finally{runCatching{r.release()}}}
 val addVideos=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
  uris.forEach{u->runCatching{context.contentResolver.takePersistableUriPermission(u,1)};val d=runCatching{duration(u)}.getOrDefault(0L);if(d>0)clips.add(EditClip(u.toString(),u.lastPathSegment?.substringAfterLast('/')?:"Video",d))}
  if(clips.isNotEmpty()){selected=clips.lastIndex;status="${uris.size} video(s) imported."}
 }
 val addAudio=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{context.contentResolver.takePersistableUriPermission(u,1)};audioUri=u;audioName=u.lastPathSegment?.substringAfterLast('/')?:"Music";status="Background music added."}}
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){u->if(u!=null)runCatching{
  val root=JSONObject().put("name",projectName).put("audio",audioUri?.toString()?:"")
  val arr=JSONArray();clips.forEach{c->arr.put(JSONObject().put("uri",c.uri).put("name",c.name).put("duration",c.duration).put("start",c.start).put("end",c.end).put("speed",c.speed.toDouble()).put("rotation",c.rotation).put("look",c.look))}
  root.put("clips",arr);context.contentResolver.openOutputStream(u)!!.use{it.write(root.toString(2).toByteArray())};status="Project saved."
 }.onFailure{status="Save failed: ${it.message}"}}
 val load=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)runCatching{
  val root=context.contentResolver.openInputStream(u)!!.bufferedReader().use{JSONObject(it.readText())};projectName=root.optString("name","My Video");clips.clear()
  val a=root.getJSONArray("clips");for(i in 0 until a.length()){val o=a.getJSONObject(i);clips.add(EditClip(o.getString("uri"),o.optString("name","Clip"),o.getLong("duration"),o.optLong("start"),o.optLong("end"),o.optDouble("speed",1.0).toFloat(),o.optInt("rotation"),o.optString("look","Original")))}
  val au=root.optString("audio","");audioUri=if(au.isBlank())null else Uri.parse(au);audioName=if(audioUri==null)"No background music" else "Background music";selected=0.coerceAtMost(clips.lastIndex);status="Project loaded."
 }.onFailure{status="Open failed: ${it.message}"}}
 LaunchedEffect(selected,clips.size){val c=clips.getOrNull(selected);if(c!=null){player.setMediaItem(MediaItem.fromUri(Uri.parse(c.uri)));player.prepare()}else player.stop()}
 val clip=clips.getOrNull(selected)
 fun update(c:EditClip){if(selected in clips.indices)clips[selected]=c}
 MaterialTheme(colorScheme=darkColorScheme(primary=mint,background=bg,surface=panel)){
  Column(Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(14.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Column{Text("ELEVORA",color=mint,fontSize=24.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("STUDIO • MOBILE VIDEO EDITOR",color=muted,fontSize=9.sp)}
    Row{IconButton(onClick={load.launch(arrayOf("application/json","text/*"))}){Icon(Icons.Default.FolderOpen,"Open project",tint=mint)};IconButton(onClick={save.launch("${projectName.replace(" ","_")}.elevora.json")}){Icon(Icons.Default.Save,"Save project",tint=mint)}}
   }
   OutlinedTextField(projectName,{projectName=it},label={Text("Project name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   Spacer(Modifier.height(12.dp));Text("PREVIEW MONITOR",color=muted,fontSize=10.sp,fontWeight=FontWeight.Bold)
   Box(Modifier.fillMaxWidth().height(215.dp).background(Color.Black,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){
    if(clip!=null)AndroidView(factory={ctx->PlayerView(ctx).apply{this.player=player;useController=true}},modifier=Modifier.fillMaxSize()) else Text("Import video clips",color=muted)
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedButton(onClick={addVideos.launch(arrayOf("video/*"))},modifier=Modifier.weight(1f)){Icon(Icons.Default.VideoLibrary,null);Text(" Import clips")}
    OutlinedButton(enabled=clip!=null,onClick={if(player.isPlaying)player.pause()else player.play()},modifier=Modifier.weight(1f)){Icon(if(player.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,null);Text(" Play")}
   }
   Spacer(Modifier.height(14.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("VIDEO TIMELINE • V1",color=mint,fontWeight=FontWeight.Bold,fontSize=12.sp);Text("${clips.size} clips",color=muted,fontSize=11.sp)}
   clips.forEachIndexed{index,c->
    Surface(color=if(index==selected)Color(0xFF203B43)else panel,shape=RoundedCornerShape(9.dp),modifier=Modifier.fillMaxWidth().padding(vertical=3.dp),onClick={selected=index}){
     Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){
      Text("${index+1}",color=mint,modifier=Modifier.width(24.dp),fontWeight=FontWeight.Bold)
      Column(Modifier.weight(1f)){Text(c.name,color=Color.White,maxLines=1,fontSize=12.sp);Text("${tc(c.start)}–${tc(c.end)} • ${c.speed}x",color=muted,fontSize=10.sp)}
      IconButton(onClick={if(index>0){val t=clips[index];clips[index]=clips[index-1];clips[index-1]=t;selected=index-1}}){Icon(Icons.Default.KeyboardArrowUp,"Move up",tint=mint)}
      IconButton(onClick={if(index<clips.lastIndex){val t=clips[index];clips[index]=clips[index+1];clips[index+1]=t;selected=index+1}}){Icon(Icons.Default.KeyboardArrowDown,"Move down",tint=mint)}
      IconButton(onClick={clips.removeAt(index);selected=selected.coerceAtMost(clips.lastIndex)}){Icon(Icons.Default.Delete,"Delete",tint=Color.Red)}
     }
    }
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedButton(enabled=clip!=null,onClick={val c=clip!!;val mid=c.start+(c.end-c.start)/2;if(mid>c.start&&mid<c.end){clips[selected]=c.copy(end=mid);clips.add(selected+1,c.copy(start=mid));status="Clip split."}},modifier=Modifier.weight(1f)){Icon(Icons.Default.ContentCut,null);Text(" Split")}
    OutlinedButton(enabled=clip!=null,onClick={clips.removeAt(selected);selected=selected.coerceAtMost(clips.lastIndex)},modifier=Modifier.weight(1f)){Icon(Icons.Default.Delete,null);Text(" Delete")}
   }
   Spacer(Modifier.height(14.dp));Text("CLIP CONTROLS",color=mint,fontWeight=FontWeight.Bold,fontSize=12.sp)
   if(clip!=null){
    Text("Trim: ${tc(clip.start)} to ${tc(clip.end)}",color=muted,fontSize=11.sp)
    RangeSlider(value=clip.start.toFloat()..clip.end.toFloat(),onValueChange={r->val s=r.start.toLong().coerceIn(0,clip.duration-1);val e=r.endInclusive.toLong().coerceIn(s+1,clip.duration);update(clip.copy(start=s,end=e))},valueRange=0f..clip.duration.toFloat().coerceAtLeast(1f))
    Text("Speed ${String.format(Locale.US,"%.2f",clip.speed)}x",color=Color.White);Slider(value=clip.speed,onValueChange={update(clip.copy(speed=it))},valueRange=.25f..3f)
    Row(verticalAlignment=Alignment.CenterVertically){Text("Rotation ${clip.rotation}°",color=Color.White,modifier=Modifier.weight(1f));IconButton(onClick={update(clip.copy(rotation=(clip.rotation+270)%360))}){Icon(Icons.Default.RotateLeft,null,tint=mint)};IconButton(onClick={update(clip.copy(rotation=(clip.rotation+90)%360))}){Icon(Icons.Default.RotateRight,null,tint=mint)}}
    Row(verticalAlignment=Alignment.CenterVertically){Text("Look: ${clip.look}",color=Color.White,modifier=Modifier.weight(1f));Box{OutlinedButton(onClick={lookMenu=true}){Text("Choose")};DropdownMenu(expanded=lookMenu,onDismissRequest={lookMenu=false}){listOf("Original","Warm","Cool","Vintage","Mono").forEach{v->DropdownMenuItem(text={Text(v)},onClick={update(clip.copy(look=v));lookMenu=false})}}}}
   }else Text("Select a clip to edit.",color=muted)
   Spacer(Modifier.height(14.dp));Text("AUDIO TRACK • A1",color=mint,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(audioName,color=muted,fontSize=11.sp)
   OutlinedButton(onClick={addAudio.launch(arrayOf("audio/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.MusicNote,null);Text(" Add background music")}
   Spacer(Modifier.height(14.dp))
   Button(enabled=clips.isNotEmpty()&&!exporting,onClick={
    exporting=true;status="Preparing MP4 export…"
    val out=File(context.cacheDir,"elevora_${System.currentTimeMillis()}.mp4")
    val items=clips.map{c->
     val media=MediaItem.Builder().setUri(Uri.parse(c.uri)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build()
     val effects=mutableListOf<Effect>();effects.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(c.rotation.toFloat()).build())
     val rgb=when(c.look){"Warm"->Triple(1.12f,1.02f,.88f);"Cool"->Triple(.88f,1.02f,1.12f);"Vintage"->Triple(1.08f,.94f,.82f);"Mono"->Triple(.65f,.65f,.65f);else->Triple(1f,1f,1f)}
     if(c.look!="Original")effects.add(RgbAdjustment.Builder().setRedScale(rgb.first).setGreenScale(rgb.second).setBlueScale(rgb.third).build())
     val sp=object:SpeedProvider{override fun getSpeed(timeUs:Long)=c.speed;override fun getNextSpeedChangeTimeUs(timeUs:Long)=androidx.media3.common.C.TIME_UNSET}
     EditedMediaItem.Builder(media).setSpeed(sp).setEffects(Effects(emptyList(),effects)).build()
    }
    val videoSeq=EditedMediaItemSequence.withAudioAndVideoFrom(items)
    val composition=if(audioUri!=null){val music=EditedMediaItem.Builder(MediaItem.fromUri(audioUri!!)).setRemoveVideo(true).build();val audioSeq=EditedMediaItemSequence.withAudioFrom(listOf(music)).buildUpon().setIsLooping(true).build();Composition.Builder(videoSeq,audioSeq).build()}else Composition.Builder(videoSeq).build()
    val listener=object:Transformer.Listener{
     override fun onCompleted(composition:Composition,exportResult:ExportResult){runCatching{
      val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"${projectName.replace(Regex("[^A-Za-z0-9_-]"),"_")}_${System.currentTimeMillis()}.mp4");put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"${Environment.DIRECTORY_MOVIES}/Elevora Studio")}
      val dest=context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)!!;context.contentResolver.openOutputStream(dest)!!.use{o->FileInputStream(out).use{it.copyTo(o)}};out.delete();status="Export complete • Movies/Elevora Studio"
     }.onFailure{status="Gallery save failed: ${it.message}"};exporting=false
     }
     override fun onError(composition:Composition,exportResult:ExportResult,exportException:ExportException){status="Export error: ${exportException.message}";out.delete();exporting=false}
    }
    runCatching{Transformer.Builder(context).addListener(listener).build().start(composition,out.absolutePath)}.onFailure{status="Export could not start: ${it.message}";out.delete();exporting=false}
   },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Movie,null);Text(if(exporting)" EXPORTING…" else " EXPORT MP4")}
   if(exporting)LinearProgressIndicator(Modifier.fillMaxWidth())
   Spacer(Modifier.height(8.dp));Text(status,color=muted,fontSize=11.sp)
   Text("v0.3.0 • Multi-clip editor foundation",color=muted,fontSize=9.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=10.dp))
  }
 }
}
private fun tc(ms:Long):String{val s=(ms/1000).coerceAtLeast(0);return String.format(Locale.US,"%02d:%02d",s/60,s%60)}
