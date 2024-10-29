package com.example.tv_j

class Channels {
    companion object {
        data class ListItem(var number: Int, var name: String, var url: String, var opts: String? = "")
        val List: MutableList<ListItem> = mutableListOf(
            // TEST
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
                "Twitch RiotGames",
                "https://pwn.sh/tools/streamapi.py?url=twitch.tv/riotgames&quality=360p"
            ),
            ListItem(
                80,
                "Tears Of Steel",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                "{\"notM3u8\": true}"
            ),
            // REAL
//            ListItem(
//                9,
//                "America TV",
//                "https://raw.githubusercontent.com/MachineSystems/archived_m3u8/main/america_hls.m3u8"
//            ),
//            ListItem(
//                10,
//                "Telefe",
//                "https://telefe.com/Api/Videos/GetSourceUrl/694564/0/HLS?.m3u8"
//            ),
//            ListItem(
//                11,
//                "TV Publica",
//                "https://www.youtube.com/@TVPublicaArgentina/live"
//            ),
//            ListItem(
//                12,
//                "El Trece",
//                "https://live-01-02-eltrece.vodgc.net/eltrecetv/index.m3u8"
//            ),
//            ListItem(
//                13,
//                "El Nueve",
//                "https://pwn.sh/tools/streamapi.py?url=twitch.tv/elnueve_ok&quality=360p"
//            ),
//            ListItem(
//                14,
//                "TN",
//                "https://www.youtube.com/todonoticias/live"
//            ),
//            ListItem(
//                15,
//                "A24",
//                "https://www.youtube.com/@A24com/live"
//            ),
//            ListItem(
//                16,
//                "C5N",
//                "https://www.youtube.com/@c5n/live"
//            ),
//            ListItem(
//                17,
//                "Cronica TV",
//                "https://www.youtube.com/@cronicatv/live"
//            ),
//            ListItem(
//                18,
//                "Canal 26",
//                "https://www.youtube.com/@canal26/live"
//            ),
//            ListItem(
//                19,
//                "La Nacion",
//                "https://www.youtube.com/@lanacion/live"
//            ),
//            ListItem(
//                20,
//                "Ciudad Magazine",
//                "https://livetrx01.vodgc.net/live-01-07-ciudad.vodgc.net/index.m3u8"
//            ),
//            ListItem(
//                21,
//                "Garage TV",
//                "https://stream1.sersat.com/hls/garagetv.m3u8"
//            ),
//            ListItem(
//                90,
//                "E-Sports",
//                "https://stitcher-ipv4.pluto.tv/v1/stitch/embed/hls/channel/5ff3934600d4c7000733ff49/master.m3u8?deviceType=samsung-tvplus&deviceMake=samsung&deviceModel=samsung&deviceVersion=unknown&appVersion=unknown&deviceLat=0&deviceLon=0&deviceDNT=%7BTARGETOPT%7D&deviceId=%7BPSID%7D&advertisingId=%7BPSID%7D&us_privacy=1YNY&samsung_app_domain=%7BAPP_DOMAIN%7D&samsung_app_name=%7BAPP_NAME%7D&profileLimit=&profileFloor=&embedPartner=samsung-tvplus"
//            ),
//            ListItem(
//                91,
//                "Fail Army",
//                "https://stitcher-ipv4.pluto.tv/v1/stitch/embed/hls/channel/5ebaccf1734aaf0007142c86/master.m3u8?deviceType=samsung-tvplus&deviceMake=samsung&deviceModel=samsung&deviceVersion=unknown&appVersion=unknown&deviceLat=0&deviceLon=0&deviceDNT=%7BTARGETOPT%7D&deviceId=%7BPSID%7D&advertisingId=%7BPSID%7D&us_privacy=1YNY&samsung_app_domain=%7BAPP_DOMAIN%7D&samsung_app_name=%7BAPP_NAME%7D&profileLimit=&profileFloor=&embedPartner=samsung-tvplus"
//            ),
//            ListItem(
//                92,
//                "MTV Conciertos",
//                "https://stitcher-ipv4.pluto.tv/v1/stitch/embed/hls/channel/5f85ca40eda1b10007b967cd/master.m3u8?deviceType=samsung-tvplus&deviceMake=samsung&deviceModel=samsung&deviceVersion=unknown&appVersion=unknown&deviceLat=0&deviceLon=0&deviceDNT=%7BTARGETOPT%7D&deviceId=%7BPSID%7D&advertisingId=%7BPSID%7D&us_privacy=1YNY&samsung_app_domain=%7BAPP_DOMAIN%7D&samsung_app_name=%7BAPP_NAME%7D&profileLimit=&profileFloor=&embedPartner=samsung-tvplus"
//            ),
//            ListItem(
//                93,
//                "Urbana Play 104",
//                "https://www.youtube.com/@UrbanaPlayFM/live"
//            )
        )
    }
}