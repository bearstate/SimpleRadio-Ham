package com.codinglitch.simpleradio;

import com.codinglitch.lexiconfig.LexiconfigApi;
import com.codinglitch.lexiconfig.annotations.Lexicon;
import com.codinglitch.lexiconfig.annotations.LexiconEntry;
import com.codinglitch.lexiconfig.annotations.LexiconPage;
import com.codinglitch.lexiconfig.classes.LexiconData;
import com.codinglitch.lexiconfig.classes.LexiconPageData;
import com.codinglitch.simpleradio.central.ConfigHolder;
import com.codinglitch.simpleradio.compat.CompatibilityInstance;

@Lexicon(name = CommonSimpleRadio.ID+"-server", location = LexiconfigApi.Location.SERVER)
public class SimpleRadioServerConfig extends LexiconData {
    @LexiconPage(comment = "These are the configurations for the wires.")
    public Wire wire = new Wire();

    @LexiconPage(comment = "These are the configurations for the transceiver item.")
    public Transceiver transceiver = new Transceiver();

    @LexiconPage(comment = "These are the configurations for the walkie talkie item.")
    public WalkieTalkie walkie_talkie = new WalkieTalkie();

    @LexiconPage(comment = "These are the configurations for the receiver block.")
    public Receiver receiver = new Receiver();

    @LexiconPage(comment = "These are the configurations for the transmitter block.")
    public Transmitter transmitter = new Transmitter();

    @LexiconPage(comment = "These are the configurations for the radio block.")
    public Radio radio = new Radio();

    @LexiconPage(comment = "These are the configurations for the microphone block.")
    public Microphone microphone = new Microphone();

    @LexiconPage(comment = "These are the configurations for the speaker block.")
    public Speaker speaker = new Speaker();

    @LexiconPage(comment = "These are the configurations for the antenna block.")
    public Antenna antenna = new Antenna();

    @LexiconPage(comment = "These are the general configurations for frequencies.")
    public Frequency frequency = new Frequency();

    @LexiconPage(comment = "These are the configurations for ALL types of routers (such as Receivers, Transmitters, Listeners, and Speakers).")
    public Router router = new Router();

    @LexiconPage(comment = "These are the general configurations for compatibilities.")
    public Compatibilities compatibilities = new Compatibilities();

    @LexiconPage(comment = "These are the configurations for handheld radio batteries (transceiver, walkie talkie, spuddie talkie).")
    public Handheld handheld = new Handheld();

    @LexiconPage(comment = "These are the configurations for the fixed radio station block.")
    public Station station = new Station();

    @LexiconPage(comment = "These are the configurations for the repeater block.")
    public Repeater repeater = new Repeater();

    @LexiconPage(comment = "These are the configurations for the yagi antenna block.")
    public Yagi yagi = new Yagi();

    @LexiconPage(comment = "These are the configurations for the CW (morse) straight key block.")
    public CwKey cw_key = new CwKey();

    @LexiconPage(comment = "These are the configurations for the handheld charging station block.")
    public Charger charger = new Charger();

    @LexiconPage(comment = "These are the configurations for electrical power (Electrodynamics / Forge Energy) of all radio equipment.")
    public Power power = new Power();

    @LexiconPage(comment = "These are the configurations for radio frequency interference cutting power in nearby uninsulated Electrodynamics wires.")
    public Interference interference = new Interference();

    @LexiconPage(comment = "These are the general configurations for signal propagation: terrain, radio horizon, buildings and caves.")
    public Propagation propagation = new Propagation();

    @LexiconPage(comment = "These are the configurations for HF sky-wave propagation (ionosphere, solar conditions).")
    public Ionosphere ionosphere = new Ionosphere();

    @LexiconPage(comment = "These are the configurations for lightning striking tall antennas.")
    public Lightning lightning = new Lightning();

    @LexiconPage(comment = "These are the configurations for man-made noise (QRM) from electric machines.")
    public Qrm qrm = new Qrm();

    @LexiconPage(comment = "These are the configurations for antenna matching (SWR), the antenna tuner and power amplifier heat.")
    public SwrPage swr = new SwrPage();

    @LexiconPage(comment = "These are the configurations for the band plan, amateur licences and call signs.")
    public BandPlanPage band_plan = new BandPlanPage();

    @LexiconPage(comment = "These are the configurations for the fox hunt (ARDF) transmitter.")
    public Fox fox = new Fox();

    @LexiconPage(comment = "These are the configurations for the direction finding (fox hunt) receiver.")
    public DirectionFinder direction_finder = new DirectionFinder();

    @LexiconPage(comment = "These are the configurations for APRS (automatic position reports).")
    public Aprs aprs = new Aprs();

    @LexiconPage(comment = "These are the configurations for the operator's manual.")
    public Guide guide = new Guide();

    @LexiconPage(comment = "These are the configurations for the mobile (vehicle) radio.")
    public Mobile mobile = new Mobile();

    @LexiconPage(comment = "These are the configurations for the SSTV (slow scan television) terminal.")
    public Sstv sstv = new Sstv();

    @LexiconPage(comment = "These are the configurations for the frequency scanner block.")
    public Scanner scanner = new Scanner();

    @LexiconPage(comment = "These are the configurations for the dual band (VHF/UHF) vertical antenna.")
    public DualBandAntenna dual_band_antenna = new DualBandAntenna();

    @LexiconPage(comment = "These are the configurations for coaxial cable between antennas and stations/repeaters.")
    public Coax coax = new Coax();

    @LexiconPage(comment = "These are the configurations for handheld radio battery packs.")
    public Battery battery = new Battery();

    @LexiconPage(comment = "These are the configurations for Simple Voice Chat integration.")
    public VoiceChat voice_chat = new VoiceChat();

    public static class Scanner extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the capability of the scanner to make use of antenna masts placed on top of it. Defaults to 10.")
        public Integer antennaAptitude = 10;

        @LexiconEntry(comment = "This is how effective the scanner is at receiving signals, and is essentially a flat bonus to transmission power. Defaults to 800.")
        public Integer receptionPower = 800;

        @LexiconEntry(comment = "Effectively, this is the *floor* for which reception power can reduce travel distance to. Defaults to 20.")
        public Integer receptionFloor = 20;

