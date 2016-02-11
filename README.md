# AndroidPlayer
This player is a MediaPlayer abstraction for Android. It mimics but simplifies the Android [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer.html) through the usage of [RxJava](https://github.com/ReactiveX/RxJava) and [Kotlin](https://kotlinlang.org/).
It also supports setting a custom playback speed for Android `API >= 16` by using a custom implementation based on [Prestissimo](https://github.com/TheRealFalcon/Prestissimo) with heavy modifications.

**This library is still in beta phase and its API is a subject to changes.**

# Get started
You just create a new instance of the player and devine the type. Then you just prepare a file and play it.
```kotlin
val mediaPlayer = Player(Player.Type.CUSTOM, c, true)
mediaPlayer.prepare(File("/storage/sdcard/test.mp3"))
mediaPlayer.playing = true
```
For events you can simply subscribe to its `Observable`s:
```kotlin
mediaPlayer.completionObservable.subscribe { Log.i("Player", "Player completed") }
mediaPlayer.errorObservable.subscribe { Log.i("Player", "There was an error") }
```

# License
```
Copyright 2015 Paul Woitaschek

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
