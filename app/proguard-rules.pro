# Classes acessadas só por nome no manifesto já são mantidas pelo AGP.
-dontwarn org.tukaani.xz.**
-keep class org.tukaani.xz.** { *; }
-keepattributes SourceFile,LineNumberTable

# A tela do Tátão chama estes métodos pelo nome.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
