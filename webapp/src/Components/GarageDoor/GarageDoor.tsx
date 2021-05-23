import React, { useEffect, useState } from 'react';
import { Button, Container, Grid } from "@material-ui/core";
import WebsocketService from "../../Websocket/websocketClient";
import Header from "../Header";
import { ArrowDownward, ArrowUpward } from "@material-ui/icons";
import { DoorStatus, GarageRequest, GarageRequestType, GarageResponse } from "../../Websocket/types/Garage";
import { RequestType, RpcRequest, RpcResponse } from "../../Websocket/types/Rpc";
import { Notify, SubscriptionType } from "../../Websocket/types/Subscription";
import { FirmwareUpload } from "./FirmwareUpload";
import { FirmwareUpgradeState } from "../../Websocket/types/EnvironmentSensors";

const GarageDoor = () => {
    const [garageStatus, setGarageStatus] = useState<GarageResponse | null>(null);
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
                        null,
                        null
                    ),
                    null,
                    null,
                    null))
                    .then(response => setGarageStatus(response.garageResponse!!));
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
                deltaInMinutes * 60,
                null
            ),
            null,
            null,
            null
        )).then((response: RpcResponse) => {
            setCmdResult(true);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
            setGarageStatus(response.garageResponse!!);
        }).finally(() => setSending(false));
    }

    const adjustTime = () => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.garageRequest,
            null,
            null,
            new GarageRequest(
                GarageRequestType.adjustTime,
                null,
                null
            ),
            null,
            null,
            null
        )).then((response: RpcResponse) => {
            setCmdResult(response.garageResponse!!.garageCommandAdjustTimeSuccess!!);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    }

    const sendCmd = (cmd: GarageRequestType) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.garageRequest,
            null,
            null,
            new GarageRequest(cmd, null, null),
            null,
            null,
            null
        )).then((response: RpcResponse) => {
            setGarageStatus(response.garageResponse!!);
            setCmdResult(response.garageResponse!!.garageCommandSendSuccess);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    };

    return (
        <Container maxWidth="sm" className="App">
            <Header
                sending={sending}
                title="Garage door controller"
            />

            {garageStatus?.firmwareUpgradeState &&
            <div>
                <ul>
                    <li>Progress: {firmwareUpgradeProgress(garageStatus.firmwareUpgradeState)}%</li>
                    <li>Clock slew: {garageStatus.firmwareUpgradeState.timestampDelta} seconds</li>
                    <li>Received: {timeToDelta(currentSeconds, garageStatus.firmwareUpgradeState.receivedAt)} ago</li>
                    <li>Rssi: {garageStatus.firmwareUpgradeState.rssi}dB</li>
                </ul>
            </div>
            }
            {garageStatus?.garageStatus &&
            <div>
                <ul>
                    <li>Door status: <DoorStatusComponent doorStatus={garageStatus.garageStatus.doorStatus}/></li>
                    <li>Light status: {garageStatus.garageStatus.lightIsOn ? "on" : "off"}</li>
                    <li>Clock slew: {garageStatus.garageStatus.timestampDelta} seconds</li>
                    <li>Firmware: {garageStatus.garageStatus.firmwareVersion}</li>
                    <li>Auto closing in: {
                        !garageStatus.garageStatus.autoCloseAfter ? "disabled" : (
                            timeToDelta(garageStatus.garageStatus.autoCloseAfter, currentSeconds)
                        )
                    }</li>
                </ul>
                <p>Updated: {timeToDelta(currentSeconds, garageStatus.garageStatus.utcTimestampInMs)} ago</p>
            </div>
            }
            {!garageStatus?.garageStatus && !garageStatus?.firmwareUpgradeState &&
            <p>Status currently not available</p>}

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
                            style={{ margin: "10px" }} variant="contained" color="primary"
                            onClick={() => sendCmd(GarageRequestType.openGarageDoor)}>
                            <ArrowUpward/> Open
                        </Button>
                    </Grid>
                    <Grid item xs={3}>
                        <Button
                            style={{ margin: "10px" }} variant="contained" color="primary"
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
                        <div style={{ marginTop: "20px" }}>Adjust auto close:</div>
                    </Grid>
                    <Grid item xs={2}>
                        <Button
                            style={{ margin: "10px" }} variant="contained" color="primary"
                            onClick={() => adjustAutoClose(-60)}>
                            -1 hour
                        </Button>
                    </Grid>
                    <Grid item xs={2}>
                        <Button
                            style={{ margin: "10px" }} variant="contained" color="primary"
                            onClick={() => adjustAutoClose(-10)}>
                            -10 min
                        </Button>
                    </Grid>
                    <Grid item xs={2}>
                        <Button style={{ margin: "10px" }} variant="contained" color="primary"
                                onClick={() => adjustAutoClose(10)}>
                            +10 min
                        </Button>
                    </Grid>
                    <Grid item xs={2}>
                        <Button style={{ margin: "10px" }} variant="contained" color="primary"
                                onClick={() => adjustAutoClose(60)}>
                            +1 hour
                        </Button>
                    </Grid>
                </Grid>
                <Grid
                    container
                    direction="row"
                >
                    <Grid item xs={4}>
                        <div style={{ marginTop: "20px" }}>Admin:</div>
                    </Grid>
                    <Grid item xs={4}>
                        <Button
                            style={{ margin: "10px" }} variant="contained" color="primary"
                            onClick={() => adjustTime()}>
                            Adjust time
                        </Button>
                    </Grid>
                    <Grid item xs={4}>
                        <FirmwareUpload setCmdResult={setCmdResult} setSending={setSending}/>
                    </Grid>
                </Grid>
            </Grid>


            {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
            {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

        </Container>
    );
};

export default GarageDoor;

export function timeToDelta(left: number, right: number): string {
    const deltaInSeconds = (left - right) / 1000;
    if (deltaInSeconds < 60) {
        return `${Math.round(deltaInSeconds)} seconds`;
    } else {
        const min = deltaInSeconds / 60;
        const seconds = deltaInSeconds % 60;
        return `${Math.floor(min)} minutes, ${Math.floor(seconds)} seconds`;
    }
}

type DoorStatusComponentProps = {
    doorStatus: DoorStatus
}
const DoorStatusComponent = ({ doorStatus }: DoorStatusComponentProps) => {
    return <>
        {doorStatus === DoorStatus.doorOpen && <span style={{
            color: "#ff0000",
            fontWeight: "bold"
        }}>Open</span>}
        {doorStatus === DoorStatus.doorClosed && <span style={{
            color: "#00ff07",
            fontWeight: "bold"
        }}>Closed</span>}
        {doorStatus === DoorStatus.doorOpening && <span style={{
            color: "#ff0000",
            fontWeight: "bold"
        }}>Opening &#11016;</span>}
        {doorStatus === DoorStatus.doorClosing && <span style={{
            color: "#00ff07",
            fontWeight: "bold"
        }}>Closing &#11018;</span>}
        {doorStatus === DoorStatus.doorMiddle && <span style={{
            color: "#ff0000",
            fontWeight: "bold"
        }}>Middle &#11016;</span>}
    </>
};

const firmwareUpgradeProgress = (firmwareUpgradeState: FirmwareUpgradeState) => {
    return (firmwareUpgradeState!!.offsetRequested * 100 / firmwareUpgradeState!!.firmwareSize).toFixed(2);
}
