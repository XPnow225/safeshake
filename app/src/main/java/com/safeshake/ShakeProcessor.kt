
package com.safeshake
import kotlin.math.*
import java.security.MessageDigest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.digests.SHA256Digest
data class ShakeResult(val key: ByteArray, val sas: String, val entropyBits: Double)
object ShakeProcessor {
  fun process(ax: FloatArray, ay: FloatArray, az: FloatArray, sr: Float): ShakeResult {
    require(ax.size==ay.size && ay.size==az.size)
    val n=ax.size
    val hpX=highPass(ax,sr); val hpY=highPass(ay,sr); val hpZ=highPass(az,sr)
    val step=(0.025f*sr).toInt().coerceAtLeast(2)
    val bits=ArrayList<Int>(n/step*3)
    var energy=0.0; for (i in 1 until n){ val dx=hpX[i]-hpX[i-1]; val dy=hpY[i]-hpY[i-1]; val dz=hpZ[i]-hpZ[i-1]; energy+=(dx*dx+dy*dy+dz*dz).toDouble() }
    val rms=sqrt(energy/n.toDouble()).toFloat()
    val minEntropy= if (rms>0.5f) 160.0 else if (rms>0.3f) 120.0 else 80.0
    var i=1
    while(i<n){
      val j=(i+step).coerceAtMost(n-1)
      val sx=sign(hpX[j]-hpX[i]); val sy=sign(hpY[j]-hpY[i]); val sz=sign(hpZ[j]-hpZ[i])
      val ex=rmsWindow(hpX,i,j); val ey=rmsWindow(hpY,i,j); val ez=rmsWindow(hpZ,i,j)
      bits.add(if (sx>=0)1 else 0); bits.add(if (sy>=0)1 else 0); bits.add(if (sz>=0)1 else 0)
      bits.add(if (ex>=ey)1 else 0); bits.add(if (ey>=ez)1 else 0); bits.add(if (ex>=ez)1 else 0)
      i=j
    }
    val grouped=bits.chunked(3).map{grp-> if(grp.sum()>=2)1 else 0}
    val bb=ByteArray((grouped.size+7)/8)
    for (k in grouped.indices){ val bIndex=k/8; val bitPos=7-(k%8); bb[bIndex]=(bb[bIndex].toInt() or ((grouped[k] and 1) shl bitPos)).toByte() }
    val digest=MessageDigest.getInstance("SHA-256").digest(bb)
    val hkdf=HKDFBytesGenerator(SHA256Digest()); hkdf.init(HKDFParameters(digest,null,"SafeShake".toByteArray()))
    val out=ByteArray(32); hkdf.generateBytes(out,0,32)
    val a=((out[0].toInt() and 0xFF) shl 12) or ((out[1].toInt() and 0xFF) shl 4) or ((out[2].toInt() and 0xF0) shr 4)
    val sasStr=String.format("%06d", a % 1000000)
    return ShakeResult(out,sasStr,minEntropy)
  }
  private fun sign(v:Float)= if(v>=0f)1 else 0
  private fun rmsWindow(a:FloatArray,i:Int,j:Int):Float{ var s=0.0; var k=i; while(k<=j){ s+=a[k].toDouble()*a[k].toDouble(); k++ }; return kotlin.math.sqrt((s/(j-i+1).toDouble())).toFloat() }
  private fun highPass(x:FloatArray,sr:Float):FloatArray{ val y=FloatArray(x.size); val dt=1f/sr; val rc=1f/(2f*Math.PI.toFloat()*0.5f); val alpha=rc/(rc+dt); var prevY=0f; var prevX=x[0]; for(i in x.indices){ val yi=alpha*(prevY + x[i]-prevX); y[i]=yi; prevY=yi; prevX=x[i] } ; return y }
}
