# Hidden-API stubs (IPackageManager, IPackageInstaller, ...) are resolved at
# runtime against the framework through Shizuku binders; keep them intact.
-keep public class android.content.pm.** { *; }

# WorkManager's Room (2.6.x) ships `-keep class * extends RoomDatabase` without
# a member spec, so R8 full mode strips WorkDatabase_Impl's no-arg constructor,
# which Room instantiates reflectively -> crash in InitializationProvider.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Shizuku starts the tools page's shell service in a process of its own and loads
# the class by name from the APK, so renaming it would leave every tool unable to
# run its script.
-keep class io.github.samolego.canta.core.shizuku.ShellUserService { *; }

# Dhizuku does the same with its own user service: Dhizuku instantiates
# CantaDhizukuService by name inside its process, so the class and its
# (Context) constructor have to survive minification.
-keep class io.github.samolego.canta.core.dhizuku.CantaDhizukuService { *; }

