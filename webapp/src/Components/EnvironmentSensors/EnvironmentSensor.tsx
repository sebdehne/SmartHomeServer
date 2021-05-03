import {
    EnvironmentSensorRequest,
    EnvironmentSensorRequestType,
    EnvironmentSensorState,
    FirmwareInfo
} from "../../Websocket/types/EnvironmentSensors";
import React from "react";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Grid,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow
} from "@material-ui/core";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import WebsocketService from "../../Websocket/websocketClient";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import { timeToDelta } from "../GarageDoor/GarageDoor";

const stateToText = (currentMilliSeconds: number, sensor: EnvironmentSensorState) => {
    if (sensor.firmwareUpgradeState != null) {
        return <span>{"Firmware upgrade: " + firmwareUpgradeProgress(sensor) + "%"}</span>
    } else if (sensor.sensorData != null) {
        return <span>{(sensor.sensorData!!.temperature / 100).toFixed(2)} &deg;C</span>
    }

    return <span/>;
}

export const isAlive = (sensor: EnvironmentSensorState, currentMilliSeconds: number) => {
    const receivedAt: number = sensor.sensorData?.receivedAt || sensor.firmwareUpgradeState?.receivedAt || 0;
    const deltaSeconds = (currentMilliSeconds - receivedAt) / 1000;
    return deltaSeconds <= sensor.sleepTimeInSeconds;
};

const firmwareUpgradeProgress = (sensor: EnvironmentSensorState) => {
    if (sensor.firmwareUpgradeState != null) {
        return (sensor.firmwareUpgradeState!!.offsetRequested * 100 / sensor.firmwareUpgradeState!!.firmwareSize).toFixed(2);
    } else {
        return null;
    }
}

type EnvironmentSensorProps = {
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
    sensor: EnvironmentSensorState;
    setSensors: (sensors: EnvironmentSensorState[]) => void;
    currentMilliSeconds: number;
    firmwareInfo: FirmwareInfo | null;
};

