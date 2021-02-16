import React, {useEffect, useState} from 'react';
import {Button, CircularProgress, Container, Grid, Paper, Typography} from "@material-ui/core";
import {useHistory} from "react-router-dom";
import WebsocketService from "../../websocketClient";
import ConnectionStatusComponent from "../ConnectionStatus";
import {DoorStatus, GarageStatus, Notify, RequestType, RpcRequest, RpcResponse} from "../../api";
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
        const subId = WebsocketService.subscribe(
            RequestType.getGarageStatus,
            (notify: Notify) => setGarageStatus(notify.garageStatus),
            () => {
                WebsocketService.rpc(new RpcRequest(RequestType.getGarageStatus, null, null, null, null))
                    .then(response => setGarageStatus(response.garageStatus));
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const adjustAutoClose = (deltaInMinutes: number) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.garageDoorExtendAutoClose,
            null,
            null,
            null,
            deltaInMinutes * 60
        )).then((response: RpcResponse) => {
            setCmdResult(true);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
            setGarageStatus(response.garageStatus);
        }).finally(() => setSending(false));
    }

    const sendCmd = (cmd: RequestType) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            cmd,
            null,
            null,
            null,
            null
        )).then((response: RpcResponse) => {
            setGarageStatus(response.garageStatus);
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
                                onClick={() => sendCmd(RequestType.openGarageDoor)}>
                                <ArrowUpward/> Open
                            </Button>
                        </Grid>
                        <Grid item xs={3}>
                            <Button
                                style={{margin: "10px"}} variant="contained" color="secondary"
                                onClick={() => sendCmd(RequestType.closeGarageDoor)}>
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
const DoorStatusComponent: React.FC<DoorStatusComponentProps> = ({doorStatus}) => {
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