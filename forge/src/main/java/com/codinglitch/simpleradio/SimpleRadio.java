package com.codinglitch.simpleradio;

import com.codinglitch.simpleradio.core.ForgeLoader;
import com.codinglitch.simpleradio.ham.HamContent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CommonSimpleRadio.ID)
public class SimpleRadio {

    public SimpleRadio() {
        CommonSimpleRadio.initialize();
        ForgeLoader.load();

        HamContent.init(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
