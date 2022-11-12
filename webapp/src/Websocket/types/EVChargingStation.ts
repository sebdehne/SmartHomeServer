export type EvChargingEventType = "clientConnectionsChanged";

export type EvChargingEvent = {
    eventType: EvChargingEventType;
    chargingStationsDataAndConfig: EvChargingStationDataAndConfig[];
}

export type EvChargingStationRequestType =
    "getChargingStationsDataAndConfig"
    | "uploadFirmwareToClient"
    | "setMode"
    | "setLoadSharingPriority"
    | "setChargeRateLimit";

export type EvChargingStationRequest = {
    type: EvChargingStationRequestType;
    clientId?: string;
    firmwareBased64Encoded?: string;
    newMode?: EvChargingMode;
    newLoadSharingPriority?: LoadSharingPriority;
    chargeRateLimit?: number;
}

export type EvChargingStationResponse = {
    chargingStationsDataAndConfig: EvChargingStationDataAndConfig[];
    uploadFirmwareToClientResult?: boolean;
    configUpdated?: boolean;
}

export type EvChargingStationDataAndConfig = {
    data: EvChargingStationData;
    config: EVChargingStationConfig;
    clientConnection: EvChargingStationClient;
}

export type EvChargingStationClient = {
    clientId: string;
    displayName: string;
    addr: string;
    port: number;
    firmwareVersion: number;
    powerConnectionId: string;
    connectedSince: number;
}

export type EvChargingStationData = {
    chargingState: ChargingState;
    reasonChargingUnavailable: string | null;
    chargingStateChangedAt: number;
    proximityPilotAmps: ProximityPilotAmps;
    maxChargingRate: number;
    phase1Millivolts: number;
    phase2Millivolts: number;
    phase3Millivolts: number;
    phase1Milliamps: number;
    phase2Milliamps: number;
    phase3Milliamps: number;
    systemUptime: number;
    wifiRSSI: number;
    utcTimestampInMs: number;
}

export type EVChargingStationConfig = {
    mode: EvChargingMode;
    loadSharingPriority: LoadSharingPriority;
    chargeRateLimit: number;
}

export type ChargingState =
    "Unconnected"
    | "ConnectedChargingUnavailable"
    | "ConnectedChargingAvailable"
    | "ChargingRequested"
    | "Charging"
    | "StoppingCharging"
    | "Error";

export type EvChargingMode = "ON" | "OFF" | "ChargeDuringCheapHours";

export type ProximityPilotAmps = "NoCable" | "Amp13" | "Amp20" | "Amp32";

export type LoadSharingPriority = "HIGH" | "NORMAL" | "LOW";
