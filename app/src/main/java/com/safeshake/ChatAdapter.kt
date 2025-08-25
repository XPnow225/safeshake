package com.safeshake
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
class ChatAdapter:RecyclerView.Adapter<ChatAdapter.VH>(){
 private val items=ArrayList<ChatMessage>()
 class VH(v:View):RecyclerView.ViewHolder(v){ val tv:TextView=v.findViewById(android.R.id.text1) }
 override fun onCreateViewHolder(p:ViewGroup,vt:Int):VH{ val v=android.widget.TextView(p.context).apply{ id=android.R.id.text1; textSize=16f; setPadding(12,8,12,8) }; return VH(v) }
 override fun getItemCount()=items.size
 override fun onBindViewHolder(h:VH,pos:Int){ val m=items[pos]; h.tv.text= if(m.mine) "Moi: ${m.text}" else m.text; h.tv.textAlignment= if(m.mine) View.TEXT_ALIGNMENT_TEXT_END else View.TEXT_ALIGNMENT_TEXT_START }
 fun add(m:ChatMessage){ items.add(m); notifyItemInserted(items.size-1) }
}
