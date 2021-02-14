import {useEffect, useState} from "react";
import WebsocketService, {ConnectionStatus} from "../websocketClient";


const ConnectionStatusComponent = () => {
    const [status, setStatus] = useState<ConnectionStatus>(ConnectionStatus.connecting);
    useEffect(() =>
        WebsocketService.monitorConnectionStatus((status: ConnectionStatus) => {
            setStatus(status);
        }), []);

    return <p>Server connection: {status}</p>
};

export default ConnectionStatusComponent;