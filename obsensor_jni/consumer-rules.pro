# Consumer rules: 消费此 library 的 app 模块将自动应用这些规则
-keep class com.orbbec.obsensor.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
