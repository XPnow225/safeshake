
package com.safeshake
import android.app.Activity
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import android.widget.*
import android.view.WindowManager
import android.net.wifi.p2p.*
import android.Manifest
import android.os.Build
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
class MainActivity:Activity(), SensorEventListener{
  private lateinit var sensorManager:SensorManager; private var accel:Sensor?=null
  private lateinit var statusText:TextView; private lateinit var startButton:Button; private lateinit var progressBar:ProgressBar
  private lateinit var sasLabel:TextView; private lateinit var sasCode:TextView; private lateinit var connectButton:Button
  private lateinit var messagesView:RecyclerView; private lateinit var inputText:EditText; private lateinit var sendButton:Button; private lateinit var fileButton:Button
  private var collecting=false; private val durationMs=3000L; private val sr=200f; private val cap=(durationMs*sr/1000f).toInt().coerceAtLeast(400)
  private val ax=FloatArray(cap); private val ay=FloatArray(cap); private val az=FloatArray(cap); private var idx=0; private var key32:ByteArray?=null
  private lateinit var p2p:WifiP2pController; private var session:EncryptedSession?=null; private val chat=ChatAdapter()
  private val pickFile=registerForActivityResult(ActivityResultContracts.GetContent()){ uri-> if(uri!=null && session!=null){ val name=uri.lastPathSegment?:"file.bin"; val bytes=contentResolver.openInputStream(uri)?.use{it.readBytes()}?:return@registerForActivityResult; val json=("{\"type\":\"file\",\"name\":\""+name+"\",\"size\":"+bytes.size+"}").toByteArray(); session!!.sendEncrypted(json); session!!.sendEncrypted(bytes); chat.add(ChatMessage("Fichier envoyé: "+name+" ("+bytes.size+" o)", true)); messagesView.scrollToPosition(chat.itemCount-1) } }
  private val reqPerm=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ _-> }
  override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); setContentView(R.layout.activity_main); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    statusText=findViewById(R.id.statusText); startButton=findViewById(R.id.startButton); progressBar=findViewById(R.id.progressBar); sasLabel=findViewById(R.id.sasLabel); sasCode=findViewById(R.id.sasCode); connectButton=findViewById(R.id.connectButton); messagesView=findViewById(R.id.messagesView); inputText=findViewById(R.id.inputText); sendButton=findViewById(R.id.sendButton); fileButton=findViewById(R.id.fileButton)
    messagesView.layoutManager=LinearLayoutManager(this); messagesView.adapter=chat
    sensorManager=getSystemService(Context.SENSOR_SERVICE) as SensorManager; accel=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    startButton.setOnClickListener{ beginCapture() }; connectButton.setOnClickListener{ startDiscovery() }; sendButton.setOnClickListener{ sendText() }; fileButton.setOnClickListener{ pickFile.launch("*/*") }
    p2p=WifiP2pController(this, onPeers={ peers-> showPeerDialog(peers) }, onConnected={ info-> onConnected(info) }, onDisconnected={ runOnUiThread{ toast("Déconnecté"); hideChat() } }); p2p.register()
    askPerms()
  }
  override fun onDestroy(){ super.onDestroy(); p2p.unregister(); session?.close() }
  private fun askPerms(){ val perms=mutableListOf<String>(); perms+=Manifest.permission.ACCESS_FINE_LOCATION; if(Build.VERSION.SDK_INT>=33) perms+=Manifest.permission.NEARBY_WIFI_DEVICES; reqPerm.launch(perms.toTypedArray()) }
  private fun beginCapture(){ hideChat(); collecting=true; idx=0; statusText.text=getString(R.string.status_collecting); progressBar.progress=0; accel?.also{ a-> sensorManager.registerListener(this,a,SensorManager.SENSOR_DELAY_FASTEST) }; progressBar.postDelayed({ finishCapture() }, durationMs) }
  private fun finishCapture(){ if(!collecting) return; collecting=false; sensorManager.unregisterListener(this); statusText.text=getString(R.string.status_processing)
    val res=ShakeProcessor.process(ax.copyOf(idx), ay.copyOf(idx), az.copyOf(idx), sr); if(res.entropyBits<120.0){ statusText.text=getString(R.string.entropy_low); return }
    key32=res.key; statusText.text=getString(R.string.status_done); sasLabel.visibility=View.VISIBLE; sasCode.visibility=View.VISIBLE; sasCode.text=res.sas; connectButton.visibility=View.VISIBLE
  }
  private fun startDiscovery(){ statusText.text=getString(R.string.status_ready_to_connect); p2p.discover{ reason-> toast(reason) } }
  private fun showPeerDialog(peers:List<WifiP2pDevice>){ if(peers.isEmpty()){ toast("Aucun appareil détecté."); return }; val names=peers.map{ d-> d.deviceName+" ("+d.deviceAddress+")" }.toTypedArray(); android.app.AlertDialog.Builder(this).setTitle(getString(R.string.peers_found)).setItems(names){ _,which-> p2p.connect(peers[which]){ reason-> toast(reason) } }.show() }
  private fun onConnected(info:WifiP2pInfo){ val k=key32?:return; if(info.isGroupOwner){ NetService.startServer(k){ sess-> onSessionReady(sess) } } else { val host=info.groupOwnerAddress?.hostAddress?:return; NetService.startClient(host,k){ sess-> onSessionReady(sess) } } }
  private fun onSessionReady(sess:EncryptedSession){ this.session=sess; runOnUiThread{ showChat(); toast("Session chiffrée établie.") }
    Thread{ try{ while(true){ val pt=sess.recvEncrypted(); val text=String(pt,Charsets.UTF_8); if(text.startsWith("{") && text.contains("\"type\":\"file\"")){ chat.add(ChatMessage("Métadonnées fichier: "+text,false)) } else { chat.add(ChatMessage(text,false)) }; runOnUiThread{ messagesView.scrollToPosition(chat.itemCount-1) } } }catch(e:Exception){ runOnUiThread{ toast("Connexion fermée"); hideChat() }; sess.close() } }.start()
  }
  private fun sendText(){ val s=inputText.text.toString().trim(); if(s.isEmpty() || session==null) return; session!!.sendEncrypted(s.toByteArray(Charsets.UTF_8)); chat.add(ChatMessage(s,true)); messagesView.scrollToPosition(chat.itemCount-1); inputText.setText("") }
  private fun showChat(){ messagesView.visibility=View.VISIBLE; inputText.visibility=View.VISIBLE; sendButton.visibility=View.VISIBLE; fileButton.visibility=View.VISIBLE }
  private fun hideChat(){ messagesView.visibility=View.GONE; inputText.visibility=View.GONE; sendButton.visibility=View.GONE; fileButton.visibility=View.GONE; connectButton.visibility=View.GONE; sasLabel.visibility=View.GONE; sasCode.visibility=View.GONE }
  private fun toast(s:String)=runOnUiThread{ android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show() }
  override fun onSensorChanged(e:SensorEvent){ if(!collecting) return; if(e.sensor.type!=Sensor.TYPE_ACCELEROMETER) return; if(idx>=ax.size) return; ax[idx]=e.values[0]; ay[idx]=e.values[1]; az[idx]=e.values[2]; idx++; val p=(idx*100/ax.size); progressBar.progress= if(p<=100) p else 100 }
  override fun onAccuracyChanged(sensor:Sensor?, accuracy:Int){ }
}
