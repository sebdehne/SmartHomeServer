import {RpcRequest, RpcResponse} from "./Rpc";
import {Notify} from "./Subscription";

export class WebsocketMessage {
    public id: string;
    public type: WebsocketMessageType;
    public rpcRequest: RpcRequest | null;
    public rpcResponse: RpcResponse | null;
    public notify: Notify | null;

    public constructor(id: string, type: WebsocketMessageType, rpcRequest: RpcRequest | null, rpcResponse: RpcResponse | null, notify: Notify | null) {
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

