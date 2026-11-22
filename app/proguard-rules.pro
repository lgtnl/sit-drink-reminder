# R8 / ProGuard 规则
#
# 本项目的四大组件都在 AndroidManifest.xml 中声明，AGP 会自动为清单里的
# 组件生成 keep 规则。下面再显式写一遍，避免将来重命名包名或改用动态注册时
# 被优化掉，也方便二次开发时查看。

-keep class com.example.sitdrinkreminder.SitDrinkApp { *; }
-keep class com.example.sitdrinkreminder.alarm.ReminderReceiver { *; }
-keep class com.example.sitdrinkreminder.alarm.BootReceiver { *; }

# 枚举名被写入 SharedPreferences（例如 ScheduleMode.name），不能被裁剪。
-keepclassmembers enum com.example.sitdrinkreminder.data.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留行号，便于看崩溃栈（不保留源文件名，省一点体积）
-keepattributes LineNumberTable
