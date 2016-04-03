# AndroidPlayer
This player is a MediaPlayer abstraction for Android. It mimics but simplifies the Android [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer.html) through the usage of [RxJava](https://github.com/ReactiveX/RxJava) and [Kotlin](https://kotlinlang.org/).
It also supports setting a custom playback speed for Android `API >= 16` by using a custom implementation based on [Prestissimo](https://github.com/TheRealFalcon/Prestissimo) with heavy modifications.

**This library is still in beta phase and its API is a subject to changes.**

# Get started
The player is defined through a simple interface called `MediaPlayer` which works like the Android MediaPlayer. There is one little difference: Instead of `setDataSource(String)` and `prepare()` it simplifies that by skipping that state so you call `prepare(File)` directly.
```kotlin
val player: MediaPlayer = SpeedPlayer(context) // or AndroidPlayer(context) if <= API 16
player.prepare(File("/storage/sdcard/test.mp3"))
player.start()
```
For events you can simply subscribe to its `Observable`s:
```kotlin
player.onCompletion.subscribe { Log.i("Player", "Player completed") }
player.onError.subscribe { Log.i("Player", "There was an error") }
player.onPrepared.subscribe { Log.i("Player", "Player prepared!") }
```

# Installation
build.gradle:
```gradle
dependencies {
    compile 'com.github.PaulWoitaschek:AndroidPlayer:0.010'
}
```
Top gradle:
```gradle
allprojects {
    repositories {
        ...
	maven { url "https://jitpack.io" }
    }
}
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
