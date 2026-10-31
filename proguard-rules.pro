# Lightweight ProGuard rules for Lazarus: rename only. No optimisation and no shrinking,
# so Fabric's mixin application and reflective entrypoint loading are never disturbed; this
# only scrambles the names of Lazarus's own classes, methods and fields. injars, outjars and
# libraryjars are added by the proguardJar Gradle task.

-dontoptimize
-dontshrink
-dontwarn **
-ignorewarnings
-keepattributes *Annotation*,Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations,AnnotationDefault,Record,InnerClasses,EnclosingMethod,PermittedSubclasses

# The Fabric client entrypoint is named in fabric.mod.json and loaded by name.
-keep class dev.lauchy.lazarus.Lazarus { *; }

# Mixin classes are listed by name in lazarus.mixins.json and applied at class load, so
# their names and members must survive untouched.
-keep class dev.lauchy.lazarus.mixin.** { *; }

# Config save/load serialises enum constants with Enum.name(); keep every enum's members so
# those names stay stable across builds and saved settings keep working.
-keepclassmembers enum ** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
