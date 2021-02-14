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

    constructor(type: RequestType, subscribe: Subscribe | null, unsubscribe: Unsubscribe | null) {
        this.type = type;
        this.subscribe = subscribe;
        this.unsubscribe = unsubscribe;
    }
}

export class RpcResponse {
    public garageStatus: GarageStatus | null;
    public subscriptionCreated: boolean | null;
    public subscriptionRemoved: boolean | null;
    public garageCommandSendSuccess: boolean | null;

    public constructor(garageStatus: GarageStatus, subscriptionCreated: boolean, subscriptionRemoved: boolean, garageCommandSendSuccess: boolean) {
        this.garageStatus = garageStatus;
        this.subscriptionCreated = subscriptionCreated;
        this.subscriptionRemoved = subscriptionRemoved;
        this.garageCommandSendSuccess = garageCommandSendSuccess;
    }
}

export class Notify {
    public subscriptionId: string;
    public garageStatus: GarageStatus | null;

    public constructor(subscriptionId: string, garageStatus: GarageStatus | null) {
        this.subscriptionId = subscriptionId;
        this.garageStatus = garageStatus;
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
