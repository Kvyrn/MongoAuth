package io.github.shroompye.mongoauth.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.oroarmor.config.ConfigItem;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

public class FormattingConfigItem extends ConfigItem<Formatting> {
    public FormattingConfigItem(String name, Formatting defaultValue, String details) {
        super(name, defaultValue, details);
    }

    @SuppressWarnings("unused")
    public FormattingConfigItem(String name, Formatting defaultValue, String details, @Nullable Consumer<ConfigItem<Formatting>> onChange) {
        super(name, defaultValue, details, onChange);
    }

    @Override
    public void fromJson(JsonElement element) {
        this.value = Formatting.valueOf(element.getAsString().toUpperCase(Locale.ROOT));
    }

    @Override
    public void toJson(JsonObject object) {
        object.addProperty(this.name, this.value.getName());
    }

    @Override
    public <T1> boolean isValidType(Class<T1> clazz) {
        return clazz == Formatting.class;
    }

    @Override
    public String getCommandValue() {
        return this.value.getName();
    }
}
