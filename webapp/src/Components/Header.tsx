import React, { useEffect, useState } from "react";
import WebsocketService, { ConnectionStatus } from "../Websocket/websocketClient";
import { Button, CircularProgress } from "@material-ui/core";
import { useNavigate } from "react-router-dom";
import ArrowBackIcon from '@material-ui/icons/ArrowBack';

type HeaderProps = {
    title: string,
    sending: boolean,
    showBackButton?: boolean
}

const Header = ({ title, sending, showBackButton = true }: HeaderProps) => {
    const [status, setStatus] = useState<ConnectionStatus>(ConnectionStatus.connecting);
    const navigate = useNavigate();

    useEffect(() =>
        WebsocketService.monitorConnectionStatus((status: ConnectionStatus) => {
            setStatus(status);
        }), []);

    return <>
        <div style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between"
        }
        }>
            {showBackButton &&
                <Button color="primary" variant="contained" onClick={() => navigate("/")}><ArrowBackIcon/>Home</Button>
            }
            {!showBackButton && <span>&nbsp;</span>}
            {sending &&
                <CircularProgress color="primary"/>
            }
            <span>Server connection: {status}</span>
        </div>
        <h2 style={{ textAlign: "center" }}>{title}</h2>

    </>
};

export default Header;