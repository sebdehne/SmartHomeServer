import { v4 as uuidv4 } from 'uuid';
import { RpcRequest, RpcResponse } from "./types/Rpc";
import { Notify, SubscriptionType } from "./types/Subscription";
import { WebsocketMessage } from "./types/WebsocketMessage";
import { UserRole, UserSettings } from "./types/UserSettings";

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
let userSettings: UserSettings | undefined = undefined;

const userCanRead = (r: UserRole) => {
    const l = userSettings?.authorization?.[r]!!;
    return l === "read" || l === "readWrite";
}

const userCanWrite = (r: UserRole) => {
    const l = userSettings?.authorization?.[r]!!;
    return l === "readWrite";
}

function subscribe(type: SubscriptionType, onNotify: (notify: Notify) => void, onOpened: () => void) {
    const subscriptionId = uuidv4();

    SubscriptionsById.set(subscriptionId, new Subscription(type, onNotify, onOpened));

    rpc({
        type: "subscribe",
        subscribe: { type, subscriptionId }
    }).then(() => {
        onOpened();
    });

    return subscriptionId;
}

function unsubscribe(subscriptionId: string) {

    const sub = SubscriptionsById.get(subscriptionId);
    if (!sub) {
        return;
    }

    rpc({
        type: "unsubscribe",
        unsubscribe: { subscriptionId }
    }).then(() => {
        SubscriptionsById.delete(subscriptionId);
    });

}

function rpc(rpcRequest: RpcRequest): Promise<RpcResponse> {
    const msgId = uuidv4();
    return new Promise<RpcResponse>((resolve) => {
        const rpc = new Rpc(
            resolve,
            {
                id: msgId,
                type: "rpcRequest",
                rpcRequest
            }
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
            if (ongoingRPC.msg.rpcRequest?.type === "subscribe") {
                subscriptions.push(ongoingRPC.msg.rpcRequest.subscribe!!.subscriptionId)
            }
        });

        // (re-fetch userSettings)
        rpc({
            type: "userSettings"
        }).then(resp => userSettings = resp.userSettings);

        SubscriptionsById.forEach((sub, key) => {
            console.log("onopen - re-subscribe: " + key)
            if (subscriptions.indexOf(key) < 0) {
                rpc({
                    type: "subscribe",
                    subscribe: { subscriptionId: key, type: sub.type }
                }).then(() => {
                    sub.onOpened();
                });
            }
        })

    };
    ws.onmessage = function (e) {
        const json: WebsocketMessage = JSON.parse(e.data);
        if (json.type === "rpcResponse") {
            let ongoingRPC = OngoingRPCsById.get(json.id);
            if (ongoingRPC) {
                OngoingRPCsById.delete(json.id);
                ongoingRPC.resolve(json.rpcResponse!!);
            }
        } else if (json.type === "notify") {
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
    userCanWrite,
    userCanRead,
    monitorConnectionStatus: (fn: (connectionStatis: ConnectionStatus) => void) => {
        const id = uuidv4();
        ConnectionStatusSubscriptionsById.set(id, fn);
        fn(connectionStatus);
        return () => {
            ConnectionStatusSubscriptionsById.delete(id);
        };
    }
};

export const useUserSettings = () => {
    return ({
        userCanWrite,
        userCanRead
    })
}

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