export enum EvChargingStationRequestType {
    getConnectedClients = "getConnectedClients",
    listAllFirmwareVersions = "listAllFirmwareVersions",
    uploadFirmwareToClient = "uploadFirmwareToClient"
}

export class EvChargingStationRequest {
    public type: EvChargingStationRequestType;
    public clientId: number | null;
    public firmwareVersion: string | null;

    public constructor(type: EvChargingStationRequestType, clientId: number | null, firmwareVersion: string | null) {
        this.type = type;
        this.clientId = clientId;
        this.firmwareVersion = firmwareVersion;
    }
}

export class EvChargingStationResponse {
    public connectedClients: EvChargingStationClient[] | null;
    public allFirmwareVersions: string[] | null;
    public uploadFirmwareToClientResult: boolean | null;

    public constructor(connectedClients: EvChargingStationClient[] | null, allFirmwareVersions: string[] | null, uploadFirmwareToClientResult: boolean | null) {
        this.connectedClients = connectedClients;
        this.allFirmwareVersions = allFirmwareVersions;
        this.uploadFirmwareToClientResult = uploadFirmwareToClientResult;
    }
}

export class EvChargingStationClient {
    public clientId: number;
    public addr: string;
    public port: number;
    public firmwareVersion: number;

    public constructor(clientId: number, addr: string, port: number, firmwareVersion: number) {
        this.clientId = clientId;
        this.addr = addr;
        this.port = port;
        this.firmwareVersion = firmwareVersion;
    }
}

export enum EventType {
    clientConnectionsChanged = "clientConnectionsChanged"
}

export class Event {
    public eventType: EventType;
    public connectedClients: EvChargingStationClient[] | null;


   public constructor(eventType: EventType, connectedClients: EvChargingStationClient[] | null) {
        this.eventType = eventType;
        this.connectedClients = connectedClients;
    }
}