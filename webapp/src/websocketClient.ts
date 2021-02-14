import {plainToClass, serialize} from 'class-transformer';

import {v4 as uuidv4} from 'uuid';
import {
    Notify,
    RequestType,
    RpcRequest,
    RpcResponse,
    Subscribe,
    Unsubscribe,
    WebsocketMessage,
    WebsocketMessageType
} from "./api";

export enum ConnectionStatus {
    connected = "connected",
    connecting = "connecting",
    closed = "closed"
}

const OngoingRPCsById = new Map<String, Rpc>();
const SubscriptionsById = new Map<String, Subscription>();
const ConnectionStatusSubscriptionsById = new Map<String, (connectionStatus: ConnectionStatus) => void>();
let ws: WebSocket | undefined = undefined;
let connectionStatus: ConnectionStatus = ConnectionStatus.closed;

function subscribe(type: RequestType, onNotify: (notify: Notify) => void) {
    const subscriptionId = uuidv4();

    SubscriptionsById.set(subscriptionId, new Subscription(type, onNotify));

    rpc(new RpcRequest(RequestType.subscribe, new Subscribe(subscriptionId, type), null, null)).then(response => {
        // OK
    });

    return subscriptionId;
}

function unsubscribe(subscriptionId: string) {

    const sub = SubscriptionsById.get(subscriptionId);
    if (!sub) {
        return;
    }

    rpc(new RpcRequest(RequestType.unsubscribe, null, new Unsubscribe(subscriptionId), null)).then(response => {
        SubscriptionsById.delete(subscriptionId);
    });

}

function rpc(rpcRequest: RpcRequest): Promise<RpcResponse> {
    const msgId = uuidv4();
    return new Promise<RpcResponse>((resolve, reject) => {
        const rpc = new Rpc(
            resolve,
            new WebsocketMessage(
                msgId,
                WebsocketMessageType.rpcRequest,
                rpcRequest,
                null,
                null
            )
        )

        OngoingRPCsById.set(msgId, rpc);

        send(rpc.msg);
    });
}

function send(msg: WebsocketMessage) {
    if (connectionStatus == ConnectionStatus.closed) {
        reconnect();
    } else if (connectionStatus == ConnectionStatus.connected) {
        try {
            ws?.send(serialize(msg));
        } catch (e) {
            console.log(e);
        }
    }
}

function setConnectionStatusChanged(newState: ConnectionStatus) {
    connectionStatus = newState;
    ConnectionStatusSubscriptionsById.forEach((value, key) => {
        value(newState);
    });
}

function reconnect() {
    setConnectionStatusChanged(ConnectionStatus.connecting);
    // @ts-ignore
    const urlTemplate: string = process.env.REACT_APP_WEBSOCKET_ENDPOINT;
    const wsUrl = urlTemplate.replace("HOST", window.location.host);
    console.log("Connecting to: " + wsUrl);

    ws = new WebSocket(wsUrl);
    ws.onopen = function () {
        setConnectionStatusChanged(ConnectionStatus.connected);
        // re-subscribe
        SubscriptionsById.forEach((sub, key) => {
            rpc(new RpcRequest(RequestType.subscribe, new Subscribe(key, sub.type), null, null)).then(response => {
                // OK
            });
        })

        // re-send ongoing RPCs
        OngoingRPCsById.forEach((ongoingRPC, key) => {
            send(ongoingRPC.msg);
        });
    };
    ws.onmessage = function (e) {
        let plain = JSON.parse(e.data);
        // @ts-ignore
        const json: WebsocketMessage = plainToClass(WebsocketMessage, plain);
        if (json.type === WebsocketMessageType.rpcResponse) {
            let ongoingRPC = OngoingRPCsById.get(json.id);
            if (ongoingRPC) {
                ongoingRPC.resolve(json.rpcResponse!!);
            }
        } else if (json.type === WebsocketMessageType.notify) {
            let notify = json.notify!!;
            const subId = notify.subscriptionId;
            let subscription = SubscriptionsById.get(subId);
            if (subscription) {
                subscription.onNotify(notify);
            }
        } else {
            console.log("Dont know what to do received websocket msg:");
            console.log(json);
        }
    };

    ws.onclose = function (e) {
        setConnectionStatusChanged(ConnectionStatus.closed);
        setTimeout(function () {
            reconnect();
        }, 1000);
    };

    ws.onerror = function (err) {
        ws?.close();
    };
}

const WebsocketService = {
    rpc,
    subscribe,
    unsubscribe,
    monitorConnectionStatus: (fn: (connectionStatis: ConnectionStatus) => void) => {
        const id = uuidv4();
        ConnectionStatusSubscriptionsById.set(id, fn);
        fn(connectionStatus);
        return () => {
            ConnectionStatusSubscriptionsById.delete(id);
        };
    }
};

export default WebsocketService;


class Subscription {
    public type: RequestType;
    public onNotify: (notify: Notify) => void;

    public constructor(type: RequestType, onNotify: (notify: Notify) => void) {
        this.type = type;
        this.onNotify = onNotify;
    }
}

class Rpc {
    public resolve: (rpcResponse: RpcResponse) => void;
    public msg: WebsocketMessage;

    public constructor(resolve: (rpcResponse: RpcResponse) => void, msg: WebsocketMessage) {
        this.resolve = resolve;
        this.msg = msg;
    }
}