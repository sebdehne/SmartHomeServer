export enum EvChargingEventType {
    clientConnectionsChanged = "clientConnectionsChanged"
}


export class EvChargingEvent {
    public eventType: EvChargingEventType;
    public chargingStationsDataAndConfig: EvChargingStationDataAndConfig[];

    public constructor(eventType: EvChargingEventType, chargingStationsDataAndConfig: EvChargingStationDataAndConfig[]) {
        this.eventType = eventType;
        this.chargingStationsDataAndConfig = chargingStationsDataAndConfig;
    }
}

export enum EvChargingStationRequestType {
    getChargingStationsDataAndConfig = "getChargingStationsDataAndConfig",
    uploadFirmwareToClient = "uploadFirmwareToClient",

    setMode = "setMode",
    setLoadSharingPriority = "setLoadSharingPriority",
    setSkipPercentExpensiveHours = "setSkipPercentExpensiveHours",
    setChargeRateLimit = "setChargeRateLimit"
}

export class EvChargingStationRequest {
    public type: EvChargingStationRequestType;
    public clientId: string | null;
    public firmwareBased64Encoded: string | null;
    public newMode: EvChargingMode | null;
    public newLoadSharingPriority: LoadSharingPriority | null;
    public skipPercentExpensiveHours: number | null;
    public chargeRateLimit: number | null;

    public constructor(type: EvChargingStationRequestType, clientId: string | null, firmwareBased64Encoded: string | null, newMode: EvChargingMode | null, newLoadSharingPriority: LoadSharingPriority | null, skipPercentExpensiveHours: number | null, chargeRateLimit: number | null) {
        this.type = type;
        this.clientId = clientId;
        this.firmwareBased64Encoded = firmwareBased64Encoded;
        this.newMode = newMode;
        this.newLoadSharingPriority = newLoadSharingPriority;
        this.skipPercentExpensiveHours = skipPercentExpensiveHours;
        this.chargeRateLimit = chargeRateLimit;
    }
}

export class EvChargingStationResponse {
    public chargingStationsDataAndConfig: EvChargingStationDataAndConfig[];
    public uploadFirmwareToClientResult: boolean | null;
    public configUpdated: boolean | null;


    public constructor(chargingStationsDataAndConfig: EvChargingStationDataAndConfig[], uploadFirmwareToClientResult: boolean | null, configUpdated: boolean | null) {
        this.chargingStationsDataAndConfig = chargingStationsDataAndConfig;
        this.uploadFirmwareToClientResult = uploadFirmwareToClientResult;
        this.configUpdated = configUpdated;
    }
}

export class EvChargingStationDataAndConfig {
    public data: EvChargingStationData;
    public config: EVChargingStationConfig;
    public clientConnection: EvChargingStationClient;


    public constructor(data: EvChargingStationData, config: EVChargingStationConfig, clientConnection: EvChargingStationClient) {
        this.data = data;
        this.config = config;
        this.clientConnection = clientConnection;
    }
}


export class EvChargingStationClient {
    public clientId: string;
    public displayName: string;
    public addr: string;
    public port: number;
    public firmwareVersion: number;
    public powerConnectionId: string;
    public connectedSince: number;

    public constructor(clientId: string, displayName: string, addr: string, port: number, firmwareVersion: number, powerConnectionId: string, connectedSince: number) {
        this.clientId = clientId;
        this.displayName = displayName;
        this.addr = addr;
        this.port = port;
        this.firmwareVersion = firmwareVersion;
        this.powerConnectionId = powerConnectionId;
        this.connectedSince = connectedSince;
    }
}

export class EvChargingStationData {
    public chargingState: ChargingState;
    public reasonChargingUnavailable: string | null;
    public chargingStateChangedAt: number;
    public proximityPilotAmps: ProximityPilotAmps;
    public maxChargingRate: number;
    public phase1Millivolts: number;
    public phase2Millivolts: number;
    public phase3Millivolts: number;
    public phase1Milliamps: number;
    public phase2Milliamps: number;
    public phase3Milliamps: number;
    public systemUptime: number;
    public wifiRSSI: number;
    public utcTimestampInMs: number;


    public constructor(chargingState: ChargingState, reasonChargingUnavailable: string | null, chargingStateChangedAt: number, proximityPilotAmps: ProximityPilotAmps, maxChargingRate: number, phase1Millivolts: number, phase2Millivolts: number, phase3Millivolts: number, phase1Milliamps: number, phase2Milliamps: number, phase3Milliamps: number, systemUptime: number, wifiRSSI: number, utcTimestampInMs: number) {
        this.chargingState = chargingState;
        this.reasonChargingUnavailable = reasonChargingUnavailable;
        this.chargingStateChangedAt = chargingStateChangedAt;
        this.proximityPilotAmps = proximityPilotAmps;
        this.maxChargingRate = maxChargingRate;
        this.phase1Millivolts = phase1Millivolts;
        this.phase2Millivolts = phase2Millivolts;
        this.phase3Millivolts = phase3Millivolts;
        this.phase1Milliamps = phase1Milliamps;
        this.phase2Milliamps = phase2Milliamps;
        this.phase3Milliamps = phase3Milliamps;
        this.systemUptime = systemUptime;
        this.wifiRSSI = wifiRSSI;
        this.utcTimestampInMs = utcTimestampInMs;
    }
}

export class EVChargingStationConfig {
    public mode: EvChargingMode;
    public loadSharingPriority: LoadSharingPriority;
    public skipPercentExpensiveHours: number;
    public chargeRateLimit: number;

    public constructor(mode: EvChargingMode, loadSharingPriority: LoadSharingPriority, skipPercentExpensiveHours: number, chargeRateLimit: number) {
        this.mode = mode;
        this.loadSharingPriority = loadSharingPriority;
        this.skipPercentExpensiveHours = skipPercentExpensiveHours;
        this.chargeRateLimit = chargeRateLimit;
    }
}

export enum ChargingState {
    Unconnected = "Unconnected",
    ConnectedChargingUnavailable = "ConnectedChargingUnavailable",
    ConnectedChargingAvailable = "ConnectedChargingAvailable",
    ChargingRequested = "ChargingRequested",
    Charging = "Charging",
    StoppingCharging = "StoppingCharging",
    Error = "Error"
}

export enum EvChargingMode {
    ON = "ON",
    OFF = "OFF",
    ChargeDuringCheapHours = "ChargeDuringCheapHours"
}

export enum ProximityPilotAmps {
    NoCable = "NoCable",
    Amp13 = "Amp13",
    Amp20 = "Amp20",
    Amp32 = "Amp32"
}

export enum LoadSharingPriority {
    HIGH = "HIGH",
    NORMAL = "NORMAL",
    LOW = "LOW"
}
