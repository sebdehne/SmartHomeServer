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
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { timeToDelta } from "../GarageDoor/GarageDoor";
import { useUserSettings } from "../../Websocket/websocketClient";

const stateToText = (currentMilliSeconds: number, sensor: EnvironmentSensorState) => {
    if (sensor.firmwareUpgradeState != null) {
        return <span>{"Firmware upgrade: " + firmwareUpgradeProgress(sensor) + "%"}</span>
    } else if (sensor.sensorData != null) {
        return <span>{(sensor.sensorData!!.temperature / 100).toFixed(2)} &deg;C</span>
    }

    return <span/>;
}

export enum SensorStatus {
    green = "green",
    yellow = "yellow",
    red = "red"
}

const statusIcon = (sensor: EnvironmentSensorState, currentMilliSeconds: number) => {
    const status = getSensorStatus(sensor, currentMilliSeconds);
    let color;
    if (status === SensorStatus.yellow) {
        color = "#ffe61d"
    } else if (status === SensorStatus.red) {
        color = "#ff0000"
    } else {
        color = "#00ff07";
    }
    return <span style={{ color: color }}> &#11044;</span>;
};
export const getSensorStatus = (sensor: EnvironmentSensorState, currentMilliSeconds: number) => {
    let status = SensorStatus.green;
    const receivedAt: number = sensor.sensorData?.receivedAt || sensor.firmwareUpgradeState?.receivedAt || 0;
    const deltaSeconds = (currentMilliSeconds - receivedAt) / 1000;

    if (sensor.sensorData != null) {
        if (sensor.sensorData!!.timestampDelta > 10 || sensor.sensorData!!.timestampDelta < -10) {
            status = SensorStatus.yellow;
        }
        if (sensor.sensorData!!.batteryMilliVolts < 3200) {
            status = SensorStatus.yellow;
        }
        if (sensor.sensorData!!.temperatureError) {
            status = SensorStatus.yellow;
        }
    }

    if (deltaSeconds > sensor.sleepTimeInSeconds) {
        status = SensorStatus.red;
    }

    return status;
};

const firmwareUpgradeProgress = (sensor: EnvironmentSensorState) => {
    if (sensor.firmwareUpgradeState != null) {
        return (sensor.firmwareUpgradeState!!.offsetRequested * 100 / sensor.firmwareUpgradeState!!.firmwareSize).toFixed(2);
    } else {
        return null;
    }
}

type EnvironmentSensorProps = {
    sensor: EnvironmentSensorState;
    currentMilliSeconds: number;
    firmwareInfo?: FirmwareInfo;
    sendUpdate: (req: EnvironmentSensorRequest) => void;
};

export const EnvironmentSensor = ({
                                      sensor,
                                      currentMilliSeconds,
                                      firmwareInfo,
                                      sendUpdate
                                  }: EnvironmentSensorProps) => {

    const userSettings = useUserSettings();

    const sendCommand = (cmd: EnvironmentSensorRequestType) => sendUpdate({
        type: cmd,
        sensorId: sensor.sensorId
    });
    const adjustSleepTimeInSeconds = (delta: number) => sendUpdate({
        type: "adjustSleepTimeInSeconds",
        sensorId: sensor.sensorId,
        sleepTimeInSecondsDelta: delta
    });

    return (
        <Accordion key={sensor.sensorId}>
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
                        {statusIcon(sensor, currentMilliSeconds)}
                    </div>
                </div>
            </AccordionSummary>
            <AccordionDetails>

                <Grid container spacing={2}>
                    <Grid item xs={12}>
                        <Grid container justifyContent="space-between" spacing={2}>
                            <ButtonGroup variant="contained" style={{
                                margin: "10px"
                            }}>
                                <Button
                                    disabled={!userSettings.userCanAdmin("environmentSensors")}
                                    color={sensor.timeAdjustmentSchedule ? 'secondary' : 'primary'}
                                    onClick={() =>
                                        sensor.timeAdjustmentSchedule
                                            ? sendCommand("cancelTimeAdjustment")
                                            : sendCommand("scheduleTimeAdjustment")
                                    }
                                >Adjust time</Button>
                                <Button
                                    disabled={!firmwareInfo?.filename || !userSettings.userCanAdmin("environmentSensors")}
                                    color={sensor.firmwareUpgradeScheduled ? 'secondary' : 'primary'}
                                    onClick={() =>
                                        sensor.firmwareUpgradeScheduled
                                            ? sendCommand("cancelFirmwareUpgrade")
                                            : sendCommand("scheduleFirmwareUpgrade")
                                    }
                                >Upgrade firmware</Button>
                                <Button
                                    disabled={!userSettings.userCanAdmin("environmentSensors")}
                                    color={sensor.resetScheduled ? 'secondary' : 'primary'}
                                    onClick={() =>
                                        sensor.resetScheduled
                                            ? sendCommand("cancelReset")
                                            : sendCommand("scheduleReset")
                                    }
                                >Reset</Button>
                            </ButtonGroup>
                        </Grid>
                    </Grid>
                    {sensor.sensorData &&
                        <>
                            <Grid item xs={12}>
                                <Grid container justifyContent="flex-start" spacing={2} alignItems={"center"}>

                                    <Grid item xs={8}>
                                        <span>Set sleep time: </span>
                                        <span>{sensor.sleepTimeInSeconds} seconds</span>
                                    </Grid>
                                    <Grid item xs={4}>
                                        <ButtonGroup variant="contained" aria-label="contained primary button group"
                                                     style={{
                                                         margin: "10px"
                                                     }}>
                                            <Button
                                                disabled={sensor.sleepTimeInSeconds < 11 || !userSettings.userCanAdmin("environmentSensors")}
                                                onClick={() => adjustSleepTimeInSeconds(-10)}>-</Button>
                                            <Button
                                                disabled={sensor.sleepTimeInSeconds >= 590 || !userSettings.userCanAdmin("environmentSensors")}
                                                onClick={() => adjustSleepTimeInSeconds(10)}>+</Button>
                                        </ButtonGroup>
                                    </Grid>
                                </Grid>
                            </Grid>

                            <Grid item xs={12}>
                                <Grid container justifyContent="flex-start" spacing={2} alignItems={"center"}>
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
                                                    <TableCell component="th" scope="row">Temperature Error</TableCell>
                                                    <TableCell
                                                        align="right">{sensor.sensorData.temperatureError ? 'true' : 'false'}</TableCell>
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
                            <Grid container justifyContent="flex-start" spacing={2} alignItems={"center"}>
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
        </Accordion>
    );
};