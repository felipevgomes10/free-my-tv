# Line numbers in stack traces (the main thread's stalls are logged from the TV), with the source
# file names hidden; scripts/release.sh keeps each release's mapping to retrace them.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
