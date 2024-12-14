import React, { useEffect, useState } from 'react';
import {
    Button,
    ButtonGroup,
    Container,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow
} from "@mui/material";
import WebsocketService, { useUserSettings } from "../../Websocket/websocketClient";
import { timeToDelta } from "../GarageDoor/GarageController";
import Header from "../Header";
import './HeaterController.css';
import { UnderFloorHeaterRequest, UnderFloorHeaterResponse } from "../../Websocket/types/UnderFloorHeater";
import { Notify } from "../../Websocket/types/Subscription";
import { RpcResponse } from "../../Websocket/types/Rpc";
import { formateDateTime } from "../Utils/dateUtils";
import { FirmwareUpgradeState } from "../../Websocket/types/EnvironmentSensors";
import { FirmwareUpload } from "./FirmwareUpload";

const HeaterController = () => {
    const [underFloorHeaterStatus, setUnderFloorHeaterStatus] = useState<UnderFloorHeaterResponse | null>(null);
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [currentSeconds, setCurrentSeconds] = useState(Date.now());
    const userSettings = useUserSettings();

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds])

    const sendUpdate = (underFloorHeaterRequest: UnderFloorHeaterRequest) => {
        setSending(true);
        WebsocketService.rpc({
            type: "underFloorHeaterRequest",
            underFloorHeaterRequest: underFloorHeaterRequest
        }).then((response: RpcResponse) => {
            setCmdResult(response.underFloorHeaterResponse!!.updateUnderFloorHeaterModeSuccess!!);
            setUnderFloorHeaterStatus(response.underFloorHeaterResponse!!);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    };

    const adjustTime = () => {
        setSending(true);
        WebsocketService.rpc({
            type: "underFloorHeaterRequest",
            underFloorHeaterRequest: { type: "adjustTime" }
        }).then((response: RpcResponse) => {
            setCmdResult(response.underFloorHeaterResponse!!.adjustTimeSuccess!!);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    }

    useEffect(() => {
        const subId = WebsocketService.subscribe(
            "getUnderFloorHeaterStatus",
            (notify: Notify) => setUnderFloorHeaterStatus(notify.underFloorHeaterStatus!!),
            () => {
                WebsocketService.rpc({
                    type: "underFloorHeaterRequest",
                    underFloorHeaterRequest: { type: "getStatus" }
                }).then(response => setUnderFloorHeaterStatus(response.underFloorHeaterResponse!!));
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    if (underFloorHeaterStatus == null) {
        return <div>Loading...</div>;
    }

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title="Heater Controller"
                sending={sending}
            />

            {!underFloorHeaterStatus.firmwareUpgradeState && underFloorHeaterStatus?.underFloorHeaterStatus &&
                <>

                    <div style={{
                        display: "flex",
                        flexDirection: "row",
                        justifyContent: "space-between"
                    }}>
                        <Button
                            disabled={!userSettings.userCanWrite("heaterUnderFloor")}
                            variant="contained"
                            color={
                                underFloorHeaterStatus.underFloorHeaterStatus.mode === "constantTemperature"
                                    ? 'secondary'
                                    : 'primary'
                            }
                            onClick={() => sendUpdate({
                                type: "updateMode",
                                newMode: "constantTemperature"
                            })}>
                            Constant Temp
                        </Button>
                        <Button
                            disabled={!userSettings.userCanWrite("heaterUnderFloor")}
                            variant="contained"
                            color={
                                underFloorHeaterStatus.underFloorHeaterStatus.mode === "permanentOn"
                                    ? 'secondary'
                                    : 'primary'
                            }
                            onClick={() => sendUpdate({
                                type: "updateMode",
                                newMode: "permanentOn"
                            })}>
                            On
                        </Button>
                        <Button
                            disabled={!userSettings.userCanWrite("heaterUnderFloor")}
                            variant="contained"
                            color={
                                underFloorHeaterStatus.underFloorHeaterStatus.mode === "permanentOff"
                                    ? 'secondary'
                                    : 'primary'
                            }
                            onClick={() => sendUpdate({
                                type: "updateMode",
                                newMode: "permanentOff"
                            })}>
                            Off
                        </Button>
                    </div>

                    <TableContainer component={Paper} style={{
                        marginTop: "20px"
                    }}>
                        <Table aria-label="simple table">
                            <TableBody>
                                <TableRow>
                                    <TableCell component="th" scope="row">Current status:</TableCell>
                                    <TableCell
                                        align="right">{underFloorHeaterStatus.underFloorHeaterStatus.status}</TableCell>
                                </TableRow>
                                <TableRow>
                                    <TableCell component="th" scope="row">Clock slew:</TableCell>
                                    <TableCell
                                        align="right">{underFloorHeaterStatus.underFloorHeaterStatus.timestampDelta} seconds</TableCell>
                                </TableRow>
                                <TableRow>
                                    <TableCell component="th" scope="row">Target temperature:</TableCell>
                                    <TableCell align="right">
                                        {underFloorHeaterStatus.underFloorHeaterStatus.targetTemperature}  &deg;C <ButtonGroup
                                        variant="contained"
                                        aria-label="contained primary button group"
                                        style={{
                                            margin: "10px"
                                        }}>
                                        <Button
                                            disabled={underFloorHeaterStatus.underFloorHeaterStatus.targetTemperature <= 10 ||!userSettings.userCanWrite("heaterUnderFloor")}
                                            onClick={() => sendUpdate({
                                                type: "updateTargetTemperature",
                                                newTargetTemperature: underFloorHeaterStatus.underFloorHeaterStatus.targetTemperature - 1
                                            })}>-</Button>
                                        <Button
                                            disabled={underFloorHeaterStatus.underFloorHeaterStatus.targetTemperature >= 50 || !userSettings.userCanWrite("heaterUnderFloor")}
                                            onClick={() => sendUpdate({
                                                type: "updateTargetTemperature",
                                                newTargetTemperature: underFloorHeaterStatus.underFloorHeaterStatus.targetTemperature + 1
                                            })}>+</Button>
                                    </ButtonGroup>
                                    </TableCell>
                                </TableRow>
                                {underFloorHeaterStatus.underFloorHeaterStatus.fromController &&
                                    <>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Current temperature:</TableCell>
                                            <TableCell
                                                align="right">{underFloorHeaterStatus.underFloorHeaterStatus.fromController.currentTemperature / 100} &deg;C</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Current temperatureError:</TableCell>
                                            <TableCell
                                                align="right">{underFloorHeaterStatus.underFloorHeaterStatus.fromController.temperatureError}</TableCell>
                                        </TableRow>
                                    </>
                                }

                                <TableRow>
                                    <TableCell component="th" scope="row">Waiting for low energy price:</TableCell>
                                    <TableCell align="right">
                                        {underFloorHeaterStatus.underFloorHeaterStatus.waitUntilCheapHour ? formateDateTime(underFloorHeaterStatus.underFloorHeaterStatus.waitUntilCheapHour) : "No"}
                                    </TableCell>
                                </TableRow>
                                {underFloorHeaterStatus.underFloorHeaterStatus.fromController &&
                                    <TableRow>
                                        <TableCell component="th" scope="row">Updated:</TableCell>
                                        <TableCell
                                            align="right">{timeToDelta(currentSeconds, underFloorHeaterStatus.underFloorHeaterStatus.fromController.receivedAt)} ago</TableCell>
                                    </TableRow>
                                }

                                <TableRow>
                                    <TableCell component="th" scope="row">Admin:</TableCell>
                                    <TableCell
                                        align="right">
                                        <Button
                                            disabled={!userSettings.userCanAdmin("heaterUnderFloor")}
                                            style={{ margin: "10px" }} variant="contained" color="primary"
                                            onClick={() => adjustTime()}>
                                            Adjust time
                                        </Button>
                                        <FirmwareUpload setCmdResult={setCmdResult} setSending={setSending}/>
                                    </TableCell>
                                </TableRow>
                            </TableBody>
                        </Table>
                    </TableContainer>

                </>
            }

            {underFloorHeaterStatus.firmwareUpgradeState &&
                <TableContainer component={Paper} style={{
                    marginTop: "20px"
                }}>
                    <Table aria-label="simple table">
                        <TableBody>
                            <TableRow>
                                <TableCell component="th" scope="row">Progress:</TableCell>
                                <TableCell
                                    align="right">{firmwareUpgradeProgress(underFloorHeaterStatus.firmwareUpgradeState)}%</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">Clock slew:</TableCell>
                                <TableCell
                                    align="right">{underFloorHeaterStatus.firmwareUpgradeState.timestampDelta} seconds</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">Received:</TableCell>
                                <TableCell
                                    align="right">{timeToDelta(currentSeconds, underFloorHeaterStatus.firmwareUpgradeState.receivedAt)} ago</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">Rssi:</TableCell>
                                <TableCell
                                    align="right">{underFloorHeaterStatus.firmwareUpgradeState.rssi}dB</TableCell>
                            </TableRow>
                        </TableBody>
                    </Table>
                </TableContainer>
            }

            {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
            {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}
        </Container>
    );
};

const firmwareUpgradeProgress = (firmwareUpgradeState: FirmwareUpgradeState) => {
    return (firmwareUpgradeState!!.offsetRequested * 100 / firmwareUpgradeState!!.firmwareSize).toFixed(2);
}

export default HeaterController;
