package com.juanczsilva.tv_j

class Channels {

    private val defaultList: MutableList<ListItem> = mutableListOf(
        ListItem(
            1,
            "Big Buck Bunny",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "{\"notM3u8\": true}"
        ),
        ListItem(
            5,
            "YouTube NASA",
            "https://www.youtube.com/@NASA/live"
        ),
        ListItem(
            8,
            "Elephants Dream",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            "{\"notM3u8\": true}"
        ),
        ListItem(
            9,
            "For Bigger Blazes",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            "{\"notM3u8\": true}"
        ),
        ListItem(
            45,
            "Twitch ShoutTV",
            "https://pwn.sh/tools/streamapi.py?url=twitch.tv/shouttv&quality=360p"
        ),
        ListItem(
            80,
            "Tears Of Steel",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            "{\"notM3u8\": true}"
        )
    )

    companion object {
        data class ListItem(var number: Int, var name: String, var url: String, var opts: String? = "")
        var List: MutableList<ListItem> = mutableListOf<ListItem>().apply { addAll(Channels().defaultList) }
        fun reset() {
            List.clear()
            List.addAll(Channels().defaultList)
        }
    }
}