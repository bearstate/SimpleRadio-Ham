package com.codinglitch.simpleradio.core.registry;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.FrequencingType;

public class SimpleRadioFrequencing {
    public static FrequencingType WALKIE_TALKIE = FrequencingRegistry.register(
            CommonSimpleRadio.id("walkie_talkie"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.walkie_talkie)
    );
    public static FrequencingType TRANSCEIVER = FrequencingRegistry.register(
            CommonSimpleRadio.id("transceiver"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.transceiver)
    );

    public static FrequencingType TRANSMITTER = FrequencingRegistry.register(
            CommonSimpleRadio.id("transmitter"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.transmitter)
    );
    public static FrequencingType RECEIVER = FrequencingRegistry.register(
            CommonSimpleRadio.id("receiver"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.receiver)
    );

    public static FrequencingType RADIO = FrequencingRegistry.register(
            CommonSimpleRadio.id("radio"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.radio)
    );

    public static FrequencingType STATION = FrequencingRegistry.register(
            CommonSimpleRadio.id("station"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.station)
    );
    public static FrequencingType REPEATER = FrequencingRegistry.register(
            CommonSimpleRadio.id("repeater"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.repeater)
    );

    public static FrequencingType SCANNER = FrequencingRegistry.register(
            CommonSimpleRadio.id("scanner"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.scanner)
    );

    public static FrequencingType FOX = FrequencingRegistry.register(
            CommonSimpleRadio.id("fox"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.fox)
    );
    public static FrequencingType DIRECTION_FINDER = FrequencingRegistry.register(
            CommonSimpleRadio.id("direction_finder"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.direction_finder)
    );

    public static FrequencingType WIRE = FrequencingRegistry.register(
            CommonSimpleRadio.id("wire"),
            FrequencingRegistry.fromConfig(SimpleRadioLibrary.SERVER_CONFIG.wire)
    );

    static {
        // a handheld's short antenna is hopeless on HF
        com.codinglitch.simpleradio.radio.propagation.RadioLink.registerHfAntennaLoss(WALKIE_TALKIE, () -> SimpleRadioLibrary.SERVER_CONFIG.propagation.handheldHfLossDb);
        com.codinglitch.simpleradio.radio.propagation.RadioLink.registerHfAntennaLoss(TRANSCEIVER, () -> SimpleRadioLibrary.SERVER_CONFIG.propagation.handheldHfLossDb);
    }

    public static void load() {}
}
