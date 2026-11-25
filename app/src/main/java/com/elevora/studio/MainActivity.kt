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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
import androidx.media3.common.Player
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition as MediaComposition
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.StaticOverlaySettings
import android.text.SpannableString
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.Locale

private val bg = Color(0xFF0B1018)
private val panel = Color(0xFF172331)
private val mint = Color(0xFF54E0C0)
private val muted = Color(0xFF9AA9B9)

data class SpeedKeyframe(val timeMs:Long,val speed:Float)
data class AnimationKeyframe(val timeMs:Long,val zoom:Float,val rotation:Float,val brightness:Float,val contrast:Float)
data class EditClip(val uri:String,val name:String,val duration:Long,val start:Long=0,val end:Long=duration,val speed:Float=1f,val rotation:Int=0,val look:String="Original",val brightness:Float=0f,val contrast:Float=0f,val cropZoom:Float=1f,val speedKeyframes:List<SpeedKeyframe> = emptyList(),val animationKeyframes:List<AnimationKeyframe> = emptyList())

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
 var overlayText by remember{ mutableStateOf("") }
 var appliedOverlayText by remember{ mutableStateOf("") }
 var appliedTextX by remember{ mutableFloatStateOf(0f) }
 var appliedTextY by remember{ mutableFloatStateOf(0f) }
 var appliedTextScale by remember{ mutableFloatStateOf(1f) }
 var textX by remember{ mutableFloatStateOf(0f) }
 var textY by remember{ mutableFloatStateOf(0f) }
 var textScale by remember{ mutableFloatStateOf(1f) }
 var activeTab by remember{ mutableStateOf("Media") }
 var exporting by remember{ mutableStateOf(false) }
 var timelinePreview by remember{ mutableStateOf(false) }
 var lookMenu by remember{ mutableStateOf(false) }
 var selectedKeyframe by remember{mutableIntStateOf(-1)}
 var selectedAnimationKeyframe by remember{mutableIntStateOf(-1)}
 val player= remember{ExoPlayer.Builder(context).build()}
 DisposableEffect(Unit){onDispose{player.release()}}
 fun duration(uri:Uri):Long{val r=MediaMetadataRetriever();return try{r.setDataSource(context,uri);r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L}finally{runCatching{r.release()}}}
 val addVideos=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
  uris.forEach{u->runCatching{context.contentResolver.takePersistableUriPermission(u,1)};val d=runCatching{duration(u)}.getOrDefault(0L);if(d>0)clips.add(EditClip(u.toString(),u.lastPathSegment?.substringAfterLast('/')?:"Video",d))}
  if(clips.isNotEmpty()){selected=clips.lastIndex;status="${uris.size} video(s) imported."}
 }
 val addAudio=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{context.contentResolver.takePersistableUriPermission(u,1)};audioUri=u;audioName=u.lastPathSegment?.substringAfterLast('/')?:"Music";status="Background music added."}}
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){u->if(u!=null)runCatching{
  val root=JSONObject().put("name",projectName).put("audio",audioUri?.toString()?:"").put("overlayText",overlayText).put("textX",textX.toDouble()).put("textY",textY.toDouble()).put("textScale",textScale.toDouble())
  val arr=JSONArray();clips.forEach{c->arr.put(JSONObject().put("uri",c.uri).put("name",c.name).put("duration",c.duration).put("start",c.start).put("end",c.end).put("speed",c.speed.toDouble()).put("rotation",c.rotation).put("look",c.look).put("brightness",c.brightness.toDouble()).put("contrast",c.contrast.toDouble()).put("cropZoom",c.cropZoom.toDouble()).put("speedKeyframes",JSONArray().apply{c.speedKeyframes.forEach{k->put(JSONObject().put("timeMs",k.timeMs).put("speed",k.speed.toDouble()))}}).put("animationKeyframes",JSONArray().apply{c.animationKeyframes.forEach{k->put(JSONObject().put("timeMs",k.timeMs).put("zoom",k.zoom.toDouble()).put("rotation",k.rotation.toDouble()).put("brightness",k.brightness.toDouble()).put("contrast",k.contrast.toDouble()))}}))}
  root.put("clips",arr);context.contentResolver.openOutputStream(u)!!.use{it.write(root.toString(2).toByteArray())};status="Project saved."
 }.onFailure{status="Save failed: ${it.message}"}}
 val load=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)runCatching{
  val root=context.contentResolver.openInputStream(u)!!.bufferedReader().use{JSONObject(it.readText())};projectName=root.optString("name","My Video");overlayText=root.optString("overlayText","");textX=root.optDouble("textX",0.0).toFloat();textY=root.optDouble("textY",0.0).toFloat();textScale=root.optDouble("textScale",1.0).toFloat();appliedOverlayText=overlayText;appliedTextX=textX;appliedTextY=textY;appliedTextScale=textScale;clips.clear()
  val a=root.getJSONArray("clips");for(i in 0 until a.length()){val o=a.getJSONObject(i);clips.add(EditClip(o.getString("uri"),o.optString("name","Clip"),o.getLong("duration"),o.optLong("start"),o.optLong("end"),o.optDouble("speed",1.0).toFloat(),o.optInt("rotation"),o.optString("look","Original"),o.optDouble("brightness",0.0).toFloat(),o.optDouble("contrast",0.0).toFloat(),o.optDouble("cropZoom",1.0).toFloat(),(o.optJSONArray("speedKeyframes")?:JSONArray()).let{keys->(0 until keys.length()).map{ki->keys.getJSONObject(ki).let{k->SpeedKeyframe(k.optLong("timeMs"),k.optDouble("speed",1.0).toFloat())}}},(o.optJSONArray("animationKeyframes")?:JSONArray()).let{keys->(0 until keys.length()).map{ki->keys.getJSONObject(ki).let{k->AnimationKeyframe(k.optLong("timeMs"),k.optDouble("zoom",1.0).toFloat(),k.optDouble("rotation",0.0).toFloat(),k.optDouble("brightness",0.0).toFloat(),k.optDouble("contrast",0.0).toFloat())}}}))}
  val au=root.optString("audio","");audioUri=if(au.isBlank())null else Uri.parse(au);audioName=if(audioUri==null)"No background music" else "Background music";selected=0.coerceAtMost(clips.lastIndex);status="Project loaded."
 }.onFailure{status="Open failed: ${it.message}"}}
 DisposableEffect(player){
  val listener=object:Player.Listener{
   override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int){
    val clipId=mediaItem?.mediaId?.toIntOrNull()
    val index=clipId?.takeIf{it in clips.indices}?:-1
    if(index>=0){selected=index;val c=clips[index];player.setVideoEffects(clipEffects(c,appliedOverlayText,appliedTextX,appliedTextY,appliedTextScale));player.setPlaybackSpeed(c.speed)}
   }
   override fun onPlaybackStateChanged(playbackState:Int){if(playbackState==Player.STATE_ENDED)timelinePreview=false}
  }
  player.addListener(listener)
  onDispose{player.removeListener(listener)}
 }
 LaunchedEffect(selected,clips.size,clipKey(clips,selected),timelinePreview){if(!timelinePreview){val c=clips.getOrNull(selected);if(c!=null){player.setVideoEffects(clipEffects(c,appliedOverlayText,appliedTextX,appliedTextY,appliedTextScale));val item=MediaItem.Builder().setUri(Uri.parse(c.uri)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build();player.setMediaItem(item);player.prepare();player.setPlaybackSpeed(c.speed)}else{player.setVideoEffects(emptyList());player.stop()}}}
 val clip=clips.getOrNull(selected)
 // Do not rebuild the video effect graph on a timer. Media3 may recreate the renderer each time,
 // which can stall or black out playback. Animation is sampled during export; preview effects
 // are applied explicitly after the user finishes editing a keyframe.
 LaunchedEffect(selected,clip?.speedKeyframes,timelinePreview){
  while(true){
   val current=clips.getOrNull(selected)
   if(current!=null&&player.isPlaying)player.setPlaybackSpeed(speedAt(current.speedKeyframes,player.currentPosition,current.speed))
   delay(60)
  }
 }
 fun update(c:EditClip){if(selected in clips.indices)clips[selected]=c}
 fun applyPreviewEffects(c:EditClip,frame:AnimationKeyframe?=null){
  // Never replace the renderer effect graph while frames are actively playing.
  if(player.isPlaying){
   status="Pause preview before applying this effect."
   return
  }
  val position=player.currentPosition
  val sample=frame?:c.animationKeyframes.getOrNull(selectedAnimationKeyframe)
   ?:if(c.animationKeyframes.isNotEmpty())animationAt(c.animationKeyframes,position,c) else null
  player.setVideoEffects(clipEffects(c,appliedOverlayText,appliedTextX,appliedTextY,appliedTextScale,sample))
  if(player.currentMediaItem!=null)player.seekTo(position)
 }
 fun removeClip(index:Int){if(index !in clips.indices)return;clips.removeAt(index);selected=when{clips.isEmpty()->0;index<selected->selected-1;index==selected->selected.coerceAtMost(clips.lastIndex);else->selected}}
 MaterialTheme(colorScheme=darkColorScheme(primary=mint,background=bg,surface=panel)){
  Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(horizontal=10.dp, vertical=6.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Column{Text("ELEVORA",color=mint,fontSize=21.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("STUDIO • MOBILE VIDEO EDITOR",color=muted,fontSize=9.sp)}
    Row{IconButton(onClick={load.launch(arrayOf("application/json","text/*"))}){Icon(Icons.Default.FolderOpen,"Open project",tint=mint)};IconButton(onClick={save.launch("${projectName.replace(" ","_")}.elevora.json")}){Icon(Icons.Default.Save,"Save project",tint=mint)}}
   }
   OutlinedTextField(projectName,{projectName=it},label={Text("Project name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   Column(Modifier.fillMaxWidth().weight(1f).border(1.5.dp,mint.copy(alpha=0.65f),RoundedCornerShape(12.dp)).background(Color.Black,RoundedCornerShape(12.dp)),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
    if(clip!=null)AndroidView(factory={ctx->PlayerView(ctx).apply{this.player=player;useController=true}},modifier=Modifier.fillMaxSize()) else Text("Import video clips",color=muted)
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
    OutlinedButton(onClick={addVideos.launch(arrayOf("video/*"))},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=6.dp,vertical=8.dp)){Icon(Icons.Default.VideoLibrary,null);Spacer(Modifier.width(4.dp));Text("Import",fontSize=12.sp,maxLines=1)}
    OutlinedButton(enabled=clip!=null,onClick={if(player.isPlaying)player.pause()else player.play()},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=6.dp,vertical=8.dp)){Icon(if(player.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,null);Spacer(Modifier.width(4.dp));Text("Play",fontSize=12.sp,maxLines=1)}
    OutlinedButton(enabled=clips.isNotEmpty(),onClick={
     val items=clips.mapIndexed{index,c->MediaItem.Builder().setMediaId(index.toString()).setUri(Uri.parse(c.uri)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build()}
     timelinePreview=true;player.setMediaItems(items,true);player.prepare()
     clips.firstOrNull()?.let{player.setVideoEffects(clipEffects(it,appliedOverlayText,appliedTextX,appliedTextY,appliedTextScale));player.setPlaybackSpeed(it.speed)}
     player.play();status="Playing full timeline • ${clips.size} clips"
    }){Icon(Icons.Default.PlayArrow,null);Text(" Full")}
   }
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
    listOf("Media","Crop","Keyframes","Text","Audio","Effects","Export").forEach{tab->
     val chosen=activeTab==tab
     Surface(color=if(chosen)mint else panel,shape=RoundedCornerShape(18.dp),modifier=Modifier.border(1.dp,if(chosen)mint else Color(0xFF344657),RoundedCornerShape(18.dp)),onClick={activeTab=tab}){
      Text(tab,color=if(chosen)bg else Color.White,fontWeight=FontWeight.Bold,fontSize=12.sp,modifier=Modifier.padding(horizontal=17.dp,vertical=10.dp))
     }
    }
   }
   Surface(Modifier.fillMaxWidth().weight(0.78f).border(1.dp,mint.copy(alpha=0.28f),RoundedCornerShape(topStart=14.dp,topEnd=14.dp)),color=panel,shape=RoundedCornerShape(topStart=14.dp,topEnd=14.dp)){
    Column(Modifier.fillMaxSize().padding(horizontal=10.dp,vertical=5.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
     when(activeTab){
      "Media"->{
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Text("TIMELINE • V1",color=mint,fontWeight=FontWeight.Bold,fontSize=12.sp)
        Text("${clips.size} clips",color=muted,fontSize=11.sp)
        TextButton(onClick={addVideos.launch(arrayOf("video/*"))}){Icon(Icons.Default.Add,null);Text("Add")}
       }
       Row(Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
        clips.forEachIndexed{index,c->
         var dragDelta by remember(index,clips.size){mutableFloatStateOf(0f)}
         Surface(color=if(index==selected)Color(0xFF24534F)else Color(0xFF243344),shape=RoundedCornerShape(9.dp),modifier=Modifier.width(132.dp).pointerInput(index,clips.size){detectDragGesturesAfterLongPress(onDragStart={dragDelta=0f},onDragEnd={
          val shift=(dragDelta/140f).roundToInt()
          val target=(index+shift).coerceIn(0,clips.lastIndex)
          if(target!=index&&index in clips.indices){val oldSelected=selected;val moved=clips.removeAt(index);clips.add(target,moved);selected=when{oldSelected==index->target;index<target&&oldSelected in (index+1)..target->oldSelected-1;target<index&&oldSelected in target until index->oldSelected+1;else->oldSelected};status="Clip moved to position ${target+1}."}
         },onDragCancel={dragDelta=0f},onDrag={change,amount->dragDelta+=amount.x;change.consume()})}.border(1.dp,if(index==selected)mint else Color(0xFF405266),RoundedCornerShape(9.dp)),onClick={timelinePreview=false;selected=index}){
          Column(Modifier.padding(9.dp)){
           Text("CLIP ${index+1}",color=mint,fontSize=10.sp,fontWeight=FontWeight.Bold)
           Text(c.name,color=Color.White,maxLines=1,fontSize=11.sp)
           Text("${tc(c.start)}–${tc(c.end)}",color=muted,fontSize=10.sp)
           Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){
            IconButton(onClick={if(index>0){val t=clips[index];clips[index]=clips[index-1];clips[index-1]=t;selected=index-1}}){Icon(Icons.Default.KeyboardArrowLeft,"Move left",tint=mint)}
            IconButton(onClick={if(index<clips.lastIndex){val t=clips[index];clips[index]=clips[index+1];clips[index+1]=t;selected=index+1}}){Icon(Icons.Default.KeyboardArrowRight,"Move right",tint=mint)}
            IconButton(onClick={removeClip(index)}){Icon(Icons.Default.Delete,"Delete",tint=Color.Red)}
           }
          }
         }
        }
        if(clips.isEmpty())Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){Icon(Icons.Default.VideoLibrary,null,tint=muted,modifier=Modifier.size(30.dp));Text("Your timeline is empty",color=Color.White,fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text("Import a video to start editing",color=muted,fontSize=11.sp)}
       }
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(enabled=clip!=null,onClick={val c=clip!!;val mid=c.start+(c.end-c.start)/2;if(mid>c.start&&mid<c.end){clips[selected]=c.copy(end=mid);clips.add(selected+1,c.copy(start=mid));status="Clip split."}},modifier=Modifier.weight(1f)){Icon(Icons.Default.ContentCut,null);Text("Split")}
        OutlinedButton(enabled=clip!=null,onClick={removeClip(selected)},modifier=Modifier.weight(1f)){Icon(Icons.Default.Delete,null);Text("Delete")}
       }
      }
      "Crop"->{
       if(clip!=null){
        Text("CROP & CLIP EDITOR",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
        Text("Trim, crop, rotate aur speed — sab yahin se control karein.",color=muted,fontSize=10.sp)
        Text("TRIM • ${tc(clip.start)} – ${tc(clip.end)}",color=mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
        RangeSlider(value=clip.start.toFloat()..clip.end.toFloat(),onValueChange={r->val st=r.start.toLong().coerceIn(0,clip.duration-1);val en=r.endInclusive.toLong().coerceIn(st+1,clip.duration);update(clip.copy(start=st,end=en))},valueRange=0f..clip.duration.toFloat().coerceAtLeast(1f),onValueChangeFinished={val latest=clips.getOrNull(selected);if(latest!=null){val resume=player.playWhenReady;val item=MediaItem.Builder().setUri(Uri.parse(latest.uri)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(latest.start).setEndPositionMs(latest.end).build()).build();player.setMediaItem(item,true);player.setPlaybackSpeed(latest.speed);player.prepare();player.playWhenReady=resume}})
        Text("CROP ZOOM  ${String.format(Locale.US,"%.2f",clip.cropZoom)}x",color=Color.White,fontSize=11.sp)
        Slider(value=clip.cropZoom,onValueChange={v->update(clip.copy(cropZoom=v))},onValueChangeFinished={clips.getOrNull(selected)?.let{applyPreviewEffects(it)}},valueRange=1f..3f)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
         OutlinedButton(onClick={update(clip.copy(cropZoom=1f));clips.getOrNull(selected)?.let{applyPreviewEffects(it)}},modifier=Modifier.weight(1f)){Text("Reset crop")}
         OutlinedButton(onClick={update(clip.copy(start=0,end=clip.duration,speed=1f,rotation=0,cropZoom=1f));status="Clip edits reset."},modifier=Modifier.weight(1f)){Text("Reset all")}
        }
        Text("Speed  ${String.format(Locale.US,"%.2f",clip.speed)}x",color=Color.White,fontSize=11.sp)
        Slider(value=clip.speed,onValueChange={update(clip.copy(speed=it));player.setPlaybackSpeed(it)},valueRange=.25f..3f)
        Row(verticalAlignment=Alignment.CenterVertically){Text("Rotation  ${clip.rotation}°",color=Color.White,modifier=Modifier.weight(1f),fontSize=11.sp);IconButton(onClick={update(clip.copy(rotation=(clip.rotation+270)%360));clips.getOrNull(selected)?.let{applyPreviewEffects(it)}}){Icon(Icons.Default.RotateLeft,null,tint=mint)};IconButton(onClick={update(clip.copy(rotation=(clip.rotation+90)%360));clips.getOrNull(selected)?.let{applyPreviewEffects(it)}}){Icon(Icons.Default.RotateRight,null,tint=mint)}}
       }else Text("Select a clip first.",color=muted)
      }
      "Keyframes"->{
       if(clip!=null){
        val clipLength=(clip.end-clip.start).coerceAtLeast(1L)
        val playhead=player.currentPosition.coerceIn(0L,clipLength)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
         Text("ANIMATION KEYFRAMES",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
         Text("Har animation keyframe apni alag Zoom, Rotation, Brightness aur Contrast values rakhta hai.",color=muted,fontSize=10.sp)
         Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
          Text("ANIMATION • ${clip.animationKeyframes.size} points",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
          Text("Playhead ${tc(playhead)}",color=muted,fontSize=10.sp)
         }
         Button(onClick={
          val t=player.currentPosition.coerceIn(0L,clipLength)
          val current=animationAt(clip.animationKeyframes,t,clip)
          val updated=(clip.animationKeyframes.filterNot{kotlin.math.abs(it.timeMs-t)<80L}+current.copy(timeMs=t)).sortedBy{it.timeMs}
          update(clip.copy(animationKeyframes=updated));selectedAnimationKeyframe=updated.indexOfFirst{it.timeMs==t}
          status="Animation keyframe added at ${tc(t)}."
         },modifier=Modifier.fillMaxWidth().heightIn(min=46.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp),shape=RoundedCornerShape(12.dp)){
          Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Add animation keyframe")
         }
         if(clip.animationKeyframes.isNotEmpty()){
          Text("SELECT A KEYFRAME • Tap to jump to its time and preview its values",color=muted,fontSize=10.sp)
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
           clip.animationKeyframes.forEachIndexed{idx,k->
            FilterChip(selected=selectedAnimationKeyframe==idx,onClick={
             selectedAnimationKeyframe=idx
             if(player.isPlaying)player.pause()
             player.seekTo(k.timeMs)
             applyPreviewEffects(clip,k)
             status="Selected animation keyframe at ${tc(k.timeMs)}."
            },label={Column(horizontalAlignment=Alignment.Start){
             Text("KF ${idx+1} • ${tc(k.timeMs)}",fontSize=11.sp,fontWeight=FontWeight.Bold)
             Text("Zoom ${String.format(Locale.US,"%.2f",k.zoom)}x",fontSize=10.sp)
             Text("Rotate ${String.format(Locale.US,"%.0f",k.rotation)}°",fontSize=10.sp)
            }})
           }
          }
          val anim=clip.animationKeyframes.getOrNull(selectedAnimationKeyframe)
          if(anim!=null){
           Surface(color=Color(0xFF203444),shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().border(1.dp,mint.copy(alpha=.55f),RoundedCornerShape(10.dp))){
            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
             Text("EDITING KF ${selectedAnimationKeyframe+1}",color=mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
             Text("Time: ${tc(anim.timeMs)} • These values belong ONLY to this keyframe.",color=muted,fontSize=10.sp)
             Text("ZOOM  ${String.format(Locale.US,"%.2f",anim.zoom)}x",color=Color.White,fontSize=11.sp)
             Slider(value=anim.zoom,onValueChange={v->val changed=clip.animationKeyframes.toMutableList();changed[selectedAnimationKeyframe]=anim.copy(zoom=v);update(clip.copy(animationKeyframes=changed))},onValueChangeFinished={clips.getOrNull(selected)?.let{applyPreviewEffects(it,clips[selected].animationKeyframes.getOrNull(selectedAnimationKeyframe))}},valueRange=1f..3f)
             Text("ROTATION  ${String.format(Locale.US,"%.0f",anim.rotation)}°",color=Color.White,fontSize=11.sp)
             Slider(value=anim.rotation,onValueChange={v->val changed=clip.animationKeyframes.toMutableList();changed[selectedAnimationKeyframe]=anim.copy(rotation=v);update(clip.copy(animationKeyframes=changed))},onValueChangeFinished={clips.getOrNull(selected)?.let{applyPreviewEffects(it,clips[selected].animationKeyframes.getOrNull(selectedAnimationKeyframe))}},valueRange=-180f..180f)
             Text("BRIGHTNESS  ${String.format(Locale.US,"%.2f",anim.brightness)}",color=Color.White,fontSize=11.sp)
             Slider(value=anim.brightness,onValueChange={v->val changed=clip.animationKeyframes.toMutableList();changed[selectedAnimationKeyframe]=anim.copy(brightness=v);update(clip.copy(animationKeyframes=changed))},onValueChangeFinished={clips.getOrNull(selected)?.let{applyPreviewEffects(it,clips[selected].animationKeyframes.getOrNull(selectedAnimationKeyframe))}},valueRange=-1f..1f)
             Text("CONTRAST  ${String.format(Locale.US,"%.2f",anim.contrast)}",color=Color.White,fontSize=11.sp)
             Slider(value=anim.contrast,onValueChange={v->val changed=clip.animationKeyframes.toMutableList();changed[selectedAnimationKeyframe]=anim.copy(contrast=v);update(clip.copy(animationKeyframes=changed))},onValueChangeFinished={clips.getOrNull(selected)?.let{applyPreviewEffects(it,clips[selected].animationKeyframes.getOrNull(selectedAnimationKeyframe))}},valueRange=-1f..1f)
             Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
              OutlinedButton(onClick={if(player.isPlaying)player.pause();player.seekTo(anim.timeMs);applyPreviewEffects(clip,anim);status="Previewing KF ${selectedAnimationKeyframe+1}."},modifier=Modifier.weight(1f)){Icon(Icons.Default.Visibility,null);Text("Preview frame")}
              OutlinedButton(onClick={val changed=clip.animationKeyframes.toMutableList();changed.removeAt(selectedAnimationKeyframe);update(clip.copy(animationKeyframes=changed));selectedAnimationKeyframe=-1;status="Animation keyframe deleted."},modifier=Modifier.weight(1f)){Icon(Icons.Default.Delete,null);Text("Delete")}
             }
            }
           }
          }else Text("Tap a keyframe above to edit its properties.",color=muted,fontSize=10.sp)
         }else Text("No animation keyframes yet. Move the playhead, add a keyframe, change its values, then repeat at another time.",color=muted,fontSize=11.sp)
         Divider(color=muted.copy(alpha=.3f),thickness=1.dp)
         Text("SPEED KEYFRAMES",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
         Text("Playhead ${tc(playhead)} • ${clip.speedKeyframes.size} speed points",color=muted,fontSize=10.sp)
         Text("Current speed: ${String.format(Locale.US,"%.2f",speedAt(clip.speedKeyframes,playhead,clip.speed))}x",color=Color.White,fontSize=12.sp)
         Button(onClick={
          val t=player.currentPosition.coerceIn(0L,clipLength)
          val value=speedAt(clip.speedKeyframes,t,clip.speed)
          val updated=(clip.speedKeyframes.filterNot{kotlin.math.abs(it.timeMs-t)<80L}+SpeedKeyframe(t,value)).sortedBy{it.timeMs}
          update(clip.copy(speedKeyframes=updated));selectedKeyframe=updated.indexOfFirst{it.timeMs==t};status="Speed keyframe added at ${tc(t)}."
         },modifier=Modifier.fillMaxWidth().heightIn(min=44.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=7.dp)){Icon(Icons.Default.Add,null);Text("Add speed keyframe")}
         if(clip.speedKeyframes.isNotEmpty()){
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
           clip.speedKeyframes.forEachIndexed{idx,k->FilterChip(selected=selectedKeyframe==idx,onClick={selectedKeyframe=idx;if(player.isPlaying)player.pause();player.seekTo(k.timeMs)},label={Text("KF ${idx+1} • ${tc(k.timeMs)} • ${String.format(Locale.US,"%.2f",k.speed)}x")})}
          }
          val kf=clip.speedKeyframes.getOrNull(selectedKeyframe)
          if(kf!=null){
           Text("EDITING SPEED KF ${selectedKeyframe+1} • ${tc(kf.timeMs)}",color=mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
           Text("Speed ${String.format(Locale.US,"%.2f",kf.speed)}x",color=Color.White,fontSize=11.sp)
           Slider(value=kf.speed,onValueChange={v->val changed=clip.speedKeyframes.toMutableList();changed[selectedKeyframe]=kf.copy(speed=v);update(clip.copy(speedKeyframes=changed));player.setPlaybackSpeed(speedAt(changed,player.currentPosition,clip.speed))},valueRange=.25f..3f)
           Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={if(player.isPlaying)player.pause();player.seekTo(kf.timeMs);status="Playhead moved to speed keyframe."},modifier=Modifier.weight(1f)){Icon(Icons.Default.LocationOn,null);Text("Go to")}
            OutlinedButton(onClick={val changed=clip.speedKeyframes.toMutableList();changed.removeAt(selectedKeyframe);update(clip.copy(speedKeyframes=changed));selectedKeyframe=-1;status="Speed keyframe deleted."},modifier=Modifier.weight(1f)){Icon(Icons.Default.Delete,null);Text("Delete")}
           }
          }
         }else Text("Add speed keyframes at different times to create a speed ramp.",color=muted,fontSize=10.sp)
         Text("Export uses the animation keyframes and interpolates between them. Preview applies the selected frame while paused.",color=muted,fontSize=10.sp)
        }
       }else Text("Select a clip first.",color=muted)
      }
      "Text"->{
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("TEXT OVERLAY",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text("Position and size",color=muted,fontSize=9.sp)}
       OutlinedTextField(overlayText,{overlayText=it},placeholder={Text("Type title or caption")},modifier=Modifier.fillMaxWidth().heightIn(max=54.dp),singleLine=true)
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("X  ${String.format(Locale.US,"%.1f",textX)}",color=Color.White,fontSize=10.sp,modifier=Modifier.width(48.dp));Slider(value=textX,onValueChange={textX=it},valueRange=-1f..1f,modifier=Modifier.weight(1f))}
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Y  ${String.format(Locale.US,"%.1f",textY)}",color=Color.White,fontSize=10.sp,modifier=Modifier.width(48.dp));Slider(value=textY,onValueChange={textY=it},valueRange=-1f..1f,modifier=Modifier.weight(1f))}
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("SIZE  ${String.format(Locale.US,"%.1f",textScale)}x",color=Color.White,fontSize=10.sp,modifier=Modifier.width(48.dp));Slider(value=textScale,onValueChange={textScale=it},valueRange=.3f..2.5f,modifier=Modifier.weight(1f))}
       Button(onClick={val current=clips.getOrNull(selected);if(current!=null){val wasPlaying=player.playWhenReady;val position=player.currentPosition;appliedOverlayText=overlayText;appliedTextX=textX;appliedTextY=textY;appliedTextScale=textScale;player.setVideoEffects(clipEffects(current,overlayText,textX,textY,textScale));player.seekTo(position);if(wasPlaying)player.play();status="Text applied to preview."}},modifier=Modifier.fillMaxWidth().heightIn(min=38.dp),contentPadding=PaddingValues(vertical=5.dp)){Text("Apply text to preview")}
      }
      "Audio"->{
       Text("AUDIO TRACK • A1",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
       Text(audioName,color=muted,fontSize=11.sp,maxLines=1)
       OutlinedButton(onClick={addAudio.launch(arrayOf("audio/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.MusicNote,null);Text("Add background music")}
       if(audioUri!=null)OutlinedButton(onClick={audioUri=null;audioName="No background music";status="Background music removed."},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Delete,null);Text("Remove audio")}
      }
      "Effects"->{
       if(clip!=null){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("BRIGHTNESS",color=Color.White,fontSize=10.sp,modifier=Modifier.width(82.dp));Slider(value=clip.brightness,onValueChange={update(clip.copy(brightness=it))},onValueChangeFinished={clips.getOrNull(selected)?.let{applyPreviewEffects(it)}},valueRange=-1f..1f,modifier=Modifier.weight(1f))}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("CONTRAST",color=Color.White,fontSize=10.sp,modifier=Modifier.width(82.dp));Slider(value=clip.contrast,onValueChange={update(clip.copy(contrast=it))},onValueChangeFinished={clips.getOrNull(selected)?.let{applyPreviewEffects(it)}},valueRange=-1f..1f,modifier=Modifier.weight(1f))}
        Row(verticalAlignment=Alignment.CenterVertically){Text("Look: ${clip.look}",color=Color.White,modifier=Modifier.weight(1f),fontSize=11.sp);Box{OutlinedButton(onClick={lookMenu=true}){Text("Choose")};DropdownMenu(expanded=lookMenu,onDismissRequest={lookMenu=false}){listOf("Original","Warm","Cool","Vintage","Mono").forEach{v->DropdownMenuItem(text={Text(v)},onClick={update(clip.copy(look=v));lookMenu=false;clips.getOrNull(selected)?.let{applyPreviewEffects(it)}})}}}}
       }else Text("Select a clip first.",color=muted)
      }
      "Export"->{
       Text("EXPORT VIDEO",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
       Text("Render all clips with selected edits, text and audio.",color=muted,fontSize=11.sp)
       Button(enabled=clips.isNotEmpty()&&!exporting,onClick={
        exporting=true;status="Preparing MP4 export…"
        val out=File(context.cacheDir,"elevora_${System.currentTimeMillis()}.mp4")
        // Bound Transformer item count: 100ms segments created hundreds/thousands of items.
        val animatedDuration=clips.filter{it.animationKeyframes.isNotEmpty()}.sumOf{(it.end-it.start).coerceAtLeast(1L)}
        val animationStepMs=if(animatedDuration<=0L)1000L else maxOf(1000L,(animatedDuration+119L)/120L)
        val items=clips.flatMap{c->
         val clipLength=(c.end-c.start).coerceAtLeast(1L)
         val segmentMs=if(c.animationKeyframes.isEmpty())clipLength else animationStepMs
         val count=((clipLength+segmentMs-1L)/segmentMs).toInt().coerceAtLeast(1)
         (0 until count).map{segmentIndex->
          val offset=segmentIndex*segmentMs
          val length=minOf(segmentMs,clipLength-offset)
          val animation=if(c.animationKeyframes.isEmpty())null else animationAt(c.animationKeyframes,offset+length/2,c)
          val media=MediaItem.Builder().setUri(Uri.parse(c.uri)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start+offset).setEndPositionMs(c.start+offset+length).build()).build()
          val effects=clipEffects(c,overlayText,textX,textY,textScale,animation)
          val sp=object:SpeedProvider{
           override fun getSpeed(timeUs:Long)=speedAt(c.speedKeyframes,offset+timeUs/1000L,c.speed)
           override fun getNextSpeedChangeTimeUs(timeUs:Long):Long{
            val next=c.speedKeyframes.firstOrNull{it.timeMs*1000L>offset*1000L+timeUs}
            return next?.let{(it.timeMs-offset)*1000L}?:androidx.media3.common.C.TIME_UNSET
           }
          }
          EditedMediaItem.Builder(media).setSpeed(sp).setEffects(Effects(emptyList(),effects)).build()
         }
        }
        val videoSeq=EditedMediaItemSequence.withAudioAndVideoFrom(items)
        val composition=if(audioUri!=null){val music=EditedMediaItem.Builder(MediaItem.fromUri(audioUri!!)).setRemoveVideo(true).build();val audioSeq=EditedMediaItemSequence.withAudioFrom(listOf(music)).buildUpon().setIsLooping(true).build();MediaComposition.Builder(listOf(videoSeq,audioSeq)).build()}else MediaComposition.Builder(listOf(videoSeq)).build()
        val listener=object:Transformer.Listener{
         override fun onCompleted(composition:MediaComposition,exportResult:ExportResult){runCatching{
          val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"${projectName.replace(Regex("[^A-Za-z0-9_-]"),"_")}_${System.currentTimeMillis()}.mp4");put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"${Environment.DIRECTORY_MOVIES}/Elevora Studio")}
          val dest=context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)!!;context.contentResolver.openOutputStream(dest)!!.use{o->FileInputStream(out).use{it.copyTo(o)}};out.delete();status="Export complete • Movies/Elevora Studio"
         }.onFailure{status="Gallery save failed: ${it.message}"};exporting=false
         }
         override fun onError(composition:MediaComposition,exportResult:ExportResult,exportException:ExportException){status="Export error: ${exportException.message}";out.delete();exporting=false}
        }
        runCatching{Transformer.Builder(context).addListener(listener).build().start(composition,out.absolutePath)}.onFailure{status="Export could not start: ${it.message}";out.delete();exporting=false}
       },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Movie,null);Text(if(exporting)"EXPORTING…" else "EXPORT MP4")}
       if(exporting)LinearProgressIndicator(Modifier.fillMaxWidth())
      }
     }
     Text(status,color=muted,fontSize=10.sp,maxLines=1)
    }
   }
  }
 }
}
private fun clipKey(clips:List<EditClip>,index:Int):String=clips.getOrNull(index)?.uri?:"empty"