export const EnvironmentSensor = ({
                                      setSending,
                                      setCmdResult,
                                      sensor,
                                      setSensors,
                                      currentMilliSeconds,
                                      firmwareInfo
                                  }: EnvironmentSensorProps) => {

    const sendUpdate = (req: EnvironmentSensorRequest) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.environmentSensorRequest,
            null,
            null,
            null,
            null,
            null,
            req))
            .then(response => {
                setCmdResult(true);
                setSensors(response.environmentSensorResponse!!.sensors);
                setTimeout(() => {
                    setCmdResult(null);
                }, 2000);
            })
            .finally(() => setSending(false));
    };
    const sendCommand = (cmd: EnvironmentSensorRequestType) => sendUpdate(new EnvironmentSensorRequest(
        cmd,
        sensor.sensorId,
        null,
        null,
        null
    ));
    const adjustSleepTimeInSeconds = (delta: number) => sendUpdate(new EnvironmentSensorRequest(
        EnvironmentSensorRequestType.adjustSleepTimeInSeconds,
        sensor.sensorId,
        null,
        null,
        delta
    ));

    return <Accordion key={sensor.sensorId}>
        <AccordionSummary
            expandIcon={<ExpandMoreIcon/>}
            aria-controls="panel1a-content"
            id="panel1a-header"
        >
            <div style={{
                display: "flex",
                flexDirection: "row",
                alignItems: "center",
                width: "100%",
                justifyContent: "space-between"
            }}>
                <div style={{
                    fontWeight: "bold",
                    fontSize: "140%"
                }}>{sensor.displayName}</div>
                <div>
                    {stateToText(currentMilliSeconds, sensor)}
                    <span
                        style={isAlive(sensor, currentMilliSeconds)
                            ? { color: "#00ff07" }
                            : { color: "#ff0000" }
                        }
                    > &#11044;</span>
                </div>
            </div>
        </AccordionSummary>
        <AccordionDetails>

            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <Grid container justify="space-between" spacing={2}>
                        <ButtonGroup variant="contained" style={{
                            margin: "10px"
                        }}>
                            <Button
                                color={sensor.timeAdjustmentSchedule ? 'secondary' : 'primary'}
                                onClick={() =>
                                    sensor.timeAdjustmentSchedule
                                        ? sendCommand(EnvironmentSensorRequestType.cancelTimeAdjustment)
                                        : sendCommand(EnvironmentSensorRequestType.scheduleTimeAdjustment)
                                }
                            >Adjust time</Button>
                            <Button
                                disabled={!firmwareInfo?.filename}
                                color={sensor.firmwareUpgradeScheduled ? 'secondary' : 'primary'}
                                onClick={() =>
                                    sensor.firmwareUpgradeScheduled
                                        ? sendCommand(EnvironmentSensorRequestType.cancelFirmwareUpgrade)
                                        : sendCommand(EnvironmentSensorRequestType.scheduleFirmwareUpgrade)
                                }
                            >Upgrade firmware</Button>
                        </ButtonGroup>
                    </Grid>
                </Grid>
                {sensor.sensorData &&
                <>
                    <Grid item xs={12}>
                        <Grid container justify="flex-start" spacing={2} alignItems={"center"}>

                            <Grid item xs={8}>
                                <span>Set sleep time: </span>
                                <span>{sensor.sleepTimeInSeconds} seconds</span>
                            </Grid>
                            <Grid item xs={4}>
                                <ButtonGroup variant="contained" aria-label="contained primary button group" style={{
                                    margin: "10px"
                                }}>
                                    <Button
                                        disabled={sensor.sleepTimeInSeconds < 11}
                                        onClick={() => adjustSleepTimeInSeconds(-10)}>-</Button>
                                    <Button
                                        disabled={sensor.sleepTimeInSeconds >= 590}
                                        onClick={() => adjustSleepTimeInSeconds(10)}>+</Button>
                                </ButtonGroup>
                            </Grid>
                        </Grid>
                    </Grid>

                    <Grid item xs={12}>
                        <Grid container justify="flex-start" spacing={2} alignItems={"center"}>
                            <TableContainer component={Paper} style={{
                                marginTop: "20px"
                            }}>
                                <Table aria-label="simple table">
                                    <TableBody>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Temperature</TableCell>
                                            <TableCell
                                                align="right">{(sensor.sensorData!!.temperature / 100).toFixed(2)} &deg;C</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Humidity</TableCell>
                                            <TableCell
                                                align="right">{(sensor.sensorData!!.humidity / 100).toFixed(2)} %</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Light</TableCell>
                                            <TableCell align="right">{sensor.sensorData!!.adcLight}</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Battery</TableCell>
                                            <TableCell
                                                align="right">{(sensor.sensorData!!.batteryMilliVolts / 1000).toFixed(2)} V</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Actual sleep time</TableCell>
                                            <TableCell
                                                align="right">{sensor.sensorData!!.sleepTimeInSeconds} seconds</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Firmware version</TableCell>
                                            <TableCell align="right">{sensor.firmwareVersion}</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Clock slew</TableCell>
                                            <TableCell
                                                align="right">{sensor.sensorData!!.timestampDelta} seconds</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Received:</TableCell>
                                            <TableCell
                                                align="right">{timeToDelta(currentMilliSeconds, sensor.sensorData!!.receivedAt)} ago</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Rssi:</TableCell>
                                            <TableCell
                                                align="right">{sensor.sensorData!!.rssi}dB</TableCell>
                                        </TableRow>
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Grid>
                    </Grid>
                </>
                }

                {sensor.firmwareUpgradeState &&
                <Grid item xs={12}>
                    <Grid container justify="flex-start" spacing={2} alignItems={"center"}>
                        <TableContainer component={Paper} style={{
                            marginTop: "20px"
                        }}>
                            <Table aria-label="simple table">
                                <TableBody>
                                    <TableRow>
                                        <TableCell component="th" scope="row">Progress:</TableCell>
                                        <TableCell align="right">{firmwareUpgradeProgress(sensor)}%</TableCell>
                                    </TableRow>
                                    <TableRow>
                                        <TableCell component="th" scope="row">Clock slew</TableCell>
                                        <TableCell
                                            align="right">{sensor.firmwareUpgradeState!!.timestampDelta} seconds</TableCell>
                                    </TableRow>
                                    <TableRow>
                                        <TableCell component="th" scope="row">Received:</TableCell>
                                        <TableCell
                                            align="right">{timeToDelta(currentMilliSeconds, sensor.firmwareUpgradeState!!.receivedAt)} ago</TableCell>
                                    </TableRow>
                                    <TableRow>
                                        <TableCell component="th" scope="row">Rssi:</TableCell>
                                        <TableCell
                                            align="right">{sensor.firmwareUpgradeState!!.rssi}dB</TableCell>
                                    </TableRow>
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </Grid>
                </Grid>
                }

            </Grid>
        </AccordionDetails>
    </Accordion>;
};