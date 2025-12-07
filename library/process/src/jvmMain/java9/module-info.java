module io.matthewnelson.kmp.process {
    requires io.matthewnelson.encoding.utf8;
    requires io.matthewnelson.immutable.collections;
    requires transitive io.matthewnelson.kmp.file;
    requires io.matthewnelson.kmp.file.async;
    requires java.management;
    requires kotlinx.coroutines.core;

    exports io.matthewnelson.kmp.process;
}
