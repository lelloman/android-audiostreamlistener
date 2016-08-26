# android-audiostreamlistener

a simple client to play a pcm network stream, works in conjunction with [this simple server](https://github.com/lelloman/java-audiostreamserver)

the server will stream uncompressed pcm frames via udp without any encryption, it has a very low latency but if the network is busy the sound will jump a lot.
open with Android Studio to build (2.1.3 atm)

