module io.matthewnelson.kmp.process {
    requires transitive io.matthewnelson.kmp.file;
    requires io.matthewnelson.immutable.collections;
    requires java.management;
    requires kotlinx.coroutines.core;

    exports io.matthewnelson.kmp.process;
}