        @LexiconEntry(comment = "Unused (the scanner only receives). Defaults to 0.")
        public Integer transmissionPowerFM = 0;
        @LexiconEntry(comment = "Unused (the scanner only receives). Defaults to 1000.")
        public Integer diminishThresholdFM = 1000;
        @LexiconEntry(comment = "Unused (the scanner only receives). Defaults to 0.")
        public Integer transmissionPowerAM = 0;
        @LexiconEntry(comment = "Unused (the scanner only receives). Defaults to 1000.")
        public Integer diminishThresholdAM = 1000;
        @LexiconEntry(comment = "Unused (the scanner only receives). Defaults to ADDITIVE.")
        public String diminishmentMethod = "ADDITIVE";
        @LexiconEntry(comment = "Unused (the scanner only receives). Defaults to 1.")
        public Double transmissionDiminishment = 1d;

        @LexiconEntry(comment = "How many channels the scanner checks per second. Defaults to 200.")
        public Integer channelsPerSecond = 200;

        @LexiconEntry(comment = "How long (in ticks) the scanner stays on a channel after the signal stops before it continues scanning. Defaults to 50.")
        public Integer hangTicks = 50;

        @LexiconEntry(comment = "How long (in ticks) a skipped channel is ignored. Defaults to 600.")
        public Integer skipTicks = 600;

        @LexiconEntry(comment = "The range in which the built-in speaker can be heard. Defaults to 12.")
        public Integer speakingRange = 12;

        @LexiconEntry(comment = "When false, removes the scanner recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class DualBandAntenna extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "Gain (dBi) on the VHF band. Defaults to 4.5.")
        public Double vhfGainDbi = 4.5d;

        @LexiconEntry(comment = "Gain (dBi) on the UHF band. Defaults to 7.2.")
        public Double uhfGainDbi = 7.2d;

        @LexiconEntry(comment = "Gain (dBi) on HF, where the antenna is far from resonance. Defaults to -15.")
        public Double hfGainDbi = -15d;

        @LexiconEntry(comment = "When false, removes the dual band antenna recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Coax extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "Longest cable run (blocks) between an antenna and a station or repeater. Defaults to 48.")
        public Integer maxLength = 48;

        @LexiconEntry(comment = "How many blocks of cable one coaxial cable item covers. Defaults to 8.")
        public Integer blocksPerItem = 8;

        @LexiconEntry(comment = "Cable loss on HF in dB per 100 blocks. Defaults to 1.5.")
        public Double hfLossPer100 = 1.5d;

        @LexiconEntry(comment = "Cable loss on VHF in dB per 100 blocks. Defaults to 5.")
        public Double vhfLossPer100 = 5d;

        @LexiconEntry(comment = "Cable loss on UHF in dB per 100 blocks. Defaults to 9.")
        public Double uhfLossPer100 = 9d;
    }

    public static class Battery extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When false, removes the battery pack recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class VoiceChat extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When true, Simple Voice Chat groups are disabled: nobody can create or join a group and existing (also persistent/default) groups are removed, so long distance talk has to go over radio. Defaults to false.")
        public Boolean disableGroups = false;
    }

    public static class Handheld extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When false, handheld radios never run out of battery. Defaults to true.")
        public Boolean requireBattery = true;

        @LexiconEntry(comment = "How many Minecraft days (24000 ticks each) a fully charged handheld lasts while switched on and only receiving. Defaults to 4.")
        public Double batteryDays = 4d;

        @LexiconEntry(comment = "How many times faster the battery drains while transmitting. Defaults to 4.")
        public Double transmitDrainMultiplier = 4d;

        @LexiconEntry(comment = "How many stored channels a handheld's memory scan checks per second. Defaults to 10.")
        public Integer scanChannelsPerSecond = 10;

