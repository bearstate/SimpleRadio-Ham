package com.codinglitch.simpleradio.ham;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * Recipe condition on a boolean of the server config, e.g. {"type": "simpleradio:config_enabled", "path": "aprs.enabled"}.
 */
public class ConfigEnabledCondition implements ICondition {
    private static final ResourceLocation NAME = CommonSimpleRadio.id("config_enabled");
    private final String path;

    public ConfigEnabledCondition(String path) {
        this.path = path;
    }

    @Override
    public ResourceLocation getID() {
        return NAME;
    }

    @Override
    public boolean test(IContext context) {
        Object value = CommonSimpleRadio.getConfigFrom(SimpleRadioLibrary.SERVER_CONFIG, path).orElse(Boolean.TRUE);
        return !(value instanceof Boolean enabled) || enabled;
    }

    @Override
    public String toString() {
        return "config_enabled(\"" + path + "\")";
    }

    public static class Serializer implements IConditionSerializer<ConfigEnabledCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, ConfigEnabledCondition value) {
            json.addProperty("path", value.path);
        }

        @Override
        public ConfigEnabledCondition read(JsonObject json) {
            return new ConfigEnabledCondition(GsonHelper.getAsString(json, "path"));
        }

        @Override
        public ResourceLocation getID() {
            return NAME;
        }
    }
}
