package com.example.simple_music_player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var songAdapter: SongAdapter
    private var songs = mutableListOf<Song>()
    private var currentSongIndex = 0
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var seekBarProgress: SeekBar
    private lateinit var seekBarVolume: SeekBar
    private lateinit var playPauseButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()

        // Initialize songAdapter early
        songAdapter = SongAdapter(songs) { position ->
            currentSongIndex = position
            playSong()
        }

        // Setup RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.song_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = songAdapter

        // Request storage permission and load songs
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 1)
        } else {
            loadSongs()
        }

        // Setup control buttons
        playPauseButton = findViewById(R.id.btn_play_pause)
        playPauseButton.setOnClickListener {
            if (isPlaying) pauseSong() else playSong()
        }
        findViewById<ImageButton>(R.id.btn_next).setOnClickListener { playNext() }
        findViewById<ImageButton>(R.id.btn_previous).setOnClickListener { playPrevious() }

        // Setup seek bars
        seekBarProgress = findViewById(R.id.seekbar_progress)
        seekBarVolume = findViewById(R.id.seekbar_volume)

        // Progress SeekBar
        seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer.seekTo((progress * exoPlayer.duration / 100).toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Volume SeekBar
        seekBarVolume.max = 100
        seekBarVolume.progress = 100 // Default to max volume
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer.volume = progress / 100f
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Update progress SeekBar periodically
        updateSeekBar()
    }

    private fun loadSongs() {
        songs.clear()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA
        )
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + "!= 0",
            null,
            MediaStore.Audio.Media.TITLE
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val uri = cursor.getString(dataColumn)
                songs.add(Song(id, title, uri))
            }
        }
        songAdapter.notifyDataSetChanged()
        updateSongTitle()
    }

    private fun playSong() {
        if (songs.isEmpty()) return
        exoPlayer.apply {
            setMediaItem(MediaItem.fromUri(songs[currentSongIndex].uri))
            prepare()
            play()
        }
        isPlaying = true
        playPauseButton.setImageResource(R.drawable.ic_pause)
        updateSongTitle()
        seekBarProgress.max = 100
        updateSeekBar()
    }

    private fun pauseSong() {
        exoPlayer.pause()
        isPlaying = false
        playPauseButton.setImageResource(R.drawable.ic_play)
    }

    private fun playNext() {
        if (songs.isEmpty()) return
        currentSongIndex = (currentSongIndex + 1) % songs.size
        playSong()
    }

    private fun playPrevious() {
        if (songs.isEmpty()) return
        currentSongIndex = if (currentSongIndex - 1 < 0) songs.size - 1 else currentSongIndex - 1
        playSong()
    }

    private fun updateSongTitle() {
        val titleTextView = findViewById<TextView>(R.id.current_song)
        titleTextView.text = if (songs.isEmpty()) "No Song Playing" else songs[currentSongIndex].title
    }

    private fun updateSeekBar() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (exoPlayer.isPlaying && exoPlayer.duration > 0) {
                    val progress = (exoPlayer.currentPosition * 100 / exoPlayer.duration).toInt()
                    seekBarProgress.progress = progress
                } else {
                    seekBarProgress.progress = 0
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        }
    }
}