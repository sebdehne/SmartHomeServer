import React, { useState } from "react";
import {
    ChargingState,
    EvChargingMode,
    EvChargingStationClient,
    EvChargingStationDataAndConfig,
    EvChargingStationRequest,
    LoadSharingPriority,
    ProximityPilotAmps
} from "../../Websocket/types/EVChargingStation";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Grid,
    Link,
    Paper,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow
} from "@mui/material";
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import WebsocketService, { useUserSettings } from "../../Websocket/websocketClient";
import { timeToDelta } from "../GarageDoor/GarageController";
import { FirmwareUpload } from "./FirmwareUpload";

const stateToText = (state: ChargingState, reasonChargingUnavailable: string | null) => {
    if (state === "Unconnected") {
        return "No EV connected";
    }
    if (state === "ConnectedChargingUnavailable") {
        return "EV connected: " + reasonChargingUnavailable;
    }
    if (state === "ConnectedChargingAvailable") {
        return "EV connected, ready";
    }
    return state.toString()
};

type EvChargingStationProps = {
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
    station: EvChargingStationDataAndConfig;
    setStations: (stations: EvChargingStationDataAndConfig[]) => void;
    currentMilliSeconds: number;
};

export const EvChargingStation = ({
                                      setSending,
                                      setCmdResult,
                                      station,
                                      setStations,
                                      currentMilliSeconds
                                  }: EvChargingStationProps) => {

    const [showData, setShowData] = useState<boolean>(false);
    const userSettings = useUserSettings();

    const sendUpdate = (req: EvChargingStationRequest) => {
        setSending(true);
        WebsocketService.rpc({
            type: "evChargingStationRequest",
            evChargingStationRequest: req
        })
            .then(response => {
                setCmdResult(response.evChargingStationResponse!!.configUpdated!!);
                setStations(response.evChargingStationResponse!!.chargingStationsDataAndConfig);
                setTimeout(() => {
                    setCmdResult(null);
                }, 2000);
            })
            .finally(() => setSending(false));
    };

    const updateModeTo = (mode: EvChargingMode) => sendUpdate({
        type: "setMode",
        clientId: station.clientConnection.clientId,
        newMode: mode
    });
    const updatePriorityTo = (priority: LoadSharingPriority) => sendUpdate({
        type: "setLoadSharingPriority",
        clientId: station.clientConnection.clientId,
        newLoadSharingPriority: priority
    });
    const setChargeRateLimit = (delta: number) => sendUpdate({
        type: "setChargeRateLimit",
        clientId: station.clientConnection.clientId,
        chargeRateLimit: station.config.chargeRateLimit + delta
    });

    return (
        <Accordion key={station.clientConnection.clientId}>
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
                        }>{station.clientConnection.displayName}</span> - {stateToText(station.data.chargingState, station.data.reasonChargingUnavailable)}
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
                                    disabled={!userSettings.userCanWrite("evCharging")}
                                    color={station.config.mode === "ON" ? 'secondary' : 'primary'}
                                    onClick={() => updateModeTo("ON")}>On</Button>
                                <Button
                                    disabled={!userSettings.userCanWrite("evCharging")}
                                    color={station.config.mode === "OFF" ? 'secondary' : 'primary'}
                                        onClick={() => updateModeTo("OFF")}>Off</Button>
                                <Button
                                    disabled={!userSettings.userCanWrite("evCharging")}
                                    color={station.config.mode === "ChargeDuringCheapHours" ? 'secondary' : 'primary'}
                                    onClick={() => updateModeTo("ChargeDuringCheapHours")}
                                >Low-cost</Button>
                            </ButtonGroup>
                            <Link
                                href={"https://dehnes.com/stats/d/dYYFH4_Mk/ev-charging?orgId=1&refresh=1m&var-clientId=" + station.clientConnection.clientId}>
                                <Button color="primary" variant="contained" style={{
                                    margin: "10px"
                                }}>Stats</Button>
                            </Link>
                        </Grid>
                    </Grid>
                    <Grid item xs={12}>
                        <Grid container justifyContent="flex-start" spacing={2}>

                            <ButtonGroup variant="contained" style={{
                                margin: "10px"
                            }}>
                                <Button
                                    disabled={!userSettings.userCanWrite("evCharging")}
                                    color={station.config.loadSharingPriority === "HIGH" ? 'secondary' : 'primary'}
                                    onClick={() => updatePriorityTo("HIGH")}>High priority</Button>
                                <Button
                                    disabled={!userSettings.userCanWrite("evCharging")}
                                    color={station.config.loadSharingPriority === "NORMAL" ? 'secondary' : 'primary'}
                                    onClick={() => updatePriorityTo("NORMAL")}>Normal priority</Button>
                                <Button
                                    disabled={!userSettings.userCanWrite("evCharging")}
                                    color={station.config.loadSharingPriority === "LOW" ? 'secondary' : 'primary'}
                                    onClick={() => updatePriorityTo("LOW")}>Low priority</Button>
                            </ButtonGroup>

                        </Grid>
                    </Grid>

                    <Grid item xs={12}>
                        <Grid container justifyContent="flex-start" spacing={2} alignItems={"center"}>

                            <Grid item xs={4}>
                                <span>Charge limit in Amps: </span>
                                <span>{station.config.chargeRateLimit}</span>
                            </Grid>
                            <Grid item xs={8}>
                                <ButtonGroup variant="contained" aria-label="contained primary button group" style={{
                                    margin: "10px"
                                }}>
                                    <Button
                                        disabled={!userSettings.userCanWrite("evCharging") || station.config.chargeRateLimit <= 6}
                                        onClick={() => setChargeRateLimit(-1)}>-</Button>
                                    <Button
                                        disabled={!userSettings.userCanWrite("evCharging") || station.config.chargeRateLimit >= 32}
                                        onClick={() => setChargeRateLimit(1)}>+</Button>
                                </ButtonGroup>
                            </Grid>
                        </Grid>
                    </Grid>

                    <Grid item xs={12}>
                        <Grid container justifyContent="flex-start" spacing={2} alignItems={"center"}>
                            <Switch
                                checked={showData}
                                onChange={e => setShowData(e.target.checked)}
                                name="showData"
                                inputProps={{ 'aria-label': 'secondary checkbox' }}
                            />
                            {showData && 'Hide details'}
                            {!showData && 'Show details'}
                        </Grid>
                    </Grid>

                    {showData &&
                        <>
                            <Grid item xs={12}>
                                <Grid container justifyContent="flex-start" spacing={2} alignItems={"center"}>
                                    <PowerComponent
                                        phase1volts={station.data.phase1Millivolts / 1000}
                                        phase2volts={station.data.phase2Millivolts / 1000}
                                        phase3volts={station.data.phase3Millivolts / 1000}
                                        phase1amps={station.data.phase1Milliamps / 1000}
                                        phase2amps={station.data.phase2Milliamps / 1000}
                                        phase3amps={station.data.phase3Milliamps / 1000}
                                    />
                                </Grid>
                            </Grid>

                            <Grid item xs={12}>
                                <Grid container justifyContent="flex-start" spacing={2} alignItems={"center"}>
                                    <StationsDetails
                                        maxChargingRate={station.data.maxChargingRate}
                                        proximityPilotAmps={station.data.proximityPilotAmps}
                                        reasonChargingUnavailable={station.data.reasonChargingUnavailable}
                                        systemUptime={station.data.systemUptime}
                                        utcTimestampInMs={station.data.utcTimestampInMs}
                                        wifiRSSI={station.data.wifiRSSI}
                                        currentSeconds={currentMilliSeconds}
                                        clientConnection={station.clientConnection}
                                        setSending={setSending}
                                        setCmdResult={setCmdResult}
                                    />
                                </Grid>
                            </Grid>
                        </>
                    }

                </Grid>
            </AccordionDetails>
        </Accordion>
    );
};

