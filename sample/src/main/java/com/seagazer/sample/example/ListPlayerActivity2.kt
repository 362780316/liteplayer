package com.seagazer.sample.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.seagazer.liteplayer.LitePlayerView
import com.seagazer.liteplayer.bean.DataSource
import com.seagazer.liteplayer.helper.MediaLogger
import com.seagazer.liteplayer.list.ListItemChangedListener
import com.seagazer.liteplayer.list.ListPlayer2
import com.seagazer.liteplayer.widget.LiteGestureController
import com.seagazer.liteplayer.widget.LiteMediaController
import com.seagazer.sample.ConfigHolder
import com.seagazer.sample.R
import com.seagazer.sample.cache.VideoCacheHelper
import com.seagazer.sample.data.DataProvider
import com.seagazer.sample.navigationTo
import com.seagazer.sample.showConfigInfo
import com.seagazer.sample.widget.LoadingOverlay
import kotlinx.android.synthetic.main.activity_list_player2.*

/**
 * Example for use in ListView.
 */
class ListPlayerActivity2 : AppCompatActivity() {
    private lateinit var listPlayer: ListPlayer2
    private var isAutoPlay = true
    private var lastPlayerHolder: ListAdapter.VideoHolder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_player2)
        showConfigInfo()

        val listAdapter = ListAdapter()
        list_view.adapter = listAdapter
        list_view.setOnItemClickListener { _, _, position, _ ->
            if (!isAutoPlay) {
                listPlayer.onItemClick(position)
            }
        }

        listPlayer = ListPlayer2(LitePlayerView(this)).apply {
            displayProgress(true)
            setProgressColor(resources.getColor(R.color.colorAccent), resources.getColor(R.color.colorPrimaryDark))
            attachOverlay(LoadingOverlay(this@ListPlayerActivity2))
            attachMediaController(LiteMediaController(this@ListPlayerActivity2))
            attachGestureController(LiteGestureController(this@ListPlayerActivity2).apply {
                supportVolume = false
                supportBrightness = false
            })
            setRenderType(ConfigHolder.renderType)
            setPlayerType(ConfigHolder.playerType)
            supportHistory = true
            // sample to show and hide video cover
            // onDetachItemView always call before onAttachItemView
            listItemChangedListener = object : ListItemChangedListener {
                override fun onDetachItemView(oldPosition: Int) {
                    MediaLogger.e("detach item: $oldPosition")
                    lastPlayerHolder?.let {
                        it.videoPoster.visibility = View.VISIBLE
                    }
                }

                override fun onAttachItemView(newPosition: Int) {
                    MediaLogger.e("attach item: $newPosition")
                    lastPlayerHolder?.let {
                        it.videoPoster.visibility = View.INVISIBLE
                    }
                }
            }
        }
        val videoScrollListener = object : ListPlayer2.VideoListScrollListener {

            override fun getVideoContainer(childIndex: Int, position: Int): ViewGroup? {
                val itemView = list_view.getChildAt(childIndex)
                return if (itemView != null && itemView.tag != null) {
                    val videoHolder = itemView.tag as ListAdapter.VideoHolder
                    lastPlayerHolder = videoHolder
                    videoHolder.videoContainer
                } else {
                    null
                }
            }

            override fun getVideoDataSource(position: Int): DataSource? {
                return DataSource(VideoCacheHelper.url(listAdapter.getItem(position)))
            }
        }
        listPlayer.attachToListView(list_view, true, videoScrollListener)

        play_mode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.auto_play -> {
                    listPlayer.setAutoPlayMode(true)
                    isAutoPlay = true
                }
                R.id.click_play -> {
                    listPlayer.setAutoPlayMode(false)
                    isAutoPlay = false
                }
            }
        }

        sensor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                listPlayer.setAutoSensorEnable(true)
            } else {
                listPlayer.setAutoSensorEnable(false)
            }
        }
    }

    inner class ListAdapter : BaseAdapter() {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val itemView: View
            val holder: VideoHolder
            if (convertView == null) {
                itemView = LayoutInflater.from(parent!!.context).inflate(R.layout.item_view_video_list, parent, false)
                holder = VideoHolder()
                holder.videoTitle = itemView.findViewById(R.id.video_index)
                holder.videoPoster = itemView.findViewById(R.id.video_poster)
                holder.videoContainer = itemView.findViewById(R.id.video_container)
                itemView.tag = holder
            } else {
                itemView = convertView
                holder = convertView.tag as VideoHolder
            }
            holder.run {
                videoPoster.setBackgroundResource(R.drawable.timg)
                videoTitle.text = position.toString()
            }
            return itemView
        }

        override fun getItem(position: Int) = DataProvider.urls[position % 2]

        override fun getItemId(position: Int) = position.toLong()

        override fun getCount() = DataProvider.urls.size * 10

        inner class VideoHolder {
            lateinit var videoTitle: TextView
            lateinit var videoPoster: ImageView
            lateinit var videoContainer: FrameLayout

        }

    }

    fun jumpToActivity(view: View) {
        navigationTo(EmptyActivity::class.java)
    }

    override fun onBackPressed() {
        if (listPlayer.isFullScreen()) {
            listPlayer.setFullScreenMode(false)
        } else {
            super.onBackPressed()
        }
    }
}