        @LexiconEntry(comment = "How long (ticks) a scanning handheld stays on a channel after the signal stops. Defaults to 50.")
        public Integer scanHangTicks = 50;
    }

    public static class Station extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the capability of the station to make use of vertical antenna masts. Defaults to 10.")
        public Integer antennaAptitude = 10;

        @LexiconEntry(comment = "This is how effective the station is at receiving signals, and is essentially a flat bonus to transmission power. Defaults to 1000.")
        public Integer receptionPower = 1000;

        @LexiconEntry(comment = "Effectively, this is the *floor* for which reception power can reduce travel distance to. Defaults to 20.")
        public Integer receptionFloor = 20;

        @LexiconEntry(comment = "Unused base transmission power for FM (the station computes its power from watts). Defaults to 1000.")
        public Integer transmissionPowerFM = 1000;

        @LexiconEntry(comment = "This is the threshold of remaining transmission power in FM at which interference begins. Defaults to 1500.")
        public Integer diminishThresholdFM = 1500;

        @LexiconEntry(comment = "Unused base transmission power for AM (the station computes its power from watts). Defaults to 1500.")
        public Integer transmissionPowerAM = 1500;

        @LexiconEntry(comment = "This is the threshold of remaining transmission power in AM at which interference begins. Defaults to 2000.")
        public Integer diminishThresholdAM = 2000;

        @LexiconEntry(comment = "This is the method of diminishment to use. ADDITIVE subtracts a flat amount, while MULTIPLICATIVE subtracts a percentage from the initial transmission power. Defaults to ADDITIVE.")
        public String diminishmentMethod = "ADDITIVE";

        @LexiconEntry(comment = "This is how much transmission power diminishes per block. Defaults to 1.")
        public Double transmissionDiminishment = 1d;

        @LexiconEntry(comment = "The lowest selectable output power in watts. Defaults to 1.")
        public Integer minWatts = 1;

        @LexiconEntry(comment = "The highest selectable output power in watts. Defaults to 100.")
        public Integer maxWatts = 100;

        @LexiconEntry(comment = "Range (in blocks) per square root of a watt with an omnidirectional antenna on VHF FM. Range grows with the square root of power like in free space. Defaults to 1000.")
        public Double rangePerSqrtWatt = 1000d;

        @LexiconEntry(comment = "Range multiplier for AM compared to FM. Defaults to 1.4.")
        public Double amRangeMultiplier = 1.4d;

        @LexiconEntry(comment = "Range multiplier for the HF band (below 30 MHz). Sky waves are handled by the ionosphere page, so this defaults to 1.")
        public Double hfRangeMultiplier = 1d;

        @LexiconEntry(comment = "Range multiplier for the VHF band (30-300 MHz). Defaults to 1.")
        public Double vhfRangeMultiplier = 1d;

        @LexiconEntry(comment = "Range multiplier for the UHF band (above 300 MHz). Defaults to 0.85.")
        public Double uhfRangeMultiplier = 0.85d;

        @LexiconEntry(comment = "Extra interference (0-100) added to HF band reception due to atmospheric noise. Defaults to 8.")
        public Double hfNoise = 8d;

        @LexiconEntry(comment = "The range the built-in microphone can hear the push-to-talk player from. Defaults to 4.")
        public Integer listeningRange = 4;

        @LexiconEntry(comment = "The range in which the built-in speaker can be heard. Defaults to 16.")
        public Integer speakingRange = 16;

        @LexiconEntry(comment = "How close (in blocks) a player must be to key a station with the push-to-talk key. Defaults to 4.5.")
        public Double pttRange = 4.5d;

        @LexiconEntry(comment = "Extra reach (blocks) of a desk or hand microphone on its cable beyond the push-to-talk range. Defaults to 4.")
        public Double microphoneReach = 4d;

        @LexiconEntry(comment = "When false, removes the station recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Repeater extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the capability of the repeater to make use of antenna masts placed on top of it. Defaults to 10.")
        public Integer antennaAptitude = 10;

        @LexiconEntry(comment = "This is how effective the repeater is at receiving signals, and is essentially a flat bonus to transmission power. Defaults to 1500.")
        public Integer receptionPower = 1500;

        @LexiconEntry(comment = "Effectively, this is the *floor* for which reception power can reduce travel distance to. Defaults to 20.")
        public Integer receptionFloor = 20;

        @LexiconEntry(comment = "This is the transmission power for frequency modulation. Defaults to 8000.")
        public Integer transmissionPowerFM = 8000;

        @LexiconEntry(comment = "This is the threshold of transmission power in frequency modulation at which it begins to have an auditory effect. Defaults to 1200.")
        public Integer diminishThresholdFM = 1200;

        @LexiconEntry(comment = "This is the transmission power for amplitude modulation. Defaults to 11000.")
        public Integer transmissionPowerAM = 11000;

        @LexiconEntry(comment = "This is the threshold of transmission power in amplitude modulation at which it begins to have an auditory effect. Defaults to 1600.")
        public Integer diminishThresholdAM = 1600;

        @LexiconEntry(comment = "This is the method of diminishment to use. ADDITIVE subtracts a flat amount, while MULTIPLICATIVE subtracts a percentage from the initial transmission power. Defaults to ADDITIVE.")
        public String diminishmentMethod = "ADDITIVE";

        @LexiconEntry(comment = "This is how much transmission power diminishes per block. Defaults to 1.")
        public Double transmissionDiminishment = 1d;

        @LexiconEntry(comment = "The default shift (in kHz) of newly placed repeaters. The repeater listens on output + shift. Defaults to -7600 (UHF).")
        public Integer defaultShift = -7600;

        @LexiconEntry(comment = "The courtesy tone sent when a relayed transmission ends: BEEP (two-tone), K (morse K) or NONE. Defaults to BEEP.")
        public String courtesyTone = "BEEP";

        @LexiconEntry(comment = "How long (in ticks) the input must be quiet before the courtesy tone is sent. Defaults to 8.")
        public Integer courtesyToneDelay = 8;

        @LexiconEntry(comment = "The volume (0-1) of the courtesy tone. Defaults to 0.35.")
        public Double courtesyToneVolume = 0.35d;

        @LexiconEntry(comment = "A repeater stops relaying a transmission that lasts longer than this many seconds (time-out timer), until the input is quiet for a moment. 0 disables it. Defaults to 180.")
        public Integer timeoutSeconds = 180;

        @LexiconEntry(comment = "A repeater with a call sign identifies itself in morse at the end of a transmission when this many minutes passed since its last identification. Defaults to 10.")
        public Integer idIntervalMinutes = 10;

        @LexiconEntry(comment = "Speed (words per minute) of the repeater's morse identification. Defaults to 20.")
        public Integer idWpm = 20;

        @LexiconEntry(comment = "Tone (Hz) of the repeater's morse identification. Defaults to 800.")
        public Integer idToneHz = 800;

        @LexiconEntry(comment = "Volume (0-1) of the repeater's morse identification. Defaults to 0.3.")
        public Double idVolume = 0.3d;

        @LexiconEntry(comment = "Delay (seconds) of audio passed between linked repeaters, like an internet link. Defaults to 0.25.")
        public Double linkDelay = 0.25d;

        @LexiconEntry(comment = "When true, an enabled repeater keeps its surrounding chunks loaded so it works even when no player is nearby. Defaults to true.")
        public Boolean chunkLoading = true;

        @LexiconEntry(comment = "Radius (in chunks) kept loaded around a repeater. 0 = only its own chunk, 1 = 3x3 chunks (so a generator next to it keeps running). Defaults to 1.")
        public Integer chunkLoadRadius = 1;

        @LexiconEntry(comment = "When false, removes the repeater recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Yagi extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "Forward gain of the yagi antenna in dBi (compared to an omnidirectional antenna). Defaults to 12.")
        public Double gainDbi = 12d;

        @LexiconEntry(comment = "Half-power beam width in degrees. Defaults to 50.")
        public Double beamWidth = 50d;

        @LexiconEntry(comment = "Front-to-back ratio in dB, i.e. how much weaker the signal is behind the antenna compared to its front. Defaults to 15.")
        public Double frontToBackDb = 15d;

        @LexiconEntry(comment = "Maximum number of antenna mast blocks between a station and its yagi. Defaults to 32.")
        public Integer maxMastHeight = 32;

        @LexiconEntry(comment = "When false, removes the yagi antenna recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class CwKey extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "The tone frequency (Hz) of transmitted CW. Defaults to 700.")
        public Integer toneFrequency = 700;

        @LexiconEntry(comment = "The volume (0-1) of transmitted CW. Defaults to 0.6.")
        public Double volume = 0.6d;

        @LexiconEntry(comment = "When false, removes the CW key recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Charger extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "How many battery ticks are charged into a handheld per tick. A full battery holds batteryDays*24000 battery ticks. Defaults to 40.")
        public Integer chargeRate = 40;

        @LexiconEntry(comment = "When false, removes the charging station recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Power extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When false, radio equipment works without electricity. Defaults to true.")
        public Boolean requirePower = true;

        @LexiconEntry(comment = "The mains voltage (Electrodynamics) that power supplies, chargers and the original radio blocks are rated for. Defaults to 120.")
        public Double voltage = 120d;

        @LexiconEntry(comment = "When true, the station, repeater, scanner and fox transmitter need 13.8 V DC from a power supply. Mains fed straight into them trips their protection. Defaults to true.")
        public Boolean requirePsu = true;

        @LexiconEntry(comment = "The DC voltage amateur radio equipment runs on. Defaults to 13.8.")
        public Double dcVoltage = 13.8d;

        @LexiconEntry(comment = "Efficiency (0-1) of the 13.8 V power supply. Defaults to 0.9.")
        public Double psuEfficiency = 0.9d;

        @LexiconEntry(comment = "When false, removes the power supply recipe. Defaults to true.")
        public Boolean psuEnabled = true;

        @LexiconEntry(comment = "When true, equipment explodes when fed a voltage higher than its rating (Electrodynamics behaviour). Defaults to true.")
        public Boolean explodeOnOverVoltage = true;

        @LexiconEntry(comment = "Joules per tick used by the radio block. Defaults to 1.")
        public Double radioUsage = 1d;
        @LexiconEntry(comment = "Joules per tick used by the receiver block. Defaults to 1.")
        public Double receiverUsage = 1d;
        @LexiconEntry(comment = "Joules per tick used by the transmitter block. Defaults to 5.")
        public Double transmitterUsage = 5d;
        @LexiconEntry(comment = "Joules per tick used by the speaker block. Defaults to 1.5.")
        public Double speakerUsage = 1.5d;

        @LexiconEntry(comment = "Joules per tick used by the station while receiving. Defaults to 2.")
        public Double stationIdleUsage = 2d;
        @LexiconEntry(comment = "Efficiency of the station power amplifier. Transmitting at W watts uses W / efficiency / 20 extra joules per tick. Defaults to 0.5.")
        public Double stationEfficiency = 0.5d;

        @LexiconEntry(comment = "Joules per tick used by the repeater while listening. Defaults to 2.")
        public Double repeaterIdleUsage = 2d;
        @LexiconEntry(comment = "Extra joules per tick used by the repeater while transmitting. Defaults to 5.")
        public Double repeaterTransmitUsage = 5d;

        @LexiconEntry(comment = "Joules per tick used by the charging station while charging. Defaults to 3.")
        public Double chargerUsage = 3d;

        @LexiconEntry(comment = "Joules per tick used by the scanner. Defaults to 1.")
        public Double scannerUsage = 1d;
    }

    public static class Interference extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When false, stations never cut power in nearby uninsulated wires. Defaults to true.")
        public Boolean enabled = true;

        @LexiconEntry(comment = "The minimum output power (watts) at which a transmitting station starts to cut power in nearby uninsulated wires. Defaults to 50.")
        public Integer minWatts = 50;

        @LexiconEntry(comment = "Affected radius (blocks) around the radiating antenna per square root of a watt. Defaults to 1.6 (16 blocks at 100 W).")
        public Double radiusPerSqrtWatt = 1.6d;

        @LexiconEntry(comment = "How long (in ticks) wires stay without power after the station stops transmitting. Defaults to 40.")
        public Integer lingerTicks = 40;
    }

    public static class Propagation extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "Legacy flat extra range (blocks) per block of altitude above sea level for stations and repeaters. The terrain and radio horizon model now rewards height, so this defaults to 0.")
        public Double heightBonusPerBlock = 0d;

        @LexiconEntry(comment = "The altitude considered as sea level for the legacy height bonus. Defaults to 64.")
        public Integer seaLevel = 64;

        @LexiconEntry(comment = "When true, hills and mountains between two antennas weaken the signal (knife-edge diffraction) and antennas beyond each other's radio horizon fade out. Defaults to true.")
        public Boolean terrainEnabled = true;

        @LexiconEntry(comment = "How many terrain heights are sampled along a radio path (one every 16 blocks up to this many). Defaults to 80.")
        public Integer terrainSamples = 80;

        @LexiconEntry(comment = "The most terrain can weaken a signal, in dB. Defaults to 60.")
        public Double maxTerrainLossDb = 60d;

        @LexiconEntry(comment = "Radius of the earth in blocks for the radio horizon (one block is one metre). Defaults to 6371000.")
        public Double earthRadius = 6_371_000d;

        @LexiconEntry(comment = "Atmospheric refraction factor (k) bending VHF/UHF slightly around the earth. Defaults to 1.333.")
        public Double refractionFactor = 4d / 3d;

        @LexiconEntry(comment = "Extra loss (dB per 1000 blocks) beyond the radio horizon at 145 MHz; it grows slowly with frequency. Defaults to 0.6.")
        public Double horizonDbPerKm = 0.6d;

        @LexiconEntry(comment = "When true, antennas indoors, in caves or under water lose signal for every block above them. Defaults to true.")
        public Boolean environmentEnabled = true;

        @LexiconEntry(comment = "Loss (dB) of going from open air into a building or cave. Defaults to 6.")
        public Double entryLossDb = 6d;

        @LexiconEntry(comment = "Loss (dB) per block of rock/earth/building above an HF antenna. Defaults to 0.3.")
        public Double hfLossPerBlock = 0.3d;

        @LexiconEntry(comment = "Loss (dB) per block of rock/earth/building above a VHF antenna. Defaults to 1.")
        public Double vhfLossPerBlock = 1d;

        @LexiconEntry(comment = "Loss (dB) per block of rock/earth/building above a UHF antenna. Defaults to 1.6.")
        public Double uhfLossPerBlock = 1.6d;

        @LexiconEntry(comment = "How many blocks of rock one block of water counts as. Defaults to 1.5.")
        public Double waterWeight = 1.5d;

        @LexiconEntry(comment = "The most an antenna's surroundings can weaken a signal, in dB. Defaults to 90.")
        public Double maxEnvironmentLossDb = 90d;

        @LexiconEntry(comment = "Loss (dB) of a handheld's short antenna on the HF bands. Defaults to 20.")
        public Double handheldHfLossDb = 20d;

        @LexiconEntry(comment = "How much better (dB) single sideband gets through than FM at the same power. Defaults to 6.")
        public Double ssbGainDb = 6d;

        @LexiconEntry(comment = "How many dB one S unit of the S-meter is. Real receivers use 6; the game's ranges are shorter, so it defaults to 4.")
        public Double sMeterDbPerUnit = 4d;
    }

    public static class Fox extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "Unused (the fox transmitter only transmits). Defaults to 0.")
        public Integer antennaAptitude = 0;
        @LexiconEntry(comment = "Unused (the fox transmitter only transmits). Defaults to 0.")
        public Integer receptionPower = 0;
        @LexiconEntry(comment = "Unused (the fox transmitter only transmits). Defaults to 20.")
        public Integer receptionFloor = 20;

        @LexiconEntry(comment = "Transmission power of a fox transmitter on FM (it is a low power beacon). Defaults to 900.")
        public Integer transmissionPowerFM = 900;
        @LexiconEntry(comment = "Threshold of remaining transmission power on FM at which noise begins. Defaults to 400.")
        public Integer diminishThresholdFM = 400;
        @LexiconEntry(comment = "Transmission power of a fox transmitter on AM. Defaults to 1200.")
        public Integer transmissionPowerAM = 1200;
        @LexiconEntry(comment = "Threshold of remaining transmission power on AM at which noise begins. Defaults to 500.")
        public Integer diminishThresholdAM = 500;
        @LexiconEntry(comment = "This is the method of diminishment to use. Defaults to ADDITIVE.")
        public String diminishmentMethod = "ADDITIVE";
        @LexiconEntry(comment = "This is how much transmission power diminishes per block. Defaults to 1.")
        public Double transmissionDiminishment = 1d;

        @LexiconEntry(comment = "Tone (Hz) of the fox's morse identification. Defaults to 1000.")
        public Integer toneHz = 1000;
        @LexiconEntry(comment = "Speed (words per minute) of the fox's morse identification (ARDF foxes send slowly). Defaults to 10.")
        public Integer wpm = 10;
        @LexiconEntry(comment = "Seconds of one fox cycle: every fox transmits for cycleSeconds / 5 seconds in turn, like an ARDF hunt with five foxes. 0 = transmit all the time. Defaults to 0.")
        public Integer cycleSeconds = 0;
        @LexiconEntry(comment = "Joules per tick used by a fox transmitter. Defaults to 0.3.")
        public Double usage = 0.3d;
        @LexiconEntry(comment = "When false, removes the fox transmitter recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class DirectionFinder extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "Unused. Defaults to 0.")
        public Integer antennaAptitude = 0;
        @LexiconEntry(comment = "How sensitive the direction finding receiver is (a flat bonus to transmission power). Defaults to 400.")
        public Integer receptionPower = 400;
        @LexiconEntry(comment = "The floor reception power can reduce travel distance to. Defaults to 5.")
        public Integer receptionFloor = 5;
        @LexiconEntry(comment = "Unused (it only receives). Defaults to 0.")
        public Integer transmissionPowerFM = 0;
        @LexiconEntry(comment = "Unused (it only receives). Defaults to 400.")
        public Integer diminishThresholdFM = 400;
        @LexiconEntry(comment = "Unused (it only receives). Defaults to 0.")
        public Integer transmissionPowerAM = 0;
        @LexiconEntry(comment = "Unused (it only receives). Defaults to 500.")
        public Integer diminishThresholdAM = 500;
        @LexiconEntry(comment = "Unused (it only receives). Defaults to ADDITIVE.")
        public String diminishmentMethod = "ADDITIVE";
        @LexiconEntry(comment = "Unused (it only receives). Defaults to 1.")
        public Double transmissionDiminishment = 1d;

        @LexiconEntry(comment = "Half-power beam width (degrees) of the handheld direction finding antenna. Defaults to 70.")
        public Double beamWidth = 70d;
        @LexiconEntry(comment = "How much weaker (dB) a signal from behind is heard. Defaults to 20.")
        public Double frontToBackDb = 20d;
        @LexiconEntry(comment = "The range in which its speaker can be heard. Defaults to 4.")
        public Integer speakingRange = 4;
        @LexiconEntry(comment = "When false, removes the direction finder recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Aprs extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "The APRS frequency. Defaults to 144.800 (Europe).")
        public String frequency = "144.800";
        @LexiconEntry(comment = "Seconds between position beacons of an APRS radio standing still. Defaults to 90.")
        public Integer beaconSeconds = 90;
        @LexiconEntry(comment = "Seconds between position beacons while moving fast (smart beaconing). Defaults to 20.")
        public Integer fastBeaconSeconds = 20;
        @LexiconEntry(comment = "Speed (km/h, 1 block = 1 m) above which beacons are sent at the fast rate. Defaults to 15.")
        public Double fastSpeedKmh = 15d;
        @LexiconEntry(comment = "How strong (dB above the edge of reception) a packet has to arrive to be decoded. Defaults to 3.")
        public Double decodeMarginDb = 3d;
        @LexiconEntry(comment = "Minutes a heard station stays on the APRS map. Defaults to 30.")
        public Integer retentionMinutes = 30;
        @LexiconEntry(comment = "Latitude of block 0,0 for the positions in the packets. Defaults to 39.0.")
        public Double originLatitude = 39d;
        @LexiconEntry(comment = "Longitude of block 0,0 for the positions in the packets. Defaults to 35.0.")
        public Double originLongitude = 35d;
        @LexiconEntry(comment = "Volume (0-1) of the packet audio. Defaults to 0.4.")
        public Double volume = 0.4d;
        @LexiconEntry(comment = "When false, removes the APRS radio recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Guide extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When true, every player gets the operator's manual the first time they join. Defaults to true.")
        public Boolean giveOnFirstJoin = true;
    }

    public static class Mobile extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "Output power (watts) of the mobile radio while its user rides a vehicle (boat, minecart, horse, Create seat, aircraft...). Defaults to 25.")
        public Double vehicleWatts = 25d;
        @LexiconEntry(comment = "Output power (watts) of the mobile radio carried by hand. Handheld transceivers are rated 5 W. Defaults to 5.")
        public Double portableWatts = 5d;
        @LexiconEntry(comment = "Gain (dB) of the roof antenna over a handheld's rubber duck antenna, for receiving. Defaults to 3.")
        public Double roofAntennaGainDb = 3d;
        @LexiconEntry(comment = "Battery charge (in handheld battery ticks) the vehicle puts back per tick while riding. Defaults to 4.")
        public Integer vehicleCharging = 4;
        @LexiconEntry(comment = "When false, removes the mobile radio recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Sstv extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "Volume (0-1) of the picture audio. Defaults to 0.4.")
        public Double volume = 0.4d;
        @LexiconEntry(comment = "Severity (0-100) of the received signal where the picture starts getting snowy. Defaults to 25.")
        public Double noiseStart = 25d;
        @LexiconEntry(comment = "Severity (0-100) of the received signal where the picture is all snow. Defaults to 90.")
        public Double noiseEnd = 90d;
        @LexiconEntry(comment = "When false, removes the SSTV terminal recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class BandPlanPage extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When true, amateur radios (transceiver, station, mobile radio) only transmit on amateur bands and walkie talkies only on the 16 PMR446 channels. Defaults to true.")
        public Boolean enforce = true;

        @LexiconEntry(comment = "When true, transmitting with amateur radios needs an amateur licence, earned with the licence exam. When false the exam only gives a call sign. Defaults to false.")
        public Boolean requireLicense = false;

        @LexiconEntry(comment = "When true, walkie and spuddie talkies are locked to the PMR446 channels. Defaults to true.")
        public Boolean pmrWalkies = true;

        @LexiconEntry(comment = "Prefix of generated call signs. Defaults to TA.")
        public String callsignPrefix = "TA";

        @LexiconEntry(comment = "How many exam questions are asked. Defaults to 10.")
        public Integer examQuestions = 10;

        @LexiconEntry(comment = "How many correct answers pass the exam. Defaults to 7.")
        public Integer examPassMark = 7;

        @LexiconEntry(comment = "Seconds to wait before retaking a failed exam. Defaults to 300.")
        public Integer examRetrySeconds = 300;
    }

    public static class SwrPage extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When true, antennas used off their band (or no antenna at all) reflect power: the station turns its power down, loses signal and heats up. Defaults to true.")
        public Boolean enabled = true;

        @LexiconEntry(comment = "Above this SWR the station reduces its power to protect itself. Defaults to 3.")
        public Double foldbackSwr = 3d;

        @LexiconEntry(comment = "The highest SWR an antenna tuner next to the station can match. Defaults to 10.")
        public Double tunerMaxSwr = 10d;

        @LexiconEntry(comment = "Gain (dBi) of the yagi on HF, far below its band. Defaults to -20.")
        public Double yagiHfGainDbi = -20d;

        @LexiconEntry(comment = "Loss (dB) of a station transmitting without any antenna. Defaults to 30.")
        public Double noAntennaLossDb = 30d;

        @LexiconEntry(comment = "Seconds of continuous 100 W transmission until the power amplifier is about 60% hot (it cools at the same pace). Defaults to 120.")
        public Double heatSeconds = 120d;

        @LexiconEntry(comment = "When false, the power amplifier never overheats. Defaults to true.")
        public Boolean overheating = true;

        @LexiconEntry(comment = "When false, removes the antenna tuner recipe. Defaults to true.")
        public Boolean tunerEnabled = true;
    }

    public static class Qrm extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When true, running electric machines near a receiver raise its noise (man-made interference). Defaults to true.")
        public Boolean enabled = true;

        @LexiconEntry(comment = "How far (blocks) machines disturb a receiver. Defaults to 12.")
        public Integer radius = 12;

        @LexiconEntry(comment = "Noise (0-100) of one running machine right next to the receiver. Defaults to 6.")
        public Double noisePerMachine = 6d;

        @LexiconEntry(comment = "The most noise (0-100) machines can add. Defaults to 40.")
        public Double maxNoise = 40d;
    }

    public static class Lightning extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When true, tall antennas attract lightning during thunderstorms and unprotected radios burn out. Defaults to true.")
        public Boolean enabled = true;

        @LexiconEntry(comment = "Average strikes per hour of thunderstorm on a 20 block high antenna (it grows with the square of the height). Defaults to 3.")
        public Double strikesPerHour = 3d;

        @LexiconEntry(comment = "Antennas lower than this (blocks above what they stand on) are never struck. Defaults to 6.")
        public Integer minAntennaHeight = 6;

        @LexiconEntry(comment = "A lightning rod within this many blocks of the antenna takes the strike instead. Defaults to 3.")
        public Integer protectionRadius = 3;

        @LexiconEntry(comment = "How much redstone dust repairs a burned out radio. Defaults to 4.")
        public Integer repairRedstone = 4;
    }

    public static class Ionosphere extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "When true, HF uses sky waves (day/night, skip zone, solar conditions). When false HF only has its ground wave. Defaults to true.")
        public Boolean enabled = true;

        @LexiconEntry(comment = "The effective distance (blocks) of a sky-wave path without any extra loss. Defaults to 2000.")
        public Double skyReference = 2000d;

        @LexiconEntry(comment = "Base loss (dB) of every sky-wave path. Defaults to 4.")
        public Double baseSkyLossDb = 4d;

        @LexiconEntry(comment = "Length (blocks) of one sky-wave hop. Defaults to 4000.")
        public Double hopLength = 4000d;

        @LexiconEntry(comment = "Loss (dB) of every extra hop. Defaults to 4.")
        public Double hopLossDb = 4d;

        @LexiconEntry(comment = "Scale (blocks) of the skip zone. Defaults to 1500.")
        public Double skipScale = 1500d;

        @LexiconEntry(comment = "Daytime D-layer absorption at noon in dB*MHz^2 (e.g. 250 means 20 dB on 3.5 MHz, 5 dB on 7 MHz). Defaults to 250.")
        public Double dayAbsorption = 250d;

        @LexiconEntry(comment = "Ground wave loss in dB per MHz per 1000 blocks. Defaults to 0.15.")
        public Double groundWaveLossDb = 0.15d;

        @LexiconEntry(comment = "Lowest solar flux (SFI) of the 27-day solar cycle. Defaults to 70.")
        public Double minSolarFlux = 70d;

        @LexiconEntry(comment = "Highest solar flux (SFI) of the 27-day solar cycle. Defaults to 200.")
        public Double maxSolarFlux = 200d;

        @LexiconEntry(comment = "Chance (0-1) that a Minecraft day starts a geomagnetic storm. Defaults to 0.08.")
        public Double stormChance = 0.08d;

        @LexiconEntry(comment = "Chance (0-1) that a Minecraft day has sporadic E openings on 10 m and 6 m. Defaults to 0.15.")
        public Double sporadicEChance = 0.15d;

        @LexiconEntry(comment = "Depth (dB) of the slow fading (QSB) of sky-wave signals. Defaults to 6.")
        public Double fadingDb = 6d;

        @LexiconEntry(comment = "Extra noise (0-100) on HF during thunderstorms. Defaults to 14.")
        public Double thunderNoise = 14d;
    }

    public static class Transceiver extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is how effective the transceiver is at receiving signals, and is essentially a flat bonus to transmission power. Defaults to 600.")
        public Integer receptionPower = 600;

        @LexiconEntry(comment = "Effectively, this is the *floor* for which reception power can reduce travel distance to. Defaults to 20.")
        public Integer receptionFloor = 10;

        @LexiconEntry(comment = "This is the transmission power for frequency modulation. Defaults to 4000.")
        public Integer transmissionPowerFM = 4000;

        @LexiconEntry(comment = "This is the threshold of transmission power in frequency modulation at which it begins to have an auditory effect. Defaults to 700.")
        public Integer diminishThresholdFM = 700;

        @LexiconEntry(comment = "This is the range after which players can no longer be heard for amplitude modulation. Defaults to 6000.")
        public Integer transmissionPowerAM = 6000;

        @LexiconEntry(comment = "This is the threshold of transmission power in amplitude modulation at which it begins to have an auditory effect. Defaults to 1000.")
        public Integer diminishThresholdAM = 1000;

        @LexiconEntry(comment = "This is the method of diminishment to use. ADDITIVE subtracts a flat amount, while MULTIPLICATIVE subtracts a percentage from the initial transmission power. Defaults to ADDITIVE.")
        public String diminishmentMethod = "ADDITIVE";

        @LexiconEntry(comment = "This is how much transmission power diminishes per block. Defaults to 1.")
        public Double transmissionDiminishment = 1d;

        @LexiconEntry(comment = "This is the cooldown in ticks after using. Defaults to 20.")
        public Integer cooldown = 20;

        @LexiconEntry(comment = "This is the range for the transceiver that it can hear from. Defaults to 4.")
        public Integer listeningRange = 4;
        @LexiconEntry(comment = "This is the range for the transceiver in which the audio played from it can be heard. Defaults to 4.")
        public Integer speakingRange = 4;

        @LexiconEntry(comment = "When false, removes the transceiver recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class WalkieTalkie extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is how effective the walkie is at receiving signals, and is essentially a flat bonus to transmission power. Defaults to 400.")
        public Integer receptionPower = 400;

        @LexiconEntry(comment = "Effectively, this is the *floor* for which reception power can reduce travel distance to. Defaults to 20.")
        public Integer receptionFloor = 20;

        @LexiconEntry(comment = "This is the transmission power for frequency modulation. Defaults to 2000.")
        public Integer transmissionPowerFM = 2000;

        @LexiconEntry(comment = "This is the threshold of transmission power in frequency modulation at which it begins to have an auditory effect. Defaults to 400.")
        public Integer diminishThresholdFM = 400;

        @LexiconEntry(comment = "This is the range after which players can no longer be heard for amplitude modulation. Defaults to 3000.")
        public Integer transmissionPowerAM = 3000;

        @LexiconEntry(comment = "This is the threshold of transmission power in amplitude modulation at which it begins to have an auditory effect. Defaults to 600.")
        public Integer diminishThresholdAM = 600;

        @LexiconEntry(comment = "This is the method of diminishment to use. ADDITIVE subtracts a flat amount, while MULTIPLICATIVE subtracts a percentage from the initial transmission power. Defaults to ADDITIVE.")
        public String diminishmentMethod = "ADDITIVE";

        @LexiconEntry(comment = "This is how much transmission power diminishes per block. Defaults to 1.")
        public Double transmissionDiminishment = 1d;

        @LexiconEntry(comment = "This is the cooldown in ticks after using. Defaults to 60.")
        public Integer cooldown = 60;

        @LexiconEntry(comment = "This is the range for the walkie that it can hear from. Defaults to 4.")
        public Integer listeningRange = 4;
        @LexiconEntry(comment = "This is the range for the walkie in which the audio played from it can be heard. Defaults to 4.")
        public Integer speakingRange = 4;

        @LexiconEntry(comment = "When true, replaces the walkie talkie with the spuddie talkie. Defaults to true.")
        public Boolean spuddieTalkie = true;

        @LexiconEntry(comment = "When false, removes both the spuddie and walkie recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Wire extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the method of diminishment to use. ADDITIVE subtracts a flat amount, while MULTIPLICATIVE subtracts a percentage from the initial transmission power. Defaults to ADDITIVE.")
        public String diminishmentMethod = "MULTIPLICATIVE";
        @LexiconEntry(comment = "This is how much transmission power diminishes per block. Defaults to 0.01.")
        public Double transmissionDiminishment = 0.01d;

        @LexiconEntry(comment = "This is the amount of time (in seconds) per block a wire takes to relay data. Defaults to 0.1.")
        public Double transmissionTime = 0.1d;

        @LexiconEntry(comment = "This is the amount of time (in ticks) between each header sent. Defaults to 5.")
        public Integer headerInterval = 5;

        @LexiconEntry(comment = "This is the range a wire can reach before breaking. Defaults to 12.")
        public Double range = 12d;

        @LexiconEntry(comment = "This is the amount of time (in ticks) a wire can survive while being invalid (no connections or out of range) before breaking. Defaults to 5.")
        public Integer invalidDeathTime = 5;

        @LexiconEntry(comment = "This is the amount of time (in ticks) between each effect for a wire. Can be disabled by setting to -1. Defaults to 5.")
        public Integer effectInterval = 5;
    }

    public static class Transmitter extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the capability of this item to make use of antennas. Essentially acts as a multiplier for the antenna score. Defaults to 10.")
        public Integer antennaAptitude = 10;

        @LexiconEntry(comment = "This is the transmission power for frequency modulation. Defaults to 10000.")
        public Integer transmissionPowerFM = 10000;

        @LexiconEntry(comment = "This is the threshold of transmission power in frequency modulation at which it begins to have an auditory effect. Defaults to 1500.")
        public Integer diminishThresholdFM = 1500;

        @LexiconEntry(comment = "This is the range after which players can no longer be heard for amplitude modulation. Defaults to 14000.")
        public Integer transmissionPowerAM = 14000;

        @LexiconEntry(comment = "This is the threshold of transmission power in amplitude modulation at which it begins to have an auditory effect. Defaults to 2000.")
        public Integer diminishThresholdAM = 2000;

        @LexiconEntry(comment = "This is the method of diminishment to use. ADDITIVE subtracts a flat amount, while MULTIPLICATIVE subtracts a percentage from the initial transmission power. Defaults to ADDITIVE.")
        public String diminishmentMethod = "ADDITIVE";

        @LexiconEntry(comment = "This is how much transmission power diminishes per block. Defaults to 1.")
        public Double transmissionDiminishment = 1d;

        @LexiconEntry(comment = "When false, removes the transmitter recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Receiver extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the capability of this item to make use of antennas. Essentially acts as a multiplier for the antenna score. Defaults to 10.")
        public Integer antennaAptitude = 10;

        @LexiconEntry(comment = "This is how effective the receiver is at receiving signals, and is essentially a flat bonus to transmission power. Defaults to 1000.")
        public Integer receptionPower = 1000;

        @LexiconEntry(comment = "Effectively, this is the *floor* for which reception power can reduce travel distance to. Defaults to 20.")
        public Integer receptionFloor = 20;

        @LexiconEntry(comment = "When false, removes the receiver recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Radio extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is how effective the radio is at receiving signals, and is essentially a flat bonus to transmission power. Defaults to 500.")
        public Integer receptionPower = 500;

        @LexiconEntry(comment = "Effectively, this is the *floor* for which reception power can reduce travel distance to. Defaults to 50.")
        public Integer receptionFloor = 50;

        @LexiconEntry(comment = "This is the range for the radio in which the audio played from it can be heard. Defaults to 24.")
        public Integer speakingRange = 24;

        @LexiconEntry(comment = "When false, removes the radio recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Microphone extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the range for the microphone that it can hear from. Defaults to 8.")
        public Integer listeningRange = 8;

        @LexiconEntry(comment = "This is how often (in ticks) the microphone will update its redstone signal according to its activity. Defaults to 5.")
        public Integer redstonePolling = 5;

        @LexiconEntry(comment = "When false, removes the microphone recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Speaker extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the range for the radio in which the audio transmitted from it can be heard. Defaults to 32.")
        public Integer speakingRange = 32;

        @LexiconEntry(comment = "This is how often (in ticks) the speaker will update its redstone signal according to its activity. Defaults to 5.")
        public Integer redstonePolling = 5;

        @LexiconEntry(comment = "When false, removes the speaker recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Antenna extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is the maximum distance an antenna can travel without support before falling. CAUTION: SETTING THIS TOO HIGH MAY CAUSE LAG WITH LARGE ANTENNAS. Defaults to 8.")
        public Integer maxDistance = 8;

        @LexiconEntry(comment = "When false, removes the antenna recipe. Defaults to true.")
        public Boolean enabled = true;
    }

    public static class Frequency extends LexiconPageData implements ConfigHolder {
        @LexiconEntry(comment = "This is how many whole places (digits before the period) can exist in a frequency. Defaults to 3.")
        public Integer wholePlaces = 3;

        @LexiconEntry(comment = "This is how many decimal places (digits after the period) can exist in a frequency. 3 gives 1 kHz resolution (e.g. 438.650). Defaults to 3.")
        public Integer decimalPlaces = 3;

        @LexiconEntry(comment = "This is the default frequency to be provided to frequency-holding items. When set to auto-generate, uses 145.500 MHz formatted to the wholePlaces and decimalPlaces configurations. Defaults to auto-generate.")
        public String defaultFrequency = "auto-generate";

        @LexiconEntry(comment = "The base amount of interference to give to all AM radio transmissions. Defaults to 6.")
        public Double baseAMInterference = 6d;
        @LexiconEntry(comment = "The base amount of interference to give to all FM radio transmissions. Defaults to 0.")
        public Double baseFMInterference = 0d;

        @LexiconEntry(comment = "When true, receivers tuned a few kHz away from a transmitter still hear it, distorted (FM/AM) or pitch shifted (SSB). Defaults to true.")
        public Boolean offFrequencyReception = true;

        @LexiconEntry(comment = "How much stronger (dB) one FM signal has to be to take over the channel from another (capture effect). Closer signals garble each other. Defaults to 6.")
        public Double captureRatioDb = 6d;

        @LexiconEntry(comment = "When true, FM receivers play a short burst of noise when a transmission ends (squelch tail). Defaults to true.")
        public Boolean squelchTail = true;

        @LexiconEntry(comment = "Whether or not the radios work across dimensions. Defaults to false.")
        public Boolean crossDimensional = false;
        @LexiconEntry(comment = "The base amount of interference to give to radio transmission per block across dimensions. Defaults to 4.")
        public Double dimensionalInterference = 4d;
    }

    public static class Router extends LexiconPageData implements ConfigHolder {

        @LexiconEntry(comment = "How many sources should be compiled for reading audio levels? Affects the rate of updates for activity levels. Changing this value greatly may cause unexpected results. Defaults to 10.")
        public Integer compileAmount = 10;

        @LexiconEntry(comment = "The factor of audio signal energy for converting to redstone signals. Higher values will cause lower redstone signals, and vice versa. Defaults to 20.")
        public Double activityRedstoneFactor = 1500d;
        @LexiconEntry(comment = "How long (in ticks) before activity expiry can we resend an update? Defaults to 2.")
        public Integer activityForgiveness = 2;
        @LexiconEntry(comment = "How long (in ticks) should a router stay active after receiving audio data? Affects the rate of updates for activity checks. Defaults to 20.")
        public Integer activityTime = 20;

        @LexiconEntry(comment = "[EXPERIMENTAL] Pick up audio from the world, not just players. Defaults to false.")
        public Boolean soundListening = false;

        @LexiconEntry(comment = "[EXPERIMENTAL] Pick up audio from speakers, can cause feedback loops. Defaults to false.")
        public Boolean feedbackListening = false;
    }

    public static class Compatibilities extends LexiconPageData {
        @LexiconPage(comment = "These are the configurations for the optional dependency Voice Chat Interaction.")
        public VoiceChatInteraction voice_chat_interaction = new VoiceChatInteraction();

        public static class VoiceChatInteraction extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for Voice Chat Interaction. Defaults to false. (NON-FUNCTIONAL)")
            public Boolean enabled = false;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency Vibrative Voice.")
        public VibrativeVoice vibrative_voice = new VibrativeVoice();

        public static class VibrativeVoice extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for Vibrative Voice. Defaults to true.")
            public Boolean enabled = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency Valkyrien Skies.")
        public ValkyrienSkies valkyrien_skies = new ValkyrienSkies();

        public static class ValkyrienSkies extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for Valkyrien Skies. Defaults to true.")
            public Boolean enabled = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency Create.")
        public Create create = new Create();

        public static class Create extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for Create. Defaults to true.")
            public Boolean enabled = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency CC: Tweaked.")
        public CCTweaked cc_tweaked = new CCTweaked();

        public static class CCTweaked extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for CC: Tweaked. Defaults to true.")
            public Boolean enabled = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency Etched.")
        public Etched etched = new Etched();

        public static class Etched extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for Etched. Defaults to true.")
            public Boolean enabled = true;

            @LexiconEntry(comment = "When true, enabled the mixin patch fixing the 'dual-download' issue. Defaults to true.")
            public Boolean streamPatch = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency AudioPlayer.")
        public AudioPlayer audioplayer = new AudioPlayer();

        public static class AudioPlayer extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for AudioPlayer. Defaults to true.")
            public Boolean enabled = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency Sable/Create Aeronautics.")
        public Sable sable = new Sable();

        public static class Sable extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for Sable. Defaults to true.")
            public Boolean enabled = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency Curios.")
        public Curios curios = new Curios();

        public static class Curios extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for Curios. Defaults to true.")
            public Boolean enabled = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }

        //----

        @LexiconPage(comment = "These are the configurations for the optional dependency Trinkets.")
        public Trinkets trinkets = new Trinkets();

        public static class Trinkets extends LexiconPageData implements CompatibilityInstance.CompatibilityConfig {
            @LexiconEntry(comment = "When false, removes compatibility for Trinkets. Defaults to true.")
            public Boolean enabled = true;

            @Override
            public boolean isEnabled() { return enabled; }
        }
    }
}