type StationsDetailsProps = {
    proximityPilotAmps: ProximityPilotAmps;
    reasonChargingUnavailable: string | null;
    maxChargingRate: number;
    systemUptime: number;
    wifiRSSI: number;
    utcTimestampInMs: number;
    currentSeconds: number;
    clientConnection: EvChargingStationClient;
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
};

const StationsDetails = (props: StationsDetailsProps) => {
    return <TableContainer component={Paper} style={{
        marginTop: "20px"
    }}>
        <Table aria-label="simple table">
            <TableBody>
                <TableRow>
                    <TableCell component="th" scope="row">Connected cable type</TableCell>
                    <TableCell align="right">{props.proximityPilotAmps}</TableCell>
                </TableRow>
                {props.reasonChargingUnavailable && <TableRow>
                    <TableCell component="th" scope="row">reason unavailable</TableCell>
                    <TableCell align="right">{props.reasonChargingUnavailable}</TableCell>
                </TableRow>}
                <TableRow>
                    <TableCell component="th" scope="row">Max charging rate</TableCell>
                    <TableCell align="right">{props.maxChargingRate}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">System Uptime</TableCell>
                    <TableCell align="right">{Math.round(props.systemUptime / 1000 / 60)} minutes</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">WiFi RSSI</TableCell>
                    <TableCell align="right">{props.wifiRSSI}dB</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Connected power line</TableCell>
                    <TableCell align="right">{props.clientConnection.powerConnectionId}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">TCP endpoint</TableCell>
                    <TableCell align="right">{props.clientConnection.addr}:{props.clientConnection.port}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">TCP connection uptime</TableCell>
                    <TableCell
                        align="right">{timeToDelta(props.currentSeconds, props.clientConnection.connectedSince)}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Updated</TableCell>
                    <TableCell align="right">{timeToDelta(props.currentSeconds, props.utcTimestampInMs)} ago</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Firmware version</TableCell>
                    <TableCell align="right">{props.clientConnection.firmwareVersion}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Firmware upgrade</TableCell>
                    <TableCell align="right">
                        <FirmwareUpload
                            setSending={props.setSending}
                            setCmdResult={props.setCmdResult}
                            clientId={props.clientConnection.clientId}/>
                    </TableCell>
                </TableRow>

            </TableBody>
        </Table>
    </TableContainer>;
};

