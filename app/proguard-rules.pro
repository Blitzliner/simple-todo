# Minimal Todo – R8-Regeln
# Room, Compose, DataStore und Navigation bringen ihre eigenen Consumer-Regeln mit.
# Die App nutzt keine Reflection-basierte Serialisierung (eigener JSON-Codec),
# daher sind keine zusätzlichen keep-Regeln nötig.

# Stacktraces lesbar halten (optional, kostet nur wenige Bytes)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
