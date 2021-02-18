import React, {useEffect, useState} from 'react';
import {Button, CircularProgress, Container, Grid, Paper, Typography} from "@material-ui/core";
import {useHistory} from "react-router-dom";
import WebsocketService from "../../Websocket/websocketClient";
import ConnectionStatusComponent from "../ConnectionStatus";
import {ArrowDownward, ArrowUpward} from "@material-ui/icons";
import {DoorStatus, GarageRequest, GarageRequestType, GarageStatus} from "../../Websocket/types/Garage";
import {RequestType, RpcRequest, RpcResponse} from "../../Websocket/types/Rpc";
import {Notify, SubscriptionType} from "../../Websocket/types/Subscription";

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
        const subId = WebsocketService.subscribe(
            SubscriptionType.getGarageStatus,
            (notify: Notify) => setGarageStatus(notify.garageStatus),
            () => {
                WebsocketService.rpc(new RpcRequest(
                    RequestType.garageRequest,
                    null,
                    null,
                    new GarageRequest(
                        GarageRequestType.getGarageStatus,
                        null
                    ),
                    null,
                    null))
                    .then(response => setGarageStatus(response.garageResponse!!.garageStatus));
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const adjustAutoClose = (deltaInMinutes: number) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.garageRequest,
            null,
            null,
            new GarageRequest(
                GarageRequestType.garageDoorExtendAutoClose,
                deltaInMinutes * 60
            ),
            null,
            null
        )).then((response: RpcResponse) => {
            setCmdResult(true);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
            setGarageStatus(response.garageResponse!!.garageStatus);
        }).finally(() => setSending(false));
    }

    const sendCmd = (cmd: GarageRequestType) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.garageRequest,
            null,
            null,
            new GarageRequest(cmd, null),
            null,
            null
        )).then((response: RpcResponse) => {
            setGarageStatus(response.garageResponse!!.garageStatus);
            setCmdResult(response.garageResponse!!.garageCommandSendSuccess);
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
                    <h2 style={{display: "inline"}}>Garage door controller</h2>
                    {sending &&
                    <CircularProgress color="secondary"/>
                    }
                </Typography>
                {garageStatus &&
                <div>
                    <ul>
                        <li>Door status: <DoorStatusComponent doorStatus={garageStatus.doorStatus}/></li>
                        <li>Light status: {garageStatus.lightIsOn ? "on" : "off"}</li>
                        <li>Auto closing in: {
                            !garageStatus.autoCloseAfter ? "disabled" : (
                                timeToDelta(garageStatus.autoCloseAfter, currentSeconds)
                            )
                        }</li>
                    </ul>
                    <p>Updated: {timeToDelta(currentSeconds, garageStatus.utcTimestampInMs)} ago</p>
                </div>
                }
                {!garageStatus && <p>Status currently not available</p>}

                <Grid
                    container
                    direction="column"
                    justify="space-between"
                    alignItems="center"
                >
                    <Grid
                        container
                        direction="row"
                    >
                        <Grid item xs={4}/>
                        <Grid item xs={3}>
                            <Button
                                style={{margin: "10px"}} variant="contained" color="secondary"
                                onClick={() => sendCmd(GarageRequestType.openGarageDoor)}>
                                <ArrowUpward/> Open
                            </Button>
                        </Grid>
                        <Grid item xs={3}>
                            <Button
                                style={{margin: "10px"}} variant="contained" color="secondary"
                                onClick={() => sendCmd(GarageRequestType.closeGarageDoor)}>
                                <ArrowDownward/> Close
                            </Button>
                        </Grid>
                    </Grid>
                    <Grid
                        container
                        direction="row"
                    >
                        <Grid item xs={3}>
                            <div style={{marginTop: "20px"}}>Adjust auto close:</div>
                        </Grid>
                        <Grid item xs={2}>
                            <Button
                                style={{margin: "10px"}} variant="contained" color="secondary"
                                onClick={() => adjustAutoClose(-10)}>
                                -10 min
                            </Button>
                        </Grid>
                        <Grid item xs={2}>
                            <Button
                                style={{margin: "10px"}} variant="contained" color="secondary"
                                onClick={() => adjustAutoClose(-1)}>
                                -1 min
                            </Button>
                        </Grid>
                        <Grid item xs={2}>
                            <Button style={{margin: "10px"}} variant="contained" color="secondary"
                                    onClick={() => adjustAutoClose(1)}>
                                +1 min
                            </Button>
                        </Grid>
                        <Grid item xs={2}>
                            <Button style={{margin: "10px"}} variant="contained" color="secondary"
                                    onClick={() => adjustAutoClose(10)}>
                                +10 min
                            </Button>
                        </Grid>
                    </Grid>

                </Grid>


                {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
                {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

            </Paper>
        </Container>
    );
};

export default GarageDoor;

export function timeToDelta(left: number, right: number): string {
    const deltaInSeconds = (left - right) / 1000;
    if (deltaInSeconds < 60) {
        return `${Math.round(deltaInSeconds)} seconds`;
    } else if (deltaInSeconds < 120) {
        return "1 minute";
    } else {
        return `${Math.round(deltaInSeconds / 60)} minutes`;
    }
}

type DoorStatusComponentProps = {
    doorStatus: DoorStatus
}
const DoorStatusComponent = ({doorStatus}: DoorStatusComponentProps) => {
    return <>
        {doorStatus === DoorStatus.doorOpen && <span style={{
            color: "#780000",
            fontWeight: "bold"
        }}>Open</span>}
        {doorStatus === DoorStatus.doorClosed && <span style={{
            color: "#007003",
            fontWeight: "bold"
        }}>Closed</span>}
        {doorStatus === DoorStatus.doorOpening && <span style={{
            color: "#780000",
            fontWeight: "bold"
        }}>Opening &#11016;</span>}
        {doorStatus === DoorStatus.doorClosing && <span style={{
            color: "#007003",
            fontWeight: "bold"
        }}>Closing &#11018;</span>}
    </>
};