@OptIn(UnstableApi::class)
private fun clipEffects(c: EditClip, text: String, textX: Float = 0f, textY: Float = 0f, textScale: Float = 1f, animation:AnimationKeyframe?=null): List<Effect> {
 val effects = mutableListOf<Effect>()
 effects.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(animation?.rotation?:c.rotation.toFloat()).setScale(animation?.zoom?:c.cropZoom,animation?.zoom?:c.cropZoom).build())
 val rgb = when(c.look) {
  "Warm" -> Triple(1.12f,1.02f,.88f)
  "Cool" -> Triple(.88f,1.02f,1.12f)
  "Vintage" -> Triple(1.08f,.94f,.82f)
  "Mono" -> Triple(.65f,.65f,.65f)
  else -> Triple(1f,1f,1f)
 }
 if(c.look != "Original") effects.add(RgbAdjustment.Builder().setRedScale(rgb.first).setGreenScale(rgb.second).setBlueScale(rgb.third).build())
 val brightness=animation?.brightness?:c.brightness
 if(brightness != 0f) effects.add(Brightness(brightness))
 val contrast=animation?.contrast?:c.contrast
 if(contrast != 0f) effects.add(Contrast(contrast))
 if(text.isNotBlank()) {
  val settings = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(textX, textY).setOverlayFrameAnchor(0f, 0f).setScale(textScale, textScale).build()
  effects.add(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(SpannableString(text), settings))))
 }
 return effects
}

