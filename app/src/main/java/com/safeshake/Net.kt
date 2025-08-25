
package com.safeshake
import android.net.wifi.p2p.*
import android.content.*
import android.net.wifi.p2p.WifiP2pManager.*
import java.net.ServerSocket
import java.net.Socket
import java.io.*
import java.security.MessageDigest
import kotlin.concurrent.thread
class WifiP2pController(private val ctx:Context, private val onPeers:(List<WifiP2pDevice>)->Unit, private val onConnected:(WifiP2pInfo)->Unit, private val onDisconnected:()->Unit){
  private val mgr=ctx.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
  private val ch=mgr.initialize(ctx, ctx.mainLooper, null)
  private val br=object:BroadcastReceiver(){
    override fun onReceive(c:Context?,intent:Intent?){
      if(intent==null) return
      when(intent.action){
        WIFI_P2P_PEERS_CHANGED_ACTION -> { mgr.requestPeers(ch){list-> onPeers(list.deviceList.toList()) } }
        WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
          val net=intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, android.net.NetworkInfo::class.java)
          if(net?.isConnected==true){ mgr.requestConnectionInfo(ch){info-> onConnected(info)} } else { onDisconnected() }
        }
      }
    }
  }
  fun register(){ val f=IntentFilter().apply{ addAction(WIFI_P2P_PEERS_CHANGED_ACTION); addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION) }; ctx.registerReceiver(br,f) }
  fun unregister(){ try{ ctx.unregisterReceiver(br) }catch(_:Exception){} }
  fun discover(onFail:(String)->Unit){ mgr.discoverPeers(ch, object:ActionListener{ override fun onSuccess(){} override fun onFailure(r:Int){ onFail("discover fail "+r) } }) }
  fun connect(d:WifiP2pDevice,onFail:(String)->Unit){ val cfg=WifiP2pConfig().apply{ deviceAddress=d.deviceAddress; wps.setup=WpsInfo.PBC }; mgr.connect(ch,cfg,object:ActionListener{ override fun onSuccess(){} override fun onFailure(r:Int){ onFail("connect fail "+r) } }) }
}
class EncryptedSession(private val socket:Socket, private val key32:ByteArray):Closeable{
  private val input=DataInputStream(BufferedInputStream(socket.getInputStream()))
  private val output=DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
  fun handshake():Boolean{
    val rndA=ByteArray(16).also{ java.security.SecureRandom().nextBytes(it) }
    output.writeInt(16); output.write(rndA); output.flush()
    val lenB=input.readInt(); if(lenB!=16) return false
    val rndB=ByteArray(16); input.readFully(rndB)
    val macA=proof(key32, rndA, rndB); output.writeInt(macA.size); output.write(macA); output.flush()
    val ml=input.readInt(); val macB=ByteArray(ml); input.readFully(macB)
    val expect=proof(key32, rndB, rndA); return macB.contentEquals(expect)
  }
  private fun proof(k:ByteArray,a:ByteArray,b:ByteArray):ByteArray{
    val h=MessageDigest.getInstance("SHA-256"); h.update("proof".toByteArray()); h.update(k); h.update(a); h.update(b); return h.digest()
  }
  fun sendEncrypted(pt:ByteArray){
    val nonce=Crypto.randomNonce12(); val ct=Crypto.seal(key32,nonce,null,pt)
    output.writeInt(nonce.size+ct.size); output.write(nonce); output.write(ct); output.flush()
  }
  fun recvEncrypted():ByteArray{
    val L=input.readInt(); val buf=ByteArray(L); input.readFully(buf); val nonce=buf.copyOfRange(0,12); val ct=buf.copyOfRange(12,buf.size); return Crypto.open(key32,nonce,null,ct)
  }
  override fun close(){ try{input.close()}catch(_:Exception){}; try{output.close()}catch(_:Exception){}; try{socket.close()}catch(_:Exception){} }
}
object NetService{
  const val PORT=8988
  fun startServer(key32:ByteArray, onReady:(EncryptedSession)->Unit){ thread{ val ss=ServerSocket(PORT); val s=ss.accept(); ss.close(); val sess=EncryptedSession(s,key32); if(sess.handshake()) onReady(sess) else sess.close() } }
  fun startClient(addr:String, key32:ByteArray, onReady:(EncryptedSession)->Unit){ thread{ val s=java.net.Socket(addr,PORT); val sess=EncryptedSession(s,key32); if(sess.handshake()) onReady(sess) else sess.close() } }
}
