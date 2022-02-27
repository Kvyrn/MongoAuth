package io.github.shroompye.mongoauth.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigHelper {
    public static final ObjectConverter OBJECT_CONVERTER = new ObjectConverter();

    public static <C> C load(Path path, Supplier<C> objectFactory) {
        CommentedFileConfig config = createConfig(path);
        mkdirParent(path);
        config.load();
        tryFixConfig(config, OBJECT_CONVERTER.toConfig(objectFactory.get(), Config::inMemory));
        C object = OBJECT_CONVERTER.toObject(config, objectFactory);
        config.close();
        return object;
    }

    public static <C> void save(Path path, C object, @Nullable Consumer<CommentedFileConfig> commentApplier) {
        CommentedFileConfig config = OBJECT_CONVERTER.toConfig(object, () -> createConfig(path));
        mkdirParent(path);
        if (commentApplier != null)
            commentApplier.accept(config);
        config.save();
        config.close();
    }

    private static void mkdirParent(Path path) {
        File parent = path.getParent().toFile();
        if (!parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
    }

    public static CommentedFileConfig createConfig(Path path) {
        return CommentedFileConfig.builder(path)
                .preserveInsertionOrder()
                .build();
    }

    private static void tryFixConfig(CommentedFileConfig config, Config defConfig) {
        fixInternal(config, defConfig);
    }

    private static void fixInternal(Config input, Config def) {
        Map<String, Object> map = def.valueMap();
        for (String entry : map.keySet()) {
            Object obj = map.get(entry);
            if (!input.contains(entry)) {
                input.set(entry, obj);
            }
            if (obj instanceof Config) {
                fixInternal(input.get(entry), def.get(entry));
            }
        }
    }
}
