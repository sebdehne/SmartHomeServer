import { v4 as uuidv4 } from 'uuid';
import { RequestType, RpcRequest, RpcResponse } from "./types/Rpc";
import { Notify, Subscribe, SubscriptionType, Unsubscribe } from "./types/Subscription";
import { WebsocketMessage, WebsocketMessageType } from "./types/WebsocketMessage";

export enum ConnectionStatus {
    connected = "connected",
    connecting = "connecting",
    closed = "closed"
}

const OngoingRPCsById = new Map<string, Rpc>();
const SubscriptionsById = new Map<string, Subscription>();
const ConnectionStatusSubscriptionsById = new Map<string, (connectionStatus: ConnectionStatus) => void>();
let ws: WebSocket | undefined = undefined;
let connectionStatus: ConnectionStatus = ConnectionStatus.closed;

function subscribe(type: SubscriptionType, onNotify: (notify: Notify) => void, onOpened: () => void) {
    const subscriptionId = uuidv4();

    SubscriptionsById.set(subscriptionId, new Subscription(type, onNotify, onOpened));

    rpc(new RpcRequest(RequestType.subscribe, new Subscribe(subscriptionId, type), null, null, null, null, null, null)).then(() => {
        onOpened();
    });

    return subscriptionId;
}

function unsubscribe(subscriptionId: string) {

    const sub = SubscriptionsById.get(subscriptionId);
    if (!sub) {
        return;
    }

    rpc(new RpcRequest(RequestType.unsubscribe, null, new Unsubscribe(subscriptionId), null, null, null, null, null)).then(() => {
        SubscriptionsById.delete(subscriptionId);
    });

}

function rpc(rpcRequest: RpcRequest): Promise<RpcResponse> {
    const msgId = uuidv4();
    return new Promise<RpcResponse>((resolve) => {
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
    if (connectionStatus === ConnectionStatus.closed) {
        reconnect();
    } else if (connectionStatus === ConnectionStatus.connected) {
        try {
            ws?.send(JSON.stringify(msg));
        } catch (e) {
            console.log(e);
        }
    }
}

function setConnectionStatusChanged(newState: ConnectionStatus) {
    connectionStatus = newState;
    ConnectionStatusSubscriptionsById.forEach((value) => {
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
        let subscriptions = new Array<string>();
        // re-send ongoing RPCs
        OngoingRPCsById.forEach((ongoingRPC) => {
            send(ongoingRPC.msg);
            if (ongoingRPC.msg.rpcRequest?.type === RequestType.subscribe) {
                subscriptions.push(ongoingRPC.msg.rpcRequest.subscribe!!.subscriptionId)
            }
        });

        SubscriptionsById.forEach((sub, key) => {
            console.log("onopen - re-subscribe: " + key)
            if (subscriptions.indexOf(key) < 0) {
                rpc(new RpcRequest(RequestType.subscribe, new Subscribe(key, sub.type), null, null, null, null, null, null)).then(() => {
                    sub.onOpened();
                });
            }
        })

    };
    ws.onmessage = function (e) {
        const json: WebsocketMessage = JSON.parse(e.data);
        if (json.type === WebsocketMessageType.rpcResponse) {
            let ongoingRPC = OngoingRPCsById.get(json.id);
            if (ongoingRPC) {
                OngoingRPCsById.delete(json.id);
                ongoingRPC.resolve(json.rpcResponse!!);
            }
        } else if (json.type === WebsocketMessageType.notify) {
            let notify = json.notify!!;
            const subId = notify.subscriptionId;
            let subscription = SubscriptionsById.get(subId);
            if (subscription) {
                subscription.onNotify(notify);
            } else {
                console.log("Could not send notify - sub not found");
            }
        } else {
            console.log("Dont know what to do received websocket msg:");
            console.log(json);
        }
    };

    ws.onclose = function () {
        setConnectionStatusChanged(ConnectionStatus.closed);
        setTimeout(function () {
            reconnect();
        }, 1000);
    };

    ws.onerror = (event) => {
        console.log(event);
        ws?.close();
    };

    console.log("Finished configuring WebSocket");
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
    public type: SubscriptionType;
    public onNotify: (notify: Notify) => void;
    public onOpened: () => void;


    public constructor(type: SubscriptionType, onNotify: (notify: Notify) => void, onOpened: () => void) {
        this.type = type;
        this.onNotify = onNotify;
        this.onOpened = onOpened;
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