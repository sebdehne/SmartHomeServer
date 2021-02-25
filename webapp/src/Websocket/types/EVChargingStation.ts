export enum EvChargingStationRequestType {
    getConnectedClients = "getConnectedClients",
    uploadFirmwareToClient = "uploadFirmwareToClient"
}

export class EvChargingStationRequest {
    public type: EvChargingStationRequestType;
    public clientId: string | null;
    public firmwareBased64Encoded: string | null;

    public constructor(type: EvChargingStationRequestType, clientId: string | null, firmwareBased64Encoded: string | null) {
        this.type = type;
        this.clientId = clientId;
        this.firmwareBased64Encoded = firmwareBased64Encoded;
    }
}

export class EvChargingStationResponse {
    public connectedClients: EvChargingStationClient[] | null;
    public uploadFirmwareToClientResult: boolean | null;

   public constructor(connectedClients: EvChargingStationClient[] | null, uploadFirmwareToClientResult: boolean | null) {
        this.connectedClients = connectedClients;
        this.uploadFirmwareToClientResult = uploadFirmwareToClientResult;
    }
}

export class EvChargingStationClient {
    public clientId: string;
    public addr: string;
    public port: number;
    public firmwareVersion: number;

    public constructor(clientId: string, addr: string, port: number, firmwareVersion: number) {
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