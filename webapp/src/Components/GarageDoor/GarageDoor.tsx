import React, {useEffect, useState} from 'react';
import {Button, CircularProgress, Container, Paper, Typography} from "@material-ui/core";
import {useHistory} from "react-router-dom";
import WebsocketService from "../../websocketClient";
import ConnectionStatusComponent from "../ConnectionStatus";
import {GarageStatus, Notify, RequestType, RpcRequest, RpcResponse} from "../../api";
import {ArrowDownward, ArrowUpward} from "@material-ui/icons";

const GarageDoor = () => {
    let history = useHistory();
    const [garageStatus, setGarageStatus] = useState<GarageStatus | null>(null);
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);

    useEffect(() => {
        WebsocketService.rpc(new RpcRequest(RequestType.getGarageStatus, null, null))
            .then(response => {
                console.log("garage rpc:");
                console.log(response);

                setGarageStatus(response.garageStatus);
            });

        const subId = WebsocketService.subscribe(
            RequestType.getGarageStatus,
            (notify: Notify) => {
                console.log("garage notify:");
                console.log(notify);

                setGarageStatus(notify.garageStatus);
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const sendCmd = (cmd: RequestType) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            cmd,
            null,
            null
        )).then((response: RpcResponse) => {
            setCmdResult(response.garageCommandSendSuccess);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    };

    return (
        <Container maxWidth="sm" className="App">
            <Paper>
                <ConnectionStatusComponent/>
                <Button variant="text" color="secondary" onClick={() => {
                    history.push("/")
                }}>
                    Home
                </Button>

                <Typography>
                    <h2>Garage door controller</h2>
                </Typography>
                <div>
                    <ul>
                        <li>Door status: {garageStatus ? (garageStatus.doorIsOpen ? "open" : "closed") : "unknown"}</li>
                        <li>Light status: {garageStatus ? (garageStatus.lightIsOn ? "on" : "off") : "unknown"}</li>
                    </ul>
                </div>

                <div>
                    <Button variant="contained" color="secondary" onClick={() => sendCmd(RequestType.openGarageDoor)}>
                        <ArrowUpward/>
                    </Button>
                    <Button variant="contained" color="secondary" onClick={() => sendCmd(RequestType.closeGarageDoor)}>
                        <ArrowDownward/>
                    </Button>
                    {sending &&
                    <CircularProgress color="secondary"/>
                    }
                </div>
                {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
                {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

            </Paper>
        </Container>
    );
};

export default GarageDoor;
