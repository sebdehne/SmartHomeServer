export class GarageStatus {
    public lightIsOn: boolean;
    public doorIsOpen: boolean;
    public utcTimestampInMs: number;

    public constructor(lightIsOn: boolean, doorIsOpen: boolean, utcTimestampInMs: number) {
        this.lightIsOn = lightIsOn;
        this.doorIsOpen = doorIsOpen;
        this.utcTimestampInMs = utcTimestampInMs;
    }
}

export enum RequestType {
    getGarageStatus = "getGarageStatus",
    openGarageDoor = "openGarageDoor",
    closeGarageDoor = "closeGarageDoor",
    getUnderFloorHeaterStatus = "getUnderFloorHeaterStatus",
    updateUnderFloorHeaterMode = "updateUnderFloorHeaterMode",

    subscribe = "subscribe",
    unsubscribe = "unsubscribe"
}

export class Subscribe {
    public subscriptionId: String;
    public type: RequestType;


    public constructor(subscriptionId: String, type: RequestType) {
        this.subscriptionId = subscriptionId;
        this.type = type;
    }
}

export class Unsubscribe {
    public subscriptionId: string;

    public constructor(subscriptionId: string) {
        this.subscriptionId = subscriptionId;
    }
}

export class RpcRequest {
    public type: RequestType;
    public subscribe: Subscribe | null;
    public unsubscribe: Unsubscribe | null;
    public updateUnderFloorHeaterMode: UpdateUnderFloorHeaterMode | null;

    constructor(type: RequestType, subscribe: Subscribe | null, unsubscribe: Unsubscribe | null, updateUnderFloorHeaterMode: UpdateUnderFloorHeaterMode | null) {
        this.type = type;
        this.subscribe = subscribe;
        this.unsubscribe = unsubscribe;
        this.updateUnderFloorHeaterMode = updateUnderFloorHeaterMode;
    }
}

export class RpcResponse {
    public garageStatus: GarageStatus | null;
    public underFloorHeaterStatus: UnderFloorHeaterStatus | null;
    public subscriptionCreated: boolean | null;
    public subscriptionRemoved: boolean | null;
    public garageCommandSendSuccess: boolean | null;
    public updateUnderFloorHeaterModeSuccess: boolean | null;

    constructor(garageStatus: GarageStatus | null, underFloorHeaterStatus: UnderFloorHeaterStatus | null, subscriptionCreated: boolean | null, subscriptionRemoved: boolean | null, garageCommandSendSuccess: boolean | null, updateUnderFloorHeaterModeSuccess: boolean | null) {
        this.garageStatus = garageStatus;
        this.underFloorHeaterStatus = underFloorHeaterStatus;
        this.subscriptionCreated = subscriptionCreated;
        this.subscriptionRemoved = subscriptionRemoved;
        this.garageCommandSendSuccess = garageCommandSendSuccess;
        this.updateUnderFloorHeaterModeSuccess = updateUnderFloorHeaterModeSuccess;
    }
}

export class Notify {
    public subscriptionId: string;
    public garageStatus: GarageStatus | null;
    public underFloorHeaterStatus: UnderFloorHeaterStatus | null;


    constructor(subscriptionId: string, garageStatus: GarageStatus | null, underFloorHeaterStatus: UnderFloorHeaterStatus | null) {
        this.subscriptionId = subscriptionId;
        this.garageStatus = garageStatus;
        this.underFloorHeaterStatus = underFloorHeaterStatus;
    }
}

export class WebsocketMessage {
    public id: String;
    public type: WebsocketMessageType;
    public rpcRequest: RpcRequest | null;
    public rpcResponse: RpcResponse | null;
    public notify: Notify | null;


    public constructor(id: String, type: WebsocketMessageType, rpcRequest: RpcRequest | null, rpcResponse: RpcResponse | null, notify: Notify | null) {
        this.id = id;
        this.type = type;
        this.rpcRequest = rpcRequest;
        this.rpcResponse = rpcResponse;
        this.notify = notify;
    }
}

export enum WebsocketMessageType {
    rpcRequest = "rpcRequest",
    rpcResponse = "rpcResponse",
    notify = "notify"
}

export enum UnderFloorHeaterMode {
    permanentOn = "permanentOn",
    permanentOff = "permanentOff",
    constantTemperature = "constantTemperature"
}

export enum OnOff {
    on = "on",
    off = "off"
}

export class UnderFloorHeaterConstantTemperaturStatus {
    public targetTemperatur: number;

    constructor(targetTemperatur: number) {
        this.targetTemperatur = targetTemperatur;
    }
}

export class UnderFloorHeaterStatus {
    public mode: UnderFloorHeaterMode;
    public status: OnOff;
    public currentTemperatur: number;
    public constantTemperaturStatus: UnderFloorHeaterConstantTemperaturStatus;
    public utcTimestampInMs: number;

    constructor(mode: UnderFloorHeaterMode, status: OnOff, currentTemperatur: number, constantTemperaturStatus: UnderFloorHeaterConstantTemperaturStatus, utcTimestampInMs: number) {
        this.mode = mode;
        this.status = status;
        this.currentTemperatur = currentTemperatur;
        this.constantTemperaturStatus = constantTemperaturStatus;
        this.utcTimestampInMs = utcTimestampInMs;
    }
}

export class UpdateUnderFloorHeaterMode {
    public newMode: UnderFloorHeaterMode;
    public newTargetTemperatur: number;

    constructor(newMode: UnderFloorHeaterMode, newTargetTemperatur: number) {
        this.newMode = newMode;
        this.newTargetTemperatur = newTargetTemperatur;
    }
}
