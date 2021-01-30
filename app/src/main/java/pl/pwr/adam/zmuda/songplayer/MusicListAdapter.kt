package pl.pwr.adam.zmuda.songplayer

import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicListAdapter(private val musicList: MutableList<MediaBrowserCompat.MediaItem>, private val onClick: (MediaBrowserCompat.MediaItem) -> Unit) : RecyclerView.Adapter<MusicListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTV : TextView = view.findViewById(R.id.musicNameTV)
    }

    override fun getItemCount(): Int {
        return musicList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.music_list_item, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.nameTV.text = musicList[position].description.title
        holder.nameTV.setOnClickListener { onClick( musicList[position]) }
    }
}