type PowerComponentProps = {
    phase1volts: number,
    phase2volts: number,
    phase3volts: number,
    phase1amps: number,
    phase2amps: number,
    phase3amps: number
};

const PowerComponent = (props: PowerComponentProps) => {

    let totalPower = props.phase1volts * props.phase1amps + props.phase2volts * props.phase2amps + props.phase3volts * props.phase3amps;

    return <TableContainer component={Paper} style={{
        marginTop: "20px"
    }}>
        <Table aria-label="simple table">
            <TableHead>
                <TableRow>
                    <TableCell/>
                    <TableCell align="right">Voltage (V)</TableCell>
                    <TableCell align="right">Current (A)</TableCell>
                    <TableCell align="right">Power (Watt)</TableCell>
                </TableRow>
            </TableHead>
            <TableBody>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Total
                    </TableCell>
                    <TableCell align="right"/>
                    <TableCell align="right"/>
                    <TableCell
                        align="right">{totalPower.toFixed(2)}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Phase 1
                    </TableCell>
                    <TableCell align="right">{props.phase1volts.toFixed(2)}</TableCell>
                    <TableCell align="right">{props.phase1amps.toFixed(2)}</TableCell>
                    <TableCell align="right">{(props.phase1amps * props.phase1volts).toFixed(2)}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Phase 2
                    </TableCell>
                    <TableCell align="right">{props.phase2volts.toFixed(2)}</TableCell>
                    <TableCell align="right">{props.phase2amps.toFixed(2)}</TableCell>
                    <TableCell align="right">{(props.phase2amps * props.phase2volts).toFixed(2)}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Phase 3
                    </TableCell>
                    <TableCell align="right">{props.phase3volts.toFixed(2)}</TableCell>
                    <TableCell align="right">{props.phase3amps.toFixed(2)}</TableCell>
                    <TableCell align="right">{(props.phase3amps * props.phase3volts).toFixed(2)}</TableCell>
                </TableRow>
            </TableBody>
        </Table>
    </TableContainer>;
};