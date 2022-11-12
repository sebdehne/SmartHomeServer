import {RpcRequest, RpcResponse} from "./Rpc";
import {Notify} from "./Subscription";

export type WebsocketMessage = {
    id: string;
    type: WebsocketMessageType;
    rpcRequest?: RpcRequest;
    rpcResponse?: RpcResponse;
    notify?: Notify;
}

export type WebsocketMessageType = 'rpcRequest' | 'rpcResponse' | 'notify';

