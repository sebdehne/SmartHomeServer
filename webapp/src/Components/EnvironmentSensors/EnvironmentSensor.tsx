import {
    EnvironmentSensorRequest,
    EnvironmentSensorRequestType,
    EnvironmentSensorState
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
        return "Upgrade: " + firmwareUpgradeProgress(sensor) + "% completed";
    } else if (sensor.sensorData != null) {
        return timeToDelta(currentMilliSeconds, sensor.sensorData!!.receivedAt) + " ago";
    }
    return "";
}

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
};

export const EnvironmentSensor = ({
                                      setSending,
                                      setCmdResult,
                                      sensor,
                                      setSensors,
                                      currentMilliSeconds
                                  }: EnvironmentSensorProps) => {

    const sendUpdate = (req: EnvironmentSensorRequest) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.evChargingStationRequest,
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
    const scheduleTimeAdjustment = () => sendUpdate(new EnvironmentSensorRequest(
        EnvironmentSensorRequestType.scheduleTimeAdjustment,
        sensor.sensorId,
        null,
        null,
        null
    ));
    const cancelTimeAdjustment = () => sendUpdate(new EnvironmentSensorRequest(
        EnvironmentSensorRequestType.cancelTimeAdjustment,
        sensor.sensorId,
        null,
        null,
        null
    ));
    const scheduleFirmwareUpgrade = () => sendUpdate(new EnvironmentSensorRequest(
        EnvironmentSensorRequestType.scheduleFirmwareUpgrade,
        sensor.sensorId,
        null,
        null,
        null
    ));
    const cancelFirmwareUpgrade = () => sendUpdate(new EnvironmentSensorRequest(
        EnvironmentSensorRequestType.cancelFirmwareUpgrade,
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
        sensor.sensorData!!.sleepTimeInSeconds + delta
    ));

    return <Accordion key={sensor.sensorId}>
        <AccordionSummary
            expandIcon={<ExpandMoreIcon/>}
            aria-controls="panel1a-content"
            id="panel1a-header"
        >
            <div>
                <div>
                    <span style={{
                        fontWeight: "bold",
                        fontSize: "140%"
                    }
                    }>{sensor.displayName}</span> - {stateToText(currentMilliSeconds, sensor)}
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
                            {sensor.timeAdjustmentSchedule &&
                            <Button color='secondary' onClick={() => cancelTimeAdjustment()}>Adjust time</Button>
                            }
                            {!sensor.timeAdjustmentSchedule &&
                            <Button color='primary' onClick={() => scheduleTimeAdjustment()}>Adjust time</Button>
                            }
                            {sensor.firmwareUpgradeScheduled &&
                            <Button color='secondary' onClick={() => cancelFirmwareUpgrade()}>Upgrade firmware</Button>
                            }
                            {!sensor.firmwareUpgradeScheduled &&
                            <Button color='primary' onClick={() => scheduleFirmwareUpgrade()}>Upgrade firmware</Button>
                            }
                        </ButtonGroup>
                    </Grid>
                </Grid>
                {sensor.sensorData &&
                <>
                    <Grid item xs={12}>
                        <Grid container justify="flex-start" spacing={2} alignItems={"center"}>

                            <Grid item xs={4}>
                                <span>Sleep seconds: </span>
                                <span>{sensor.sleepTimeInSeconds}</span>
                            </Grid>
                            <Grid item xs={8}>
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
                                            <TableCell align="right">{sensor.sensorData!!.temperature}</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Humidity</TableCell>
                                            <TableCell align="right">{sensor.sensorData!!.humidity}</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Light</TableCell>
                                            <TableCell align="right">{sensor.sensorData!!.adcLight}</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Battery</TableCell>
                                            <TableCell align="right">{sensor.sensorData!!.batteryMilliVolts}</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell component="th" scope="row">Sleep seconds</TableCell>
                                            <TableCell align="right">{sensor.sensorData!!.sleepTimeInSeconds}</TableCell>
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