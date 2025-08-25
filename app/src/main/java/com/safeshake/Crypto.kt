
package com.safeshake
import org.bouncycastle.crypto.engines.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom
object Crypto{
  fun seal(key:ByteArray,nonce:ByteArray,ad:ByteArray?,pt:ByteArray):ByteArray{
    val c=ChaCha20Poly1305(); c.init(true,AEADParameters(KeyParameter(key),128,nonce,ad))
    val out=ByteArray(c.getOutputSize(pt.size)); val l=c.processBytes(pt,0,pt.size,out,0); val l2=c.doFinal(out,l); return if(l+l2==out.size) out else out.copyOf(l+l2)
  }
  fun open(key:ByteArray,nonce:ByteArray,ad:ByteArray?,ct:ByteArray):ByteArray{
    val c=ChaCha20Poly1305(); c.init(false,AEADParameters(KeyParameter(key),128,nonce,ad))
    val out=ByteArray(c.getOutputSize(ct.size)); val l=c.processBytes(ct,0,ct.size,out,0); val l2=c.doFinal(out,l); return if(l+l2==out.size) out else out.copyOf(l+l2)
  }
  fun randomNonce12():ByteArray{ val r=SecureRandom(); val n=ByteArray(12); r.nextBytes(n); return n }
}