private fun tc(ms:Long):String{val s=(ms/1000).coerceAtLeast(0);return String.format(Locale.US,"%02d:%02d",s/60,s%60)}

private fun speedAt(keyframes:List<SpeedKeyframe>,timeMs:Long,base:Float):Float{
 if(keyframes.isEmpty())return base
 val sorted=keyframes.sortedBy{it.timeMs}
 val before=sorted.lastOrNull{it.timeMs<=timeMs}
 val after=sorted.firstOrNull{it.timeMs>timeMs}
 if(before==null)return sorted.first().speed
 if(after==null)return before.speed
 val span=(after.timeMs-before.timeMs).coerceAtLeast(1L)
 val fraction=((timeMs-before.timeMs).toFloat()/span).coerceIn(0f,1f)
 return (before.speed+(after.speed-before.speed)*fraction).coerceIn(.25f,3f)
}

private fun animationAt(keyframes:List<AnimationKeyframe>,timeMs:Long,clip:EditClip):AnimationKeyframe{
 if(keyframes.isEmpty())return AnimationKeyframe(timeMs,clip.cropZoom,clip.rotation.toFloat(),clip.brightness,clip.contrast)
 val sorted=keyframes.sortedBy{it.timeMs}
 val before=sorted.lastOrNull{it.timeMs<=timeMs}
 val after=sorted.firstOrNull{it.timeMs>timeMs}
 if(before==null)return sorted.first().copy(timeMs=timeMs)
 if(after==null)return before.copy(timeMs=timeMs)
 val span=(after.timeMs-before.timeMs).coerceAtLeast(1L)
 val f=((timeMs-before.timeMs).toFloat()/span).coerceIn(0f,1f)
 fun mix(a:Float,b:Float)=(a+(b-a)*f)
 return AnimationKeyframe(timeMs,mix(before.zoom,after.zoom),mix(before.rotation,after.rotation),mix(before.brightness,after.brightness),mix(before.contrast,after.contrast))
}
