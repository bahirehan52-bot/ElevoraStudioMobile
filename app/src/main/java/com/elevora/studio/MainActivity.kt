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

data class EditClip(val uri:String,val name:String,val duration:Long,val start:Long=0,val end:Long=duration,val speed:Float=1f,val rotation:Int=0,val look:String="Original",val brightness:Float=0f,val contrast:Float=0f)

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
  val arr=JSONArray();clips.forEach{c->arr.put(JSONObject().put("uri",c.uri).put("name",c.name).put("duration",c.duration).put("start",c.start).put("end",c.end).put("speed",c.speed.toDouble()).put("rotation",c.rotation).put("look",c.look).put("brightness",c.brightness.toDouble()).put("contrast",c.contrast.toDouble()))}
  root.put("clips",arr);context.contentResolver.openOutputStream(u)!!.use{it.write(root.toString(2).toByteArray())};status="Project saved."
 }.onFailure{status="Save failed: ${it.message}"}}
 val load=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)runCatching{
  val root=context.contentResolver.openInputStream(u)!!.bufferedReader().use{JSONObject(it.readText())};projectName=root.optString("name","My Video");overlayText=root.optString("overlayText","");textX=root.optDouble("textX",0.0).toFloat();textY=root.optDouble("textY",0.0).toFloat();textScale=root.optDouble("textScale",1.0).toFloat();appliedOverlayText=overlayText;appliedTextX=textX;appliedTextY=textY;appliedTextScale=textScale;clips.clear()
  val a=root.getJSONArray("clips");for(i in 0 until a.length()){val o=a.getJSONObject(i);clips.add(EditClip(o.getString("uri"),o.optString("name","Clip"),o.getLong("duration"),o.optLong("start"),o.optLong("end"),o.optDouble("speed",1.0).toFloat(),o.optInt("rotation"),o.optString("look","Original"),o.optDouble("brightness",0.0).toFloat(),o.optDouble("contrast",0.0).toFloat()))}
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
 LaunchedEffect(clip?.brightness,clip?.contrast,clip?.look,clip?.rotation,timelinePreview,selected){val current=clips.getOrNull(selected);if(current!=null)player.setVideoEffects(clipEffects(current,appliedOverlayText,appliedTextX,appliedTextY,appliedTextScale))}
 fun update(c:EditClip){if(selected in clips.indices)clips[selected]=c}
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
    OutlinedButton(onClick={addVideos.launch(arrayOf("video/*"))},modifier=Modifier.weight(1f)){Icon(Icons.Default.VideoLibrary,null);Text(" Import")}
    OutlinedButton(enabled=clip!=null,onClick={if(player.isPlaying)player.pause()else player.play()},modifier=Modifier.weight(1f)){Icon(if(player.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,null);Text(" Play")}
    OutlinedButton(enabled=clips.isNotEmpty(),onClick={
     val items=clips.mapIndexed{index,c->MediaItem.Builder().setMediaId(index.toString()).setUri(Uri.parse(c.uri)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build()}
     timelinePreview=true;player.setMediaItems(items,true);player.prepare()
     clips.firstOrNull()?.let{player.setVideoEffects(clipEffects(it,appliedOverlayText,appliedTextX,appliedTextY,appliedTextScale));player.setPlaybackSpeed(it.speed)}
     player.play();status="Playing full timeline • ${clips.size} clips"
    }){Icon(Icons.Default.PlayArrow,null);Text(" Full")}
   }
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
    listOf("Media","Edit","Text","Audio","Effects","Export").forEach{tab->
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
         Surface(color=if(index==selected)Color(0xFF24534F)else Color(0xFF243344),shape=RoundedCornerShape(9.dp),modifier=Modifier.width(132.dp).border(1.dp,if(index==selected)mint else Color(0xFF405266),RoundedCornerShape(9.dp)),onClick={timelinePreview=false;selected=index}){
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
      "Edit"->{
       if(clip!=null){
        Text("TRIM • ${tc(clip.start)} – ${tc(clip.end)}",color=mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
        RangeSlider(value=clip.start.toFloat()..clip.end.toFloat(),onValueChange={r->val st=r.start.toLong().coerceIn(0,clip.duration-1);val en=r.endInclusive.toLong().coerceIn(st+1,clip.duration);update(clip.copy(start=st,end=en))},valueRange=0f..clip.duration.toFloat().coerceAtLeast(1f),onValueChangeFinished={val latest=clips.getOrNull(selected);if(latest!=null){val resume=player.playWhenReady;val item=MediaItem.Builder().setUri(Uri.parse(latest.uri)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(latest.start).setEndPositionMs(latest.end).build()).build();player.setMediaItem(item,true);player.setPlaybackSpeed(latest.speed);player.prepare();player.playWhenReady=resume}})
        Text("Speed  ${String.format(Locale.US,"%.2f",clip.speed)}x",color=Color.White,fontSize=11.sp)
        Slider(value=clip.speed,onValueChange={update(clip.copy(speed=it));player.setPlaybackSpeed(it)},valueRange=.25f..3f)
        Row(verticalAlignment=Alignment.CenterVertically){Text("Rotation  ${clip.rotation}°",color=Color.White,modifier=Modifier.weight(1f),fontSize=11.sp);IconButton(onClick={update(clip.copy(rotation=(clip.rotation+270)%360))}){Icon(Icons.Default.RotateLeft,null,tint=mint)};IconButton(onClick={update(clip.copy(rotation=(clip.rotation+90)%360))}){Icon(Icons.Default.RotateRight,null,tint=mint)}}
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
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("BRIGHTNESS",color=Color.White,fontSize=10.sp,modifier=Modifier.width(82.dp));Slider(value=clip.brightness,onValueChange={update(clip.copy(brightness=it))},valueRange=-1f..1f,modifier=Modifier.weight(1f))}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("CONTRAST",color=Color.White,fontSize=10.sp,modifier=Modifier.width(82.dp));Slider(value=clip.contrast,onValueChange={update(clip.copy(contrast=it))},valueRange=-1f..1f,modifier=Modifier.weight(1f))}
        Row(verticalAlignment=Alignment.CenterVertically){Text("Look: ${clip.look}",color=Color.White,modifier=Modifier.weight(1f),fontSize=11.sp);Box{OutlinedButton(onClick={lookMenu=true}){Text("Choose")};DropdownMenu(expanded=lookMenu,onDismissRequest={lookMenu=false}){listOf("Original","Warm","Cool","Vintage","Mono").forEach{v->DropdownMenuItem(text={Text(v)},onClick={update(clip.copy(look=v));lookMenu=false})}}}}
       }else Text("Select a clip first.",color=muted)
      }
      "Export"->{
       Text("EXPORT VIDEO",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
       Text("Render all clips with selected edits, text and audio.",color=muted,fontSize=11.sp)
       Button(enabled=clips.isNotEmpty()&&!exporting,onClick={
        exporting=true;status="Preparing MP4 export…"
        val out=File(context.cacheDir,"elevora_${System.currentTimeMillis()}.mp4")
        val items=clips.map{c->
         val media=MediaItem.Builder().setUri(Uri.parse(c.uri)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build()
         val effects=clipEffects(c,overlayText,textX,textY,textScale)
         val sp=object:SpeedProvider{override fun getSpeed(timeUs:Long)=c.speed;override fun getNextSpeedChangeTimeUs(timeUs:Long)=androidx.media3.common.C.TIME_UNSET}
         EditedMediaItem.Builder(media).setSpeed(sp).setEffects(Effects(emptyList(),effects)).build()
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
private fun clipEffects(c: EditClip, text: String, textX: Float = 0f, textY: Float = 0f, textScale: Float = 1f): List<Effect> {
 val effects = mutableListOf<Effect>()
 effects.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(c.rotation.toFloat()).build())
 val rgb = when(c.look) {
  "Warm" -> Triple(1.12f,1.02f,.88f)
  "Cool" -> Triple(.88f,1.02f,1.12f)
  "Vintage" -> Triple(1.08f,.94f,.82f)
  "Mono" -> Triple(.65f,.65f,.65f)
  else -> Triple(1f,1f,1f)
 }
 if(c.look != "Original") effects.add(RgbAdjustment.Builder().setRedScale(rgb.first).setGreenScale(rgb.second).setBlueScale(rgb.third).build())
 if(c.brightness != 0f) effects.add(Brightness(c.brightness))
 if(c.contrast != 0f) effects.add(Contrast(c.contrast))
 if(text.isNotBlank()) {
  val settings = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(textX, textY).setOverlayFrameAnchor(0f, 0f).setScale(textScale, textScale).build()
  effects.add(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(SpannableString(text), settings))))
 }
 return effects
}

private fun tc(ms:Long):String{val s=(ms/1000).coerceAtLeast(0);return String.format(Locale.US,"%02d:%02d",s/60,s%60)}
