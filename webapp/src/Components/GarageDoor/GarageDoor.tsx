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
    const [currentSeconds, setCurrentSeconds] = useState(Date.now());

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds])

    useEffect(() => {
        WebsocketService.rpc(new RpcRequest(RequestType.getGarageStatus, null, null, null))
            .then(response => setGarageStatus(response.garageStatus));

        const subId = WebsocketService.subscribe(
            RequestType.getGarageStatus,
            (notify: Notify) => setGarageStatus(notify.garageStatus)
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const sendCmd = (cmd: RequestType) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            cmd,
            null,
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
                {garageStatus &&
                <div>
                    <ul>
                        <li>Door status: {garageStatus.doorIsOpen ? "open" : "closed"}</li>
                        <li>Light status: {garageStatus.lightIsOn ? "on" : "off"}</li>
                    </ul>
                    <p>Updated: {timeToDelta(currentSeconds, garageStatus.utcTimestampInMs)} ago</p>
                </div>
                }
                {!garageStatus && <p>Status currently not available</p>}

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

export function timeToDelta(now: number, utcTimestampInMs: number): string {
    const deltaInSeconds = (now - utcTimestampInMs) / 1000;
    if (deltaInSeconds < 60) {
        return `${Math.round(deltaInSeconds)} seconds`;
    } else if (deltaInSeconds < 120) {
        return "1 minute";
    } else {
        return `${Math.round(deltaInSeconds / 60) } minutes`;
    }
}